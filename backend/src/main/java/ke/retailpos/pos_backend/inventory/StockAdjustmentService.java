package ke.retailpos.pos_backend.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingConfig;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.accounting.ErpNextClient;
import ke.retailpos.pos_backend.accounting.JournalEntryDto;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles stock shrinkage, expiry write-offs, damaged goods, and physical cycle-count adjustments
 * while posting balanced GL-only Journal Entries to ERPNext (with Outbox retry when offline).
 */
@Service
public class StockAdjustmentService {

  private static final Set<String> VALID_TYPES = Set.of("SHRINKAGE", "EXPIRED", "DAMAGED", "CYCLE_COUNT");

  private final StockAdjustmentRepository adjustmentRepository;
  private final ProductRepository productRepository;
  private final ProductBatchRepository batchRepository;
  private final AccountingService accountingService;
  private final ErpNextClient erpNextClient;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final AuditLogService auditLogService;

  @Autowired
  public StockAdjustmentService(
      StockAdjustmentRepository adjustmentRepository,
      ProductRepository productRepository,
      ProductBatchRepository batchRepository,
      AccountingService accountingService,
      ErpNextClient erpNextClient,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper,
      AuditLogService auditLogService
  ) {
    this.adjustmentRepository = adjustmentRepository;
    this.productRepository = productRepository;
    this.batchRepository = batchRepository;
    this.accountingService = accountingService;
    this.erpNextClient = erpNextClient;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.auditLogService = auditLogService;
  }

  public record CreateAdjustmentRequest(
      @NotNull UUID productId,
      @NotBlank String adjustmentType,
      @NotNull BigDecimal quantityDelta,
      @NotBlank String reason,
      boolean postToErpNext
  ) {}

  public record StockAdjustmentDto(
      UUID id,
      UUID productId,
      String productName,
      String adjustmentType,
      BigDecimal quantityDelta,
      BigDecimal unitCostKes,
      BigDecimal totalCostKes,
      String reason,
      String recordedByUsername,
      String erpnextJournalEntryId,
      String erpnextStatus,
      String errorMessage,
      Instant createdAt
  ) {}

  public List<StockAdjustmentDto> listAdjustments(int limit) {
    int cap = Math.max(1, Math.min(limit, 500));
    return adjustmentRepository.findAllByOrderByCreatedAtDesc().stream()
        .limit(cap)
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public StockAdjustmentDto createAdjustment(CreateAdjustmentRequest req) {
    String type = req.adjustmentType().trim().toUpperCase();
    if (!VALID_TYPES.contains(type)) {
      throw new IllegalArgumentException("Unsupported adjustment type: " + type + ". Expected SHRINKAGE, EXPIRED, DAMAGED, or CYCLE_COUNT.");
    }
    if (req.quantityDelta().compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("Quantity adjustment must be non-zero.");
    }

    Product product = productRepository.findById(req.productId())
        .orElseThrow(() -> new IllegalArgumentException("Product not found: " + req.productId()));

    // Shrinkage, expired, and damaged adjustments are always inventory reductions
    BigDecimal effectiveDelta = "CYCLE_COUNT".equals(type)
        ? req.quantityDelta().setScale(3, RoundingMode.HALF_UP)
        : req.quantityDelta().abs().negate().setScale(3, RoundingMode.HALF_UP);

    BigDecimal updatedStock = product.getStockQuantity().add(effectiveDelta).max(BigDecimal.ZERO);
    product.setStockQuantity(updatedStock);
    productRepository.save(product);

    if (effectiveDelta.compareTo(BigDecimal.ZERO) < 0 && batchRepository != null) {
      deductBatchesFefo(product.getId(), effectiveDelta.abs());
    }

    BigDecimal unitCost = product.getCostPriceKes() != null ? product.getCostPriceKes() : BigDecimal.ZERO;
    BigDecimal totalCost = effectiveDelta.abs().multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

    AuthenticatedUser actor = AuditLogService.currentActor();
    StockAdjustment adj = new StockAdjustment();
    adj.setId(UUID.randomUUID());
    adj.setProduct(product);
    adj.setAdjustmentType(type);
    adj.setQuantityDelta(effectiveDelta);
    adj.setUnitCostKes(unitCost.setScale(2, RoundingMode.HALF_UP));
    adj.setTotalCostKes(totalCost);
    adj.setReason(req.reason().trim());
    if (actor != null) {
      adj.setRecordedBy(actor.userId());
      adj.setRecordedByUsername(actor.username());
    }
    adj.setCreatedAt(Instant.now());

    if (req.postToErpNext() && totalCost.compareTo(BigDecimal.ZERO) > 0) {
      syncAdjustmentToErpNext(adj, true);
    } else {
      adj.setErpnextStatus("LOCAL_ONLY");
    }

    StockAdjustment saved = adjustmentRepository.save(adj);
    if (auditLogService != null) {
      auditLogService.record(
          "STOCK_ADJUSTED",
          "stock_adjustment",
          saved.getId().toString(),
          type + " adjustment of " + effectiveDelta + " on " + product.getName() + " (KES " + totalCost + "): " + saved.getReason()
      );
    }
    return toDto(saved);
  }

  @Transactional
  public boolean retryErpNextPost(UUID adjustmentId) {
    var opt = adjustmentRepository.findById(adjustmentId);
    if (opt.isEmpty()) return true;
    StockAdjustment adj = opt.get();
    if ("POSTED".equalsIgnoreCase(adj.getErpnextStatus()) && adj.getErpnextJournalEntryId() != null) {
      return true;
    }
    boolean ok = syncAdjustmentToErpNext(adj, false);
    adjustmentRepository.save(adj);
    return ok;
  }

  private boolean syncAdjustmentToErpNext(StockAdjustment adj, boolean enqueueOnFailure) {
    if (adj.getTotalCostKes().compareTo(BigDecimal.ZERO) <= 0) {
      adj.setErpnextStatus("LOCAL_ONLY");
      return true;
    }
    AccountingConfig cfg = accountingService.getAccountingConfig();
    String secret = accountingService.getDecryptedApiSecret(cfg);
    String costCenter = cfg.getAccountCostCenter();
    boolean isLoss = adj.getQuantityDelta().compareTo(BigDecimal.ZERO) < 0;
    String remark = "POS Stock " + adj.getAdjustmentType() + " — " + adj.getProduct().getName()
        + " (" + adj.getQuantityDelta() + "): " + adj.getReason();
    String refKey = "POS-ADJ-" + adj.getId().toString().substring(0, 8).toUpperCase();

    List<JournalEntryDto.JournalAccountLineDto> lines = isLoss
        ? List.of(
            new JournalEntryDto.JournalAccountLineDto(cfg.getAccountCogs(), adj.getTotalCostKes(), BigDecimal.ZERO, remark, costCenter),
            new JournalEntryDto.JournalAccountLineDto(cfg.getAccountInventory(), BigDecimal.ZERO, adj.getTotalCostKes(), remark, costCenter)
        )
        : List.of(
            new JournalEntryDto.JournalAccountLineDto(cfg.getAccountInventory(), adj.getTotalCostKes(), BigDecimal.ZERO, remark, costCenter),
            new JournalEntryDto.JournalAccountLineDto(cfg.getAccountCogs(), BigDecimal.ZERO, adj.getTotalCostKes(), remark, costCenter)
        );

    JournalEntryDto je = JournalEntryDto.of(
        cfg.getCompanyName(),
        LocalDate.now(AccountingService.KENYA_ZONE),
        remark,
        lines,
        refKey
    );

    ErpNextClient.ErpNextResponse res = erpNextClient.postJournalEntry(
        cfg.getErpnextBaseUrl(),
        cfg.getErpnextApiKey(),
        secret,
        je,
        cfg.isSubmitOnPost()
    );
    if (res.success()) {
      adj.setErpnextStatus("POSTED");
      adj.setErpnextJournalEntryId(res.journalEntryId());
      adj.setErrorMessage(null);
      return true;
    } else {
      adj.setErrorMessage(res.errorMessage());
      if (enqueueOnFailure && outboxRepository != null && objectMapper != null) {
        adj.setErpnextStatus("QUEUED");
        try {
          String json = objectMapper.writeValueAsString(
              Map.of("adjustmentId", adj.getId().toString(), "referenceKey", refKey)
          );
          Outbox row = new Outbox(UUID.randomUUID(), "accounting.erpnext.stock_adjustment", json, Instant.now(), null);
          row.setNextRetryAt(Instant.now().plusSeconds(15));
          outboxRepository.save(row);
        } catch (Exception ignored) {
          adj.setErpnextStatus("FAILED");
        }
      } else {
        adj.setErpnextStatus("FAILED");
      }
      return false;
    }
  }

  private void deductBatchesFefo(UUID productId, BigDecimal qtyToDeduct) {
    BigDecimal remaining = qtyToDeduct;
    for (ProductBatch b : batchRepository.findAvailableBatchesFefo(productId)) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
      BigDecimal take = b.getQuantityRemaining().min(remaining);
      b.setQuantityRemaining(b.getQuantityRemaining().subtract(take));
      batchRepository.save(b);
      remaining = remaining.subtract(take);
    }
  }

  private StockAdjustmentDto toDto(StockAdjustment a) {
    return new StockAdjustmentDto(
        a.getId(),
        a.getProduct().getId(),
        a.getProduct().getName(),
        a.getAdjustmentType(),
        a.getQuantityDelta(),
        a.getUnitCostKes(),
        a.getTotalCostKes(),
        a.getReason(),
        a.getRecordedByUsername(),
        a.getErpnextJournalEntryId(),
        a.getErpnextStatus(),
        a.getErrorMessage(),
        a.getCreatedAt()
    );
  }
}
