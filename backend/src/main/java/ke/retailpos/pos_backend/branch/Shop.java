package ke.retailpos.pos_backend.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop")
public class Shop {

  @Id
  private UUID id;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "location")
  private String location;

  @Column(name = "cost_center", nullable = false)
  private String costCenter = "Main - SP";

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Shop() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getLocation() { return location; }
  public void setLocation(String location) { this.location = location; }

  public String getCostCenter() { return costCenter; }
  public void setCostCenter(String costCenter) { this.costCenter = costCenter; }

  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
