package ke.retailpos.pos_backend.returns;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

  private final ReturnService returnService;

  public ReturnController(ReturnService returnService) {
    this.returnService = returnService;
  }

  @PostMapping
  public ResponseEntity<?> processReturn(@Valid @RequestBody ReturnService.ProcessReturnRequest request) {
    try {
      ReturnService.ProcessReturnRequest effective = request;
      AuthenticatedUser actor = AuditLogService.currentActor();
      if (actor != null) {
        UUID reqCashier = request.cashierId();
        if (reqCashier != null && !reqCashier.equals(actor.userId())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(new ErrorResponse("Cannot process return under another user identity"));
        }
        effective = new ReturnService.ProcessReturnRequest(
            request.saleId(),
            request.receiptNumber(),
            actor.userId(),
            request.reason(),
            request.items(),
            request.refundMethod()
        );
      }
      ReturnService.ReturnResponseDto resp = returnService.processReturn(effective);
      return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
  }

  @GetMapping
  public List<ReturnService.ReturnResponseDto> getAllReturns(@RequestParam(defaultValue = "200") int limit) {
    int cap = Math.max(1, Math.min(limit, 500));
    return returnService.getAllReturns().stream().limit(cap).toList();
  }

  public record ErrorResponse(String message) {}
}
