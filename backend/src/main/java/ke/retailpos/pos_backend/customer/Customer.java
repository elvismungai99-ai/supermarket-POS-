package ke.retailpos.pos_backend.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

  @Id
  private UUID id;

  @Column(name = "phone_number", nullable = false, unique = true)
  private String phoneNumber;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "points_balance", nullable = false)
  private BigDecimal pointsBalance = BigDecimal.ZERO;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Version
  @Column(name = "version", nullable = false)
  private long version = 0L;

  public Customer() {}

  public Customer(UUID id, String phoneNumber, String name, BigDecimal pointsBalance, Instant createdAt) {
    this.id = id;
    this.phoneNumber = phoneNumber;
    this.name = name;
    this.pointsBalance = pointsBalance != null ? pointsBalance : BigDecimal.ZERO;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getPointsBalance() {
    return pointsBalance;
  }

  public void setPointsBalance(BigDecimal pointsBalance) {
    this.pointsBalance = pointsBalance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
