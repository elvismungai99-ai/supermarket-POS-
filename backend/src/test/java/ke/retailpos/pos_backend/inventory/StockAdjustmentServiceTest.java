package ke.retailpos.pos_backend.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingConfig;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.accounting.ErpNextClient;
import ke.retailpos.pos_backend.accounting.JournalEntryDto;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class StockAdjustmentServiceTest {

  private StockAdjustmentRepository adjustmentRepository;
  private ProductRepository productRepository;
  private ProductBatchRepository batchRepository;
  private AccountingService accountingService;
  private ErpNextClient erpNextClient;
  private OutboxRepository outboxRepository;
  private ObjectMapper objectMapper;
  private AuditLogService auditLogService;
  private StockAdjustmentService service;

  private Product milk;
  private AccountingConfig cfg;

  @BeforeEach
  void setUp() {
    adjustmentRepository = mock(StockAdjustmentRepository.class);
    productRepository = mock(ProductRepository.class);
    batchRepository = mock(ProductBatchRepository.class);
    accountingService = mock(AccountingService.class);
    erpNextClient = mock(ErpNextClient.class);
    outboxRepository = mock(OutboxRepository.class);
    objectMapper = new ObjectMapper();
    auditLogService = mock(AuditLogService.class);

    service = new StockAdjustmentService(
        adjustmentRepository,
        productRepository,
        batchRepository,
        accountingService,
        erpNextClient,
        outboxRepository,
        objectMapper,
        auditLogService
    );

    milk = new Product();
    milk.setId(UUID.randomUUID());
    milk.setName("Fresh Milk 500ml");
    milk.setPriceKes(new BigDecimal("70.00"));
    milk.setCostPriceKes(new BigDecimal("50.00"));
    milk.setStockQuantity(new BigDecimal("20.000"));

    cfg = new AccountingConfig();
    cfg.setCompanyName("Supermarket POS Ltd");
    cfg.setErpnextBaseUrl("http://localhost:8000");
    cfg.setErpnextApiKey("testkey");
    cfg.setErpnextApiSecret("enc");
    cfg.setSubmitOnPost(true);

    when(productRepository.findById(milk.getId())).thenReturn(Optional.of(milk));
    when(batchRepository.findAvailableBatchesFefo(milk.getId())).thenReturn(List.of());
    when(accountingService.getAccountingConfig()).thenReturn(cfg);
    when(accountingService.getDecryptedApiSecret(cfg)).thenReturn("testsecret");
    when(adjustmentRepository.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void testShrinkageDeductsStockAndPostsBalancedGlJournalEntry() {
    when(erpNextClient.postJournalEntry(anyString(), anyString(), anyString(), any(JournalEntryDto.class), eq(true)))
        .thenReturn(new ErpNextClient.ErpNextResponse(true, "ACC-JV-2026-00012", null, 200));

    var req = new StockAdjustmentService.CreateAdjustmentRequest(
        milk.getId(),
        "SHRINKAGE",
        new BigDecimal("3"),
        "Broken cartons during restock",
        true
    );

    var dto = service.createAdjustment(req);

    assertEquals(new BigDecimal("17.000"), milk.getStockQuantity());
    assertEquals(new BigDecimal("-3.000"), dto.quantityDelta());
    assertEquals(new BigDecimal("150.00"), dto.totalCostKes());
    assertEquals("POSTED", dto.erpnextStatus());
    assertEquals("ACC-JV-2026-00012", dto.erpnextJournalEntryId());

    ArgumentCaptor<JournalEntryDto> jeCaptor = ArgumentCaptor.forClass(JournalEntryDto.class);
    verify(erpNextClient).postJournalEntry(eq("http://localhost:8000"), eq("testkey"), eq("testsecret"), jeCaptor.capture(), eq(true));
    JournalEntryDto postedJe = jeCaptor.getValue();
    assertTrue(postedJe.isBalanced());
    assertNotNull(postedJe.referenceKey());
    assertTrue(postedJe.referenceKey().startsWith("POS-ADJ-"));
    assertEquals(cfg.getAccountCogs(), postedJe.accounts().get(0).account());
    assertEquals(new BigDecimal("150.00"), postedJe.accounts().get(0).debitInAccountCurrency());
    assertEquals(cfg.getAccountInventory(), postedJe.accounts().get(1).account());
    assertEquals(new BigDecimal("150.00"), postedJe.accounts().get(1).creditInAccountCurrency());

    verify(outboxRepository, never()).save(any());
  }

  @Test
  void testOfflineErpNextQueuesStockAdjustmentInOutbox() {
    when(erpNextClient.postJournalEntry(anyString(), anyString(), anyString(), any(JournalEntryDto.class), eq(true)))
        .thenReturn(new ErpNextClient.ErpNextResponse(false, null, "Connection refused — ensure ERPNext is running at http://localhost:8000", 0));

    var req = new StockAdjustmentService.CreateAdjustmentRequest(
        milk.getId(),
        "EXPIRED",
        new BigDecimal("2"),
        "Past sell-by date",
        true
    );

    var dto = service.createAdjustment(req);

    assertEquals(new BigDecimal("18.000"), milk.getStockQuantity());
    assertEquals("QUEUED", dto.erpnextStatus());
    assertNull(dto.erpnextJournalEntryId());

    ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);
    verify(outboxRepository).save(outboxCaptor.capture());
    assertEquals("accounting.erpnext.stock_adjustment", outboxCaptor.getValue().getTopic());
    assertNotNull(outboxCaptor.getValue().getNextRetryAt());
  }
}
