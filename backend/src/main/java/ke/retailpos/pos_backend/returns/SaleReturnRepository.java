package ke.retailpos.pos_backend.returns;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {
  List<SaleReturn> findByOriginalSaleIdOrderByCreatedAtDesc(UUID originalSaleId);
  List<SaleReturn> findAllByOrderByCreatedAtDesc();
  List<SaleReturn> findByCreatedAtBetween(Instant start, Instant end);
  List<SaleReturn> findByCashierIdAndCreatedAtBetween(UUID cashierId, Instant start, Instant end);

  @Query("select r from SaleReturn r join fetch r.cashier c join fetch r.originalSale s order by r.createdAt desc")
  List<SaleReturn> findAllWithDetails();
}
