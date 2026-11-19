package ke.retailpos.pos_backend.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

  @Id
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "price_kes", nullable = false)
  private BigDecimal priceKes;

  @Column(name = "tax_category", nullable = false)
  private String taxCategory;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "stock_quantity", nullable = false)
  private BigDecimal stockQuantity = new BigDecimal("100.000");

  @Column(name = "reorder_level", nullable = false)
  private BigDecimal reorderLevel = new BigDecimal("10.000");

  @Column(name = "cost_price_kes", nullable = false)
  private BigDecimal costPriceKes = BigDecimal.ZERO;

  @Version
  @Column(name = "version", nullable = false)
  private long version = 0L;

  public Product() {}

  public Product(UUID id, String name, BigDecimal priceKes, String taxCategory, boolean active) {
    this(id, name, priceKes, taxCategory, active, new BigDecimal("100.000"), new BigDecimal("10.000"), BigDecimal.ZERO);
  }

  public Product(UUID id, String name, BigDecimal priceKes, String taxCategory, boolean active, BigDecimal stockQuantity, BigDecimal reorderLevel) {
    this(id, name, priceKes, taxCategory, active, stockQuantity, reorderLevel, BigDecimal.ZERO);
  }

  public Product(UUID id, String name, BigDecimal priceKes, String taxCategory, boolean active, BigDecimal stockQuantity, BigDecimal reorderLevel, BigDecimal costPriceKes) {
    this.id = id;
    this.name = name;
    this.priceKes = priceKes;
    this.taxCategory = taxCategory;
    this.active = active;
    this.stockQuantity = stockQuantity != null ? stockQuantity : new BigDecimal("100.000");
    this.reorderLevel = reorderLevel != null ? reorderLevel : new BigDecimal("10.000");
    this.costPriceKes = costPriceKes != null ? costPriceKes : BigDecimal.ZERO;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getPriceKes() {
    return priceKes;
  }

  public void setPriceKes(BigDecimal priceKes) {
    this.priceKes = priceKes;
  }

  public String getTaxCategory() {
    return taxCategory;
  }

  public void setTaxCategory(String taxCategory) {
    this.taxCategory = taxCategory;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public BigDecimal getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(BigDecimal stockQuantity) {
    this.stockQuantity = stockQuantity;
  }

  public BigDecimal getReorderLevel() {
    return reorderLevel;
  }

  public void setReorderLevel(BigDecimal reorderLevel) {
    this.reorderLevel = reorderLevel;
  }

  public BigDecimal getCostPriceKes() {
    return costPriceKes;
  }

  public void setCostPriceKes(BigDecimal costPriceKes) {
    this.costPriceKes = costPriceKes;
  }
}
