package ke.retailpos.pos_backend.reports;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import ke.retailpos.pos_backend.accounting.AccountingService;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculates monthly VAT liabilities (Output VAT - Returns Credit Notes - Claimable Input VAT)
 * and tracks KRA tax remittances paid by the supermarket.
 */
@Service
public class MonthlyTaxService {

  private static final DateTimeFormatter MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

  private final JdbcTemplate jdbcTemplate;
  private final TaxPaymentRepository taxPaymentRepository;
  private final AuditLogService auditLogService;

  public MonthlyTaxService(JdbcTemplate jdbcTemplate, TaxPaymentRepository taxPaymentRepository) {
    this(jdbcTemplate, taxPaymentRepository, null);
  }

  @Autowired
  public MonthlyTaxService(
      JdbcTemplate jdbcTemplate,
      TaxPaymentRepository taxPaymentRepository,
      AuditLogService auditLogService
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.taxPaymentRepository = taxPaymentRepository;
    this.auditLogService = auditLogService;
  }

  public record MonthlyTaxSummaryDto(
      String month,
      String monthLabel,
      long salesCount,
      BigDecimal grossSalesKes,
      BigDecimal taxableSalesKes,
      BigDecimal outputVatKes,
      BigDecimal returnsTotalKes,
      BigDecimal returnsVatKes,
      BigDecimal netOutputVatKes,
      BigDecimal purchasesTotalKes,
      BigDecimal inputVatKes,
      BigDecimal netTaxPayableKes,
      BigDecimal vatCreditCarryoverKes,
      BigDecimal taxPaidKes,
      BigDecimal balanceDueKes,
      String status
  ) {}

  public record CategoryTaxBreakdownDto(
      String taxCategory,
      String categoryLabel,
      String vatRateLabel,
      BigDecimal grossSalesKes,
      BigDecimal taxableSalesKes,
      BigDecimal outputVatKes,
      BigDecimal returnsTotalKes,
      BigDecimal returnsVatKes,
      BigDecimal netVatKes
  ) {}

  public record CumulativeTaxSummaryDto(
      BigDecimal totalOutputVatKes,
      BigDecimal totalReturnsVatKes,
      BigDecimal totalInputVatKes,
      BigDecimal totalNetTaxPayableKes,
      BigDecimal totalTaxPaidKes,
      BigDecimal totalBalanceDueKes
  ) {}

  public record TaxPaymentDto(
      UUID id,
      String taxPeriod,
      String taxType,
      BigDecimal amountPaidKes,
      LocalDate paymentDate,
      String paymentMethod,
      String kraPrn,
      String notes,
      String recordedByUsername,
      Instant createdAt
  ) {}

  public record MonthlyTaxReportResponse(
      String selectedMonth,
      MonthlyTaxSummaryDto currentMonth,
      CumulativeTaxSummaryDto cumulative,
      List<MonthlyTaxSummaryDto> monthlySummaries,
      List<CategoryTaxBreakdownDto> categoryBreakdown,
      List<TaxPaymentDto> payments
  ) {}

  public record RecordTaxPaymentRequest(
      @NotBlank String taxPeriod,
      String taxType,
      @NotNull @DecimalMin("0.01") BigDecimal amountPaidKes,
      LocalDate paymentDate,
      String paymentMethod,
      @NotBlank String kraPrn,
      String notes
  ) {}

  public YearMonth resolveMonth(String month) {
    if (month == null || month.isBlank()) {
      return YearMonth.now(AccountingService.KENYA_ZONE);
    }
    try {
      return YearMonth.parse(month.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid month format '" + month + "'. Expected YYYY-MM (e.g. 2026-11).");
    }
  }

  public MonthlyTaxReportResponse getMonthlyTaxReport(String requestedMonth) {
    YearMonth targetYm = resolveMonth(requestedMonth);
    String targetMonth = targetYm.toString();

    Map<String, MonthAccumulator> byMonth = new TreeMap<>(Comparator.reverseOrder());
    byMonth.put(targetMonth, new MonthAccumulator());

    // 1. Sales counts & gross totals by Kenya calendar month
    String salesCountSql = """
        select
          to_char(created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM') as ym,
          count(id) as sale_cnt,
          coalesce(sum(total_kes), 0) as gross_sales
        from sale
        group by ym
        """;
    jdbcTemplate.queryForList(salesCountSql).forEach(row -> {
      String ym = (String) row.get("ym");
      MonthAccumulator acc = byMonth.computeIfAbsent(ym, k -> new MonthAccumulator());
      acc.salesCount = ((Number) row.get("sale_cnt")).longValue();
      acc.grossSales = acc.grossSales.add(toBigDecimal(row.get("gross_sales")));
    });

    // 2. Output VAT & Taxable Sales grouped by month + category
    Map<String, CategoryAccumulator> selectedCategories = initCategoriesMap();
    String saleLinesSql = """
        select
          to_char(s.created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM') as ym,
          upper(coalesce(sl.tax_category, 'A')) as tax_cat,
          coalesce(sum(sl.line_total_kes), 0) as gross_kes,
          coalesce(sum(sl.tax_amount_kes), 0) as vat_kes,
          coalesce(sum(sl.line_total_kes - sl.tax_amount_kes), 0) as net_kes
        from sale_line sl
        join sale s on s.id = sl.sale_id
        group by ym, tax_cat
        """;
    jdbcTemplate.queryForList(saleLinesSql).forEach(row -> {
      String ym = (String) row.get("ym");
      String cat = normalizeCat((String) row.get("tax_cat"));
      BigDecimal gross = toBigDecimal(row.get("gross_kes"));
      BigDecimal vat = toBigDecimal(row.get("vat_kes"));
      BigDecimal net = toBigDecimal(row.get("net_kes"));

      MonthAccumulator acc = byMonth.computeIfAbsent(ym, k -> new MonthAccumulator());
      acc.taxableSales = acc.taxableSales.add(net);
      acc.outputVat = acc.outputVat.add(vat);

      if (targetMonth.equals(ym)) {
        CategoryAccumulator cAcc = selectedCategories.get(cat);
        cAcc.grossSales = cAcc.grossSales.add(gross);
        cAcc.taxableSales = cAcc.taxableSales.add(net);
        cAcc.outputVat = cAcc.outputVat.add(vat);
      }
    });

    // 3. Returns & VAT Credit Notes grouped by month + category
    String returnsSql = """
        select
          to_char(sr.created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM') as ym,
          upper(coalesce(p.tax_category, 'A')) as tax_cat,
          coalesce(sum(srl.refund_total_kes), 0) as refund_kes
        from sale_return_line srl
        join sale_return sr on sr.id = srl.sale_return_id
        join product p on p.id = srl.product_id
        group by ym, tax_cat
        """;
    jdbcTemplate.queryForList(returnsSql).forEach(row -> {
      String ym = (String) row.get("ym");
      String cat = normalizeCat((String) row.get("tax_cat"));
      BigDecimal refundGross = toBigDecimal(row.get("refund_kes"));
      BigDecimal refundVat = extractVatFromGross(refundGross, cat);

      MonthAccumulator acc = byMonth.computeIfAbsent(ym, k -> new MonthAccumulator());
      acc.returnsTotal = acc.returnsTotal.add(refundGross);
      acc.returnsVat = acc.returnsVat.add(refundVat);

      if (targetMonth.equals(ym)) {
        CategoryAccumulator cAcc = selectedCategories.get(cat);
        cAcc.returnsTotal = cAcc.returnsTotal.add(refundGross);
        cAcc.returnsVat = cAcc.returnsVat.add(refundVat);
      }
    });

    // 4. Input VAT on Supplier Stock Receipts grouped by month
    String purchasesSql = """
        select
          to_char(received_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM') as ym,
          coalesce(sum(total_cost_kes), 0) as purchases_kes,
          coalesce(sum(round(total_cost_kes * vat_rate, 2)), 0) as input_vat_kes
        from stock_receipt
        group by ym
        """;
    jdbcTemplate.queryForList(purchasesSql).forEach(row -> {
      String ym = (String) row.get("ym");
      MonthAccumulator acc = byMonth.computeIfAbsent(ym, k -> new MonthAccumulator());
      acc.purchasesTotal = acc.purchasesTotal.add(toBigDecimal(row.get("purchases_kes")));
      acc.inputVat = acc.inputVat.add(toBigDecimal(row.get("input_vat_kes")));
    });

    // 5. Tax Remittances Paid by the Supermarket
    List<TaxPayment> allPayments = taxPaymentRepository.findAllByOrderByPaymentDateDescCreatedAtDesc();
    for (TaxPayment p : allPayments) {
      MonthAccumulator acc = byMonth.computeIfAbsent(p.getTaxPeriod(), k -> new MonthAccumulator());
      acc.taxPaid = acc.taxPaid.add(p.getAmountPaidKes() != null ? p.getAmountPaidKes() : BigDecimal.ZERO);
    }

    // Build Monthly DTOs and cumulative totals
    List<MonthlyTaxSummaryDto> monthlySummaries = new ArrayList<>();
    BigDecimal cumOutputVat = BigDecimal.ZERO;
    BigDecimal cumReturnsVat = BigDecimal.ZERO;
    BigDecimal cumInputVat = BigDecimal.ZERO;
    BigDecimal cumNetPayable = BigDecimal.ZERO;
    BigDecimal cumTaxPaid = BigDecimal.ZERO;

    for (Map.Entry<String, MonthAccumulator> entry : byMonth.entrySet()) {
      String ym = entry.getKey();
      MonthAccumulator a = entry.getValue();

      BigDecimal netOutputVat = a.outputVat.subtract(a.returnsVat).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      BigDecimal netBeforeFloor = netOutputVat.subtract(a.inputVat);
      BigDecimal netTaxPayable = netBeforeFloor.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      BigDecimal vatCreditCarryover = netBeforeFloor.compareTo(BigDecimal.ZERO) < 0
          ? netBeforeFloor.abs().setScale(2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      BigDecimal paid = a.taxPaid.setScale(2, RoundingMode.HALF_UP);
      BigDecimal balanceDue = netTaxPayable.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

      String status;
      if (vatCreditCarryover.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(BigDecimal.ZERO) == 0) {
        status = "CREDIT_CARRYOVER";
      } else if (netTaxPayable.compareTo(BigDecimal.ZERO) == 0 && paid.compareTo(BigDecimal.ZERO) == 0) {
        status = "NIL_RETURN";
      } else if (paid.compareTo(netTaxPayable) >= 0) {
        status = "PAID";
      } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
        status = "PARTIALLY_PAID";
      } else {
        status = "UNPAID";
      }

      cumOutputVat = cumOutputVat.add(a.outputVat);
      cumReturnsVat = cumReturnsVat.add(a.returnsVat);
      cumInputVat = cumInputVat.add(a.inputVat);
      cumNetPayable = cumNetPayable.add(netTaxPayable);
      cumTaxPaid = cumTaxPaid.add(paid);

      monthlySummaries.add(new MonthlyTaxSummaryDto(
          ym,
          formatMonthLabel(ym),
          a.salesCount,
          a.grossSales.setScale(2, RoundingMode.HALF_UP),
          a.taxableSales.setScale(2, RoundingMode.HALF_UP),
          a.outputVat.setScale(2, RoundingMode.HALF_UP),
          a.returnsTotal.setScale(2, RoundingMode.HALF_UP),
          a.returnsVat.setScale(2, RoundingMode.HALF_UP),
          netOutputVat,
          a.purchasesTotal.setScale(2, RoundingMode.HALF_UP),
          a.inputVat.setScale(2, RoundingMode.HALF_UP),
          netTaxPayable,
          vatCreditCarryover,
          paid,
          balanceDue,
          status
      ));
    }

    MonthlyTaxSummaryDto currentMonth = monthlySummaries.stream()
        .filter(m -> targetMonth.equals(m.month()))
        .findFirst()
        .orElseThrow();

    BigDecimal cumBalanceDue = cumNetPayable.subtract(cumTaxPaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    CumulativeTaxSummaryDto cumulative = new CumulativeTaxSummaryDto(
        cumOutputVat.setScale(2, RoundingMode.HALF_UP),
        cumReturnsVat.setScale(2, RoundingMode.HALF_UP),
        cumInputVat.setScale(2, RoundingMode.HALF_UP),
        cumNetPayable.setScale(2, RoundingMode.HALF_UP),
        cumTaxPaid.setScale(2, RoundingMode.HALF_UP),
        cumBalanceDue
    );

    List<CategoryTaxBreakdownDto> categoryBreakdown = selectedCategories.values().stream()
        .map(c -> new CategoryTaxBreakdownDto(
            c.code,
            c.label,
            c.rateLabel,
            c.grossSales.setScale(2, RoundingMode.HALF_UP),
            c.taxableSales.setScale(2, RoundingMode.HALF_UP),
            c.outputVat.setScale(2, RoundingMode.HALF_UP),
            c.returnsTotal.setScale(2, RoundingMode.HALF_UP),
            c.returnsVat.setScale(2, RoundingMode.HALF_UP),
            c.outputVat.subtract(c.returnsVat).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        ))
        .toList();

    List<TaxPaymentDto> paymentDtos = allPayments.stream()
        .map(this::toPaymentDto)
        .toList();

    return new MonthlyTaxReportResponse(
        targetMonth,
        currentMonth,
        cumulative,
        monthlySummaries,
        categoryBreakdown,
        paymentDtos
    );
  }

  @Transactional
  public TaxPaymentDto recordTaxPayment(RecordTaxPaymentRequest req) {
    if (req == null) {
      throw new IllegalArgumentException("Tax payment request is required");
    }
    YearMonth ym = resolveMonth(req.taxPeriod());
    if (req.amountPaidKes() == null || req.amountPaidKes().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount paid (KES) must be greater than zero");
    }
    if (req.kraPrn() == null || req.kraPrn().isBlank()) {
      throw new IllegalArgumentException("KRA Payment Registration Number (PRN / eSlip reference) is required");
    }

    AuthenticatedUser actor = AuditLogService.currentActor();
    TaxPayment tp = new TaxPayment();
    tp.setId(UUID.randomUUID());
    tp.setTaxPeriod(ym.toString());
    tp.setTaxType(req.taxType() != null && !req.taxType().isBlank() ? req.taxType().trim().toUpperCase() : "VAT");
    tp.setAmountPaidKes(req.amountPaidKes().setScale(2, RoundingMode.HALF_UP));
    tp.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now(AccountingService.KENYA_ZONE));
    tp.setPaymentMethod(req.paymentMethod() != null && !req.paymentMethod().isBlank()
        ? req.paymentMethod().trim().toUpperCase() : "KRA_ITAX_MPESA");
    tp.setKraPrn(req.kraPrn().trim());
    tp.setNotes(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : null);
    if (actor != null) {
      tp.setRecordedBy(actor.userId());
      tp.setRecordedByUsername(actor.username());
    }
    tp.setCreatedAt(Instant.now());

    TaxPayment saved = taxPaymentRepository.save(tp);
    if (auditLogService != null) {
      auditLogService.record(
          "TAX_PAYMENT_RECORDED",
          "tax_payment",
          saved.getId().toString(),
          "Recorded KRA " + saved.getTaxType() + " remittance for " + saved.getTaxPeriod()
              + ": KES " + saved.getAmountPaidKes() + " (PRN " + saved.getKraPrn() + ")"
      );
    }
    return toPaymentDto(saved);
  }

  private TaxPaymentDto toPaymentDto(TaxPayment p) {
    return new TaxPaymentDto(
        p.getId(),
        p.getTaxPeriod(),
        p.getTaxType(),
        p.getAmountPaidKes(),
        p.getPaymentDate(),
        p.getPaymentMethod(),
        p.getKraPrn(),
        p.getNotes(),
        p.getRecordedByUsername(),
        p.getCreatedAt()
    );
  }

  private static BigDecimal extractVatFromGross(BigDecimal gross, String category) {
    if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    if ("A".equals(category)) {
      return gross.multiply(new BigDecimal("16")).divide(new BigDecimal("116"), 2, RoundingMode.HALF_UP);
    }
    if ("C".equals(category)) {
      return gross.multiply(new BigDecimal("8")).divide(new BigDecimal("108"), 2, RoundingMode.HALF_UP);
    }
    return BigDecimal.ZERO;
  }

  private static String normalizeCat(String cat) {
    if ("B".equalsIgnoreCase(cat) || "C".equalsIgnoreCase(cat) || "E".equalsIgnoreCase(cat)) {
      return cat.toUpperCase();
    }
    return "A";
  }

  private static Map<String, CategoryAccumulator> initCategoriesMap() {
    Map<String, CategoryAccumulator> map = new LinkedHashMap<>();
    map.put("A", new CategoryAccumulator("A", "Category A — Standard Rated", "16%"));
    map.put("C", new CategoryAccumulator("C", "Category C — Special Rated", "8%"));
    map.put("B", new CategoryAccumulator("B", "Category B — Zero-Rated", "0%"));
    map.put("E", new CategoryAccumulator("E", "Category E — VAT Exempt", "0%"));
    return map;
  }

  private static String formatMonthLabel(String ym) {
    try {
      return YearMonth.parse(ym).format(MONTH_LABEL_FMT);
    } catch (Exception e) {
      return ym;
    }
  }

  private static BigDecimal toBigDecimal(Object val) {
    if (val == null) return BigDecimal.ZERO;
    if (val instanceof BigDecimal bd) return bd;
    if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    return new BigDecimal(val.toString());
  }

  private static class MonthAccumulator {
    long salesCount = 0;
    BigDecimal grossSales = BigDecimal.ZERO;
    BigDecimal taxableSales = BigDecimal.ZERO;
    BigDecimal outputVat = BigDecimal.ZERO;
    BigDecimal returnsTotal = BigDecimal.ZERO;
    BigDecimal returnsVat = BigDecimal.ZERO;
    BigDecimal purchasesTotal = BigDecimal.ZERO;
    BigDecimal inputVat = BigDecimal.ZERO;
    BigDecimal taxPaid = BigDecimal.ZERO;
  }

  private static class CategoryAccumulator {
    final String code;
    final String label;
    final String rateLabel;
    BigDecimal grossSales = BigDecimal.ZERO;
    BigDecimal taxableSales = BigDecimal.ZERO;
    BigDecimal outputVat = BigDecimal.ZERO;
    BigDecimal returnsTotal = BigDecimal.ZERO;
    BigDecimal returnsVat = BigDecimal.ZERO;

    CategoryAccumulator(String code, String label, String rateLabel) {
      this.code = code;
      this.label = label;
      this.rateLabel = rateLabel;
    }
  }
}
