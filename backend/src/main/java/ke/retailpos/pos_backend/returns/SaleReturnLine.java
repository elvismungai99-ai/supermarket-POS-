package ke.retailpos.pos_backend.returns;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import ke.retailpos.pos_backend.product.Product;

@Entity
@Table(name = "sale_return_line")
public class SaleReturnLine {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sale_return_id", nullable = false)
  private SaleReturn saleReturn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price_kes", nullable = false)
  private BigDecimal unitPriceKes;

  @Column(name = "refund_total_kes", nullable = false)
  private BigDecimal refundTotalKes;

  public SaleReturnLine() {}

  public SaleReturnLine(
      UUID id,
      SaleReturn saleReturn,
      Product product,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      BigDecimal refundTotalKes
  ) {
    this.id = id;
    this.saleReturn = saleReturn;
    this.product = product;
    this.quantity = quantity;
    this.unitPriceKes = unitPriceKes;
    this.refundTotalKes = refundTotalKes;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public SaleReturn getSaleReturn() {
    return saleReturn;
  }

  public void setSaleReturn(SaleReturn saleReturn) {
    this.saleReturn = saleReturn;
  }

  public Product getProduct() {
    return product;
  }

  public void setProduct(Product product) {
    this.product = product;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPriceKes() {
    return unitPriceKes;
  }

  public void setUnitPriceKes(BigDecimal unitPriceKes) {
    this.unitPriceKes = unitPriceKes;
  }

  public BigDecimal getRefundTotalKes() {
    return refundTotalKes;
  }

  public void setRefundTotalKes(BigDecimal refundTotalKes) {
    this.refundTotalKes = refundTotalKes;
  }
}
