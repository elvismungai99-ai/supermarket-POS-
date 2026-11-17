package ke.retailpos.pos_backend.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

  @Id
  private UUID id;

  @Column(name = "sale_id", nullable = false)
  private UUID saleId;

  @Column(name = "method", nullable = false)
  private String method;

  @Column(name = "amount_kes", nullable = false)
  private BigDecimal amountKes;

  @Column(name = "reference")
  private String reference;

  @Column(name = "state", nullable = false)
  private String state;

  public Payment() {}

  public Payment(UUID id, UUID saleId, String method, BigDecimal amountKes, String reference, String state) {
    this.id = id;
    this.saleId = saleId;
    this.method = method;
    this.amountKes = amountKes;
    this.reference = reference;
    this.state = state;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getSaleId() { return saleId; }
  public void setSaleId(UUID saleId) { this.saleId = saleId; }

  public String getMethod() { return method; }
  public void setMethod(String method) { this.method = method; }

  public BigDecimal getAmountKes() { return amountKes; }
  public void setAmountKes(BigDecimal amountKes) { this.amountKes = amountKes; }

  public String getReference() { return reference; }
  public void setReference(String reference) { this.reference = reference; }

  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
}
