package ke.retailpos.pos_backend.sales;

import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

  private final SaleService saleService;

  public SaleController(SaleService saleService) {
    this.saleService = saleService;
  }

  @PostMapping
  public ResponseEntity<?> createSale(@RequestBody CreateSaleRequest request) {
    try {
      CreateSaleRequest effective = request;
      AuthenticatedUser actor = AuditLogService.currentActor();
      if (actor != null) {
        UUID reqCashier = request.cashierId();
        if (reqCashier != null && !reqCashier.equals(actor.userId())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
              .body(new ErrorResponse("Cannot attribute sale to another cashier identity"));
        }
        effective = new CreateSaleRequest(
            request.shopId(),
            request.tillId(),
            actor.userId(),
            request.items(),
            request.payment(),
            request.clientSaleId(),
            request.payments(),
            request.overrideBy(),
            request.overrideToken()
        );
      }
      ReceiptDto receipt = saleService.processCashSale(effective);
      return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
  }

  public record ErrorResponse(String message) {}
}
