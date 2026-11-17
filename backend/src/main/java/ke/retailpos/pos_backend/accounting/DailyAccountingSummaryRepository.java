package ke.retailpos.pos_backend.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyAccountingSummaryRepository extends JpaRepository<DailyAccountingSummary, UUID> {
  Optional<DailyAccountingSummary> findByShopIdAndBusinessDate(UUID shopId, LocalDate businessDate);
  List<DailyAccountingSummary> findByShopIdOrderByBusinessDateDesc(UUID shopId);
  List<DailyAccountingSummary> findAllByOrderByBusinessDateDesc();
  List<DailyAccountingSummary> findByStatus(String status);
}
