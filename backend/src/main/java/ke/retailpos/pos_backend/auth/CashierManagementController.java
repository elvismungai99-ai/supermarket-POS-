package ke.retailpos.pos_backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.JwtAuthFilter;
import ke.retailpos.pos_backend.shift.Shift;
import ke.retailpos.pos_backend.shift.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashiers")
public class CashierManagementController {

  private final CashierRepository cashierRepository;
  private final ShiftRepository shiftRepository;
  private final PasswordEncoder passwordEncoder;
  private final LoginRateLimiter rateLimiter;
  private final JdbcTemplate jdbcTemplate;
  private final AuditLogService auditLogService;

  public CashierManagementController(
      CashierRepository cashierRepository,
      ShiftRepository shiftRepository,
      PasswordEncoder passwordEncoder,
      LoginRateLimiter rateLimiter,
      JdbcTemplate jdbcTemplate
  ) {
    this(cashierRepository, shiftRepository, passwordEncoder, rateLimiter, jdbcTemplate, null);
  }

  @Autowired
  public CashierManagementController(
      CashierRepository cashierRepository,
      ShiftRepository shiftRepository,
      PasswordEncoder passwordEncoder,
      LoginRateLimiter rateLimiter,
      JdbcTemplate jdbcTemplate,
      AuditLogService auditLogService
  ) {
    this.cashierRepository = cashierRepository;
    this.shiftRepository = shiftRepository;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.jdbcTemplate = jdbcTemplate;
    this.auditLogService = auditLogService;
  }

  public record CashierDetailsDto(
      UUID id,
      String username,
      String name,
      String role,
      String status,
      boolean active,
      Instant createdAt,
      boolean isOnline,
      UUID activeTillId,
      Instant shiftOpenedAt,
      BigDecimal shiftFloatKes,
      long totalSalesCount,
      BigDecimal totalRevenueKes
  ) {}

  @GetMapping
  public List<CashierDetailsDto> listCashiers() {
    List<Cashier> all = cashierRepository.findAllByOrderByCreatedAtDesc();

    // Query sales aggregate per cashier
    String salesSql = """
        select cashier_id, count(id) as cnt, coalesce(sum(total_kes), 0) as rev
        from sale
        group by cashier_id
        """;
    Map<UUID, Map<String, Object>> salesByCashier = new java.util.HashMap<>();
    jdbcTemplate.queryForList(salesSql).forEach(row -> {
      salesByCashier.put((UUID) row.get("cashier_id"), row);
    });

    return all.stream().map(c -> {
      var activeShift = shiftRepository.findFirstByCashierIdAndStatusOrderByOpenedAtDesc(c.getId(), "OPEN");
      boolean online = activeShift.isPresent();
      UUID activeTill = online ? activeShift.get().getTillId() : null;
      Instant openedAt = online ? activeShift.get().getOpenedAt() : null;
      BigDecimal shiftFloat = online ? activeShift.get().getOpeningFloatKes() : BigDecimal.ZERO;

      Map<String, Object> stats = salesByCashier.get(c.getId());
      long salesCount = stats != null ? ((Number) stats.get("cnt")).longValue() : 0L;
      BigDecimal rev = stats != null ? (BigDecimal) stats.get("rev") : BigDecimal.ZERO;

      return new CashierDetailsDto(
          c.getId(),
          c.getUsername(),
          c.getName(),
          c.getRole(),
          c.getStatus(),
          c.isActive(),
          c.getCreatedAt(),
          online,
          activeTill,
          openedAt,
          shiftFloat,
          salesCount,
          rev
      );
    }).toList();
  }

  @PostMapping("/{id}/authorize")
  @Transactional
  public ResponseEntity<?> authorizeCashier(@PathVariable UUID id) {
    return cashierRepository.findById(id).map(c -> {
      c.setStatus("ACTIVE");
      c.setActive(true);
      cashierRepository.save(c);
      JwtAuthFilter.evictCashier(c.getId());
      rateLimiter.reset(c.getUsername());
      if (auditLogService != null) {
        auditLogService.record("AUTHORIZE_CASHIER", "cashier", c.getId().toString(), "Authorized @" + c.getUsername());
      }
      return ResponseEntity.ok(Map.of(
          "message", "Cashier " + c.getName() + " (@" + c.getUsername() + ") authorized successfully",
          "cashierId", c.getId(),
          "status", c.getStatus()
      ));
    }).orElse(ResponseEntity.notFound().build());
  }

  public record ResetPinRequest(@NotBlank String newPin) {}

  @PostMapping("/{id}/reset-pin")
  @Transactional
  public ResponseEntity<?> resetPin(
      @PathVariable UUID id,
      @Valid @RequestBody ResetPinRequest req
  ) {
    var pinErr = PinPolicy.validate(req.newPin());
    if (pinErr.isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("message", pinErr.get()));
    }
    return cashierRepository.findById(id).map(c -> {
      c.setPinHash(passwordEncoder.encode(req.newPin().trim()));
      // Reset rate limiting lockout immediately
      rateLimiter.reset(c.getUsername());
      cashierRepository.save(c);
      if (auditLogService != null) {
        auditLogService.record("RESET_CASHIER_PIN", "cashier", c.getId().toString(), "Reset PIN for @" + c.getUsername());
      }
      return ResponseEntity.ok(Map.of(
          "message", "PIN successfully reset for " + c.getName() + ". Temporary lockouts cleared.",
          "cashierId", c.getId()
      ));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @Transactional
  public ResponseEntity<?> deleteCashier(@PathVariable UUID id) {
    return cashierRepository.findById(id).map(c -> {
      // Check if cashier has sales or shifts
      Long salesCount = jdbcTemplate.queryForObject(
          "select count(*) from sale where cashier_id = ?", Long.class, id
      );
      Long shiftsCount = jdbcTemplate.queryForObject(
          "select count(*) from shift where cashier_id = ?", Long.class, id
      );

      if ((salesCount != null && salesCount > 0) || (shiftsCount != null && shiftsCount > 0)) {
        // Soft delete to maintain audit log & database referential integrity
        c.setActive(false);
        c.setStatus("DELETED");
        cashierRepository.save(c);
        JwtAuthFilter.evictCashier(c.getId());
        rateLimiter.reset(c.getUsername());
        if (auditLogService != null) {
          auditLogService.record("SOFT_DELETE_CASHIER", "cashier", c.getId().toString(), "Archived @" + c.getUsername());
        }
        return ResponseEntity.ok(Map.of(
            "message", "Cashier marked as DELETED and disabled (archived due to existing financial transaction history)",
            "cashierId", c.getId(),
            "status", "DELETED"
        ));
      } else {
        // Hard delete if no financial history
        cashierRepository.delete(c);
        JwtAuthFilter.evictCashier(id);
        rateLimiter.reset(c.getUsername());
        if (auditLogService != null) {
          auditLogService.record("DELETE_CASHIER", "cashier", id.toString(), "Deleted @" + c.getUsername());
        }
        return ResponseEntity.ok(Map.of(
            "message", "Cashier completely deleted from system",
            "cashierId", id
        ));
      }
    }).orElse(ResponseEntity.notFound().build());
  }
}
