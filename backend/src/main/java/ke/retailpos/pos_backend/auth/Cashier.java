package ke.retailpos.pos_backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cashier")
public class Cashier {

  @Id
  private UUID id;

  @Column(name = "username", nullable = false, unique = true)
  private String username;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "pin_hash", nullable = false)
  private String pinHash;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "status", nullable = false)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "mfa_secret")
  private String mfaSecret;

  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled = false;

  public Cashier() {}

  public Cashier(UUID id, String username, String name, String pinHash, String role, boolean active) {
    this(id, username, name, pinHash, role, active, active ? "ACTIVE" : "PENDING_APPROVAL", Instant.now());
  }

  public Cashier(UUID id, String username, String name, String pinHash, String role, boolean active, String status, Instant createdAt) {
    this.id = id;
    this.username = username;
    this.name = name;
    this.pinHash = pinHash;
    this.role = role;
    this.active = active;
    this.status = status != null ? status : (active ? "ACTIVE" : "PENDING_APPROVAL");
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getPinHash() { return pinHash; }
  public void setPinHash(String pinHash) { this.pinHash = pinHash; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public String getMfaSecret() { return mfaSecret; }
  public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

  public boolean isMfaEnabled() { return mfaEnabled; }
  public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
}
