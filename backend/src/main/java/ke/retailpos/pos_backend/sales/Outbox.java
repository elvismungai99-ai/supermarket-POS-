package ke.retailpos.pos_backend.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox")
public class Outbox {

  @Id
  private UUID id;

  @Column(name = "topic", nullable = false)
  private String topic;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  public Outbox() {}

  public Outbox(UUID id, String topic, String payload, Instant createdAt, Instant sentAt) {
    this.id = id;
    this.topic = topic;
    this.payload = payload;
    this.createdAt = createdAt;
    this.sentAt = sentAt;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getTopic() { return topic; }
  public void setTopic(String topic) { this.topic = topic; }

  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
