package ke.retailpos.pos_backend.branch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TillRepository extends JpaRepository<Till, UUID> {
  List<Till> findByShopIdAndActiveTrueOrderByTillNumberAsc(UUID shopId);
  List<Till> findByActiveTrueOrderByTillNumberAsc();
}
