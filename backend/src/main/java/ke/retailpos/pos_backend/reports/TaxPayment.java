package ke.retailpos.pos_backend.reports;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tax_payment")
public class TaxPayment {

  @Id
  private UUID id;

  @Column(name = "tax_period", nullable = false)
  private String taxPeriod;

  @Column(name = "tax_type", nullable = false)
  private String taxType = "VAT";

  @Column(name = "amount_paid_kes", nullable = false)
  private BigDecimal amountPaidKes;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(name = "payment_method", nullable = false)
  private String paymentMethod = "KRA_ITAX_MPESA";

  @Column(name = "kra_prn", nullable = false)
  private String kraPrn;

  @Column(name = "notes")
  private String notes;

  @Column(name = "recorded_by")
  private UUID recordedBy;

  @Column(name = "recorded_by_username")
  private String recordedByUsername;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public TaxPayment() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getTaxPeriod() { return taxPeriod; }
  public void setTaxPeriod(String taxPeriod) { this.taxPeriod = taxPeriod; }

  public String getTaxType() { return taxType; }
  public void setTaxType(String taxType) { this.taxType = taxType; }

  public BigDecimal getAmountPaidKes() { return amountPaidKes; }
  public void setAmountPaidKes(BigDecimal amountPaidKes) { this.amountPaidKes = amountPaidKes; }

  public LocalDate getPaymentDate() { return paymentDate; }
  public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

  public String getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

  public String getKraPrn() { return kraPrn; }
  public void setKraPrn(String kraPrn) { this.kraPrn = kraPrn; }

  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }

  public UUID getRecordedBy() { return recordedBy; }
  public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }

  public String getRecordedByUsername() { return recordedByUsername; }
  public void setRecordedByUsername(String recordedByUsername) { this.recordedByUsername = recordedByUsername; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
