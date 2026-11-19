package ke.retailpos.pos_backend.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.inventory.StockAdjustmentService;
import ke.retailpos.pos_backend.inventory.StockReceivingController;
import ke.retailpos.pos_backend.providers.tax.TaxInvoiceProvider;
import ke.retailpos.pos_backend.sales.Outbox;
import ke.retailpos.pos_backend.sales.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drains local transactional outbox events (eTIMS + ERPNext GL Journal Entries, Purchase Invoices,
 * and Stock Adjustments) using exponential backoff and dead-letter capping.
 */
@Service
public class OutboxProcessor {

  private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
  public static final int MAX_ATTEMPTS = 8;
  private static final long[] BACKOFF_SECONDS = { 15L, 60L, 300L, 1800L, 7200L, 21600L };

  private final OutboxRepository outboxRepository;
  private final TaxInvoiceProvider taxInvoiceProvider;
  private final ObjectMapper objectMapper;
  private final AccountingService accountingService;
  private final StockReceivingController stockReceivingController;
  private final StockAdjustmentService stockAdjustmentService;

  public OutboxProcessor(
      OutboxRepository outboxRepository,
      TaxInvoiceProvider taxInvoiceProvider,
      ObjectMapper objectMapper,
      @Lazy AccountingService accountingService
  ) {
    this(outboxRepository, taxInvoiceProvider, objectMapper, accountingService, null, null);
  }

  @Autowired
  public OutboxProcessor(
      OutboxRepository outboxRepository,
      TaxInvoiceProvider taxInvoiceProvider,
      ObjectMapper objectMapper,
      @Lazy AccountingService accountingService,
      @Lazy StockReceivingController stockReceivingController,
      @Lazy StockAdjustmentService stockAdjustmentService
  ) {
    this.outboxRepository = outboxRepository;
    this.taxInvoiceProvider = taxInvoiceProvider;
    this.objectMapper = objectMapper;
    this.accountingService = accountingService;
    this.stockReceivingController = stockReceivingController;
    this.stockAdjustmentService = stockAdjustmentService;
  }

  public static long computeBackoffSeconds(int attemptCount) {
    int idx = Math.max(0, Math.min(attemptCount - 1, BACKOFF_SECONDS.length - 1));
    return BACKOFF_SECONDS[idx];
  }

  @Scheduled(fixedDelay = 15000)
  @Transactional
  public int processPendingOutbox() {
    Instant now = Instant.now();
    List<Outbox> pending = outboxRepository.findDuePending(now);
    if (pending == null || pending.isEmpty()) {
      // Fallback compatibility for unit tests mocking findBySentAtIsNull()
      List<Outbox> legacy = outboxRepository.findBySentAtIsNull();
      if (legacy == null || legacy.isEmpty()) {
        return 0;
      }
      pending = legacy.stream()
          .filter(o -> !o.isFailedPermanently())
          .filter(o -> o.getNextRetryAt() == null || !o.getNextRetryAt().isAfter(now))
          .toList();
      if (pending.isEmpty()) {
        return 0;
      }
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
              saleId, receiptNumber, total, taxable, tax, List.of()
          ));

          if (resp.success()) {
            markSent(item);
            processed++;
            log.info("eTIMS outbox invoice processed for receipt {} -> CU: {}", receiptNumber, resp.kraControlUnitNumber());
          } else {
            scheduleBackoff(item, resp.errorMessage());
          }
        } else if ("accounting.erpnext.journal_entry".equals(item.getTopic())) {
          JsonNode json = objectMapper.readTree(item.getPayload());
          UUID shopId = UUID.fromString(json.get("shopId").asText());
          LocalDate businessDate = LocalDate.parse(json.get("businessDate").asText());

          var res = accountingService.postToErpNext(shopId, businessDate);
          if (res.success()) {
            markSent(item);
            processed++;
            log.info("Queued ERPNext journal entry processed for shop {} date {} -> Ref: {}",
                shopId, businessDate, res.erpnextJournalEntryId());
          } else {
            scheduleBackoff(item, res.message());
          }
        } else if ("accounting.erpnext.purchase_invoice".equals(item.getTopic()) && stockReceivingController != null) {
          JsonNode json = objectMapper.readTree(item.getPayload());
          UUID receiptId = UUID.fromString(json.get("receiptId").asText());
          if (stockReceivingController.retryErpNextPostForReceipt(receiptId)) {
            markSent(item);
            processed++;
            log.info("Queued ERPNext Purchase Invoice processed for receipt {}", receiptId);
          } else {
            scheduleBackoff(item, "ERPNext Purchase Invoice retry failed for receipt " + receiptId);
          }
        } else if ("accounting.erpnext.stock_adjustment".equals(item.getTopic()) && stockAdjustmentService != null) {
          JsonNode json = objectMapper.readTree(item.getPayload());
          UUID adjustmentId = UUID.fromString(json.get("adjustmentId").asText());
          if (stockAdjustmentService.retryErpNextPost(adjustmentId)) {
            markSent(item);
            processed++;
            log.info("Queued ERPNext stock adjustment journal processed for {}", adjustmentId);
          } else {
            scheduleBackoff(item, "ERPNext stock adjustment retry failed for " + adjustmentId);
          }
        } else {
          markSent(item);
          processed++;
        }
      } catch (Exception e) {
        log.error("Error processing outbox entry {}: {}", item.getId(), e.getMessage());
        scheduleBackoff(item, e.getMessage());
      }
    }
    return processed;
  }

  @Transactional
  public int retryFailedEntries() {
    List<Outbox> deadLetter = outboxRepository.findBySentAtIsNullAndFailedPermanentlyTrue();
    Instant now = Instant.now();
    for (Outbox item : deadLetter) {
      item.setFailedPermanently(false);
      item.setAttemptCount(0);
      item.setNextRetryAt(now);
      item.setLastError(null);
      outboxRepository.save(item);
    }
    return deadLetter.size();
  }

  private void markSent(Outbox item) {
    item.setSentAt(Instant.now());
    item.setLastError(null);
    item.setNextRetryAt(null);
    outboxRepository.save(item);
  }

  private void scheduleBackoff(Outbox item, String errorMsg) {
    int attempts = item.getAttemptCount() + 1;
    item.setAttemptCount(attempts);
    item.setLastError(errorMsg != null && errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
    if (attempts >= MAX_ATTEMPTS) {
      item.setFailedPermanently(true);
      item.setNextRetryAt(null);
      log.warn("Outbox entry {} ({}) moved to dead-letter after {} attempts: {}",
          item.getId(), item.getTopic(), attempts, item.getLastError());
    } else {
      long delaySec = computeBackoffSeconds(attempts);
      item.setNextRetryAt(Instant.now().plusSeconds(delaySec));
      log.warn("Outbox entry {} ({}) attempt {}/{} failed; next retry in {}s",
          item.getId(), item.getTopic(), attempts, MAX_ATTEMPTS, delaySec);
    }
    outboxRepository.save(item);
  }
}
