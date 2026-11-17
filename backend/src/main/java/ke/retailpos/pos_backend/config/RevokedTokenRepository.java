package ke.retailpos.pos_backend.config;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
  void deleteByExpiresAtBefore(Instant cutoff);
}
