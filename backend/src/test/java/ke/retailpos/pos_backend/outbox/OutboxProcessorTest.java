package ke.retailpos.pos_backend.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
  private ke.retailpos.pos_backend.accounting.AccountingService accountingService;
  private OutboxProcessor outboxProcessor;

  @BeforeEach
  void setUp() {
    outboxRepository = mock(OutboxRepository.class);
    taxInvoiceProvider = mock(TaxInvoiceProvider.class);
    accountingService = mock(ke.retailpos.pos_backend.accounting.AccountingService.class);
    objectMapper = new ObjectMapper();
    outboxProcessor = new OutboxProcessor(outboxRepository, taxInvoiceProvider, objectMapper, accountingService);
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
}
