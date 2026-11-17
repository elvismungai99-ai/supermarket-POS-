package ke.retailpos.pos_backend.sales;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import ke.retailpos.pos_backend.product.Product;

@Entity
@Table(name = "sale_line")
public class SaleLine {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sale_id", nullable = false)
  private Sale sale;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price_kes", nullable = false)
  private BigDecimal unitPriceKes;

  @Column(name = "tax_category")
  private String taxCategory;

  @Column(name = "tax_amount_kes")
  private BigDecimal taxAmountKes;

  @Column(name = "line_total_kes")
  private BigDecimal lineTotalKes;

  @Column(name = "cost_price_kes")
  private BigDecimal costPriceKes;

  @Column(name = "discount_kes", nullable = false)
  private BigDecimal discountKes = BigDecimal.ZERO;

  public SaleLine() {}

  public SaleLine(
      UUID id,
      Sale sale,
      Product product,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      String taxCategory,
      BigDecimal taxAmountKes,
      BigDecimal lineTotalKes
  ) {
    this(id, sale, product, quantity, unitPriceKes, taxCategory, taxAmountKes, lineTotalKes, BigDecimal.ZERO);
  }

  public SaleLine(
      UUID id,
      Sale sale,
      Product product,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      String taxCategory,
      BigDecimal taxAmountKes,
      BigDecimal lineTotalKes,
      BigDecimal costPriceKes
  ) {
    this.id = id;
    this.sale = sale;
    this.product = product;
    this.quantity = quantity;
    this.unitPriceKes = unitPriceKes;
    this.taxCategory = taxCategory;
    this.taxAmountKes = taxAmountKes;
    this.lineTotalKes = lineTotalKes;
    this.costPriceKes = costPriceKes != null ? costPriceKes : BigDecimal.ZERO;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public Sale getSale() { return sale; }
  public void setSale(Sale sale) { this.sale = sale; }

  public Product getProduct() { return product; }
  public void setProduct(Product product) { this.product = product; }

  public BigDecimal getQuantity() { return quantity; }
  public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

  public BigDecimal getUnitPriceKes() { return unitPriceKes; }
  public void setUnitPriceKes(BigDecimal unitPriceKes) { this.unitPriceKes = unitPriceKes; }

  public String getTaxCategory() { return taxCategory; }
  public void setTaxCategory(String taxCategory) { this.taxCategory = taxCategory; }

  public BigDecimal getTaxAmountKes() { return taxAmountKes; }
  public void setTaxAmountKes(BigDecimal taxAmountKes) { this.taxAmountKes = taxAmountKes; }

  public BigDecimal getLineTotalKes() { return lineTotalKes; }
  public void setLineTotalKes(BigDecimal lineTotalKes) { this.lineTotalKes = lineTotalKes; }

  public BigDecimal getCostPriceKes() { return costPriceKes; }
  public void setCostPriceKes(BigDecimal costPriceKes) { this.costPriceKes = costPriceKes; }

  public BigDecimal getDiscountKes() { return discountKes; }
  public void setDiscountKes(BigDecimal discountKes) {
    this.discountKes = discountKes != null ? discountKes : BigDecimal.ZERO;
  }
}
