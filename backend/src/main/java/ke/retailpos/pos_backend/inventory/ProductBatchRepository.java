package ke.retailpos.pos_backend.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID> {

  @Query("""
      select b from ProductBatch b
      where b.product.id = :productId and b.quantityRemaining > 0
      order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate asc, b.receivedAt asc
      """)
  List<ProductBatch> findAvailableBatchesFefo(@Param("productId") UUID productId);

  List<ProductBatch> findByExpiryDateNotNullAndExpiryDateLessThanEqualAndQuantityRemainingGreaterThanOrderByExpiryDateAsc(
      LocalDate cutoffDate,
      BigDecimal minQuantity
  );
}
