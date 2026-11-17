package ke.retailpos.pos_backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_username")
  private String actorUsername;

  @Column(name = "actor_role")
  private String actorRole;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "target_entity")
  private String targetEntity;

  @Column(name = "target_id")
  private String targetId;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "details")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public AuditLog() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getActorId() { return actorId; }
  public void setActorId(UUID actorId) { this.actorId = actorId; }

  public String getActorUsername() { return actorUsername; }
  public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

  public String getActorRole() { return actorRole; }
  public void setActorRole(String actorRole) { this.actorRole = actorRole; }

  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }

  public String getTargetEntity() { return targetEntity; }
  public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

  public String getTargetId() { return targetId; }
  public void setTargetId(String targetId) { this.targetId = targetId; }

  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

  public String getDetails() { return details; }
  public void setDetails(String details) { this.details = details; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
