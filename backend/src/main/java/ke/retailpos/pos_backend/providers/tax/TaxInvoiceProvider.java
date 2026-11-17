package ke.retailpos.pos_backend.providers.tax;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TaxInvoiceProvider {

  record TaxInvoiceRequest(
      UUID saleId,
      String receiptNumber,
      BigDecimal totalAmountKes,
      BigDecimal taxableAmountKes,
      BigDecimal taxAmountKes,
      List<TaxCategoryItem> items
  ) {
    public record TaxCategoryItem(
        String category,
        BigDecimal rate,
        BigDecimal taxableKes,
        BigDecimal taxKes,
        BigDecimal grossKes
    ) {}
  }

  record TaxInvoiceResponse(
      boolean success,
      String kraControlUnitNumber,
      String kraInternalData,
      String kraQrCodeUrl,
      String errorMessage
  ) {}

  TaxInvoiceResponse submitInvoice(TaxInvoiceRequest request);
}
