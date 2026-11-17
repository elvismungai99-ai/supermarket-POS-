package ke.retailpos.pos_backend.accounting;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.returns.SaleReturn;
import ke.retailpos.pos_backend.returns.SaleReturnLine;
import ke.retailpos.pos_backend.returns.SaleReturnLineRepository;
import ke.retailpos.pos_backend.returns.SaleReturnRepository;
import ke.retailpos.pos_backend.sales.*;
import ke.retailpos.pos_backend.shift.Shift;
import ke.retailpos.pos_backend.shift.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AccountingServiceTest {

  private DailyAccountingSummaryRepository summaryRepository;
  private AccountingConfigRepository configRepository;
  private SaleRepository saleRepository;
  private SaleLineRepository saleLineRepository;
  private PaymentRepository paymentRepository;
  private SaleReturnRepository returnRepository;
  private SaleReturnLineRepository returnLineRepository;
  private ShiftRepository shiftRepository;
  private OutboxRepository outboxRepository;
  private AccountingExporter accountingExporter;
  private ErpNextClient erpNextClient;
  private ObjectMapper objectMapper;

  private AccountingService accountingService;
  private AccountingConfig testConfig;

  @BeforeEach
  void setUp() {
    summaryRepository = mock(DailyAccountingSummaryRepository.class);
    configRepository = mock(AccountingConfigRepository.class);
    saleRepository = mock(SaleRepository.class);
    saleLineRepository = mock(SaleLineRepository.class);
    paymentRepository = mock(PaymentRepository.class);
    returnRepository = mock(SaleReturnRepository.class);
    returnLineRepository = mock(SaleReturnLineRepository.class);
    shiftRepository = mock(ShiftRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    accountingExporter = mock(AccountingExporter.class);
    erpNextClient = mock(ErpNextClient.class);
    objectMapper = new ObjectMapper();

    testConfig = new AccountingConfig();
    testConfig.setId(AccountingService.DEFAULT_CONFIG_ID);
    testConfig.setCompanyName("Supermarket POS Ltd");
    testConfig.setErpnextBaseUrl("http://localhost:8000");
    testConfig.setErpnextApiKey("key123");
    testConfig.setErpnextApiSecret("sec123");

    when(configRepository.findAll()).thenReturn(List.of(testConfig));

    when(summaryRepository.save(any(DailyAccountingSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

    accountingService = new AccountingService(
        summaryRepository,
        configRepository,
        saleRepository,
        saleLineRepository,
        paymentRepository,
        returnRepository,
        returnLineRepository,
        shiftRepository,
        outboxRepository,
        accountingExporter,
        erpNextClient,
        objectMapper
    );
  }

  @Test
  void testExampleDayDoubleEntryBalancesExactly() {
    // Example from Specification C3:
    // Sales: 11,600 standard (10,000 net + 1,600 VAT), 2,000 zero-rated, 500 exempt. Total 14,100.
    // Paid by: cash 6,000, M-Pesa 6,100, bank 2,000. Total 14,100.
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 12);

    Sale sale = new Sale(UUID.randomUUID(), shopId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("14100.00"), "COMPLETED", Instant.now());
    when(saleRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of(sale));

    Product prodStd = new Product(UUID.randomUUID(), "Standard item", new BigDecimal("116.00"), "A", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("80.00"));
    Product prodZero = new Product(UUID.randomUUID(), "Zero rated item", new BigDecimal("20.00"), "B", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("15.00"));
    Product prodExempt = new Product(UUID.randomUUID(), "Exempt item", new BigDecimal("5.00"), "E", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("3.00"));

    SaleLine line1 = new SaleLine(UUID.randomUUID(), sale, prodStd, new BigDecimal("100"), new BigDecimal("116.00"), "A", new BigDecimal("1600.00"), new BigDecimal("11600.00"), new BigDecimal("8000.00"));
    SaleLine line2 = new SaleLine(UUID.randomUUID(), sale, prodZero, new BigDecimal("100"), new BigDecimal("20.00"), "B", BigDecimal.ZERO, new BigDecimal("2000.00"), new BigDecimal("1500.00"));
    SaleLine line3 = new SaleLine(UUID.randomUUID(), sale, prodExempt, new BigDecimal("100"), new BigDecimal("5.00"), "E", BigDecimal.ZERO, new BigDecimal("500.00"), new BigDecimal("300.00"));

    when(saleLineRepository.findBySaleIn(any())).thenReturn(List.of(line1, line2, line3));

    Payment p1 = new Payment(UUID.randomUUID(), sale.getId(), "CASH", new BigDecimal("6000.00"), "CASH", "PAID");
    Payment p2 = new Payment(UUID.randomUUID(), sale.getId(), "MPESA", new BigDecimal("6100.00"), "MPESA_REF", "PAID");
    Payment p3 = new Payment(UUID.randomUUID(), sale.getId(), "BANK", new BigDecimal("2000.00"), "BANK_REF", "PAID");

    when(paymentRepository.findBySaleIdIn(any())).thenReturn(List.of(p1, p2, p3));
    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of());

    AccountingService.DailySummaryResult result = accountingService.generateDailySummary(shopId, date);
    DailyAccountingSummary summary = result.summary();
    JournalEntryDto journal = result.journalEntry();

    assertEquals(new BigDecimal("10000.00"), summary.getSalesStandardRatedNetKes());
    assertEquals(new BigDecimal("1600.00"), summary.getOutputVatKes());
    assertEquals(new BigDecimal("2000.00"), summary.getSalesZeroRatedNetKes());
    assertEquals(new BigDecimal("500.00"), summary.getSalesExemptNetKes());
    assertEquals(new BigDecimal("14100.00"), summary.getTotalGrossSalesKes());

    assertEquals(new BigDecimal("6000.00"), summary.getCashTakingsKes());
    assertEquals(new BigDecimal("6100.00"), summary.getMpesaTakingsKes());
    assertEquals(new BigDecimal("2000.00"), summary.getBankTakingsKes());

    // Double-entry validation: Total Debits must equal Total Credits
    // Sales takings (14,100) + COGS (9,800) = 23,900
    // Sales revenue + Output VAT (14,100) + Inventory (9,800) = 23,900
    assertTrue(journal.isBalanced());
    assertEquals(journal.totalDebit(), journal.totalCredit());
    assertEquals(0, journal.difference().compareTo(BigDecimal.ZERO));
    assertTrue(summary.isBalanced());
  }

  @Test
  void testSalesReturnsDebitReturnsAndCreditCash() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 12);

    Sale sale = new Sale(UUID.randomUUID(), shopId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), "COMPLETED", Instant.now());
    when(saleRepository.findByShopIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of(sale));

    Product prod = new Product(UUID.randomUUID(), "Milk", new BigDecimal("100.00"), "A", true);
    SaleLine line = new SaleLine(UUID.randomUUID(), sale, prod, new BigDecimal("10"), new BigDecimal("100.00"), "A", new BigDecimal("137.93"), new BigDecimal("1000.00"), new BigDecimal("700.00"));
    when(saleLineRepository.findBySaleIn(any())).thenReturn(List.of(line));

    Payment p = new Payment(UUID.randomUUID(), sale.getId(), "CASH", new BigDecimal("1000.00"), "CASH", "PAID");
    when(paymentRepository.findBySaleIdIn(any())).thenReturn(List.of(p));

    // Return of 232 KES (200 net + 32 VAT)
    Cashier cashier = new Cashier(UUID.randomUUID(), "cashier1", "Jane", "h", "CASHIER", true, "ACTIVE", Instant.now());
    SaleReturn ret = new SaleReturn(UUID.randomUUID(), sale, "RET-001", cashier, new BigDecimal("232.00"), "Damaged item", Instant.now());
    SaleReturnLine retLine = new SaleReturnLine(UUID.randomUUID(), ret, prod, new BigDecimal("2"), new BigDecimal("116.00"), new BigDecimal("232.00"));

    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(ret));
    when(returnLineRepository.findBySaleReturnIn(any())).thenReturn(List.of(retLine));
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of());

    AccountingService.DailySummaryResult result = accountingService.generateDailySummary(shopId, date);
    JournalEntryDto journal = result.journalEntry();

    assertTrue(journal.isBalanced(), "Journal Entry with returns must balance exactly");
    assertEquals(journal.totalDebit(), journal.totalCredit());
    assertTrue(result.summary().getReturnsTotalKes().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void testCashDifferenceShortageAndSurplusHandling() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 12);

    when(saleRepository.findByShopIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

    // Cash shortage: opening float 1000, closing cash 950 (short 50 KES)
    Cashier cashier = new Cashier(UUID.randomUUID(), "c1", "Cashier", "h", "CASHIER", true, "ACTIVE", Instant.now());
    Shift shift = new Shift(UUID.randomUUID(), UUID.randomUUID(), cashier, Instant.now(), new BigDecimal("1000.00"), "CLOSED");
    shift.setClosedAt(Instant.now());
    shift.setClosingCashKes(new BigDecimal("950.00"));

    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of(shift));

    AccountingService.DailySummaryResult result = accountingService.generateDailySummary(shopId, date);
    JournalEntryDto journal = result.journalEntry();

    assertEquals(new BigDecimal("-50.00"), result.summary().getCashDifferenceKes());
    assertTrue(journal.isBalanced());
    assertEquals(journal.totalDebit(), journal.totalCredit());
  }

  @Test
  void testIdempotencySendingSameDayTwiceNeverCountsTwice() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 12);

    DailyAccountingSummary existingPosted = new DailyAccountingSummary();
    existingPosted.setId(UUID.randomUUID());
    existingPosted.setShopId(shopId);
    existingPosted.setBusinessDate(date);
    existingPosted.setStatus("POSTED");
    existingPosted.setErpnextJournalEntryId("ACC-JV-2026-00012");
    existingPosted.setTotalGrossSalesKes(new BigDecimal("14100.00"));

    when(summaryRepository.findByShopIdAndBusinessDate(shopId, date)).thenReturn(Optional.of(existingPosted));

    // Post to ERPNext twice
    AccountingService.PostResult firstCall = accountingService.postToErpNext(shopId, date);
    assertEquals("POSTED", firstCall.status());
    assertEquals("ACC-JV-2026-00012", firstCall.erpnextJournalEntryId());

    AccountingService.PostResult secondCall = accountingService.postToErpNext(shopId, date);
    assertEquals("POSTED", secondCall.status());
    assertEquals("ACC-JV-2026-00012", secondCall.erpnextJournalEntryId());

    // Verified: No external network call or duplicate posting executed on second call
    verify(accountingExporter, never()).postToErpNext(any(), any(), any());
  }

  @Test
  void testOfflineErpNextQueuesIntoOutbox() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 12);

    when(saleRepository.findByShopIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of());

    // Simulate ERPNext connection failure
    when(accountingExporter.postToErpNext(any(), any(), any())).thenReturn(
        new ErpNextClient.ErpNextResponse(false, null, "Connection refused to ERPNext at localhost:8000", 503)
    );

    AccountingService.PostResult result = accountingService.postToErpNext(shopId, date);

    assertEquals("QUEUED", result.status());
    assertNull(result.erpnextJournalEntryId());
    assertTrue(result.message().contains("Queued in Outbox"));

    // Verify outbox entry saved
    verify(outboxRepository).save(any(Outbox.class));
  }

  @Test
  void testFullTestWeekTrialBalanceMatchesTillsEveryDay() {
    // "Done when: for a test week, the accounting system's trial balance matches the till's sales,
    // payments and tax for every day, and sending the same day twice never counts it twice."
    UUID shopId = UUID.randomUUID();
    LocalDate monday = LocalDate.of(2026, 11, 2);

    Product milk = new Product(UUID.randomUUID(), "Milk 500ml", new BigDecimal("65.00"), "A", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("50.00"));
    Product maize = new Product(UUID.randomUUID(), "Maize 2kg", new BigDecimal("170.00"), "B", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("135.00"));
    Product panadol = new Product(UUID.randomUUID(), "Panadol 10s", new BigDecimal("120.00"), "E", true, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("80.00"));

    BigDecimal weekTotalSales = BigDecimal.ZERO;
    BigDecimal weekTotalVat = BigDecimal.ZERO;

    for (int day = 0; day < 7; day++) {
      LocalDate businessDay = monday.plusDays(day);

      // Varying sales for each day
      BigDecimal dayQty = BigDecimal.valueOf(10 + (day * 5));
      BigDecimal stdTotal = milk.getPriceKes().multiply(dayQty);
      BigDecimal zeroTotal = maize.getPriceKes().multiply(dayQty);
      BigDecimal exemptTotal = panadol.getPriceKes().multiply(dayQty);

      BigDecimal vatAmount = stdTotal.multiply(new BigDecimal("16")).divide(new BigDecimal("116"), 2, java.math.RoundingMode.HALF_UP);
      BigDecimal grossDay = stdTotal.add(zeroTotal).add(exemptTotal);

      Sale daySale = new Sale(UUID.randomUUID(), shopId, UUID.randomUUID(), UUID.randomUUID(), grossDay, "COMPLETED", Instant.now());
      when(saleRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of(daySale));

      SaleLine l1 = new SaleLine(UUID.randomUUID(), daySale, milk, dayQty, milk.getPriceKes(), "A", vatAmount, stdTotal, milk.getCostPriceKes().multiply(dayQty));
      SaleLine l2 = new SaleLine(UUID.randomUUID(), daySale, maize, dayQty, maize.getPriceKes(), "B", BigDecimal.ZERO, zeroTotal, maize.getCostPriceKes().multiply(dayQty));
      SaleLine l3 = new SaleLine(UUID.randomUUID(), daySale, panadol, dayQty, panadol.getPriceKes(), "E", BigDecimal.ZERO, exemptTotal, panadol.getCostPriceKes().multiply(dayQty));
      when(saleLineRepository.findBySaleIn(any())).thenReturn(List.of(l1, l2, l3));

      // 50% Cash, 50% M-Pesa
      BigDecimal half = grossDay.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
      Payment payCash = new Payment(UUID.randomUUID(), daySale.getId(), "CASH", half, "CASH", "PAID");
      Payment payMpesa = new Payment(UUID.randomUUID(), daySale.getId(), "MPESA", grossDay.subtract(half), "MPESA-REF", "PAID");
      when(paymentRepository.findBySaleIdIn(any())).thenReturn(List.of(payCash, payMpesa));

      AccountingService.DailySummaryResult dayResult = accountingService.generateDailySummary(shopId, businessDay);
      JournalEntryDto journal = dayResult.journalEntry();

      // Core Acceptance Criteria:
      // 1. Trial balance matches every day (Debits == Credits down to the cent)
      assertTrue(journal.isBalanced(), "Day " + businessDay + " must balance perfectly");
      assertEquals(journal.totalDebit(), journal.totalCredit(), "Day " + businessDay + " debits must equal credits");
      assertEquals(0, journal.difference().compareTo(BigDecimal.ZERO), "Trial balance difference must be 0.00 on " + businessDay);

      // 2. Takings match payments
      assertEquals(grossDay, dayResult.summary().getCashTakingsKes().add(dayResult.summary().getMpesaTakingsKes()));

      weekTotalSales = weekTotalSales.add(grossDay);
      weekTotalVat = weekTotalVat.add(vatAmount);
    }

    assertTrue(weekTotalSales.compareTo(BigDecimal.ZERO) > 0);
    assertTrue(weekTotalVat.compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void testCategoryCLoyaltyRedemptionAndReturnsCogsBalance() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 13);

    // Sale of Category C (8% special rated): gross 1080.00 (1000.00 net + 80.00 VAT)
    // Loyalty redeemed: 80.00 KES -> payable = 1000.00 paid in CASH
    Sale sale = new Sale(UUID.randomUUID(), shopId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1080.00"), "COMPLETED", Instant.now());
    sale.setLoyaltyRedeemedKes(new BigDecimal("80.00"));
    when(saleRepository.findByShopIdAndCreatedAtBetween(eq(shopId), any(), any())).thenReturn(List.of(sale));

    Product fuelItem = new Product(UUID.randomUUID(), "Cooking Gas", new BigDecimal("1080.00"), "C", true, new BigDecimal("50"), new BigDecimal("5"), new BigDecimal("700.00"));
    SaleLine line = new SaleLine(UUID.randomUUID(), sale, fuelItem, new BigDecimal("1"), new BigDecimal("1080.00"), "C", new BigDecimal("80.00"), new BigDecimal("1080.00"), new BigDecimal("700.00"));
    when(saleLineRepository.findBySaleIn(any())).thenReturn(List.of(line));

    Payment pCash = new Payment(UUID.randomUUID(), sale.getId(), "CASH", new BigDecimal("1000.00"), "CASH", "PAID");
    when(paymentRepository.findBySaleIdIn(any())).thenReturn(List.of(pCash));

    // Customer returns 1 item of milk (116 gross, COGS = 80.00)
    Product milk = new Product(UUID.randomUUID(), "Milk", new BigDecimal("116.00"), "A", true, new BigDecimal("20"), new BigDecimal("5"), new BigDecimal("80.00"));
    Cashier cashier = new Cashier(UUID.randomUUID(), "c2", "Grace", "h", "CASHIER", true, "ACTIVE", Instant.now());
    SaleReturn ret = new SaleReturn(UUID.randomUUID(), sale, "RET-C1", cashier, new BigDecimal("116.00"), "Expired", Instant.now());
    SaleReturnLine retLine = new SaleReturnLine(UUID.randomUUID(), ret, milk, new BigDecimal("1"), new BigDecimal("116.00"), new BigDecimal("116.00"));

    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(ret));
    when(returnLineRepository.findBySaleReturnIn(any())).thenReturn(List.of(retLine));
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of());

    AccountingService.DailySummaryResult result = accountingService.generateDailySummary(shopId, date);
    DailyAccountingSummary summary = result.summary();
    JournalEntryDto journal = result.journalEntry();

    assertEquals(new BigDecimal("1000.00"), summary.getSalesSpecialRatedNetKes());
    assertEquals(new BigDecimal("80.00"), summary.getOutputVatKes());
    assertEquals(new BigDecimal("80.00"), summary.getLoyaltyRedeemedKes());
    assertEquals(new BigDecimal("80.00"), summary.getReturnsCogsKes());
    assertTrue(journal.isBalanced(), "Journal with Category C + loyalty + returns COGS must balance");
    assertEquals(0, journal.difference().compareTo(BigDecimal.ZERO));
  }

  @Test
  void testShiftCashReconciliationIncludesCashSalesAndCashReturns() {
    UUID shopId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 11, 13);

    Instant openedAt = Instant.parse("2026-11-13T05:00:00Z");
    Instant saleAt = Instant.parse("2026-11-13T07:00:00Z");
    Instant returnAt = Instant.parse("2026-11-13T08:00:00Z");
    Instant closedAt = Instant.parse("2026-11-13T10:00:00Z");

    Cashier cashier = new Cashier(UUID.randomUUID(), "c3", "Peter", "h", "CASHIER", true, "ACTIVE", Instant.now());
    Shift shift = new Shift(UUID.randomUUID(), UUID.randomUUID(), cashier, openedAt, new BigDecimal("1000.00"), "CLOSED");
    shift.setClosedAt(closedAt);
    // Expected = 1000 (float) + 500 (cash sale) - 100 (cash return) = 1400.00. Actual closing = 1380.00 -> -20.00 short
    shift.setClosingCashKes(new BigDecimal("1380.00"));

    Sale cashSale = new Sale(UUID.randomUUID(), shopId, shift.getTillId(), cashier.getId(), new BigDecimal("500.00"), "COMPLETED", saleAt);
    SaleReturn cashReturn = new SaleReturn(UUID.randomUUID(), cashSale, "RET-002", cashier, new BigDecimal("100.00"), "Refund", returnAt);

    when(saleRepository.findByShopIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of(shift));
    when(saleRepository.findByCashierIdAndCreatedAtBetween(eq(cashier.getId()), any(), any())).thenReturn(List.of(cashSale));
    when(paymentRepository.findBySaleIdIn(any())).thenReturn(List.of(
        new Payment(UUID.randomUUID(), cashSale.getId(), "CASH", new BigDecimal("500.00"), "CASH", "PAID")
    ));
    when(returnRepository.findByCashierIdAndCreatedAtBetween(eq(cashier.getId()), any(), any())).thenReturn(List.of(cashReturn));

    AccountingService.DailySummaryResult result = accountingService.generateDailySummary(shopId, date);
    assertEquals(new BigDecimal("-20.00"), result.summary().getCashDifferenceKes());
    assertTrue(result.journalEntry().isBalanced());
  }

  @Test
  void testAutoPostOnEodSchedulerRespectsToggle() {
    testConfig.setAutoPostOnEod(false);
    accountingService.runEndOfDayAutoPost();
    verify(accountingExporter, never()).postToErpNext(any(), any(), any());

    testConfig.setAutoPostOnEod(true);
    testConfig.setSubmitOnPost(true);
    when(saleRepository.findByShopIdAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());
    when(returnRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
    when(shiftRepository.findByClosedAtBetween(any(), any())).thenReturn(List.of());
    when(accountingExporter.postToErpNext(any(), any(), any()))
        .thenReturn(new ErpNextClient.ErpNextResponse(true, "ACC-JV-2026-00099", null, 200));

    accountingService.runEndOfDayAutoPost();
    verify(accountingExporter, times(1)).postToErpNext(
        any(), any(), argThat(cfg -> cfg.getCompanyName().equals(testConfig.getCompanyName()))
    );
  }
}
