package ke.retailpos.pos_backend.returns;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.sales.Sale;

@Entity
@Table(name = "sale_return")
public class SaleReturn {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "original_sale_id", nullable = false)
  private Sale originalSale;

  @Column(name = "receipt_number", nullable = false)
  private String receiptNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cashier_id", nullable = false)
  private Cashier cashier;

  @Column(name = "refund_amount_kes", nullable = false)
  private BigDecimal refundAmountKes;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "refund_method", nullable = false)
  private String refundMethod = "CASH";

  public SaleReturn() {}

  public SaleReturn(
      UUID id,
      Sale originalSale,
      String receiptNumber,
      Cashier cashier,
      BigDecimal refundAmountKes,
      String reason,
      Instant createdAt
  ) {
    this.id = id;
    this.originalSale = originalSale;
    this.receiptNumber = receiptNumber;
    this.cashier = cashier;
    this.refundAmountKes = refundAmountKes;
    this.reason = reason;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Sale getOriginalSale() {
    return originalSale;
  }

  public void setOriginalSale(Sale originalSale) {
    this.originalSale = originalSale;
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }

  public void setReceiptNumber(String receiptNumber) {
    this.receiptNumber = receiptNumber;
  }

  public Cashier getCashier() {
    return cashier;
  }

  public void setCashier(Cashier cashier) {
    this.cashier = cashier;
  }

  public BigDecimal getRefundAmountKes() {
    return refundAmountKes;
  }

  public void setRefundAmountKes(BigDecimal refundAmountKes) {
    this.refundAmountKes = refundAmountKes;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getRefundMethod() {
    return refundMethod;
  }

  public void setRefundMethod(String refundMethod) {
    this.refundMethod = (refundMethod != null && !refundMethod.isBlank()) ? refundMethod.toUpperCase() : "CASH";
  }
}
