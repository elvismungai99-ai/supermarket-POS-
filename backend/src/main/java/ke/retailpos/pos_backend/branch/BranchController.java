package ke.retailpos.pos_backend.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

  private final ShopRepository shopRepository;
  private final TillRepository tillRepository;
  private final AuditLogService auditLogService;

  public BranchController(
      ShopRepository shopRepository,
      TillRepository tillRepository,
      AuditLogService auditLogService
  ) {
    this.shopRepository = shopRepository;
    this.tillRepository = tillRepository;
    this.auditLogService = auditLogService;
  }

  public record BranchOverviewDto(List<Shop> shops, List<Till> tills) {}

  public record CreateShopRequest(@NotBlank String code, @NotBlank String name, String location, String costCenter) {}

  public record CreateTillRequest(@NotNull UUID shopId, @NotBlank String tillNumber, @NotBlank String label) {}

  @GetMapping
  public BranchOverviewDto listBranches() {
    return new BranchOverviewDto(
        shopRepository.findByActiveTrueOrderByNameAsc(),
        tillRepository.findByActiveTrueOrderByTillNumberAsc()
    );
  }

  @PostMapping("/shops")
  public ResponseEntity<Shop> createShop(@Valid @RequestBody CreateShopRequest req) {
    Shop s = new Shop();
    s.setId(UUID.randomUUID());
    s.setCode(req.code().trim().toUpperCase());
    s.setName(req.name().trim());
    s.setLocation(req.location() != null ? req.location().trim() : null);
    s.setCostCenter(req.costCenter() != null && !req.costCenter().isBlank() ? req.costCenter().trim() : "Main - SP");
    s.setActive(true);
    s.setCreatedAt(Instant.now());
    Shop saved = shopRepository.save(s);
    auditLogService.record("CREATE_SHOP", "shop", saved.getId().toString(), "Created branch " + saved.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @PostMapping("/tills")
  public ResponseEntity<?> createTill(@Valid @RequestBody CreateTillRequest req) {
    if (!shopRepository.existsById(req.shopId())) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Shop not found");
    }
    Till t = new Till();
    t.setId(UUID.randomUUID());
    t.setShopId(req.shopId());
    t.setTillNumber(req.tillNumber().trim());
    t.setLabel(req.label().trim());
    t.setActive(true);
    t.setCreatedAt(Instant.now());
    Till saved = tillRepository.save(t);
    auditLogService.record("CREATE_TILL", "till", saved.getId().toString(), "Created till " + saved.getLabel());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }
}
