package ke.retailpos.pos_backend.providers.payment;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
  public ResponseEntity<Map<String, Object>> handleDarajaCallback(
      @RequestBody String rawPayload,
      @RequestParam(value = "token", required = false) String callbackToken
  ) {
    String clientIp = AuditLogService.currentClientIp();
    if (!isAllowedCallbackIp(clientIp)) {
      log.warn("Rejected M-Pesa callback from unauthorized IP: {}", clientIp);
      if (auditLogService != null) {
        auditLogService.record(null, "daraja-webhook", "SYSTEM", "MPESA_CALLBACK_REJECTED_IP", "payment", null, clientIp, "Unauthorized IP");
      }
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", 1, "ResultDesc", "Forbidden IP"));
    }

    if (!isValidCallbackSecret(callbackToken)) {
      log.warn("Rejected M-Pesa callback with invalid or missing callback token from IP: {}", clientIp);
      if (auditLogService != null) {
        auditLogService.record(null, "daraja-webhook", "SYSTEM", "MPESA_CALLBACK_REJECTED_TOKEN", "payment", null, clientIp, "Invalid callback token");
      }
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ResultCode", 1, "ResultDesc", "Invalid callback token"));
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
          // Idempotent guard: only update PENDING or PENDING_STK payments
          if (!"PENDING".equalsIgnoreCase(p.getState()) && !"PENDING_STK".equalsIgnoreCase(p.getState())) {
            log.info("Ignoring duplicate Daraja callback for finalized payment {}", checkoutRequestId);
            return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
          }

          if (resultCode == 0) {
            // Fail-closed: require paidAmount to be present and >= expected payable amount
            if (paidAmount == null || paidAmount.compareTo(p.getAmountKes()) < 0) {
              log.error("Daraja callback amount missing or mismatch for {}: paid={} expected={}",
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

  public ResponseEntity<Map<String, Object>> handleDarajaCallback(String rawPayload) {
    return handleDarajaCallback(rawPayload, null);
  }

  @GetMapping("/status/{reference}")
  public ResponseEntity<PaymentProvider.PaymentResult> checkPaymentStatus(@PathVariable String reference) {
    return ResponseEntity.ok(paymentProvider.checkStatus(reference));
  }

  private boolean isValidCallbackSecret(String providedToken) {
    String expected = darajaProperties != null ? darajaProperties.getCallbackSecret() : "";
    if (expected == null || expected.isBlank()) {
      return true; // Optional in sandbox/dev mode
    }
    if (providedToken == null || providedToken.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.trim().getBytes(StandardCharsets.UTF_8),
        providedToken.trim().getBytes(StandardCharsets.UTF_8)
    );
  }

  private boolean isAllowedCallbackIp(String clientIp) {
    String configured = darajaProperties != null ? darajaProperties.getAllowedCallbackIps() : "";
    if (configured == null || configured.isBlank()) {
      // Allow in fake sandbox/dev mode; warn if live Daraja is enabled without an IP allowlist
      if (darajaProperties != null && darajaProperties.isEnabled()) {
        log.warn("SECURITY NOTICE: POS_MPESA_ENABLED=true without POS_MPESA_ALLOWED_CALLBACK_IPS configured");
      }
      return true;
    }
    if (clientIp == null || clientIp.isBlank()) {
      return false;
    }
    Set<String> allowed = Arrays.stream(configured.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
    for (String entry : allowed) {
      if (entry.contains("/")) {
        if (matchesCidr(clientIp.trim(), entry)) {
          return true;
        }
      } else if (entry.equals(clientIp.trim())) {
        return true;
      }
    }
    return false;
  }

  static boolean matchesCidr(String ip, String cidr) {
    try {
      String[] parts = cidr.split("/");
      if (parts.length != 2) return false;
      int prefixLen = Integer.parseInt(parts[1].trim());
      byte[] target = InetAddress.getByName(ip).getAddress();
      byte[] network = InetAddress.getByName(parts[0].trim()).getAddress();
      if (target.length != network.length) return false;
      int fullBytes = prefixLen / 8;
      int remainingBits = prefixLen % 8;
      for (int i = 0; i < fullBytes; i++) {
        if (target[i] != network[i]) return false;
      }
      if (remainingBits > 0) {
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (target[fullBytes] & mask) == (network[fullBytes] & mask);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
