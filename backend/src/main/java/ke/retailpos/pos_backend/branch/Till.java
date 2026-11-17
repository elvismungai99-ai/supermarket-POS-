package ke.retailpos.pos_backend.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "till")
public class Till {

  @Id
  private UUID id;

  @Column(name = "shop_id", nullable = false)
  private UUID shopId;

  @Column(name = "till_number", nullable = false)
  private String tillNumber;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Till() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getShopId() { return shopId; }
  public void setShopId(UUID shopId) { this.shopId = shopId; }

  public String getTillNumber() { return tillNumber; }
  public void setTillNumber(String tillNumber) { this.tillNumber = tillNumber; }

  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
