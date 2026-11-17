package ke.retailpos.pos_backend.providers.payment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pos.mpesa.enabled", havingValue = "false", matchIfMissing = true)
public class FakePaymentProvider implements PaymentProvider {

  public enum Mode {
    ALWAYS_SUCCESS,
    ALWAYS_FAIL,
    DELAYED,
    DUPLICATE
  }

  private volatile Mode mode = Mode.ALWAYS_SUCCESS;
  private final Map<String, Integer> seenTransactions = new ConcurrentHashMap<>();

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public Mode getMode() {
    return mode;
  }

  @Override
  public PaymentResult initiatePayment(PaymentRequest request) {
    String refKey = request.reference() != null
        ? request.reference()
        : (request.saleId() != null ? request.saleId().toString() : UUID.randomUUID().toString());

    int count = seenTransactions.merge(refKey, 1, Integer::sum);
    boolean isDuplicate = count > 1;

    if (mode == Mode.ALWAYS_FAIL) {
      return new PaymentResult(
          PaymentStatus.FAILED,
          null,
          "Payment rejected by provider (SIMULATED_FAILURE)",
          false
      );
    }

    if (mode == Mode.DUPLICATE || isDuplicate) {
      return new PaymentResult(
          PaymentStatus.SUCCESS,
          "MPESA-" + refKey.substring(0, Math.min(refKey.length(), 10)).toUpperCase(),
          "Duplicate transaction callback ignored",
          true
      );
    }

    if (mode == Mode.DELAYED && count == 1) {
      return new PaymentResult(
          PaymentStatus.PENDING,
          "PENDING-" + refKey,
          "Customer prompt sent; awaiting PIN entry",
          false
      );
    }

    String receiptNumber = "WS" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return new PaymentResult(
        PaymentStatus.SUCCESS,
        receiptNumber,
        "Payment processed successfully",
        false
    );
  }

  @Override
  public PaymentResult checkStatus(String transactionReference) {
    if (mode == Mode.ALWAYS_FAIL) {
      return new PaymentResult(PaymentStatus.FAILED, transactionReference, "Payment failed", false);
    }
    return new PaymentResult(PaymentStatus.SUCCESS, transactionReference, "Payment confirmed", false);
  }
}
