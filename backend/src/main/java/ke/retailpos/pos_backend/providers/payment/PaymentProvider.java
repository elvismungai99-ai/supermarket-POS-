package ke.retailpos.pos_backend.providers.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {

  enum PaymentType {
    CASH,
    MPESA,
    BANK
  }

  enum PaymentStatus {
    SUCCESS,
    PENDING,
    FAILED
  }

  record PaymentRequest(
      UUID saleId,
      PaymentType type,
      BigDecimal amountKes,
      String customerPhone,
      String reference
  ) {}

  record PaymentResult(
      PaymentStatus status,
      String transactionReference,
      String message,
      boolean duplicate
  ) {}

  PaymentResult initiatePayment(PaymentRequest request);

  PaymentResult checkStatus(String transactionReference);
}
