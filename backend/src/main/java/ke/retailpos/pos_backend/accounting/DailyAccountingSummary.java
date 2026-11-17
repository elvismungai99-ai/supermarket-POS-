package ke.retailpos.pos_backend.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_accounting_summary")
public class DailyAccountingSummary {

  @Id
  private UUID id;

  @Column(name = "shop_id", nullable = false)
  private UUID shopId;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "sales_standard_rated_net_kes", nullable = false)
  private BigDecimal salesStandardRatedNetKes = BigDecimal.ZERO;

  @Column(name = "sales_special_rated_net_kes", nullable = false)
  private BigDecimal salesSpecialRatedNetKes = BigDecimal.ZERO;

  @Column(name = "sales_zero_rated_net_kes", nullable = false)
  private BigDecimal salesZeroRatedNetKes = BigDecimal.ZERO;

  @Column(name = "sales_exempt_net_kes", nullable = false)
  private BigDecimal salesExemptNetKes = BigDecimal.ZERO;

  @Column(name = "output_vat_kes", nullable = false)
  private BigDecimal outputVatKes = BigDecimal.ZERO;

  @Column(name = "total_gross_sales_kes", nullable = false)
  private BigDecimal totalGrossSalesKes = BigDecimal.ZERO;

  @Column(name = "cash_takings_kes", nullable = false)
  private BigDecimal cashTakingsKes = BigDecimal.ZERO;

  @Column(name = "mpesa_takings_kes", nullable = false)
  private BigDecimal mpesaTakingsKes = BigDecimal.ZERO;

  @Column(name = "bank_takings_kes", nullable = false)
  private BigDecimal bankTakingsKes = BigDecimal.ZERO;

  @Column(name = "loyalty_redeemed_kes", nullable = false)
  private BigDecimal loyaltyRedeemedKes = BigDecimal.ZERO;

  @Column(name = "returns_total_kes", nullable = false)
  private BigDecimal returnsTotalKes = BigDecimal.ZERO;

  @Column(name = "returns_tax_kes", nullable = false)
  private BigDecimal returnsTaxKes = BigDecimal.ZERO;

  @Column(name = "returns_net_kes", nullable = false)
  private BigDecimal returnsNetKes = BigDecimal.ZERO;

  @Column(name = "returns_cogs_kes", nullable = false)
  private BigDecimal returnsCogsKes = BigDecimal.ZERO;

  @Column(name = "cash_difference_kes", nullable = false)
  private BigDecimal cashDifferenceKes = BigDecimal.ZERO;

  @Column(name = "cogs_total_kes", nullable = false)
  private BigDecimal cogsTotalKes = BigDecimal.ZERO;

  @Column(name = "total_debits_kes", nullable = false)
  private BigDecimal totalDebitsKes = BigDecimal.ZERO;

  @Column(name = "total_credits_kes", nullable = false)
  private BigDecimal totalCreditsKes = BigDecimal.ZERO;

  @Column(name = "is_balanced", nullable = false)
  private boolean isBalanced = true;

  @Column(name = "status", nullable = false)
  private String status = "DRAFT";

  @Column(name = "erpnext_journal_entry_id")
  private String erpnextJournalEntryId;

  @Column(name = "erpnext_company")
  private String erpnextCompany;

  @Column(name = "posted_at")
  private Instant postedAt;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public DailyAccountingSummary() {}

  public DailyAccountingSummary(
      UUID id,
      UUID shopId,
      LocalDate businessDate,
      BigDecimal salesStandardRatedNetKes,
      BigDecimal salesZeroRatedNetKes,
      BigDecimal salesExemptNetKes,
      BigDecimal outputVatKes,
      BigDecimal totalGrossSalesKes,
      BigDecimal cashTakingsKes,
      BigDecimal mpesaTakingsKes,
      BigDecimal bankTakingsKes,
      BigDecimal returnsTotalKes,
      BigDecimal returnsTaxKes,
      BigDecimal returnsNetKes,
      BigDecimal cashDifferenceKes,
      BigDecimal cogsTotalKes,
      BigDecimal totalDebitsKes,
      BigDecimal totalCreditsKes,
      boolean isBalanced,
      String status
  ) {
    this.id = id;
    this.shopId = shopId;
    this.businessDate = businessDate;
    this.salesStandardRatedNetKes = salesStandardRatedNetKes != null ? salesStandardRatedNetKes : BigDecimal.ZERO;
    this.salesZeroRatedNetKes = salesZeroRatedNetKes != null ? salesZeroRatedNetKes : BigDecimal.ZERO;
    this.salesExemptNetKes = salesExemptNetKes != null ? salesExemptNetKes : BigDecimal.ZERO;
    this.outputVatKes = outputVatKes != null ? outputVatKes : BigDecimal.ZERO;
    this.totalGrossSalesKes = totalGrossSalesKes != null ? totalGrossSalesKes : BigDecimal.ZERO;
    this.cashTakingsKes = cashTakingsKes != null ? cashTakingsKes : BigDecimal.ZERO;
    this.mpesaTakingsKes = mpesaTakingsKes != null ? mpesaTakingsKes : BigDecimal.ZERO;
    this.bankTakingsKes = bankTakingsKes != null ? bankTakingsKes : BigDecimal.ZERO;
    this.returnsTotalKes = returnsTotalKes != null ? returnsTotalKes : BigDecimal.ZERO;
    this.returnsTaxKes = returnsTaxKes != null ? returnsTaxKes : BigDecimal.ZERO;
    this.returnsNetKes = returnsNetKes != null ? returnsNetKes : BigDecimal.ZERO;
    this.cashDifferenceKes = cashDifferenceKes != null ? cashDifferenceKes : BigDecimal.ZERO;
    this.cogsTotalKes = cogsTotalKes != null ? cogsTotalKes : BigDecimal.ZERO;
    this.totalDebitsKes = totalDebitsKes != null ? totalDebitsKes : BigDecimal.ZERO;
    this.totalCreditsKes = totalCreditsKes != null ? totalCreditsKes : BigDecimal.ZERO;
    this.isBalanced = isBalanced;
    this.status = status != null ? status : "DRAFT";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getShopId() { return shopId; }
  public void setShopId(UUID shopId) { this.shopId = shopId; }

  public LocalDate getBusinessDate() { return businessDate; }
  public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

  public BigDecimal getSalesStandardRatedNetKes() { return salesStandardRatedNetKes; }
  public void setSalesStandardRatedNetKes(BigDecimal salesStandardRatedNetKes) { this.salesStandardRatedNetKes = salesStandardRatedNetKes; }

  public BigDecimal getSalesZeroRatedNetKes() { return salesZeroRatedNetKes; }
  public void setSalesZeroRatedNetKes(BigDecimal salesZeroRatedNetKes) { this.salesZeroRatedNetKes = salesZeroRatedNetKes; }

  public BigDecimal getSalesExemptNetKes() { return salesExemptNetKes; }
  public void setSalesExemptNetKes(BigDecimal salesExemptNetKes) { this.salesExemptNetKes = salesExemptNetKes; }

  public BigDecimal getOutputVatKes() { return outputVatKes; }
  public void setOutputVatKes(BigDecimal outputVatKes) { this.outputVatKes = outputVatKes; }

  public BigDecimal getTotalGrossSalesKes() { return totalGrossSalesKes; }
  public void setTotalGrossSalesKes(BigDecimal totalGrossSalesKes) { this.totalGrossSalesKes = totalGrossSalesKes; }

  public BigDecimal getCashTakingsKes() { return cashTakingsKes; }
  public void setCashTakingsKes(BigDecimal cashTakingsKes) { this.cashTakingsKes = cashTakingsKes; }

  public BigDecimal getMpesaTakingsKes() { return mpesaTakingsKes; }
  public void setMpesaTakingsKes(BigDecimal mpesaTakingsKes) { this.mpesaTakingsKes = mpesaTakingsKes; }

  public BigDecimal getBankTakingsKes() { return bankTakingsKes; }
  public void setBankTakingsKes(BigDecimal bankTakingsKes) { this.bankTakingsKes = bankTakingsKes; }

  public BigDecimal getReturnsTotalKes() { return returnsTotalKes; }
  public void setReturnsTotalKes(BigDecimal returnsTotalKes) { this.returnsTotalKes = returnsTotalKes; }

  public BigDecimal getReturnsTaxKes() { return returnsTaxKes; }
  public void setReturnsTaxKes(BigDecimal returnsTaxKes) { this.returnsTaxKes = returnsTaxKes; }

  public BigDecimal getReturnsNetKes() { return returnsNetKes; }
  public void setReturnsNetKes(BigDecimal returnsNetKes) { this.returnsNetKes = returnsNetKes; }

  public BigDecimal getCashDifferenceKes() { return cashDifferenceKes; }
  public void setCashDifferenceKes(BigDecimal cashDifferenceKes) { this.cashDifferenceKes = cashDifferenceKes; }

  public BigDecimal getCogsTotalKes() { return cogsTotalKes; }
  public void setCogsTotalKes(BigDecimal cogsTotalKes) { this.cogsTotalKes = cogsTotalKes; }

  public BigDecimal getTotalDebitsKes() { return totalDebitsKes; }
  public void setTotalDebitsKes(BigDecimal totalDebitsKes) { this.totalDebitsKes = totalDebitsKes; }

  public BigDecimal getTotalCreditsKes() { return totalCreditsKes; }
  public void setTotalCreditsKes(BigDecimal totalCreditsKes) { this.totalCreditsKes = totalCreditsKes; }

  public boolean isBalanced() { return isBalanced; }
  public void setBalanced(boolean balanced) { isBalanced = balanced; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getErpnextJournalEntryId() { return erpnextJournalEntryId; }
  public void setErpnextJournalEntryId(String erpnextJournalEntryId) { this.erpnextJournalEntryId = erpnextJournalEntryId; }

  public String getErpnextCompany() { return erpnextCompany; }
  public void setErpnextCompany(String erpnextCompany) { this.erpnextCompany = erpnextCompany; }

  public Instant getPostedAt() { return postedAt; }
  public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public BigDecimal getSalesSpecialRatedNetKes() { return salesSpecialRatedNetKes; }
  public void setSalesSpecialRatedNetKes(BigDecimal salesSpecialRatedNetKes) {
    this.salesSpecialRatedNetKes = salesSpecialRatedNetKes != null ? salesSpecialRatedNetKes : BigDecimal.ZERO;
  }

  public BigDecimal getLoyaltyRedeemedKes() { return loyaltyRedeemedKes; }
  public void setLoyaltyRedeemedKes(BigDecimal loyaltyRedeemedKes) {
    this.loyaltyRedeemedKes = loyaltyRedeemedKes != null ? loyaltyRedeemedKes : BigDecimal.ZERO;
  }

  public BigDecimal getReturnsCogsKes() { return returnsCogsKes; }
  public void setReturnsCogsKes(BigDecimal returnsCogsKes) {
    this.returnsCogsKes = returnsCogsKes != null ? returnsCogsKes : BigDecimal.ZERO;
  }
}
