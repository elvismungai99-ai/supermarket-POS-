package ke.retailpos.pos_backend.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.inventory.StockAdjustmentService;
import ke.retailpos.pos_backend.inventory.StockReceivingController;
import ke.retailpos.pos_backend.providers.tax.TaxInvoiceProvider;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OutboxProcessorTest {

  private OutboxRepository outboxRepository;
  private TaxInvoiceProvider taxInvoiceProvider;
  private ObjectMapper objectMapper;
  private AccountingService accountingService;
  private StockReceivingController stockReceivingController;
  private StockAdjustmentService stockAdjustmentService;
  private OutboxProcessor outboxProcessor;

  @BeforeEach
  void setUp() {
    outboxRepository = mock(OutboxRepository.class);
    taxInvoiceProvider = mock(TaxInvoiceProvider.class);
    accountingService = mock(AccountingService.class);
    stockReceivingController = mock(StockReceivingController.class);
    stockAdjustmentService = mock(StockAdjustmentService.class);
    objectMapper = new ObjectMapper();
    outboxProcessor = new OutboxProcessor(
        outboxRepository,
        taxInvoiceProvider,
        objectMapper,
        accountingService,
        stockReceivingController,
        stockAdjustmentService
    );
  }

  @Test
  void testProcessPendingOutboxSubmitsEtimsInvoice() {
    UUID saleId = UUID.randomUUID();
    String payload = String.format("""
        {
          "saleId": "%s",
          "receiptNumber": "REC-20261112-001",
          "totalAmount": 165.00,
          "taxableAmount": 142.24,
          "taxAmount": 22.76
        }
        """, saleId);

    Outbox item = new Outbox(UUID.randomUUID(), "etims.invoice.queued", payload, Instant.now(), null);

    when(outboxRepository.findBySentAtIsNull()).thenReturn(List.of(item));
    when(taxInvoiceProvider.submitInvoice(any())).thenReturn(
        new TaxInvoiceProvider.TaxInvoiceResponse(true, "KRA-VSCU-12345", "DATA", "https://etims.kra.go.ke", null)
    );

    int processed = outboxProcessor.processPendingOutbox();

    assertEquals(1, processed);
    assertNotNull(item.getSentAt());
    verify(outboxRepository).save(item);
  }

  @Test
  void testFailedAttemptSchedulesExponentialBackoff() {
    UUID shopId = UUID.randomUUID();
    String payload = String.format("{\"shopId\":\"%s\",\"businessDate\":\"2026-11-19\"}", shopId);
    Outbox item = new Outbox(UUID.randomUUID(), "accounting.erpnext.journal_entry", payload, Instant.now(), null);

    when(outboxRepository.findDuePending(any())).thenReturn(List.of(item));
    when(accountingService.postToErpNext(eq(shopId), any())).thenReturn(
        new AccountingService.PostResult(false, "FAILED", null, "Connection refused")
    );

    int processed = outboxProcessor.processPendingOutbox();

    assertEquals(0, processed);
    assertNull(item.getSentAt());
    assertEquals(1, item.getAttemptCount());
    assertFalse(item.isFailedPermanently());
    assertNotNull(item.getNextRetryAt());
    assertEquals("Connection refused", item.getLastError());
    assertEquals(15L, OutboxProcessor.computeBackoffSeconds(1));
    assertEquals(60L, OutboxProcessor.computeBackoffSeconds(2));
    assertEquals(300L, OutboxProcessor.computeBackoffSeconds(3));
    assertEquals(1800L, OutboxProcessor.computeBackoffSeconds(4));
    assertEquals(7200L, OutboxProcessor.computeBackoffSeconds(5));
    assertEquals(21600L, OutboxProcessor.computeBackoffSeconds(6));
    verify(outboxRepository).save(item);
  }

  @Test
  void testDeadLetterAfterMaxAttemptsAndManualReplay() {
    UUID receiptId = UUID.randomUUID();
    String payload = String.format("{\"receiptId\":\"%s\"}", receiptId);
    Outbox item = new Outbox(UUID.randomUUID(), "accounting.erpnext.purchase_invoice", payload, Instant.now(), null);
    item.setAttemptCount(OutboxProcessor.MAX_ATTEMPTS - 1);

    when(outboxRepository.findDuePending(any())).thenReturn(List.of(item));
    when(stockReceivingController.retryErpNextPostForReceipt(receiptId)).thenReturn(false);

    int processed = outboxProcessor.processPendingOutbox();

    assertEquals(0, processed);
    assertEquals(OutboxProcessor.MAX_ATTEMPTS, item.getAttemptCount());
    assertTrue(item.isFailedPermanently());
    assertNull(item.getNextRetryAt());

    // Now simulate Super Admin manual replay
    when(outboxRepository.findBySentAtIsNullAndFailedPermanentlyTrue()).thenReturn(List.of(item));
    int requeued = outboxProcessor.retryFailedEntries();

    assertEquals(1, requeued);
    assertFalse(item.isFailedPermanently());
    assertEquals(0, item.getAttemptCount());
    assertNotNull(item.getNextRetryAt());
    assertNull(item.getLastError());
  }

  @Test
  void testDispatchesPurchaseInvoiceAndStockAdjustmentTopics() {
    UUID receiptId = UUID.randomUUID();
    UUID adjustmentId = UUID.randomUUID();
    Outbox piItem = new Outbox(
        UUID.randomUUID(),
        "accounting.erpnext.purchase_invoice",
        String.format("{\"receiptId\":\"%s\"}", receiptId),
        Instant.now(),
        null
    );
    Outbox adjItem = new Outbox(
        UUID.randomUUID(),
        "accounting.erpnext.stock_adjustment",
        String.format("{\"adjustmentId\":\"%s\"}", adjustmentId),
        Instant.now(),
        null
    );

    when(outboxRepository.findDuePending(any())).thenReturn(List.of(piItem, adjItem));
    when(stockReceivingController.retryErpNextPostForReceipt(receiptId)).thenReturn(true);
    when(stockAdjustmentService.retryErpNextPost(adjustmentId)).thenReturn(true);

    int processed = outboxProcessor.processPendingOutbox();

    assertEquals(2, processed);
    assertNotNull(piItem.getSentAt());
    assertNotNull(adjItem.getSentAt());
    verify(stockReceivingController).retryErpNextPostForReceipt(receiptId);
    verify(stockAdjustmentService).retryErpNextPost(adjustmentId);
  }
}
