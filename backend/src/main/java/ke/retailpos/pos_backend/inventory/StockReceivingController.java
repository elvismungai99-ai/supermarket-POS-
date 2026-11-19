package ke.retailpos.pos_backend.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingConfig;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.accounting.ErpNextClient;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/inventory")
public class StockReceivingController {

  private final StockReceiptRepository receipts;
  private final ProductRepository products;
  private final AccountingService accountingService;
  private final ErpNextClient erpNextClient;
  private final ProductBatchRepository batchRepository;
  private final AuditLogService auditLogService;
  private final JdbcTemplate jdbcTemplate;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public StockReceivingController(
      StockReceiptRepository receipts,
      ProductRepository products,
      AccountingService accountingService,
      ErpNextClient erpNextClient
  ) {
    this(receipts, products, accountingService, erpNextClient, null, null, null, null, null);
  }

  public StockReceivingController(
      StockReceiptRepository receipts,
      ProductRepository products,
      AccountingService accountingService,
      ErpNextClient erpNextClient,
      ProductBatchRepository batchRepository,
      AuditLogService auditLogService
  ) {
    this(receipts, products, accountingService, erpNextClient, batchRepository, auditLogService, null, null, null);
  }

  public StockReceivingController(
      StockReceiptRepository receipts,
      ProductRepository products,
      AccountingService accountingService,
      ErpNextClient erpNextClient,
      ProductBatchRepository batchRepository,
      AuditLogService auditLogService,
      JdbcTemplate jdbcTemplate
  ) {
    this(receipts, products, accountingService, erpNextClient, batchRepository, auditLogService, jdbcTemplate, null, null);
  }

  @Autowired
  public StockReceivingController(
      StockReceiptRepository receipts,
      ProductRepository products,
      AccountingService accountingService,
      ErpNextClient erpNextClient,
      ProductBatchRepository batchRepository,
      AuditLogService auditLogService,
      JdbcTemplate jdbcTemplate,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper
  ) {
    this.receipts = receipts;
    this.products = products;
    this.accountingService = accountingService;
    this.erpNextClient = erpNextClient;
    this.batchRepository = batchRepository;
    this.auditLogService = auditLogService;
    this.jdbcTemplate = jdbcTemplate;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  public record NewProductInput(
      @NotBlank String name,
      @NotNull @DecimalMin("0.01") BigDecimal priceKes,
      @NotBlank String taxCategory,
      @NotBlank String barcode,
      BigDecimal reorderLevel
  ) {}

  public record ReceiveStockRequest(
      @NotBlank String supplierName,
      String supplierInvoiceNo,
      UUID productId,
      @NotNull @DecimalMin("0.001") BigDecimal quantity,
      @NotNull @DecimalMin("0.00") BigDecimal unitCostKes,
      BigDecimal vatRate,
      UUID receivedBy,
      String notes,
      boolean postToErpNext,
      String batchNumber,
      LocalDate expiryDate,
      @Valid NewProductInput newProduct
  ) {
    public ReceiveStockRequest(
        String supplierName,
        String supplierInvoiceNo,
        UUID productId,
        BigDecimal quantity,
        BigDecimal unitCostKes,
        BigDecimal vatRate,
        UUID receivedBy,
        String notes,
        boolean postToErpNext
    ) {
      this(supplierName, supplierInvoiceNo, productId, quantity, unitCostKes, vatRate, receivedBy, notes, postToErpNext, null, null, null);
    }

    public ReceiveStockRequest(
        String supplierName,
        String supplierInvoiceNo,
        UUID productId,
        BigDecimal quantity,
        BigDecimal unitCostKes,
        BigDecimal vatRate,
        UUID receivedBy,
        String notes,
        boolean postToErpNext,
        String batchNumber,
        LocalDate expiryDate
    ) {
      this(supplierName, supplierInvoiceNo, productId, quantity, unitCostKes, vatRate, receivedBy, notes, postToErpNext, batchNumber, expiryDate, null);
    }
  }

  public record StockReceiptDto(
      UUID id,
      String supplierName,
      String supplierInvoiceNo,
      UUID productId,
      String productName,
      BigDecimal quantity,
      BigDecimal unitCostKes,
      BigDecimal totalCostKes,
      String erpnextPurchaseInvoiceId,
      String erpnextStatus,
      String errorMessage,
      Instant receivedAt,
      String batchNumber,
      LocalDate expiryDate
  ) {}

  public record ExpiringBatchDto(
      UUID id,
      UUID productId,
      String productName,
      String batchNumber,
      LocalDate expiryDate,
      BigDecimal quantityRemaining,
      BigDecimal unitCostKes
  ) {}

  @GetMapping("/receipts")
  public List<StockReceiptDto> listReceipts(@RequestParam(defaultValue = "200") int limit) {
    int cap = Math.max(1, Math.min(limit, 500));
    return receipts.findAllByOrderByReceivedAtDesc().stream()
        .limit(cap)
        .map(this::toDto)
        .toList();
  }

  @GetMapping("/expiring")
  public List<ExpiringBatchDto> listExpiringBatches(@RequestParam(defaultValue = "14") int days) {
    if (batchRepository == null) return List.of();
    LocalDate cutoff = LocalDate.now(AccountingService.KENYA_ZONE).plusDays(Math.max(0, days));
    return batchRepository
        .findByExpiryDateNotNullAndExpiryDateLessThanEqualAndQuantityRemainingGreaterThanOrderByExpiryDateAsc(
            cutoff, BigDecimal.ZERO)
        .stream()
        .map(b -> new ExpiringBatchDto(
            b.getId(),
            b.getProduct().getId(),
            b.getProduct().getName(),
            b.getBatchNumber(),
            b.getExpiryDate(),
            b.getQuantityRemaining(),
            b.getUnitCostKes()
        ))
        .toList();
  }

  @GetMapping("/batches/{productId}")
  public List<ExpiringBatchDto> listProductBatches(@PathVariable UUID productId) {
    if (batchRepository == null) return List.of();
    return batchRepository.findAvailableBatchesFefo(productId).stream()
        .map(b -> new ExpiringBatchDto(
            b.getId(),
            b.getProduct().getId(),
            b.getProduct().getName(),
            b.getBatchNumber(),
            b.getExpiryDate(),
            b.getQuantityRemaining(),
            b.getUnitCostKes()
        ))
        .toList();
  }

  @PostMapping("/receive")
  @Transactional
  public ResponseEntity<?> receiveStock(@Valid @RequestBody ReceiveStockRequest req) {
    Product product;
    if (req.productId() != null) {
      product = products.findById(req.productId()).orElse(null);
      if (product == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
      }
    } else if (req.newProduct() != null) {
      NewProductInput np = req.newProduct();
      String barcode = np.barcode().trim();
      var existingByBarcode = products.findByBarcode(barcode);
      if (existingByBarcode.isPresent()) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body("Barcode '" + barcode + "' already belongs to '" + existingByBarcode.get().getName() + "'. Select it from the product list instead.");
      }

      BigDecimal reorder = np.reorderLevel() != null ? np.reorderLevel() : new BigDecimal("10.000");
      BigDecimal unitCost = req.unitCostKes().setScale(2, RoundingMode.HALF_UP);
      Product createdProduct = new Product(
          UUID.randomUUID(),
          np.name().trim(),
          np.priceKes().setScale(2, RoundingMode.HALF_UP),
          np.taxCategory().trim().toUpperCase(),
          true,
          BigDecimal.ZERO,
          reorder,
          unitCost
      );
      product = products.saveAndFlush(createdProduct);

      if (jdbcTemplate != null) {
        jdbcTemplate.update(
            "INSERT INTO product_barcode (barcode, product_id) VALUES (?, ?) ON CONFLICT (barcode) DO UPDATE SET product_id = excluded.product_id",
            barcode, product.getId()
        );
      }

      if (auditLogService != null) {
        auditLogService.record(
            "PRODUCT_CREATED",
            "product",
            product.getId().toString(),
            "Created during GRN stock receiving: " + product.getName() + " (barcode=" + barcode + ", price=" + product.getPriceKes() + ")"
        );
      }
    } else {
      return ResponseEntity.badRequest().body("Either productId or newProduct details must be provided.");
    }

    BigDecimal newStock = product.getStockQuantity().add(req.quantity());
    product.setStockQuantity(newStock);
    product.setCostPriceKes(req.unitCostKes().setScale(2, RoundingMode.HALF_UP));
    products.save(product);

    BigDecimal totalCost = req.quantity().multiply(req.unitCostKes()).setScale(2, RoundingMode.HALF_UP);
    String batchNo = (req.batchNumber() != null && !req.batchNumber().isBlank())
        ? req.batchNumber().trim()
        : "BATCH-" + LocalDate.now(AccountingService.KENYA_ZONE).toString().replace("-", "");

    StockReceipt sr = new StockReceipt();
    sr.setId(UUID.randomUUID());
    sr.setSupplierName(req.supplierName().trim());
    sr.setSupplierInvoiceNo(req.supplierInvoiceNo() != null ? req.supplierInvoiceNo().trim() : null);
    sr.setProduct(product);
    sr.setQuantity(req.quantity());
    sr.setUnitCostKes(req.unitCostKes().setScale(2, RoundingMode.HALF_UP));
    sr.setVatRate(req.vatRate() != null ? req.vatRate() : BigDecimal.ZERO);
    sr.setTotalCostKes(totalCost);
    sr.setReceivedBy(req.receivedBy());
    sr.setNotes(req.notes());
    sr.setReceivedAt(Instant.now());
    sr.setBatchNumber(batchNo);
    sr.setExpiryDate(req.expiryDate());

    if (req.postToErpNext()) {
      syncReceiptToErpNext(sr);
    }

    StockReceipt saved = receipts.save(sr);

    if (batchRepository != null) {
      ProductBatch batch = new ProductBatch();
      batch.setId(UUID.randomUUID());
      batch.setProduct(product);
      batch.setBatchNumber(batchNo);
      batch.setExpiryDate(req.expiryDate());
      batch.setQuantityRemaining(req.quantity());
      batch.setUnitCostKes(sr.getUnitCostKes());
      batch.setStockReceiptId(saved.getId());
      batch.setReceivedAt(saved.getReceivedAt());
      batchRepository.save(batch);
    }

    if (auditLogService != null) {
      auditLogService.record(
          "STOCK_RECEIVED",
          "stock_receipt",
          saved.getId().toString(),
          "Received " + saved.getQuantity() + " of " + product.getName() + " (Batch " + batchNo + ")"
      );
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
  }

  @PostMapping("/receipts/{id}/post-erpnext")
  @Transactional
  public ResponseEntity<?> postExistingReceiptToErpNext(@PathVariable UUID id) {
    return receipts.findById(id).map(sr -> {
      if ("POSTED".equalsIgnoreCase(sr.getErpnextStatus()) && sr.getErpnextPurchaseInvoiceId() != null) {
        return ResponseEntity.ok(toDto(sr));
      }
      syncReceiptToErpNext(sr);
      return ResponseEntity.ok(toDto(receipts.save(sr)));
    }).orElse(ResponseEntity.notFound().build());
  }

  @Transactional
  public boolean retryErpNextPostForReceipt(UUID receiptId) {
    var opt = receipts.findById(receiptId);
    if (opt.isEmpty()) return true;
    StockReceipt sr = opt.get();
    if ("POSTED".equalsIgnoreCase(sr.getErpnextStatus()) && sr.getErpnextPurchaseInvoiceId() != null) {
      return true;
    }
    boolean ok = syncReceiptToErpNext(sr, false);
    receipts.save(sr);
    return ok;
  }

  private void syncReceiptToErpNext(StockReceipt sr) {
    syncReceiptToErpNext(sr, true);
  }

  private boolean syncReceiptToErpNext(StockReceipt sr, boolean enqueueOnFailure) {
    AccountingConfig cfg = accountingService.getAccountingConfig();
    String decryptedSecret = accountingService.getDecryptedApiSecret(cfg);
    String billNo = (sr.getSupplierInvoiceNo() != null && !sr.getSupplierInvoiceNo().isBlank())
        ? sr.getSupplierInvoiceNo().trim()
        : "POS-GRN-" + sr.getId().toString().substring(0, 8).toUpperCase();

    ErpNextClient.PurchaseInvoicePayload payload = new ErpNextClient.PurchaseInvoicePayload(
        cfg.getCompanyName(),
        sr.getSupplierName(),
        billNo,
        LocalDate.now(AccountingService.KENYA_ZONE).toString(),
        sr.getProduct().getName(),
        cfg.getAccountInventory(),
        cfg.getAccountAccountsPayable(),
        cfg.getAccountCostCenter(),
        sr.getQuantity().doubleValue(),
        sr.getUnitCostKes().doubleValue(),
        cfg.isSubmitOnPost(),
        sr.getVatRate() != null ? sr.getVatRate().doubleValue() : 0.0,
        cfg.getAccountInputVat()
    );
    ErpNextClient.ErpNextResponse res = erpNextClient.postPurchaseInvoice(
        cfg.getErpnextBaseUrl(),
        cfg.getErpnextApiKey(),
        decryptedSecret,
        payload
    );
    if (res.success()) {
      sr.setErpnextStatus("POSTED");
      sr.setErpnextPurchaseInvoiceId(res.journalEntryId());
      sr.setErrorMessage(null);
      return true;
    } else {
      sr.setErrorMessage(res.errorMessage());
      if (enqueueOnFailure && outboxRepository != null && objectMapper != null) {
        sr.setErpnextStatus("QUEUED");
        boolean alreadyQueued = outboxRepository.findBySentAtIsNull().stream()
            .anyMatch(o -> "accounting.erpnext.purchase_invoice".equals(o.getTopic())
                && o.getPayload().contains(sr.getId().toString()));
        if (!alreadyQueued) {
          try {
            String outboxJson = objectMapper.writeValueAsString(
                java.util.Map.of("receiptId", sr.getId().toString(), "billNo", billNo)
            );
            Outbox row = new Outbox(UUID.randomUUID(), "accounting.erpnext.purchase_invoice", outboxJson, Instant.now(), null);
            row.setNextRetryAt(Instant.now().plusSeconds(15));
            outboxRepository.save(row);
          } catch (Exception ignored) {
            sr.setErpnextStatus("FAILED");
          }
        }
      } else {
        sr.setErpnextStatus("FAILED");
      }
      return false;
    }
  }

  private StockReceiptDto toDto(StockReceipt sr) {
    return new StockReceiptDto(
        sr.getId(),
        sr.getSupplierName(),
        sr.getSupplierInvoiceNo(),
        sr.getProduct().getId(),
        sr.getProduct().getName(),
        sr.getQuantity(),
        sr.getUnitCostKes(),
        sr.getTotalCostKes(),
        sr.getErpnextPurchaseInvoiceId(),
        sr.getErpnextStatus(),
        sr.getErrorMessage(),
        sr.getReceivedAt(),
        sr.getBatchNumber(),
        sr.getExpiryDate()
    );
  }
}
