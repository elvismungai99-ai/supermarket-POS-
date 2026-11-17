package ke.retailpos.pos_backend.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSaleRequest(
    UUID shopId,
    UUID tillId,
    UUID cashierId,
    List<SaleItemRequest> items,
    PaymentRequest payment,
    UUID clientSaleId,
    List<PaymentRequest> payments,
    UUID overrideBy,
    UUID overrideToken
) {
  public CreateSaleRequest(
      UUID shopId,
      UUID tillId,
      UUID cashierId,
      List<SaleItemRequest> items,
      PaymentRequest payment
  ) {
    this(shopId, tillId, cashierId, items, payment, null, null, null, null);
  }

  public CreateSaleRequest(
      UUID shopId,
      UUID tillId,
      UUID cashierId,
      List<SaleItemRequest> items,
      PaymentRequest payment,
      UUID clientSaleId
  ) {
    this(shopId, tillId, cashierId, items, payment, clientSaleId, null, null, null);
  }

  public CreateSaleRequest(
      UUID shopId,
      UUID tillId,
      UUID cashierId,
      List<SaleItemRequest> items,
      PaymentRequest payment,
      UUID clientSaleId,
      List<PaymentRequest> payments,
      UUID overrideBy
  ) {
    this(shopId, tillId, cashierId, items, payment, clientSaleId, payments, overrideBy, null);
  }

  public record SaleItemRequest(
      UUID productId,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      BigDecimal discountKes
  ) {
    public SaleItemRequest(UUID productId, BigDecimal quantity, BigDecimal unitPriceKes) {
      this(productId, quantity, unitPriceKes, BigDecimal.ZERO);
    }
  }

  public record PaymentRequest(
      String method,
      BigDecimal amountTenderedKes,
      String customerPhone,
      BigDecimal pointsToRedeem,
      String bankReference
  ) {
    public PaymentRequest(String method, BigDecimal amountTenderedKes) {
      this(method, amountTenderedKes, null, null, null);
    }

    public PaymentRequest(String method, BigDecimal amountTenderedKes, String customerPhone, BigDecimal pointsToRedeem) {
      this(method, amountTenderedKes, customerPhone, pointsToRedeem, null);
    }
  }
}
