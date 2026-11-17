package ke.retailpos.pos_backend.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingConfig;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.accounting.ErpNextClient;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class StockReceivingControllerTest {

  private StockReceiptRepository receipts;
  private ProductRepository products;
  private AccountingService accountingService;
  private ErpNextClient erpNextClient;
  private StockReceivingController controller;

  @BeforeEach
  void setUp() {
    receipts = mock(StockReceiptRepository.class);
    products = mock(ProductRepository.class);
    accountingService = mock(AccountingService.class);
    erpNextClient = mock(ErpNextClient.class);

    when(receipts.save(any(StockReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
    when(products.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    AccountingConfig cfg = new AccountingConfig();
    cfg.setCompanyName("Supermarket POS Ltd");
    cfg.setErpnextBaseUrl("http://localhost:8000");
    cfg.setErpnextApiKey("key");
    cfg.setErpnextApiSecret("sec");
    when(accountingService.getAccountingConfig()).thenReturn(cfg);

    controller = new StockReceivingController(receipts, products, accountingService, erpNextClient);
  }

  @Test
  void testReceiveStockUpdatesQuantityAndCostPriceAndPostsPurchaseInvoice() {
    UUID productId = UUID.randomUUID();
    Product milk = new Product(productId, "Brookside Milk 500ml", new BigDecimal("65.00"), "A", true, new BigDecimal("10.000"), new BigDecimal("5.000"), new BigDecimal("45.00"));
    when(products.findById(productId)).thenReturn(Optional.of(milk));
    when(erpNextClient.postPurchaseInvoice(any(), any(), any(), any()))
        .thenReturn(new ErpNextClient.ErpNextResponse(true, "ACC-PINV-2026-0001", null, 200));

    StockReceivingController.ReceiveStockRequest req = new StockReceivingController.ReceiveStockRequest(
        "Brookside Dairy Ltd",
        "INV-2026-01",
        productId,
        new BigDecimal("25.000"),
        new BigDecimal("50.00"),
        BigDecimal.ZERO,
        null,
        "Morning delivery",
        true
    );

    ResponseEntity<?> resp = controller.receiveStock(req);
    assertEquals(HttpStatus.CREATED, resp.getStatusCode());

    StockReceivingController.StockReceiptDto dto = (StockReceivingController.StockReceiptDto) resp.getBody();
    assertNotNull(dto);
    assertEquals(new BigDecimal("1250.00"), dto.totalCostKes());
    assertEquals("POSTED", dto.erpnextStatus());
    assertEquals("ACC-PINV-2026-0001", dto.erpnextPurchaseInvoiceId());

    // Product stock increased 10 -> 35 and unit cost updated 45 -> 50
    assertEquals(new BigDecimal("35.000"), milk.getStockQuantity());
    assertEquals(new BigDecimal("50.00"), milk.getCostPriceKes());
    verify(erpNextClient, times(1)).postPurchaseInvoice(any(), any(), any(), any());
  }

  @Test
  void testReceiveStockMissingProductReturnsNotFound() {
    UUID missingId = UUID.randomUUID();
    when(products.findById(missingId)).thenReturn(Optional.empty());

    StockReceivingController.ReceiveStockRequest req = new StockReceivingController.ReceiveStockRequest(
        "Supplier A",
        null,
        missingId,
        new BigDecimal("5"),
        new BigDecimal("100.00"),
        null,
        null,
        null,
        false
    );

    ResponseEntity<?> resp = controller.receiveStock(req);
    assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
  }

  @Test
  void testReceiveStockWithNewProductCreatesCatalogItemAndAddsExactQuantity() {
    when(products.findByBarcode("616110009999")).thenReturn(Optional.empty());
    when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(erpNextClient.postPurchaseInvoice(any(), any(), any(), any()))
        .thenReturn(new ErpNextClient.ErpNextResponse(false, null, "Offline", 503));

    StockReceivingController.NewProductInput np = new StockReceivingController.NewProductInput(
        "Pembe Maize Flour 2kg",
        new BigDecimal("185.00"),
        "B",
        "616110009999",
        new BigDecimal("15.000")
    );
    StockReceivingController.ReceiveStockRequest req = new StockReceivingController.ReceiveStockRequest(
        "Pembe Millers Ltd",
        "INV-PM-88",
        null,
        new BigDecimal("40.000"),
        new BigDecimal("150.00"),
        BigDecimal.ZERO,
        null,
        null,
        false,
        "BATCH-PM-01",
        null,
        np
    );

    ResponseEntity<?> resp = controller.receiveStock(req);
    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    StockReceivingController.StockReceiptDto dto = (StockReceivingController.StockReceiptDto) resp.getBody();
    assertNotNull(dto);
    assertEquals("Pembe Maize Flour 2kg", dto.productName());
    assertEquals(new BigDecimal("40.000"), dto.quantity());
    assertEquals(new BigDecimal("6000.00"), dto.totalCostKes());
    verify(products).saveAndFlush(argThat(p -> "Pembe Maize Flour 2kg".equals(p.getName()) && "B".equals(p.getTaxCategory())));
    verify(products).save(argThat(p -> new BigDecimal("40.000").compareTo(p.getStockQuantity()) == 0));
  }
}
