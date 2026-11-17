package ke.retailpos.pos_backend.sales;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
  List<Sale> findByShopIdAndCreatedAtBetween(UUID shopId, Instant start, Instant end);
  List<Sale> findByCreatedAtBetween(Instant start, Instant end);
  List<Sale> findByCashierIdAndCreatedAtBetween(UUID cashierId, Instant start, Instant end);
  Optional<Sale> findByClientSaleId(UUID clientSaleId);
}
