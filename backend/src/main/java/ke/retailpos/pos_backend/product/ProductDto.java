package ke.retailpos.pos_backend.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductDto(
    UUID id,
    String name,
    BigDecimal priceKes,
    String taxCategory,
    BigDecimal stockQuantity,
    BigDecimal reorderLevel,
    String barcode,
    BigDecimal costPriceKes,
    BigDecimal detectedWeightKg
) {
  public ProductDto(UUID id, String name, BigDecimal priceKes) {
    this(id, name, priceKes, null, null, null, null, BigDecimal.ZERO, null);
  }

  public ProductDto(UUID id, String name, BigDecimal priceKes, String taxCategory) {
    this(id, name, priceKes, taxCategory, null, null, null, BigDecimal.ZERO, null);
  }

  public ProductDto(UUID id, String name, BigDecimal priceKes, String taxCategory,
      BigDecimal stockQuantity, BigDecimal reorderLevel, String barcode) {
    this(id, name, priceKes, taxCategory, stockQuantity, reorderLevel, barcode, BigDecimal.ZERO, null);
  }

  public ProductDto(UUID id, String name, BigDecimal priceKes, String taxCategory,
      BigDecimal stockQuantity, BigDecimal reorderLevel, String barcode, BigDecimal costPriceKes) {
    this(id, name, priceKes, taxCategory, stockQuantity, reorderLevel, barcode, costPriceKes, null);
  }
}
