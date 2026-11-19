package ke.retailpos.pos_backend.config;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges expired JWT revocation rows daily so the revoked_token table stays small and fast.
 */
@Component
public class TokenCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

  private final RevokedTokenRepository revokedTokenRepository;

  public TokenCleanupScheduler(RevokedTokenRepository revokedTokenRepository) {
    this.revokedTokenRepository = revokedTokenRepository;
  }

  @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Nairobi")
  @Transactional
  public void purgeExpiredRevokedTokens() {
    Instant now = Instant.now();
    revokedTokenRepository.deleteByExpiresAtBefore(now);
    log.info("Purged expired revoked JWT records older than {}", now);
  }
}
