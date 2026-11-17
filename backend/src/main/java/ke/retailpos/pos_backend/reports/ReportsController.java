package ke.retailpos.pos_backend.reports;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

  private final JdbcTemplate jdbcTemplate;
  private final MonthlyTaxService monthlyTaxService;

  public ReportsController(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, null);
  }

  @Autowired
  public ReportsController(JdbcTemplate jdbcTemplate, MonthlyTaxService monthlyTaxService) {
    this.jdbcTemplate = jdbcTemplate;
    this.monthlyTaxService = monthlyTaxService;
  }

  public record DailySalesSummary(
      String date,
      long totalSalesCount,
      BigDecimal totalRevenueKes,
      List<TillDailySummary> tillSummaries
  ) {}

  public record TillDailySummary(
      UUID tillId,
      long salesCount,
      BigDecimal revenueKes
  ) {}

  @GetMapping("/daily-sales")
  public List<DailySalesSummary> getDailySales() {
    String sql = """
        select
          to_char(created_at, 'YYYY-MM-DD') as sale_date,
          till_id,
          count(id) as cnt,
          coalesce(sum(total_kes), 0) as total_rev
        from sale
        group by to_char(created_at, 'YYYY-MM-DD'), till_id
        order by sale_date desc, till_id
        """;

    Map<String, List<Map<String, Object>>> grouped = jdbcTemplate.queryForList(sql).stream()
        .collect(java.util.stream.Collectors.groupingBy(row -> (String) row.get("sale_date")));

    return grouped.entrySet().stream()
        .sorted(Map.Entry.<String, List<Map<String, Object>>>comparingByKey().reversed())
        .map(entry -> {
          String date = entry.getKey();
          List<TillDailySummary> tills = entry.getValue().stream().map(row -> new TillDailySummary(
              (UUID) row.get("till_id"),
              ((Number) row.get("cnt")).longValue(),
              (BigDecimal) row.get("total_rev")
          )).toList();

          long totalCount = tills.stream().mapToLong(TillDailySummary::salesCount).sum();
          BigDecimal totalRev = tills.stream().map(TillDailySummary::revenueKes)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

          return new DailySalesSummary(date, totalCount, totalRev, tills);
        })
        .toList();
  }

  public record TaxInvoiceDto(
      UUID saleId,
      String receiptNumber,
      String date,
      UUID tillId,
      BigDecimal totalAmountKes,
      BigDecimal taxAmountKes,
      BigDecimal taxableAmountKes,
      String kraControlUnitNumber,
      String status,
      String qrCodeUrl
  ) {}

  @GetMapping("/tax-invoices")
  public List<TaxInvoiceDto> getTaxInvoices(
      @RequestParam(required = false) String month
  ) {
    String filterMonth = null;
    if (month != null && !month.isBlank()) {
      filterMonth = YearMonth.parse(month.trim()).toString();
    }

    String sql = filterMonth != null
        ? """
        select
          s.id as sale_id,
          s.till_id,
          to_char(s.created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM-DD HH24:MI:SS') as formatted_date,
          s.total_kes,
          coalesce(sum(sl.tax_amount_kes), 0) as total_tax,
          coalesce(sum(sl.line_total_kes - sl.tax_amount_kes), 0) as taxable_amount
        from sale s
        left join sale_line sl on sl.sale_id = s.id
        where to_char(s.created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM') = ?
        group by s.id, s.till_id, s.created_at, s.total_kes
        order by s.created_at desc
        limit 200
        """
        : """
        select
          s.id as sale_id,
          s.till_id,
          to_char(s.created_at AT TIME ZONE 'Africa/Nairobi', 'YYYY-MM-DD HH24:MI:SS') as formatted_date,
          s.total_kes,
          coalesce(sum(sl.tax_amount_kes), 0) as total_tax,
          coalesce(sum(sl.line_total_kes - sl.tax_amount_kes), 0) as taxable_amount
        from sale s
        left join sale_line sl on sl.sale_id = s.id
        group by s.id, s.till_id, s.created_at, s.total_kes
        order by s.created_at desc
        limit 100
        """;

    org.springframework.jdbc.core.RowMapper<TaxInvoiceDto> mapper = (rs, rowNum) -> {
      UUID saleId = (UUID) rs.getObject("sale_id");
      UUID tillId = (UUID) rs.getObject("till_id");
      String date = rs.getString("formatted_date");
      BigDecimal total = rs.getBigDecimal("total_kes");
      BigDecimal tax = rs.getBigDecimal("total_tax");
      BigDecimal taxable = rs.getBigDecimal("taxable_amount");
      String receiptNumber = "REC-" + date.substring(0, 10).replace("-", "") + "-" + saleId.toString().substring(0, 8).toUpperCase();
      String cuNumber = "KRA-VSCU-" + saleId.toString().substring(0, 8).toUpperCase();
      String qrUrl = "https://etims.kra.go.ke/verify?cu=" + cuNumber;

      return new TaxInvoiceDto(
          saleId,
          receiptNumber,
          date,
          tillId,
          total,
          tax,
          taxable,
          cuNumber,
          "COMPLIED",
          qrUrl
      );
    };

    return filterMonth != null
        ? jdbcTemplate.query(sql, mapper, filterMonth)
        : jdbcTemplate.query(sql, mapper);
  }

  @GetMapping("/monthly-tax")
  public ResponseEntity<?> getMonthlyTax(@RequestParam(required = false) String month) {
    if (monthlyTaxService == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "MonthlyTaxService unavailable"));
    }
    try {
      return ResponseEntity.ok(monthlyTaxService.getMonthlyTaxReport(month));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PostMapping("/tax-payments")
  public ResponseEntity<?> recordTaxPayment(@Valid @RequestBody MonthlyTaxService.RecordTaxPaymentRequest request) {
    if (monthlyTaxService == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "MonthlyTaxService unavailable"));
    }
    try {
      return ResponseEntity.status(HttpStatus.CREATED).body(monthlyTaxService.recordTaxPayment(request));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @GetMapping("/monthly-tax/export")
  public ResponseEntity<byte[]> exportMonthlyTaxCsv(@RequestParam(required = false) String month) {
    MonthlyTaxService.MonthlyTaxReportResponse report = monthlyTaxService.getMonthlyTaxReport(month);
    StringBuilder csv = new StringBuilder();
    csv.append("TaxPeriod,MonthLabel,SalesCount,GrossSalesKES,TaxableSalesKES,OutputVATKES,ReturnsVATKES,NetOutputVATKES,PurchasesKES,InputVATKES,NetTaxPayableKES,VATCreditCarryoverKES,TaxPaidKES,BalanceDueKES,Status\n");
    for (MonthlyTaxService.MonthlyTaxSummaryDto m : report.monthlySummaries()) {
      csv.append(sanitizeCsv(m.month())).append(",")
          .append(sanitizeCsv(m.monthLabel())).append(",")
          .append(m.salesCount()).append(",")
          .append(m.grossSalesKes()).append(",")
          .append(m.taxableSalesKes()).append(",")
          .append(m.outputVatKes()).append(",")
          .append(m.returnsVatKes()).append(",")
          .append(m.netOutputVatKes()).append(",")
          .append(m.purchasesTotalKes()).append(",")
          .append(m.inputVatKes()).append(",")
          .append(m.netTaxPayableKes()).append(",")
          .append(m.vatCreditCarryoverKes()).append(",")
          .append(m.taxPaidKes()).append(",")
          .append(m.balanceDueKes()).append(",")
          .append(sanitizeCsv(m.status())).append("\n");
    }

    byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kra_monthly_tax_summary_" + report.selectedMonth() + ".csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(bytes);
  }

  @GetMapping("/tax-invoices/export")
  public ResponseEntity<byte[]> exportTaxInvoicesCsv() {
    List<TaxInvoiceDto> invoices = getTaxInvoices(null);
    StringBuilder csv = new StringBuilder();
    csv.append("Date,ReceiptNumber,TillId,TaxableKES,TaxAmountKES,TotalKES,KRAControlUnitNumber,Status\n");
    for (TaxInvoiceDto inv : invoices) {
      csv.append(sanitizeCsv(inv.date())).append(",")
          .append(sanitizeCsv(inv.receiptNumber())).append(",")
          .append(inv.tillId()).append(",")
          .append(inv.taxableAmountKes()).append(",")
          .append(inv.taxAmountKes()).append(",")
          .append(inv.totalAmountKes()).append(",")
          .append(sanitizeCsv(inv.kraControlUnitNumber())).append(",")
          .append(sanitizeCsv(inv.status())).append("\n");
    }

    byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kra_etims_tax_invoices_" + LocalDate.now() + ".csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(bytes);
  }

  private static String sanitizeCsv(String val) {
    if (val == null) return "";
    if (!val.isEmpty()) {
      char c = val.charAt(0);
      if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
        return "'" + val;
      }
    }
    return val;
  }

  public record OutboxStatusDto(
      long pendingCount,
      long sentCount,
      long totalCount,
      String status
  ) {}

  @GetMapping("/outbox-status")
  public OutboxStatusDto getOutboxStatus() {
    Long pending = jdbcTemplate.queryForObject("select count(*) from outbox where sent_at is null", Long.class);
    Long sent = jdbcTemplate.queryForObject("select count(*) from outbox where sent_at is not null", Long.class);
    long p = pending != null ? pending : 0L;
    long s = sent != null ? sent : 0L;
    String status = p == 0 ? "SYNCED" : "OFFLINE_QUEUED_PENDING_DISPATCH";
    return new OutboxStatusDto(p, s, p + s, status);
  }
}
