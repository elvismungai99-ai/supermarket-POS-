package ke.retailpos.pos_backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supervisor_override_token")
public class SupervisorOverrideToken {

  @Id
  private UUID id;

  @Column(name = "supervisor_id", nullable = false)
  private UUID supervisorId;

  @Column(name = "cashier_id")
  private UUID cashierId;

  @Column(name = "reason")
  private String reason;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public SupervisorOverrideToken() {}

  public SupervisorOverrideToken(UUID id, UUID supervisorId, UUID cashierId, String reason, Instant expiresAt) {
    this.id = id;
    this.supervisorId = supervisorId;
    this.cashierId = cashierId;
    this.reason = reason;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getSupervisorId() { return supervisorId; }
  public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }

  public UUID getCashierId() { return cashierId; }
  public void setCashierId(UUID cashierId) { this.cashierId = cashierId; }

  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getConsumedAt() { return consumedAt; }
  public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
