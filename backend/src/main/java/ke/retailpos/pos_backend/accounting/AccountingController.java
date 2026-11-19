package ke.retailpos.pos_backend.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting")
public class AccountingController {

  public static final UUID DEFAULT_SHOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final AccountingService accountingService;

  public AccountingController(AccountingService accountingService) {
    this.accountingService = accountingService;
  }

  @GetMapping("/summary")
  public ResponseEntity<AccountingService.DailySummaryResult> getSummary(
      @RequestParam(required = false) UUID shopId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    UUID targetShop = shopId != null ? shopId : DEFAULT_SHOP_ID;
    LocalDate targetDate = date != null ? date : LocalDate.now(AccountingService.KENYA_ZONE);
    return ResponseEntity.ok(accountingService.generateDailySummary(targetShop, targetDate));
  }

  @PostMapping("/generate")
  public ResponseEntity<AccountingService.DailySummaryResult> generateSummary(
      @RequestParam(required = false) UUID shopId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    UUID targetShop = shopId != null ? shopId : DEFAULT_SHOP_ID;
    LocalDate targetDate = date != null ? date : LocalDate.now(AccountingService.KENYA_ZONE);
    return ResponseEntity.ok(accountingService.generateDailySummary(targetShop, targetDate));
  }

  @PostMapping("/post")
  public ResponseEntity<AccountingService.PostResult> postToErpNext(
      @RequestParam(required = false) UUID shopId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false, defaultValue = "false") boolean force
  ) {
    UUID targetShop = shopId != null ? shopId : DEFAULT_SHOP_ID;
    LocalDate targetDate = date != null ? date : LocalDate.now(AccountingService.KENYA_ZONE);
    return ResponseEntity.ok(accountingService.postToErpNext(targetShop, targetDate, force));
  }

  @PostMapping("/verify-accounts")
  public ResponseEntity<ErpNextClient.AccountVerificationResult> verifyAccounts(
      @RequestBody(required = false) AccountingConfig override
  ) {
    return ResponseEntity.ok(accountingService.verifyErpNextAccounts(override));
  }

  @GetMapping("/export/csv")
  public ResponseEntity<byte[]> exportCsv(
      @RequestParam(required = false) UUID shopId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
  ) {
    UUID targetShop = shopId != null ? shopId : DEFAULT_SHOP_ID;
    LocalDate targetDate = date != null ? date : LocalDate.now(AccountingService.KENYA_ZONE);
    byte[] csvData = accountingService.exportCsv(targetShop, targetDate);

    String filename = "gl_summary_" + targetDate + ".csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(csvData);
  }

  @GetMapping("/config")
  public ResponseEntity<AccountingConfig> getConfig() {
    return ResponseEntity.ok(accountingService.getMaskedAccountingConfig());
  }

  @PutMapping("/config")
  public ResponseEntity<AccountingConfig> updateConfig(@RequestBody AccountingConfig config) {
    accountingService.updateAccountingConfig(config);
    return ResponseEntity.ok(accountingService.getMaskedAccountingConfig());
  }

  @PostMapping("/test-connection")
  public ResponseEntity<ErpNextClient.ConnectionTestResult> testConnection(
      @RequestBody(required = false) AccountingService.TestConnectionRequest override
  ) {
    return ResponseEntity.ok(accountingService.testErpNextConnection(override));
  }

  @GetMapping("/history")
  public ResponseEntity<List<DailyAccountingSummary>> getHistory(@RequestParam(required = false) UUID shopId) {
    return ResponseEntity.ok(accountingService.getHistory(shopId));
  }
}
