package ke.retailpos.pos_backend.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.providers.tax.TaxInvoiceProvider;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxProcessor {

  private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

  private final OutboxRepository outboxRepository;
  private final TaxInvoiceProvider taxInvoiceProvider;
  private final ObjectMapper objectMapper;
  private final AccountingService accountingService;

  public OutboxProcessor(
      OutboxRepository outboxRepository,
      TaxInvoiceProvider taxInvoiceProvider,
      ObjectMapper objectMapper,
      @Lazy AccountingService accountingService
  ) {
    this.outboxRepository = outboxRepository;
    this.taxInvoiceProvider = taxInvoiceProvider;
    this.objectMapper = objectMapper;
    this.accountingService = accountingService;
  }

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public int processPendingOutbox() {
    List<Outbox> pending = outboxRepository.findBySentAtIsNull();
    if (pending.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (Outbox item : pending) {
      try {
        if ("etims.invoice.queued".equals(item.getTopic())) {
          JsonNode json = objectMapper.readTree(item.getPayload());
          UUID saleId = UUID.fromString(json.get("saleId").asText());
          String receiptNumber = json.get("receiptNumber").asText();
          BigDecimal total = BigDecimal.valueOf(json.get("totalAmount").asDouble());
          BigDecimal taxable = BigDecimal.valueOf(json.get("taxableAmount").asDouble());
          BigDecimal tax = BigDecimal.valueOf(json.get("taxAmount").asDouble());

          var resp = taxInvoiceProvider.submitInvoice(new TaxInvoiceProvider.TaxInvoiceRequest(
              saleId,
              receiptNumber,
              total,
              taxable,
              tax,
              List.of()
          ));

          if (resp.success()) {
            item.setSentAt(Instant.now());
            outboxRepository.save(item);
            processed++;
            log.info("eTIMS outbox invoice processed for receipt {} -> CU: {}", receiptNumber, resp.kraControlUnitNumber());
          } else {
            log.warn("eTIMS invoice submission failed, will retry: {}", resp.errorMessage());
          }
        } else if ("accounting.erpnext.journal_entry".equals(item.getTopic())) {
          JsonNode json = objectMapper.readTree(item.getPayload());
          UUID shopId = UUID.fromString(json.get("shopId").asText());
          LocalDate businessDate = LocalDate.parse(json.get("businessDate").asText());

          var res = accountingService.postToErpNext(shopId, businessDate);
          if (res.success()) {
            item.setSentAt(Instant.now());
            outboxRepository.save(item);
            processed++;
            log.info("Queued ERPNext journal entry processed for shop {} date {} -> Ref: {}",
                shopId, businessDate, res.erpnextJournalEntryId());
          }
        } else {
          // Standard business event (e.g. sales.completed)
          item.setSentAt(Instant.now());
          outboxRepository.save(item);
          processed++;
        }
      } catch (Exception e) {
        log.error("Error processing outbox entry {}: {}", item.getId(), e.getMessage());
      }
    }
    return processed;
  }
}
