package ke.retailpos.pos_backend.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.SecretEncryptionService;
import ke.retailpos.pos_backend.returns.SaleReturn;
import ke.retailpos.pos_backend.returns.SaleReturnLine;
import ke.retailpos.pos_backend.returns.SaleReturnLineRepository;
import ke.retailpos.pos_backend.returns.SaleReturnRepository;
import ke.retailpos.pos_backend.sales.*;
import ke.retailpos.pos_backend.shift.Shift;
import ke.retailpos.pos_backend.shift.ShiftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccountingService {

  private static final Logger log = LoggerFactory.getLogger(AccountingService.class);
  public static final ZoneId KENYA_ZONE = ZoneId.of("Africa/Nairobi");
  public static final UUID DEFAULT_CONFIG_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private final DailyAccountingSummaryRepository summaryRepository;
  private final AccountingConfigRepository configRepository;
  private final SaleRepository saleRepository;
  private final SaleLineRepository saleLineRepository;
  private final PaymentRepository paymentRepository;
  private final SaleReturnRepository returnRepository;
  private final SaleReturnLineRepository returnLineRepository;
  private final ShiftRepository shiftRepository;
  private final OutboxRepository outboxRepository;
  private final AccountingExporter accountingExporter;
  private final ErpNextClient erpNextClient;
  private final ObjectMapper objectMapper;
  private final SecretEncryptionService secretEncryptionService;
  private final AuditLogService auditLogService;

  public AccountingService(
      DailyAccountingSummaryRepository summaryRepository,
      AccountingConfigRepository configRepository,
      SaleRepository saleRepository,
      SaleLineRepository saleLineRepository,
      PaymentRepository paymentRepository,
      SaleReturnRepository returnRepository,
      SaleReturnLineRepository returnLineRepository,
      ShiftRepository shiftRepository,
      OutboxRepository outboxRepository,
      AccountingExporter accountingExporter,
      ErpNextClient erpNextClient,
      ObjectMapper objectMapper
  ) {
    this(
        summaryRepository, configRepository, saleRepository, saleLineRepository,
        paymentRepository, returnRepository, returnLineRepository, shiftRepository,
        outboxRepository, accountingExporter, erpNextClient, objectMapper, null, null
    );
  }

  @Autowired
  public AccountingService(
      DailyAccountingSummaryRepository summaryRepository,
      AccountingConfigRepository configRepository,
      SaleRepository saleRepository,
      SaleLineRepository saleLineRepository,
      PaymentRepository paymentRepository,
      SaleReturnRepository returnRepository,
      SaleReturnLineRepository returnLineRepository,
      ShiftRepository shiftRepository,
      OutboxRepository outboxRepository,
      AccountingExporter accountingExporter,
      ErpNextClient erpNextClient,
      ObjectMapper objectMapper,
      SecretEncryptionService secretEncryptionService,
      AuditLogService auditLogService
  ) {
    this.summaryRepository = summaryRepository;
    this.configRepository = configRepository;
    this.saleRepository = saleRepository;
    this.saleLineRepository = saleLineRepository;
    this.paymentRepository = paymentRepository;
    this.returnRepository = returnRepository;
    this.returnLineRepository = returnLineRepository;
    this.shiftRepository = shiftRepository;
    this.outboxRepository = outboxRepository;
    this.accountingExporter = accountingExporter;
    this.erpNextClient = erpNextClient;
    this.objectMapper = objectMapper;
    this.secretEncryptionService = secretEncryptionService;
    this.auditLogService = auditLogService;
  }

  public record DailySummaryResult(
      DailyAccountingSummary summary,
      JournalEntryDto journalEntry
  ) {}

  public record PostResult(
      boolean success,
      String status,
      String erpnextJournalEntryId,
      String message
  ) {}

  /**
   * Generates or refreshes the end-of-day summary for a shop on a business date.
   * Ensures idempotency: unique constraint on (shop_id, business_date).
   */
  @Transactional
  public DailySummaryResult generateDailySummary(UUID shopId, LocalDate businessDate) {
    Objects.requireNonNull(shopId, "shopId is required");
    Objects.requireNonNull(businessDate, "businessDate is required");

    Instant startOfDay = businessDate.atStartOfDay(KENYA_ZONE).toInstant();
    Instant endOfDay = businessDate.plusDays(1).atStartOfDay(KENYA_ZONE).toInstant();

    // 1. Fetch sales for the day
    List<Sale> sales = saleRepository.findByShopIdAndCreatedAtBetween(shopId, startOfDay, endOfDay);
    if (sales.isEmpty()) {
      // Also check by createdAt across all if shopId is not strictly isolated
      sales = saleRepository.findByCreatedAtBetween(startOfDay, endOfDay);
    }

    BigDecimal salesStandardRatedNet = BigDecimal.ZERO;
    BigDecimal salesSpecialRatedNet = BigDecimal.ZERO;
    BigDecimal salesZeroRatedNet = BigDecimal.ZERO;
    BigDecimal salesExemptNet = BigDecimal.ZERO;
    BigDecimal outputVat = BigDecimal.ZERO;
    BigDecimal totalGrossSales = BigDecimal.ZERO;
    BigDecimal cogsTotal = BigDecimal.ZERO;
    BigDecimal loyaltyRedeemed = BigDecimal.ZERO;

    if (!sales.isEmpty()) {
      List<SaleLine> lines = saleLineRepository.findBySaleIn(sales);
      for (SaleLine line : lines) {
        String taxCat = line.getTaxCategory() != null ? line.getTaxCategory().toUpperCase() : "A";
        BigDecimal lineTotal = line.getLineTotalKes() != null ? line.getLineTotalKes() : BigDecimal.ZERO;
        BigDecimal taxAmount = line.getTaxAmountKes() != null ? line.getTaxAmountKes() : BigDecimal.ZERO;
        BigDecimal netAmount = lineTotal.subtract(taxAmount);

        if ("A".equals(taxCat)) {
          salesStandardRatedNet = salesStandardRatedNet.add(netAmount);
          outputVat = outputVat.add(taxAmount);
        } else if ("C".equals(taxCat)) {
          salesSpecialRatedNet = salesSpecialRatedNet.add(netAmount);
          outputVat = outputVat.add(taxAmount);
        } else if ("B".equals(taxCat)) {
          salesZeroRatedNet = salesZeroRatedNet.add(lineTotal);
        } else if ("E".equals(taxCat)) {
          salesExemptNet = salesExemptNet.add(lineTotal);
        } else {
          salesStandardRatedNet = salesStandardRatedNet.add(netAmount);
          outputVat = outputVat.add(taxAmount);
        }

        BigDecimal lineCost = line.getCostPriceKes();
        if ((lineCost == null || lineCost.compareTo(BigDecimal.ZERO) == 0)
            && line.getProduct() != null && line.getProduct().getCostPriceKes() != null
            && line.getProduct().getCostPriceKes().compareTo(BigDecimal.ZERO) > 0) {
          lineCost = line.getProduct().getCostPriceKes().multiply(line.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        }
        if (lineCost != null) {
          cogsTotal = cogsTotal.add(lineCost);
        }
      }

      for (Sale sale : sales) {
        if (sale.getTotalKes() != null) {
          totalGrossSales = totalGrossSales.add(sale.getTotalKes());
        }
        if (sale.getLoyaltyRedeemedKes() != null) {
          loyaltyRedeemed = loyaltyRedeemed.add(sale.getLoyaltyRedeemedKes());
        }
      }
    }

    // 2. Takings by payment method
    BigDecimal cashTakings = BigDecimal.ZERO;
    BigDecimal mpesaTakings = BigDecimal.ZERO;
    BigDecimal bankTakings = BigDecimal.ZERO;

    if (!sales.isEmpty()) {
      List<UUID> saleIds = sales.stream().map(Sale::getId).toList();
      List<Payment> payments = paymentRepository.findBySaleIdIn(saleIds);
      for (Payment p : payments) {
        String method = p.getMethod() != null ? p.getMethod().toUpperCase() : "CASH";
        BigDecimal amount = p.getAmountKes() != null ? p.getAmountKes() : BigDecimal.ZERO;
        if ("CASH".equals(method)) {
          cashTakings = cashTakings.add(amount);
        } else if ("MPESA".equals(method)) {
          mpesaTakings = mpesaTakings.add(amount);
        } else if ("BANK".equals(method)) {
          bankTakings = bankTakings.add(amount);
        } else {
          cashTakings = cashTakings.add(amount);
        }
      }
    }

    // If payments table had no explicit entries but gross sales exist (e.g. cash walk-in skeleton), assign to cash
    BigDecimal totalTakings = cashTakings.add(mpesaTakings).add(bankTakings);
    if (totalTakings.add(loyaltyRedeemed).compareTo(BigDecimal.ZERO) == 0 && totalGrossSales.compareTo(BigDecimal.ZERO) > 0) {
      cashTakings = totalGrossSales;
    }

    // 3. Returns, Refunds, VAT Reversal & Restocked COGS Reversal
    BigDecimal returnsTotal = BigDecimal.ZERO;
    BigDecimal returnsTax = BigDecimal.ZERO;
    BigDecimal returnsNet = BigDecimal.ZERO;
    BigDecimal returnsCogs = BigDecimal.ZERO;

    List<SaleReturn> returns = returnRepository.findByCreatedAtBetween(startOfDay, endOfDay);
    if (!returns.isEmpty()) {
      for (SaleReturn r : returns) {
        if (r.getRefundAmountKes() != null) {
          returnsTotal = returnsTotal.add(r.getRefundAmountKes());
        }
      }

      List<SaleReturnLine> returnLines = returnLineRepository.findBySaleReturnIn(returns);
      for (SaleReturnLine rl : returnLines) {
        if (rl.getProduct() != null) {
          String retTaxCat = rl.getProduct().getTaxCategory() != null
              ? rl.getProduct().getTaxCategory().toUpperCase() : "A";
          if ("A".equals(retTaxCat)) {
            BigDecimal lineVat = rl.getRefundTotalKes().multiply(new BigDecimal("16"))
                .divide(new BigDecimal("116"), 2, RoundingMode.HALF_UP);
            returnsTax = returnsTax.add(lineVat);
          } else if ("C".equals(retTaxCat)) {
            BigDecimal lineVat = rl.getRefundTotalKes().multiply(new BigDecimal("8"))
                .divide(new BigDecimal("108"), 2, RoundingMode.HALF_UP);
            returnsTax = returnsTax.add(lineVat);
          }

          if (rl.getProduct().getCostPriceKes() != null && rl.getQuantity() != null) {
            BigDecimal retCost = rl.getProduct().getCostPriceKes()
                .multiply(rl.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);
            returnsCogs = returnsCogs.add(retCost);
          }
        }
      }
      returnsNet = returnsTotal.subtract(returnsTax);
    }

    // 4. Cash Differences from Shift Reconciliation: expected = openingFloat + cashSales - cashReturns
    BigDecimal cashDifference = BigDecimal.ZERO;
    List<Shift> closedShifts = shiftRepository.findByClosedAtBetween(startOfDay, endOfDay);
    for (Shift shift : closedShifts) {
      if (shift.getClosingCashKes() != null) {
        BigDecimal openingFloat = shift.getOpeningFloatKes() != null ? shift.getOpeningFloatKes() : BigDecimal.ZERO;
        BigDecimal cashSalesInShift = BigDecimal.ZERO;
        BigDecimal cashReturnsInShift = BigDecimal.ZERO;

        if (shift.getCashier() != null && shift.getOpenedAt() != null && shift.getClosedAt() != null) {
          List<Sale> shiftSales = saleRepository.findByCashierIdAndCreatedAtBetween(
              shift.getCashier().getId(), shift.getOpenedAt(), shift.getClosedAt());
          if (!shiftSales.isEmpty()) {
            List<UUID> shiftSaleIds = shiftSales.stream().map(Sale::getId).toList();
            List<Payment> shiftPayments = paymentRepository.findBySaleIdIn(shiftSaleIds);
            for (Payment sp : shiftPayments) {
              if ("CASH".equalsIgnoreCase(sp.getMethod()) && sp.getAmountKes() != null) {
                cashSalesInShift = cashSalesInShift.add(sp.getAmountKes());
              }
            }
          }

          List<SaleReturn> shiftReturns = returnRepository.findByCashierIdAndCreatedAtBetween(
              shift.getCashier().getId(), shift.getOpenedAt(), shift.getClosedAt());
          for (SaleReturn sr : shiftReturns) {
            if ("CASH".equalsIgnoreCase(sr.getRefundMethod()) && sr.getRefundAmountKes() != null) {
              cashReturnsInShift = cashReturnsInShift.add(sr.getRefundAmountKes());
            }
          }
        }

        BigDecimal expected = openingFloat.add(cashSalesInShift).subtract(cashReturnsInShift);
        BigDecimal diff = shift.getClosingCashKes().subtract(expected);
        cashDifference = cashDifference.add(diff);
      }
    }

    // 5. Existing summary lookup
    Optional<DailyAccountingSummary> existingOpt = summaryRepository.findByShopIdAndBusinessDate(shopId, businessDate);
    DailyAccountingSummary summary = existingOpt.orElseGet(() -> {
      DailyAccountingSummary s = new DailyAccountingSummary();
      s.setId(UUID.randomUUID());
      s.setShopId(shopId);
      s.setBusinessDate(businessDate);
      return s;
    });

    summary.setSalesStandardRatedNetKes(salesStandardRatedNet);
    summary.setSalesSpecialRatedNetKes(salesSpecialRatedNet);
    summary.setSalesZeroRatedNetKes(salesZeroRatedNet);
    summary.setSalesExemptNetKes(salesExemptNet);
    summary.setOutputVatKes(outputVat);
    summary.setTotalGrossSalesKes(totalGrossSales);
    summary.setCashTakingsKes(cashTakings);
    summary.setMpesaTakingsKes(mpesaTakings);
    summary.setBankTakingsKes(bankTakings);
    summary.setLoyaltyRedeemedKes(loyaltyRedeemed);
    summary.setReturnsTotalKes(returnsTotal);
    summary.setReturnsTaxKes(returnsTax);
    summary.setReturnsNetKes(returnsNet);
    summary.setReturnsCogsKes(returnsCogs);
    summary.setCashDifferenceKes(cashDifference);
    summary.setCogsTotalKes(cogsTotal);
    summary.setUpdatedAt(Instant.now());

    // 6. Build double entry and calculate debits/credits
    AccountingConfig config = getAccountingConfig();
    JournalEntryDto journalEntry = buildJournalEntry(summary, config);
    summary.setTotalDebitsKes(journalEntry.totalDebit());
    summary.setTotalCreditsKes(journalEntry.totalCredit());
    summary.setBalanced(journalEntry.isBalanced());

    DailyAccountingSummary saved = summaryRepository.save(summary);
    return new DailySummaryResult(saved, journalEntry);
  }

  /**
   * Constructs the balanced double-entry Journal Entry from the summary.
   * Debits must equal Credits down to the exact cent (KES 0.01).
   */
  public JournalEntryDto buildJournalEntry(DailyAccountingSummary summary, AccountingConfig config) {
    List<JournalEntryDto.JournalAccountLineDto> lines = new ArrayList<>();
    String costCenter = config.getAccountCostCenter();

    // 1. Assets: Takings by Payment Method (DEBIT)
    if (summary.getCashTakingsKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountCashOnHand(),
          summary.getCashTakingsKes(),
          BigDecimal.ZERO,
          "Cash takings from till sales",
          costCenter
      ));
    }
    if (summary.getMpesaTakingsKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountMpesaClearing(),
          summary.getMpesaTakingsKes(),
          BigDecimal.ZERO,
          "M-Pesa Till clearing settlement",
          costCenter
      ));
    }
    if (summary.getBankTakingsKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountBankClearing(),
          summary.getBankTakingsKes(),
          BigDecimal.ZERO,
          "Bank card clearing takings",
          costCenter
      ));
    }
    if (summary.getLoyaltyRedeemedKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountLoyaltyDiscount(),
          summary.getLoyaltyRedeemedKes(),
          BigDecimal.ZERO,
          "Customer loyalty points redeemed against sales",
          costCenter
      ));
    }

    // 2. Revenues: Sales by Tax Category (CREDIT)
    if (summary.getSalesStandardRatedNetKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountSalesStandard(),
          BigDecimal.ZERO,
          summary.getSalesStandardRatedNetKes(),
          "Standard-rated (16%) retail sales net of VAT",
          costCenter
      ));
    }
    if (summary.getSalesSpecialRatedNetKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountSalesSpecial(),
          BigDecimal.ZERO,
          summary.getSalesSpecialRatedNetKes(),
          "Special-rated (8%) retail sales net of VAT",
          costCenter
      ));
    }
    if (summary.getSalesZeroRatedNetKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountSalesZeroRated(),
          BigDecimal.ZERO,
          summary.getSalesZeroRatedNetKes(),
          "Zero-rated (0%) essential foods retail sales",
          costCenter
      ));
    }
    if (summary.getSalesExemptNetKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountSalesExempt(),
          BigDecimal.ZERO,
          summary.getSalesExemptNetKes(),
          "Exempt goods retail sales",
          costCenter
      ));
    }

    // 3. Liabilities: Output VAT (CREDIT)
    if (summary.getOutputVatKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountOutputVat(),
          BigDecimal.ZERO,
          summary.getOutputVatKes(),
          "Output VAT 16% payable to KRA",
          costCenter
      ));
    }

    // 4. Returns and Refunds (DEBIT Sales Returns & Output VAT, CREDIT Cash)
    if (summary.getReturnsTotalKes().compareTo(BigDecimal.ZERO) > 0) {
      if (summary.getReturnsNetKes().compareTo(BigDecimal.ZERO) > 0) {
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountSalesReturns(),
            summary.getReturnsNetKes(),
            BigDecimal.ZERO,
            "Customer merchandise returns",
            costCenter
        ));
      }
      if (summary.getReturnsTaxKes().compareTo(BigDecimal.ZERO) > 0) {
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountOutputVat(),
            summary.getReturnsTaxKes(),
            BigDecimal.ZERO,
            "Output VAT reversal on customer returns",
            costCenter
        ));
      }
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountCashOnHand(),
          BigDecimal.ZERO,
          summary.getReturnsTotalKes(),
          "Cash refunded to customers for returns",
          costCenter
      ));
    }

    // 5. Cash Differences (Over/Short)
    if (summary.getCashDifferenceKes().compareTo(BigDecimal.ZERO) != 0) {
      if (summary.getCashDifferenceKes().compareTo(BigDecimal.ZERO) < 0) {
        // Cash short (Expense) -> Debit Cash Over/Short, Credit Cash on Hand
        BigDecimal shortage = summary.getCashDifferenceKes().abs();
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountCashOverShort(),
            shortage,
            BigDecimal.ZERO,
            "Till cash shortage at shift close",
            costCenter
        ));
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountCashOnHand(),
            BigDecimal.ZERO,
            shortage,
            "Till cash shortage adjustment",
            costCenter
        ));
      } else {
        // Cash over (Income) -> Debit Cash on Hand, Credit Cash Over/Short
        BigDecimal overage = summary.getCashDifferenceKes();
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountCashOnHand(),
            overage,
            BigDecimal.ZERO,
            "Till cash surplus adjustment",
            costCenter
        ));
        lines.add(new JournalEntryDto.JournalAccountLineDto(
            config.getAccountCashOverShort(),
            BigDecimal.ZERO,
            overage,
            "Till cash surplus at shift close",
            costCenter
        ));
      }
    }

    // 6. Cost of Goods Sold & Inventory (DEBIT COGS, CREDIT Inventory)
    if (summary.getCogsTotalKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountCogs(),
          summary.getCogsTotalKes(),
          BigDecimal.ZERO,
          "Cost of merchandise goods sold today",
          costCenter
      ));
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountInventory(),
          BigDecimal.ZERO,
          summary.getCogsTotalKes(),
          "Merchandise inventory reduction from daily sales",
          costCenter
      ));
    }

    // 7. Restocked Customer Returns COGS Reversal (DEBIT Inventory, CREDIT COGS)
    if (summary.getReturnsCogsKes().compareTo(BigDecimal.ZERO) > 0) {
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountInventory(),
          summary.getReturnsCogsKes(),
          BigDecimal.ZERO,
          "Merchandise inventory restored from customer returns",
          costCenter
      ));
      lines.add(new JournalEntryDto.JournalAccountLineDto(
          config.getAccountCogs(),
          BigDecimal.ZERO,
          summary.getReturnsCogsKes(),
          "COGS reversal for returned merchandise",
          costCenter
      ));
    }

    String remark = "POS End-of-Day Summary for Shop " + summary.getShopId() + " (" + summary.getBusinessDate() + ")";
    String refKey = "POS-EOD-" + summary.getShopId().toString().substring(0, 8).toUpperCase() + "-" + summary.getBusinessDate();
    return JournalEntryDto.of(config.getCompanyName(), summary.getBusinessDate(), remark, lines, refKey);
  }

  /**
   * Posts daily summary to ERPNext via REST API.
   * Sending the same day twice never counts it twice (Idempotency guarantee).
   */
  @Transactional
  public PostResult postToErpNext(UUID shopId, LocalDate businessDate) {
    return postToErpNext(shopId, businessDate, false);
  }

  /**
   * Posts daily summary to ERPNext via REST API, with optional forceRepost for amended days.
   */
  @Transactional
  public PostResult postToErpNext(UUID shopId, LocalDate businessDate, boolean forceRepost) {
    DailySummaryResult summaryResult = generateDailySummary(shopId, businessDate);
    DailyAccountingSummary summary = summaryResult.summary();
    JournalEntryDto journalEntry = summaryResult.journalEntry();

    // Idempotency: If already posted and not explicitly forcing a re-post, return existing journal entry id
    if (!forceRepost && "POSTED".equalsIgnoreCase(summary.getStatus()) && summary.getErpnextJournalEntryId() != null) {
      log.info("Daily summary for shop {} date {} already posted to ERPNext: {}",
          shopId, businessDate, summary.getErpnextJournalEntryId());
      return new PostResult(
          true,
          "POSTED",
          summary.getErpnextJournalEntryId(),
          "Already posted to ERPNext (Document: " + summary.getErpnextJournalEntryId() + ")"
      );
    }

    AccountingConfig config = getAccountingConfig();
    AccountingConfig effectiveConfig = copyWithDecryptedSecret(config);
    ErpNextClient.ErpNextResponse erpResponse = accountingExporter.postToErpNext(summary, journalEntry, effectiveConfig);

    if (erpResponse.success()) {
      summary.setStatus("POSTED");
      summary.setErpnextJournalEntryId(erpResponse.journalEntryId());
      summary.setErpnextCompany(config.getCompanyName());
      summary.setPostedAt(Instant.now());
      summary.setErrorMessage(null);
      summaryRepository.save(summary);
      if (auditLogService != null) {
        auditLogService.record(
            forceRepost ? "ERPNEXT_FORCE_REPOST" : "ERPNEXT_JOURNAL_POSTED",
            "daily_accounting_summary",
            summary.getId().toString(),
            "Posted journal " + erpResponse.journalEntryId() + " for date " + businessDate
        );
      }

      return new PostResult(
          true,
          "POSTED",
          erpResponse.journalEntryId(),
          "Successfully posted to ERPNext with Journal Entry: " + erpResponse.journalEntryId()
      );
    } else {
      // ERPNext unreachable or returned error: Queue in Outbox for automatic retry
      summary.setStatus("QUEUED");
      summary.setErrorMessage(erpResponse.errorMessage());
      summaryRepository.save(summary);

      boolean alreadyQueued = outboxRepository.findBySentAtIsNull().stream()
          .anyMatch(o -> "accounting.erpnext.journal_entry".equals(o.getTopic()) && o.getPayload().contains(summary.getId().toString()));

      if (!alreadyQueued) {
        try {
          Map<String, Object> outboxPayload = new HashMap<>();
          outboxPayload.put("summaryId", summary.getId().toString());
          outboxPayload.put("shopId", shopId.toString());
          outboxPayload.put("businessDate", businessDate.toString());
          outboxPayload.put("company", config.getCompanyName());
          outboxPayload.put("erpnextBaseUrl", config.getErpnextBaseUrl());
          outboxPayload.put("totalDebit", journalEntry.totalDebit().doubleValue());
          outboxPayload.put("totalCredit", journalEntry.totalCredit().doubleValue());

          outboxRepository.save(new Outbox(
              UUID.randomUUID(),
              "accounting.erpnext.journal_entry",
              objectMapper.writeValueAsString(outboxPayload),
              Instant.now(),
              null
          ));
          log.info("ERPNext offline or rejected. Queued daily journal entry into outbox: {}", summary.getId());
        } catch (Exception e) {
          log.warn("Failed to serialize outbox event for ERPNext: {}", e.getMessage());
        }
      }

      return new PostResult(
          false,
          "QUEUED",
          null,
          "ERPNext offline or rejected (" + erpResponse.errorMessage() + "). Queued in Outbox for automatic sync."
      );
    }
  }

  /**
   * Exports the balanced double-entry summary as a standard CSV file for accountants.
   */
  public byte[] exportCsv(UUID shopId, LocalDate businessDate) {
    DailySummaryResult res = generateDailySummary(shopId, businessDate);
    return accountingExporter.exportToCsv(res.summary(), res.journalEntry());
  }

  /**
   * Retrieves or creates default accounting configuration.
   */
  public AccountingConfig getAccountingConfig() {
    return configRepository.findAll().stream().findFirst().orElseGet(() -> {
      AccountingConfig cfg = new AccountingConfig();
      cfg.setId(DEFAULT_CONFIG_ID);
      return configRepository.save(cfg);
    });
  }

  @Transactional
  public AccountingConfig updateAccountingConfig(AccountingConfig updated) {
    AccountingConfig current = getAccountingConfig();
    if (updated.getCompanyName() != null) current.setCompanyName(updated.getCompanyName().trim());
    if (updated.getErpnextBaseUrl() != null) {
      current.setErpnextBaseUrl(ErpNextClient.normalizeBaseUrl(updated.getErpnextBaseUrl()));
    }
    if (updated.getErpnextApiKey() != null) current.setErpnextApiKey(updated.getErpnextApiKey().trim());
    if (updated.getErpnextApiSecret() != null && !SecretEncryptionService.isMasked(updated.getErpnextApiSecret())) {
      String rawSecret = updated.getErpnextApiSecret().trim();
      current.setErpnextApiSecret(
          secretEncryptionService != null ? secretEncryptionService.encrypt(rawSecret) : rawSecret
      );
    }
    current.setAutoPostOnEod(updated.isAutoPostOnEod());
    current.setSubmitOnPost(updated.isSubmitOnPost());

    if (updated.getAccountCashOnHand() != null) current.setAccountCashOnHand(updated.getAccountCashOnHand().trim());
    if (updated.getAccountMpesaClearing() != null) current.setAccountMpesaClearing(updated.getAccountMpesaClearing().trim());
    if (updated.getAccountBankClearing() != null) current.setAccountBankClearing(updated.getAccountBankClearing().trim());
    if (updated.getAccountSalesStandard() != null) current.setAccountSalesStandard(updated.getAccountSalesStandard().trim());
    if (updated.getAccountSalesSpecial() != null) current.setAccountSalesSpecial(updated.getAccountSalesSpecial().trim());
    if (updated.getAccountSalesZeroRated() != null) current.setAccountSalesZeroRated(updated.getAccountSalesZeroRated().trim());
    if (updated.getAccountSalesExempt() != null) current.setAccountSalesExempt(updated.getAccountSalesExempt().trim());
    if (updated.getAccountOutputVat() != null) current.setAccountOutputVat(updated.getAccountOutputVat().trim());
    if (updated.getAccountSalesReturns() != null) current.setAccountSalesReturns(updated.getAccountSalesReturns().trim());
    if (updated.getAccountCashOverShort() != null) current.setAccountCashOverShort(updated.getAccountCashOverShort().trim());
    if (updated.getAccountCogs() != null) current.setAccountCogs(updated.getAccountCogs().trim());
    if (updated.getAccountInventory() != null) current.setAccountInventory(updated.getAccountInventory().trim());
    if (updated.getAccountCostCenter() != null) current.setAccountCostCenter(updated.getAccountCostCenter().trim());
    if (updated.getAccountLoyaltyDiscount() != null) current.setAccountLoyaltyDiscount(updated.getAccountLoyaltyDiscount().trim());
    if (updated.getAccountInputVat() != null) current.setAccountInputVat(updated.getAccountInputVat().trim());
    if (updated.getAccountAccountsPayable() != null) current.setAccountAccountsPayable(updated.getAccountAccountsPayable().trim());

    current.setUpdatedAt(Instant.now());
    AccountingConfig saved = configRepository.save(current);
    if (auditLogService != null) {
      auditLogService.record(
          "ACCOUNTING_CONFIG_UPDATED",
          "accounting_config",
          saved.getId().toString(),
          "Updated ERPNext settings / Chart of Accounts for " + saved.getCompanyName()
      );
    }
    return saved;
  }

  public String getDecryptedApiSecret(AccountingConfig cfg) {
    if (cfg == null) return "";
    return secretEncryptionService != null
        ? secretEncryptionService.decrypt(cfg.getErpnextApiSecret())
        : cfg.getErpnextApiSecret();
  }

  public AccountingConfig getMaskedAccountingConfig() {
    AccountingConfig cfg = getAccountingConfig();
    AccountingConfig copy = copyWithDecryptedSecret(cfg);
    copy.setErpnextApiSecret(SecretEncryptionService.mask(cfg.getErpnextApiSecret()));
    return copy;
  }

  private AccountingConfig copyWithDecryptedSecret(AccountingConfig src) {
    AccountingConfig copy = new AccountingConfig();
    copy.setId(src.getId());
    copy.setCompanyName(src.getCompanyName());
    copy.setErpnextBaseUrl(src.getErpnextBaseUrl());
    copy.setErpnextApiKey(src.getErpnextApiKey());
    copy.setErpnextApiSecret(getDecryptedApiSecret(src));
    copy.setAutoPostOnEod(src.isAutoPostOnEod());
    copy.setSubmitOnPost(src.isSubmitOnPost());
    copy.setAccountCashOnHand(src.getAccountCashOnHand());
    copy.setAccountMpesaClearing(src.getAccountMpesaClearing());
    copy.setAccountBankClearing(src.getAccountBankClearing());
    copy.setAccountSalesStandard(src.getAccountSalesStandard());
    copy.setAccountSalesSpecial(src.getAccountSalesSpecial());
    copy.setAccountSalesZeroRated(src.getAccountSalesZeroRated());
    copy.setAccountSalesExempt(src.getAccountSalesExempt());
    copy.setAccountOutputVat(src.getAccountOutputVat());
    copy.setAccountSalesReturns(src.getAccountSalesReturns());
    copy.setAccountCashOverShort(src.getAccountCashOverShort());
    copy.setAccountCogs(src.getAccountCogs());
    copy.setAccountInventory(src.getAccountInventory());
    copy.setAccountCostCenter(src.getAccountCostCenter());
    copy.setAccountLoyaltyDiscount(src.getAccountLoyaltyDiscount());
    copy.setAccountInputVat(src.getAccountInputVat());
    copy.setAccountAccountsPayable(src.getAccountAccountsPayable());
    copy.setUpdatedAt(src.getUpdatedAt());
    return copy;
  }

  public record TestConnectionRequest(
      String erpnextBaseUrl,
      String erpnextApiKey,
      String erpnextApiSecret
  ) {}

  public ErpNextClient.ConnectionTestResult testErpNextConnection() {
    return testErpNextConnection(null);
  }

  public ErpNextClient.ConnectionTestResult testErpNextConnection(TestConnectionRequest override) {
    AccountingConfig cfg = getAccountingConfig();
    String baseUrl = (override != null && override.erpnextBaseUrl() != null && !override.erpnextBaseUrl().isBlank())
        ? override.erpnextBaseUrl()
        : cfg.getErpnextBaseUrl();
    String apiKey = (override != null && override.erpnextApiKey() != null && !override.erpnextApiKey().isBlank())
        ? override.erpnextApiKey()
        : cfg.getErpnextApiKey();
    String apiSecret = (override != null && override.erpnextApiSecret() != null
        && !override.erpnextApiSecret().isBlank()
        && !SecretEncryptionService.isMasked(override.erpnextApiSecret()))
        ? override.erpnextApiSecret().trim()
        : getDecryptedApiSecret(cfg);
    return erpNextClient.testConnection(baseUrl, apiKey, apiSecret);
  }

  public ErpNextClient.AccountVerificationResult verifyErpNextAccounts() {
    return verifyErpNextAccounts(null);
  }

  public ErpNextClient.AccountVerificationResult verifyErpNextAccounts(AccountingConfig override) {
    AccountingConfig cfg = getAccountingConfig();
    AccountingConfig eff = override != null ? override : cfg;
    String baseUrl = (eff.getErpnextBaseUrl() != null && !eff.getErpnextBaseUrl().isBlank())
        ? eff.getErpnextBaseUrl()
        : cfg.getErpnextBaseUrl();
    String apiKey = (eff.getErpnextApiKey() != null && !eff.getErpnextApiKey().isBlank())
        ? eff.getErpnextApiKey()
        : cfg.getErpnextApiKey();
    String apiSecret = (override != null && override.getErpnextApiSecret() != null
        && !override.getErpnextApiSecret().isBlank()
        && !SecretEncryptionService.isMasked(override.getErpnextApiSecret()))
        ? override.getErpnextApiSecret().trim()
        : getDecryptedApiSecret(cfg);
    String company = (eff.getCompanyName() != null && !eff.getCompanyName().isBlank())
        ? eff.getCompanyName()
        : cfg.getCompanyName();

    List<String> required = List.of(
        firstNonBlank(eff.getAccountCashOnHand(), cfg.getAccountCashOnHand()),
        firstNonBlank(eff.getAccountMpesaClearing(), cfg.getAccountMpesaClearing()),
        firstNonBlank(eff.getAccountBankClearing(), cfg.getAccountBankClearing()),
        firstNonBlank(eff.getAccountSalesStandard(), cfg.getAccountSalesStandard()),
        firstNonBlank(eff.getAccountSalesSpecial(), cfg.getAccountSalesSpecial()),
        firstNonBlank(eff.getAccountSalesZeroRated(), cfg.getAccountSalesZeroRated()),
        firstNonBlank(eff.getAccountSalesExempt(), cfg.getAccountSalesExempt()),
        firstNonBlank(eff.getAccountOutputVat(), cfg.getAccountOutputVat()),
        firstNonBlank(eff.getAccountSalesReturns(), cfg.getAccountSalesReturns()),
        firstNonBlank(eff.getAccountCashOverShort(), cfg.getAccountCashOverShort()),
        firstNonBlank(eff.getAccountCogs(), cfg.getAccountCogs()),
        firstNonBlank(eff.getAccountInventory(), cfg.getAccountInventory()),
        firstNonBlank(eff.getAccountLoyaltyDiscount(), cfg.getAccountLoyaltyDiscount()),
        firstNonBlank(eff.getAccountInputVat(), cfg.getAccountInputVat()),
        firstNonBlank(eff.getAccountAccountsPayable(), cfg.getAccountAccountsPayable())
    );
    return erpNextClient.verifyCompanyAccounts(baseUrl, apiKey, apiSecret, company, required);
  }

  private static String firstNonBlank(String candidate, String fallback) {
    return (candidate != null && !candidate.isBlank()) ? candidate.trim() : fallback;
  }

  /**
   * Automated End-of-Day job (23:55 Nairobi time) when autoPostOnEod is enabled.
   */
  @Scheduled(cron = "0 55 23 * * *", zone = "Africa/Nairobi")
  @Transactional
  public void runEndOfDayAutoPost() {
    AccountingConfig cfg = getAccountingConfig();
    if (!cfg.isAutoPostOnEod()) {
      return;
    }
    LocalDate today = LocalDate.now(KENYA_ZONE);
    log.info("Running scheduled EOD ERPNext accounting post for {}", today);
    PostResult res = postToErpNext(AccountingController.DEFAULT_SHOP_ID, today, false);
    log.info("EOD auto-post status for {}: {} ({})", today, res.status(), res.message());
  }

  public List<DailyAccountingSummary> getHistory(UUID shopId) {
    if (shopId != null) {
      return summaryRepository.findByShopIdOrderByBusinessDateDesc(shopId);
    }
    return summaryRepository.findAllByOrderByBusinessDateDesc();
  }
}
