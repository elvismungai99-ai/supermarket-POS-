package ke.retailpos.pos_backend.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sale")
public class Sale {

  @Id
  private UUID id;

  @Column(name = "shop_id", nullable = false)
  private UUID shopId;

  @Column(name = "till_id", nullable = false)
  private UUID tillId;

  @Column(name = "cashier_id", nullable = false)
  private UUID cashierId;

  @Column(name = "total_kes", nullable = false)
  private BigDecimal totalKes;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "client_sale_id")
  private UUID clientSaleId;

  @Column(name = "loyalty_redeemed_kes", nullable = false)
  private BigDecimal loyaltyRedeemedKes = BigDecimal.ZERO;

  @Column(name = "discount_kes", nullable = false)
  private BigDecimal discountKes = BigDecimal.ZERO;

  @Column(name = "override_by")
  private UUID overrideBy;

  public Sale() {}

  public Sale(UUID id, UUID shopId, UUID tillId, UUID cashierId, BigDecimal totalKes, String status, Instant createdAt) {
    this.id = id;
    this.shopId = shopId;
    this.tillId = tillId;
    this.cashierId = cashierId;
    this.totalKes = totalKes;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getShopId() { return shopId; }
  public void setShopId(UUID shopId) { this.shopId = shopId; }

  public UUID getTillId() { return tillId; }
  public void setTillId(UUID tillId) { this.tillId = tillId; }

  public UUID getCashierId() { return cashierId; }
  public void setCashierId(UUID cashierId) { this.cashierId = cashierId; }

  public BigDecimal getTotalKes() { return totalKes; }
  public void setTotalKes(BigDecimal totalKes) { this.totalKes = totalKes; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public UUID getClientSaleId() { return clientSaleId; }
  public void setClientSaleId(UUID clientSaleId) { this.clientSaleId = clientSaleId; }

  public BigDecimal getLoyaltyRedeemedKes() { return loyaltyRedeemedKes; }
  public void setLoyaltyRedeemedKes(BigDecimal loyaltyRedeemedKes) {
    this.loyaltyRedeemedKes = loyaltyRedeemedKes != null ? loyaltyRedeemedKes : BigDecimal.ZERO;
  }

  public BigDecimal getDiscountKes() { return discountKes; }
  public void setDiscountKes(BigDecimal discountKes) {
    this.discountKes = discountKes != null ? discountKes : BigDecimal.ZERO;
  }

  public UUID getOverrideBy() { return overrideBy; }
  public void setOverrideBy(UUID overrideBy) { this.overrideBy = overrideBy; }
}
