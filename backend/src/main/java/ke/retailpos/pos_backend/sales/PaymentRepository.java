package ke.retailpos.pos_backend.sales;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  List<Payment> findBySaleId(UUID saleId);
  List<Payment> findBySaleIdIn(List<UUID> saleIds);
  Optional<Payment> findByReference(String reference);
  List<Payment> findByState(String state);
}
