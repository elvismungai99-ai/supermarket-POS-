package ke.retailpos.pos_backend.sales;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
  List<Outbox> findBySentAtIsNull();

  @Query(
      value = """
          SELECT * FROM outbox
          WHERE sent_at IS NULL
            AND failed_permanently = FALSE
            AND (next_retry_at IS NULL OR next_retry_at <= :now)
          ORDER BY created_at ASC
          LIMIT 50
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<Outbox> findDuePending(@Param("now") Instant now);

  List<Outbox> findBySentAtIsNullAndFailedPermanentlyTrue();
}
