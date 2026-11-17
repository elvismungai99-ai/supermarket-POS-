package ke.retailpos.pos_backend.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ke.retailpos.pos_backend.product.Product;

@Entity
@Table(name = "stock_receipt")
public class StockReceipt {

  @Id
  private UUID id;

  @Column(name = "supplier_name", nullable = false)
  private String supplierName;

  @Column(name = "supplier_invoice_no")
  private String supplierInvoiceNo;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_cost_kes", nullable = false)
  private BigDecimal unitCostKes;

  @Column(name = "vat_rate", nullable = false)
  private BigDecimal vatRate = BigDecimal.ZERO;

  @Column(name = "total_cost_kes", nullable = false)
  private BigDecimal totalCostKes;

  @Column(name = "received_by")
  private UUID receivedBy;

  @Column(name = "notes")
  private String notes;

  @Column(name = "erpnext_purchase_invoice_id")
  private String erpnextPurchaseInvoiceId;

  @Column(name = "erpnext_status", nullable = false)
  private String erpnextStatus = "DRAFT";

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();

  @Column(name = "batch_number")
  private String batchNumber;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  public StockReceipt() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getSupplierName() { return supplierName; }
  public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

  public String getSupplierInvoiceNo() { return supplierInvoiceNo; }
  public void setSupplierInvoiceNo(String supplierInvoiceNo) { this.supplierInvoiceNo = supplierInvoiceNo; }

  public Product getProduct() { return product; }
  public void setProduct(Product product) { this.product = product; }

  public BigDecimal getQuantity() { return quantity; }
  public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

  public BigDecimal getUnitCostKes() { return unitCostKes; }
  public void setUnitCostKes(BigDecimal unitCostKes) { this.unitCostKes = unitCostKes; }

  public BigDecimal getVatRate() { return vatRate; }
  public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }

  public BigDecimal getTotalCostKes() { return totalCostKes; }
  public void setTotalCostKes(BigDecimal totalCostKes) { this.totalCostKes = totalCostKes; }

  public UUID getReceivedBy() { return receivedBy; }
  public void setReceivedBy(UUID receivedBy) { this.receivedBy = receivedBy; }

  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }

  public String getErpnextPurchaseInvoiceId() { return erpnextPurchaseInvoiceId; }
  public void setErpnextPurchaseInvoiceId(String erpnextPurchaseInvoiceId) { this.erpnextPurchaseInvoiceId = erpnextPurchaseInvoiceId; }

  public String getErpnextStatus() { return erpnextStatus; }
  public void setErpnextStatus(String erpnextStatus) { this.erpnextStatus = erpnextStatus; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Instant getReceivedAt() { return receivedAt; }
  public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

  public String getBatchNumber() { return batchNumber; }
  public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

  public LocalDate getExpiryDate() { return expiryDate; }
  public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
