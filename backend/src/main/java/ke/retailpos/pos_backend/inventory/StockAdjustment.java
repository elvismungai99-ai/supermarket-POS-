package ke.retailpos.pos_backend.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.retailpos.pos_backend.product.Product;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "adjustment_type", nullable = false)
  private String adjustmentType;

  @Column(name = "quantity_delta", nullable = false)
  private BigDecimal quantityDelta;

  @Column(name = "unit_cost_kes", nullable = false)
  private BigDecimal unitCostKes = BigDecimal.ZERO;

  @Column(name = "total_cost_kes", nullable = false)
  private BigDecimal totalCostKes = BigDecimal.ZERO;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "recorded_by")
  private UUID recordedBy;

  @Column(name = "recorded_by_username")
  private String recordedByUsername;

  @Column(name = "erpnext_journal_entry_id")
  private String erpnextJournalEntryId;

  @Column(name = "erpnext_status", nullable = false)
  private String erpnextStatus = "LOCAL_ONLY";

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public StockAdjustment() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public Product getProduct() { return product; }
  public void setProduct(Product product) { this.product = product; }

  public String getAdjustmentType() { return adjustmentType; }
  public void setAdjustmentType(String adjustmentType) { this.adjustmentType = adjustmentType; }

  public BigDecimal getQuantityDelta() { return quantityDelta; }
  public void setQuantityDelta(BigDecimal quantityDelta) { this.quantityDelta = quantityDelta; }

  public BigDecimal getUnitCostKes() { return unitCostKes; }
  public void setUnitCostKes(BigDecimal unitCostKes) { this.unitCostKes = unitCostKes; }

  public BigDecimal getTotalCostKes() { return totalCostKes; }
  public void setTotalCostKes(BigDecimal totalCostKes) { this.totalCostKes = totalCostKes; }

  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }

  public UUID getRecordedBy() { return recordedBy; }
  public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }

  public String getRecordedByUsername() { return recordedByUsername; }
  public void setRecordedByUsername(String recordedByUsername) { this.recordedByUsername = recordedByUsername; }

  public String getErpnextJournalEntryId() { return erpnextJournalEntryId; }
  public void setErpnextJournalEntryId(String erpnextJournalEntryId) { this.erpnextJournalEntryId = erpnextJournalEntryId; }

  public String getErpnextStatus() { return erpnextStatus; }
  public void setErpnextStatus(String erpnextStatus) { this.erpnextStatus = erpnextStatus; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
