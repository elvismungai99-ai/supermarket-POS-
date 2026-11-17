package ke.retailpos.pos_backend.sales;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
  List<Outbox> findBySentAtIsNull();
}
