package ke.retailpos.pos_backend.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashierRepository extends JpaRepository<Cashier, UUID> {
  Optional<Cashier> findByUsernameAndActiveTrue(String username);
  Optional<Cashier> findByUsername(String username);
  List<Cashier> findByStatusOrderByCreatedAtDesc(String status);
  List<Cashier> findAllByOrderByCreatedAtDesc();
  long countByRoleIgnoreCaseAndActiveTrue(String role);
}
