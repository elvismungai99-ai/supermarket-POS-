package ke.retailpos.pos_backend.inventory;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/adjustments")
public class StockAdjustmentController {

  private final StockAdjustmentService adjustmentService;

  public StockAdjustmentController(StockAdjustmentService adjustmentService) {
    this.adjustmentService = adjustmentService;
  }

  @GetMapping
  public List<StockAdjustmentService.StockAdjustmentDto> listAdjustments(
      @RequestParam(defaultValue = "100") int limit
  ) {
    return adjustmentService.listAdjustments(limit);
  }

  @PostMapping
  public ResponseEntity<StockAdjustmentService.StockAdjustmentDto> createAdjustment(
      @Valid @RequestBody StockAdjustmentService.CreateAdjustmentRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED).body(adjustmentService.createAdjustment(request));
  }
}
