package ke.retailpos.pos_backend.shift;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

  public record OpenShiftRequest(UUID tillId, UUID cashierId, BigDecimal openingFloatKes) {}
  public record CloseShiftRequest(UUID shiftId, BigDecimal closingCashKes) {}
  public record ShiftDto(
      UUID shiftId,
      UUID tillId,
      UUID cashierId,
      String cashierName,
      Instant openedAt,
      Instant closedAt,
      BigDecimal openingFloatKes,
      BigDecimal closingCashKes,
      String status
  ) {}
  public record ErrorResponse(String message) {}

  private final ShiftRepository shifts;
  private final CashierRepository cashiers;

  public ShiftController(ShiftRepository shifts, CashierRepository cashiers) {
    this.shifts = shifts;
    this.cashiers = cashiers;
  }

  @GetMapping("/current")
  public ResponseEntity<?> getCurrentShift(@RequestParam UUID tillId) {
    return shifts.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")
        .map(this::toDto)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/open")
  public ResponseEntity<?> openShift(@RequestBody OpenShiftRequest req) {
    AuthenticatedUser actor = AuditLogService.currentActor();
    UUID cashierId = req.cashierId();
    if (actor != null) {
      if (cashierId != null && !cashierId.equals(actor.userId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("Cannot open a shift under another cashier's identity"));
      }
      cashierId = actor.userId();
    }

    if (req.tillId() == null || cashierId == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("tillId and cashierId are required"));
    }

    var cashierOpt = cashiers.findById(cashierId);
    if (cashierOpt.isEmpty()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Cashier not found"));
    }

    Cashier cashier = cashierOpt.get();
    if (!"CASHIER".equalsIgnoreCase(cashier.getRole())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new ErrorResponse("Only cashiers are permitted to open shifts and operate tills. Managers and Admins have oversight roles and do not work as cashiers."));
    }

    // Check if there is already an open shift on this till
    var existing = shifts.findFirstByTillIdAndStatusOrderByOpenedAtDesc(req.tillId(), "OPEN");
    if (existing.isPresent()) {
      Shift cur = existing.get();
      // If the current cashier already has an active open shift on this till, return it idempotently
      if (cur.getCashier() != null && cur.getCashier().getId().equals(req.cashierId())) {
        return ResponseEntity.ok(toDto(cur));
      }
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new ErrorResponse("Till is already occupied by cashier " +
              (cur.getCashier() != null ? cur.getCashier().getName() : "another user") +
              ". The active shift must be closed first."));
    }

    BigDecimal floatAmount = req.openingFloatKes() != null ? req.openingFloatKes() : BigDecimal.ZERO;
    Shift shift = new Shift(
        UUID.randomUUID(),
        req.tillId(),
        cashier,
        Instant.now(),
        floatAmount,
        "OPEN"
    );
    Shift saved = shifts.save(shift);
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
  }

  @PostMapping("/close")
  public ResponseEntity<?> closeShift(@RequestBody CloseShiftRequest req) {
    if (req.shiftId() == null) {
      return ResponseEntity.badRequest().body(new ErrorResponse("shiftId is required"));
    }

    var shiftOpt = shifts.findById(req.shiftId());
    if (shiftOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    Shift shift = shiftOpt.get();
    // Ownership guard (prevents IDOR where one cashier closes another cashier's shift)
    AuthenticatedUser actor = AuditLogService.currentActor();
    if (actor != null) {
      boolean isPrivileged = "SUPERVISOR".equalsIgnoreCase(actor.role())
          || "SUPER_ADMIN".equalsIgnoreCase(actor.role());
      boolean isOwner = shift.getCashier() != null && shift.getCashier().getId().equals(actor.userId());
      if (!isOwner && !isPrivileged) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("You can only close your own active till shift."));
      }
    }

    if (!"OPEN".equalsIgnoreCase(shift.getStatus())) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Shift is already closed"));
    }

    shift.setClosedAt(Instant.now());
    shift.setClosingCashKes(req.closingCashKes() != null ? req.closingCashKes() : BigDecimal.ZERO);
    shift.setStatus("CLOSED");

    Shift saved = shifts.save(shift);
    return ResponseEntity.ok(toDto(saved));
  }

  private ShiftDto toDto(Shift s) {
    return new ShiftDto(
        s.getId(),
        s.getTillId(),
        s.getCashier().getId(),
        s.getCashier().getName(),
        s.getOpenedAt(),
        s.getClosedAt(),
        s.getOpeningFloatKes(),
        s.getClosingCashKes(),
        s.getStatus()
    );
  }
}
