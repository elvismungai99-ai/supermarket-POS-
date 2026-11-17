package ke.retailpos.pos_backend.providers.payment;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.sales.Payment;
import ke.retailpos.pos_backend.sales.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/payments")
public class MpesaCallbackController {

  private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);

  private final PaymentRepository paymentRepository;
  private final PaymentProvider paymentProvider;
  private final ObjectMapper objectMapper;
  private final DarajaProperties darajaProperties;
  private final AuditLogService auditLogService;

  public MpesaCallbackController(
      PaymentRepository paymentRepository,
      PaymentProvider paymentProvider,
      ObjectMapper objectMapper
  ) {
    this(paymentRepository, paymentProvider, objectMapper, new DarajaProperties(), null);
  }

  @Autowired
  public MpesaCallbackController(
      PaymentRepository paymentRepository,
      PaymentProvider paymentProvider,
      ObjectMapper objectMapper,
      DarajaProperties darajaProperties,
      AuditLogService auditLogService
  ) {
    this.paymentRepository = paymentRepository;
    this.paymentProvider = paymentProvider;
    this.objectMapper = objectMapper;
    this.darajaProperties = darajaProperties;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/mpesa-callback")
  @Transactional
  public ResponseEntity<Map<String, Object>> handleDarajaCallback(@RequestBody String rawPayload) {
    String clientIp = AuditLogService.currentClientIp();
    if (!isAllowedCallbackIp(clientIp)) {
      log.warn("Rejected M-Pesa callback from unauthorized IP: {}", clientIp);
      if (auditLogService != null) {
        auditLogService.record(null, "daraja-webhook", "SYSTEM", "MPESA_CALLBACK_REJECTED_IP", "payment", null, clientIp, "Unauthorized IP");
      }
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", 1, "ResultDesc", "Forbidden IP"));
    }

    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      JsonNode stkCallback = root.path("Body").path("stkCallback");
      String checkoutRequestId = stkCallback.path("CheckoutRequestID").asText("");
      int resultCode = stkCallback.path("ResultCode").asInt(-1);
      String resultDesc = stkCallback.path("ResultDesc").asText("");

      String mpesaReceiptNumber = null;
      BigDecimal paidAmount = null;
      JsonNode items = stkCallback.path("CallbackMetadata").path("Item");
      if (items.isArray()) {
        for (JsonNode item : items) {
          String name = item.path("Name").asText("");
          if ("MpesaReceiptNumber".equals(name)) {
            mpesaReceiptNumber = item.path("Value").asText();
          } else if ("Amount".equals(name)) {
            paidAmount = new BigDecimal(item.path("Value").asText("0"));
          }
        }
      }

      if (!checkoutRequestId.isBlank()) {
        var opt = paymentRepository.findByReference(checkoutRequestId);
        if (opt.isPresent()) {
          Payment p = opt.get();
          // Idempotent guard: only update PENDING payments
          if (!"PENDING".equalsIgnoreCase(p.getState())) {
            log.info("Ignoring duplicate Daraja callback for finalized payment {}", checkoutRequestId);
            return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
          }

          if (resultCode == 0) {
            // Verify paid amount matches expected payable amount
            if (paidAmount != null && paidAmount.compareTo(p.getAmountKes()) < 0) {
              log.error("Daraja callback amount mismatch for {}: paid={} expected={}",
                  checkoutRequestId, paidAmount, p.getAmountKes());
              p.setState("FAILED");
              paymentRepository.save(p);
              if (auditLogService != null) {
                auditLogService.record(null, "daraja-webhook", "SYSTEM", "MPESA_AMOUNT_MISMATCH", "payment", p.getId().toString(), clientIp,
                    "Paid " + paidAmount + " < expected " + p.getAmountKes());
              }
              return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
            }

            p.setState("PAID");
            if (mpesaReceiptNumber != null && !mpesaReceiptNumber.isBlank()) {
              p.setReference(mpesaReceiptNumber);
            }
          } else {
            p.setState("FAILED");
          }
          paymentRepository.save(p);
          log.info("Updated M-Pesa payment {} -> state={} desc={}", checkoutRequestId, p.getState(), resultDesc);
        }
      }
    } catch (Exception e) {
      log.warn("Error parsing Daraja callback payload: {}", e.getMessage());
    }

    return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
  }

  @GetMapping("/status/{reference}")
  public ResponseEntity<PaymentProvider.PaymentResult> checkPaymentStatus(@PathVariable String reference) {
    return ResponseEntity.ok(paymentProvider.checkStatus(reference));
  }

  private boolean isAllowedCallbackIp(String clientIp) {
    String configured = darajaProperties != null ? darajaProperties.getAllowedCallbackIps() : "";
    if (configured == null || configured.isBlank()) {
      return true;
    }
    if (clientIp == null || clientIp.isBlank()) {
      return false;
    }
    Set<String> allowed = Arrays.stream(configured.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
    return allowed.contains(clientIp);
  }
}
