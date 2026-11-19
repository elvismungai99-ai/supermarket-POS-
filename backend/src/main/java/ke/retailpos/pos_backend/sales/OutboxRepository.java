package ke.retailpos.pos_backend.sales;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
  List<Outbox> findBySentAtIsNull();

  @Query("""
      select o from Outbox o
      where o.sentAt is null
        and o.failedPermanently = false
        and (o.nextRetryAt is null or o.nextRetryAt <= :now)
      order by o.createdAt asc
      """)
  List<Outbox> findDuePending(@Param("now") Instant now);

  List<Outbox> findBySentAtIsNullAndFailedPermanentlyTrue();
}
