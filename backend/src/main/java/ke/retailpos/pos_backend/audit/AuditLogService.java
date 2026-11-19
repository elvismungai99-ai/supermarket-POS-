package ke.retailpos.pos_backend.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private static volatile boolean trustForwardedHeaders = false;

  private final AuditLogRepository repository;

  public AuditLogService(AuditLogRepository repository) {
    this(repository, false);
  }

  @Autowired
  public AuditLogService(
      AuditLogRepository repository,
      @Value("${pos.security.trust-forwarded-headers:false}") boolean trustForwarded
  ) {
    this.repository = repository;
    AuditLogService.trustForwardedHeaders = trustForwarded;
  }

  public void record(String action, String targetEntity, String targetId, String details) {
    AuthenticatedUser actor = currentActor();
    record(
        actor != null ? actor.userId() : null,
        actor != null ? actor.username() : null,
        actor != null ? actor.role() : null,
        action,
        targetEntity,
        targetId,
        currentClientIp(),
        details
    );
  }

  public void record(
      UUID actorId,
      String actorUsername,
      String actorRole,
      String action,
      String targetEntity,
      String targetId,
      String ipAddress,
      String details
  ) {
    try {
      AuditLog entry = new AuditLog();
      entry.setId(UUID.randomUUID());
      entry.setActorId(actorId);
      entry.setActorUsername(actorUsername);
      entry.setActorRole(actorRole);
      entry.setAction(action);
      entry.setTargetEntity(targetEntity);
      entry.setTargetId(targetId);
      entry.setIpAddress(ipAddress != null ? ipAddress : currentClientIp());
      entry.setDetails(details);
      entry.setCreatedAt(Instant.now());
      repository.save(entry);
    } catch (Exception e) {
      log.warn("Failed to persist audit log for action={}: {}", action, e.getMessage());
    }
  }

  public List<AuditLog> listRecent(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
  }

  public static AuthenticatedUser currentActor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    return null;
  }

  public static String currentClientIp() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      if (trustForwardedHeaders) {
        // Prefer X-Real-IP set directly by trusted reverse proxy (Nginx $remote_addr)
        String realIp = sra.getRequest().getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
          return realIp.trim();
        }
        // Fallback: rightmost entry in X-Forwarded-For (appended by trusted proxy, not client-spoofed leftmost)
        String xff = sra.getRequest().getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
          String[] parts = xff.split(",");
          return parts[parts.length - 1].trim();
        }
      }
      return sra.getRequest().getRemoteAddr();
    }
    return null;
  }
}
