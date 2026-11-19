package ke.retailpos.pos_backend.product;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      value = "select p.* from product p join product_barcode b on b.product_id = p.id where b.barcode = :barcode",
      nativeQuery = true)
  Optional<Product> findByBarcode(@Param("barcode") String barcode);

  @Query(value = "select barcode from product_barcode where product_id = :productId limit 1", nativeQuery = true)
  Optional<String> findBarcodeByProductId(@Param("productId") UUID productId);

  @Query(
      value = "select distinct on (product_id) product_id, barcode from product_barcode where product_id in (:productIds) order by product_id, barcode",
      nativeQuery = true)
  List<Object[]> findPrimaryBarcodesByProductIds(@Param("productIds") Collection<UUID> productIds);

  @Query("select p from Product p where p.stockQuantity <= p.reorderLevel and p.active = true order by p.stockQuantity asc")
  List<Product> findLowStockProducts();

  @Query("select p from Product p where lower(p.name) like lower(concat('%', :query, '%')) and p.active = true order by p.name asc")
  List<Product> searchByName(@Param("query") String query);
}
