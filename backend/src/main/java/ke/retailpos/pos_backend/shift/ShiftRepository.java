package ke.retailpos.pos_backend.shift;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
  Optional<Shift> findFirstByTillIdAndStatusOrderByOpenedAtDesc(UUID tillId, String status);
  Optional<Shift> findFirstByCashierIdAndStatusOrderByOpenedAtDesc(UUID cashierId, String status);
  List<Shift> findByClosedAtBetween(Instant start, Instant end);
  List<Shift> findByOpenedAtBetween(Instant start, Instant end);
}
