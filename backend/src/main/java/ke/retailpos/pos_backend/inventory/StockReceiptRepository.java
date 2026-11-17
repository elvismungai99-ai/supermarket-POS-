package ke.retailpos.pos_backend.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, UUID> {
  List<StockReceipt> findAllByOrderByReceivedAtDesc();
}
