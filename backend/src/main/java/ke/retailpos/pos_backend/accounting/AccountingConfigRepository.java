package ke.retailpos.pos_backend.accounting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingConfigRepository extends JpaRepository<AccountingConfig, UUID> {
}
