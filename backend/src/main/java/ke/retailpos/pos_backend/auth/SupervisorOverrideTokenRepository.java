package ke.retailpos.pos_backend.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupervisorOverrideTokenRepository extends JpaRepository<SupervisorOverrideToken, UUID> {
  Optional<SupervisorOverrideToken> findByIdAndConsumedAtIsNull(UUID id);
}
