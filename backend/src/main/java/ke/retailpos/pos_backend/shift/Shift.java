package ke.retailpos.pos_backend.shift;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.retailpos.pos_backend.auth.Cashier;

@Entity
@Table(name = "shift")
public class Shift {

  @Id
  private UUID id;

  @Column(name = "till_id", nullable = false)
  private UUID tillId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "cashier_id", nullable = false)
  private Cashier cashier;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "opening_float_kes", nullable = false)
  private BigDecimal openingFloatKes;

  @Column(name = "closing_cash_kes")
  private BigDecimal closingCashKes;

  @Column(name = "status", nullable = false)
  private String status;

  public Shift() {}

  public Shift(UUID id, UUID tillId, Cashier cashier, Instant openedAt, BigDecimal openingFloatKes, String status) {
    this.id = id;
    this.tillId = tillId;
    this.cashier = cashier;
    this.openedAt = openedAt;
    this.openingFloatKes = openingFloatKes;
    this.status = status;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getTillId() { return tillId; }
  public void setTillId(UUID tillId) { this.tillId = tillId; }

  public Cashier getCashier() { return cashier; }
  public void setCashier(Cashier cashier) { this.cashier = cashier; }

  public Instant getOpenedAt() { return openedAt; }
  public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

  public Instant getClosedAt() { return closedAt; }
  public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

  public BigDecimal getOpeningFloatKes() { return openingFloatKes; }
  public void setOpeningFloatKes(BigDecimal openingFloatKes) { this.openingFloatKes = openingFloatKes; }

  public BigDecimal getClosingCashKes() { return closingCashKes; }
  public void setClosingCashKes(BigDecimal closingCashKes) { this.closingCashKes = closingCashKes; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
