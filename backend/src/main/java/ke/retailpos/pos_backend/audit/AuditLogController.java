package ke.retailpos.pos_backend.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

  public record AuditLogDto(
      UUID id,
      UUID actorId,
      String actorUsername,
      String actorRole,
      String action,
      String targetEntity,
      String targetId,
      String ipAddress,
      String details,
      Instant createdAt
  ) {}

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  public List<AuditLogDto> getAuditLogs(@RequestParam(defaultValue = "100") int limit) {
    return auditLogService.listRecent(limit).stream()
        .map(a -> new AuditLogDto(
            a.getId(),
            a.getActorId(),
            a.getActorUsername(),
            a.getActorRole(),
            a.getAction(),
            a.getTargetEntity(),
            a.getTargetId(),
            a.getIpAddress(),
            a.getDetails(),
            a.getCreatedAt()
        ))
        .toList();
  }
}
