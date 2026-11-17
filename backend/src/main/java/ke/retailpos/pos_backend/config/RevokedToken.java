package ke.retailpos.pos_backend.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "revoked_token")
public class RevokedToken {

  @Id
  private String jti;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at", nullable = false)
  private Instant revokedAt = Instant.now();

  public RevokedToken() {}

  public RevokedToken(String jti, Instant expiresAt) {
    this.jti = jti;
    this.expiresAt = expiresAt;
    this.revokedAt = Instant.now();
  }

  public String getJti() { return jti; }
  public void setJti(String jti) { this.jti = jti; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
