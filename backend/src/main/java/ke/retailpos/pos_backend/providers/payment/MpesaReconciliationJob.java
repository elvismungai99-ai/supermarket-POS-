package ke.retailpos.pos_backend.providers.payment;

import java.util.List;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.sales.Payment;
import ke.retailpos.pos_backend.sales.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fallback reconciliation job for M-Pesa STK Push transactions stuck in PENDING_STK
 * when webhook callbacks are delayed or dropped on unstable ISP links.
 */
@Component
@ConditionalOnProperty(name = "pos.mpesa.enabled", havingValue = "true")
public class MpesaReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(MpesaReconciliationJob.class);

  private final PaymentRepository paymentRepository;
  private final PaymentProvider paymentProvider;
  private final AuditLogService auditLogService;

  public MpesaReconciliationJob(
      PaymentRepository paymentRepository,
      PaymentProvider paymentProvider,
      AuditLogService auditLogService
  ) {
    this.paymentRepository = paymentRepository;
    this.paymentProvider = paymentProvider;
    this.auditLogService = auditLogService;
  }

  @Scheduled(fixedDelayString = "${pos.mpesa.reconcile-interval-ms:30000}")
  @Transactional
  public void reconcilePendingStkPayments() {
    List<Payment> pending = paymentRepository.findByState("PENDING_STK");
    for (Payment p : pending) {
      if (p.getReference() == null || p.getReference().isBlank()) {
        continue;
      }
      PaymentProvider.PaymentResult result = paymentProvider.checkStatus(p.getReference());
      if (result.status() == PaymentProvider.PaymentStatus.SUCCESS) {
        p.setState("CONFIRMED");
        paymentRepository.save(p);
        log.info("Reconciled PENDING_STK payment {} -> CONFIRMED", p.getReference());
        if (auditLogService != null) {
          auditLogService.record(null, "system", "SYSTEM", "MPESA_RECONCILED_CONFIRMED",
              "payment", p.getId().toString(), "scheduler", "Daraja STKQuery confirmed " + p.getReference());
        }
      } else if (result.status() == PaymentProvider.PaymentStatus.FAILED) {
        p.setState("FAILED");
        paymentRepository.save(p);
        log.warn("Reconciled PENDING_STK payment {} -> FAILED ({})", p.getReference(), result.message());
        if (auditLogService != null) {
          auditLogService.record(null, "system", "SYSTEM", "MPESA_RECONCILED_FAILED",
              "payment", p.getId().toString(), "scheduler", "Daraja STKQuery failed: " + result.message());
        }
      }
    }
  }
}
