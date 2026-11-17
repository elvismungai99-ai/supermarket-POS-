package ke.retailpos.pos_backend.sales;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {
  List<SaleLine> findBySaleId(UUID saleId);
  List<SaleLine> findBySaleIn(List<Sale> sales);
}
