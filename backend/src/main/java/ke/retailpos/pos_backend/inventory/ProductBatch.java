package ke.retailpos.pos_backend.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ke.retailpos.pos_backend.product.Product;

@Entity
@Table(name = "product_batch")
public class ProductBatch {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "batch_number", nullable = false)
  private String batchNumber;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(name = "quantity_remaining", nullable = false)
  private BigDecimal quantityRemaining = BigDecimal.ZERO;

  @Column(name = "unit_cost_kes", nullable = false)
  private BigDecimal unitCostKes = BigDecimal.ZERO;

  @Column(name = "stock_receipt_id")
  private UUID stockReceiptId;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();

  @Version
  @Column(name = "version", nullable = false)
  private long version = 0L;

  public ProductBatch() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public Product getProduct() { return product; }
  public void setProduct(Product product) { this.product = product; }

  public String getBatchNumber() { return batchNumber; }
  public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

  public LocalDate getExpiryDate() { return expiryDate; }
  public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

  public BigDecimal getQuantityRemaining() { return quantityRemaining; }
  public void setQuantityRemaining(BigDecimal quantityRemaining) { this.quantityRemaining = quantityRemaining; }

  public BigDecimal getUnitCostKes() { return unitCostKes; }
  public void setUnitCostKes(BigDecimal unitCostKes) { this.unitCostKes = unitCostKes; }

  public UUID getStockReceiptId() { return stockReceiptId; }
  public void setStockReceiptId(UUID stockReceiptId) { this.stockReceiptId = stockReceiptId; }

  public Instant getReceivedAt() { return receivedAt; }
  public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
}
