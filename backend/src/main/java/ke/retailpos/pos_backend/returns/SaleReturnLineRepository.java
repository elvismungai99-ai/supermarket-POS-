package ke.retailpos.pos_backend.returns;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnLineRepository extends JpaRepository<SaleReturnLine, UUID> {
  List<SaleReturnLine> findBySaleReturnId(UUID saleReturnId);
  List<SaleReturnLine> findBySaleReturnIn(List<SaleReturn> returns);
}
