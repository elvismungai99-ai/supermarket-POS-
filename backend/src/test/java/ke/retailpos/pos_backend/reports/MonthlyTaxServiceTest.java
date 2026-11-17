package ke.retailpos.pos_backend.reports;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonthlyTaxServiceTest {

  private JdbcTemplate jdbcTemplate;
  private TaxPaymentRepository taxPaymentRepository;
  private AuditLogService auditLogService;
  private MonthlyTaxService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    taxPaymentRepository = mock(TaxPaymentRepository.class);
    auditLogService = mock(AuditLogService.class);
    service = new MonthlyTaxService(jdbcTemplate, taxPaymentRepository, auditLogService);
  }

  @Test
  void testMonthlyTaxCalculatesOutputMinusReturnsMinusInputAndTracksPaid() {
    when(jdbcTemplate.queryForList(contains("from sale"))).thenReturn(List.of(
        Map.of("ym", "2026-11", "sale_cnt", 12L, "gross_sales", new BigDecimal("11600.00"))
    ));
    when(jdbcTemplate.queryForList(contains("from sale_line"))).thenReturn(List.of(
        Map.of(
            "ym", "2026-11",
            "tax_cat", "A",
            "gross_kes", new BigDecimal("11600.00"),
            "vat_kes", new BigDecimal("1600.00"),
            "net_kes", new BigDecimal("10000.00")
        )
    ));
    when(jdbcTemplate.queryForList(contains("from sale_return_line"))).thenReturn(List.of(
        Map.of(
            "ym", "2026-11",
            "tax_cat", "A",
            "refund_kes", new BigDecimal("1160.00")
        )
    ));
    when(jdbcTemplate.queryForList(contains("from stock_receipt"))).thenReturn(List.of(
        Map.of(
            "ym", "2026-11",
            "purchases_kes", new BigDecimal("2500.00"),
            "input_vat_kes", new BigDecimal("400.00")
        )
    ));

    TaxPayment p1 = new TaxPayment();
    p1.setId(UUID.randomUUID());
    p1.setTaxPeriod("2026-11");
    p1.setTaxType("VAT");
    p1.setAmountPaidKes(new BigDecimal("600.00"));
    p1.setPaymentDate(LocalDate.of(2026, 11, 15));
    p1.setPaymentMethod("KRA_ITAX_MPESA");
    p1.setKraPrn("PRN-2026-001");
    p1.setCreatedAt(Instant.now());

    when(taxPaymentRepository.findAllByOrderByPaymentDateDescCreatedAtDesc()).thenReturn(List.of(p1));

    var report = service.getMonthlyTaxReport("2026-11");
    var cur = report.currentMonth();

    // Output VAT = 1600, Returns VAT = 1160 * 16/116 = 160 -> Net Output VAT = 1440
    // Input VAT = 400 -> Net Tax Payable = 1040
    // Tax Paid = 600 -> Balance Due = 440, status = PARTIALLY_PAID
    assertEquals("2026-11", cur.month());
    assertEquals(new BigDecimal("1600.00"), cur.outputVatKes());
    assertEquals(new BigDecimal("160.00"), cur.returnsVatKes());
    assertEquals(new BigDecimal("1440.00"), cur.netOutputVatKes());
    assertEquals(new BigDecimal("400.00"), cur.inputVatKes());
    assertEquals(new BigDecimal("1040.00"), cur.netTaxPayableKes());
    assertEquals(new BigDecimal("600.00"), cur.taxPaidKes());
    assertEquals(new BigDecimal("440.00"), cur.balanceDueKes());
    assertEquals("PARTIALLY_PAID", cur.status());
    assertEquals(new BigDecimal("600.00"), report.cumulative().totalTaxPaidKes());
  }

  @Test
  void testRecordTaxPaymentPersistsAndAudits() {
    when(taxPaymentRepository.save(any(TaxPayment.class))).thenAnswer(inv -> inv.getArgument(0));

    var saved = service.recordTaxPayment(new MonthlyTaxService.RecordTaxPaymentRequest(
        "2026-11", "VAT", new BigDecimal("1040.00"), LocalDate.of(2026, 11, 17),
        "KRA_ITAX_MPESA", "202611170099", "Settled Nov VAT"
    ));

    assertEquals("2026-11", saved.taxPeriod());
    assertEquals(new BigDecimal("1040.00"), saved.amountPaidKes());
    assertEquals("202611170099", saved.kraPrn());
    verify(taxPaymentRepository).save(any(TaxPayment.class));
    verify(auditLogService).record(eq("TAX_PAYMENT_RECORDED"), eq("tax_payment"), any(), contains("PRN 202611170099"));
  }

  @Test
  void testInvalidMonthOrZeroPaymentRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.getMonthlyTaxReport("not-a-month"));
    assertThrows(IllegalArgumentException.class, () -> service.recordTaxPayment(
        new MonthlyTaxService.RecordTaxPaymentRequest("2026-11", "VAT", BigDecimal.ZERO, null, null, "PRN1", null)
    ));
  }
}
