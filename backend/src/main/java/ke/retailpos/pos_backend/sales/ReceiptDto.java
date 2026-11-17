package ke.retailpos.pos_backend.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceiptDto(
    UUID saleId,
    String receiptNumber,
    UUID shopId,
    UUID tillId,
    UUID cashierId,
    String cashierName,
    Instant createdAt,
    List<ReceiptLineDto> lines,
    BigDecimal subtotalKes,
    BigDecimal totalTaxKes,
    BigDecimal totalKes,
    List<TaxBreakdownDto> taxBreakdowns,
    BigDecimal amountTenderedKes,
    BigDecimal changeKes,
    String paymentMethod,
    String etimsInvoiceStatus,
    String paymentReference,
    BigDecimal loyaltyPointsEarned,
    BigDecimal loyaltyPointsBalance
) {
  public ReceiptDto(
      UUID saleId,
      String receiptNumber,
      UUID shopId,
      UUID tillId,
      UUID cashierId,
      String cashierName,
      Instant createdAt,
      List<ReceiptLineDto> lines,
      BigDecimal subtotalKes,
      BigDecimal totalTaxKes,
      BigDecimal totalKes,
      List<TaxBreakdownDto> taxBreakdowns,
      BigDecimal amountTenderedKes,
      BigDecimal changeKes,
      String paymentMethod,
      String etimsInvoiceStatus
  ) {
    this(
        saleId,
        receiptNumber,
        shopId,
        tillId,
        cashierId,
        cashierName,
        createdAt,
        lines,
        subtotalKes,
        totalTaxKes,
        totalKes,
        taxBreakdowns,
        amountTenderedKes,
        changeKes,
        paymentMethod,
        etimsInvoiceStatus,
        null,
        null,
        null
    );
  }

  public record ReceiptLineDto(
      UUID productId,
      String name,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      BigDecimal lineTotalKes,
      String taxCategory,
      BigDecimal taxRate,
      BigDecimal taxAmountKes
  ) {}

  public record TaxBreakdownDto(
      String category,
      BigDecimal rate,
      BigDecimal taxableKes,
      BigDecimal taxKes,
      BigDecimal grossKes
  ) {}
}
