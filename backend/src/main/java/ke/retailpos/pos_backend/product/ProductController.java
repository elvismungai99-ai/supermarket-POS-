package ke.retailpos.pos_backend.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductRepository products;
  private final JdbcTemplate jdbcTemplate;
  private final BarcodeParserService barcodeParserService;
  private final AuditLogService auditLogService;

  public ProductController(ProductRepository products, JdbcTemplate jdbcTemplate) {
    this(products, jdbcTemplate, new BarcodeParserService(), null);
  }

  @Autowired
  public ProductController(
      ProductRepository products,
      JdbcTemplate jdbcTemplate,
      BarcodeParserService barcodeParserService,
      AuditLogService auditLogService
  ) {
    this.products = products;
    this.jdbcTemplate = jdbcTemplate;
    this.barcodeParserService = barcodeParserService;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/by-barcode/{barcode}")
  public ResponseEntity<ProductDto> byBarcode(@PathVariable String barcode) {
    var exact = products.findByBarcode(barcode);
    if (exact.isPresent()) {
      return ResponseEntity.ok(toDto(exact.get(), barcode, null));
    }

    var parsedOpt = barcodeParserService.parseWeightedEan13(barcode);
    if (parsedOpt.isPresent()) {
      var parsed = parsedOpt.get();
      var byPrefix = products.findByBarcode(parsed.prefixPluBarcode())
          .or(() -> products.findByBarcode(parsed.shortPlu()));
      if (byPrefix.isPresent()) {
        return ResponseEntity.ok(toDto(byPrefix.get(), barcode, parsed.weightKg()));
      }
    }
    return ResponseEntity.notFound().build();
  }

  private ProductDto toDto(Product p, String barcode) {
    return toDto(p, barcode, null);
  }

  private ProductDto toDto(Product p, String barcode, BigDecimal detectedWeightKg) {
    return new ProductDto(
        p.getId(),
        p.getName(),
        p.getPriceKes(),
        p.getTaxCategory(),
        p.getStockQuantity(),
        p.getReorderLevel(),
        barcode,
        p.getCostPriceKes(),
        detectedWeightKg
    );
  }

  @GetMapping("/search")
  public List<ProductDto> searchProducts(@RequestParam(value = "q", defaultValue = "") String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<Product> found = products.searchByName(query.trim());
    return mapProductsWithBarcodes(found);
  }

  @GetMapping
  public List<ProductDto> getAllProducts(
      @RequestParam(defaultValue = "500") int limit,
      @RequestParam(defaultValue = "0") int page
  ) {
    int cap = Math.max(1, Math.min(limit, 500));
    int safePage = Math.max(0, page);
    Page<Product> paged = products.findAll(PageRequest.of(safePage, cap, Sort.by("name").ascending()));
    List<Product> list = paged != null
        ? paged.getContent()
        : products.findAll().stream().limit(cap).toList();
    return mapProductsWithBarcodes(list);
  }

  private List<ProductDto> mapProductsWithBarcodes(List<Product> list) {
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = list.stream().map(Product::getId).toList();
    Map<UUID, String> barcodeByProductId = new HashMap<>();
    List<Object[]> batchRows = products.findPrimaryBarcodesByProductIds(ids);
    if (batchRows != null && !batchRows.isEmpty()) {
      for (Object[] row : batchRows) {
        if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
          UUID pid = row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString());
          barcodeByProductId.putIfAbsent(pid, row[1].toString());
        }
      }
      return list.stream()
          .map(p -> toDto(p, barcodeByProductId.get(p.getId())))
          .toList();
    }
    // Fallback for unit tests that mock only findBarcodeByProductId(...)
    return list.stream()
        .map(p -> toDto(p, products.findBarcodeByProductId(p.getId()).orElse(null)))
        .toList();
  }

  @GetMapping("/low-stock")
  public List<ProductDto> getLowStockAlerts() {
    return products.findLowStockProducts().stream()
        .map(p -> toDto(p, products.findBarcodeByProductId(p.getId()).orElse(null)))
        .toList();
  }

  public record UpdateProductRequest(
      @NotBlank String name,
      @NotNull @DecimalMin("0.00") BigDecimal priceKes,
      @NotBlank String taxCategory,
      BigDecimal stockQuantity,
      BigDecimal reorderLevel,
      BigDecimal costPriceKes
  ) {
    public UpdateProductRequest(String name, BigDecimal priceKes, String taxCategory,
        BigDecimal stockQuantity, BigDecimal reorderLevel) {
      this(name, priceKes, taxCategory, stockQuantity, reorderLevel, null);
    }
  }

  @PutMapping("/{id}")
  @Transactional
  public ResponseEntity<ProductDto> updateProduct(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateProductRequest req
  ) {
    return products.findById(id).map(prod -> {
      BigDecimal oldPrice = prod.getPriceKes();
      BigDecimal oldStock = prod.getStockQuantity();
      String oldTax = prod.getTaxCategory();
      prod.setName(req.name());
      prod.setPriceKes(req.priceKes());
      prod.setTaxCategory(req.taxCategory().toUpperCase());
      if (req.stockQuantity() != null) {
        prod.setStockQuantity(req.stockQuantity());
      }
      if (req.reorderLevel() != null) {
        prod.setReorderLevel(req.reorderLevel());
      }
      if (req.costPriceKes() != null) {
        prod.setCostPriceKes(req.costPriceKes());
      }
      Product saved = products.save(prod);
      String barcode = products.findBarcodeByProductId(saved.getId()).orElse(null);
      if (auditLogService != null) {
        auditLogService.record(
            "PRODUCT_UPDATED",
            "product",
            saved.getId().toString(),
            "Updated " + saved.getName() + " price " + oldPrice + "->" + saved.getPriceKes()
                + ", tax " + oldTax + "->" + saved.getTaxCategory()
                + ", stock " + oldStock + "->" + saved.getStockQuantity()
        );
      }
      return ResponseEntity.ok(toDto(saved, barcode));
    }).orElse(ResponseEntity.notFound().build());
  }

  public record CreateProductRequest(
      @NotBlank String name,
      @NotNull @DecimalMin("0.00") BigDecimal priceKes,
      @NotBlank String taxCategory,
      @NotBlank String barcode,
      BigDecimal stockQuantity,
      BigDecimal reorderLevel,
      BigDecimal costPriceKes
  ) {
    public CreateProductRequest(String name, BigDecimal priceKes, String taxCategory, String barcode,
        BigDecimal stockQuantity, BigDecimal reorderLevel) {
      this(name, priceKes, taxCategory, barcode, stockQuantity, reorderLevel, null);
    }
  }

  @PostMapping
  @Transactional
  public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest req) {
    UUID id = UUID.randomUUID();
    BigDecimal stock = req.stockQuantity() != null ? req.stockQuantity() : new BigDecimal("100.000");
    BigDecimal reorder = req.reorderLevel() != null ? req.reorderLevel() : new BigDecimal("10.000");
    BigDecimal cost = req.costPriceKes() != null ? req.costPriceKes() : BigDecimal.ZERO;

    Product product = new Product(id, req.name(), req.priceKes(), req.taxCategory().toUpperCase(), true, stock, reorder, cost);
    Product saved = products.saveAndFlush(product);

    jdbcTemplate.update(
        "INSERT INTO product_barcode (barcode, product_id) VALUES (?, ?) ON CONFLICT (barcode) DO UPDATE SET product_id = excluded.product_id",
        req.barcode(), saved.getId()
    );

    if (auditLogService != null) {
      auditLogService.record(
          "PRODUCT_CREATED",
          "product",
          saved.getId().toString(),
          "Created " + saved.getName() + " (barcode=" + req.barcode() + ", price=" + saved.getPriceKes() + ")"
      );
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved, req.barcode()));
  }
}
