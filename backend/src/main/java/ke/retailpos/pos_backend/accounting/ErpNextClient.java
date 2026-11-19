package ke.retailpos.pos_backend.accounting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ErpNextClient {

  private static final Logger log = LoggerFactory.getLogger(ErpNextClient.class);

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public ErpNextClient(ObjectMapper objectMapper) {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    this.objectMapper = objectMapper;
  }

  /**
   * Normalizes user-entered ERPNext base URLs (e.g. strips /app/..., trailing slashes,
   * and prepends https:// for cloud domains or http:// for localhost).
   */
  public static String normalizeBaseUrl(String rawUrl) {
    if (rawUrl == null) return "";
    String s = rawUrl.trim();
    if (s.isBlank()) return "";

    if (!s.matches("(?i)^https?://.*")) {
      boolean local = s.startsWith("localhost") || s.startsWith("127.") || s.startsWith("10.") || s.startsWith("192.168.");
      s = (local ? "http://" : "https://") + s;
    }

    int hashIdx = s.indexOf('#');
    if (hashIdx >= 0) s = s.substring(0, hashIdx);
    int queryIdx = s.indexOf('?');
    if (queryIdx >= 0) s = s.substring(0, queryIdx);

    // Strip accidental Frappe UI or API paths copied from browser address bar
    s = s.replaceFirst("(?i)/(app|desk|api)(/.*)?$", "");
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  public record ErpNextResponse(
      boolean success,
      String journalEntryId,
      String errorMessage,
      int statusCode
  ) {}

  public record ConnectionTestResult(
      boolean connected,
      String message,
      int statusCode
  ) {}

  public record AccountVerificationResult(
      boolean reachable,
      List<String> matchedAccounts,
      List<String> missingAccounts,
      String message
  ) {}

  public record PurchaseInvoicePayload(
      String company,
      String supplierName,
      String supplierInvoiceNo,
      String postingDate,
      String itemName,
      String expenseOrInventoryAccount,
      String payableAccount,
      String costCenter,
      double qty,
      double rate,
      boolean submitOnPost,
      double vatRate,
      String inputVatAccount
  ) {
    public PurchaseInvoicePayload(
        String company,
        String supplierName,
        String supplierInvoiceNo,
        String postingDate,
        String itemName,
        String expenseOrInventoryAccount,
        String payableAccount,
        String costCenter,
        double qty,
        double rate,
        boolean submitOnPost
    ) {
      this(company, supplierName, supplierInvoiceNo, postingDate, itemName,
          expenseOrInventoryAccount, payableAccount, costCenter, qty, rate, submitOnPost, 0.0, null);
    }
  }

  public ErpNextResponse postJournalEntry(
      String baseUrl,
      String apiKey,
      String apiSecret,
      JournalEntryDto journalEntry
  ) {
    return postJournalEntry(baseUrl, apiKey, apiSecret, journalEntry, true);
  }

  /**
   * Posts a Journal Entry to ERPNext via REST API.
   * Endpoint: POST /api/resource/Journal Entry
   * Header: Authorization: token <api_key>:<api_secret>
   */
  public ErpNextResponse postJournalEntry(
      String baseUrl,
      String apiKey,
      String apiSecret,
      JournalEntryDto journalEntry,
      boolean submitOnPost
  ) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return new ErpNextResponse(false, null, "ERPNext base URL is not configured", 400);
    }

    String cleanBaseUrl = normalizeBaseUrl(baseUrl);
    if (cleanBaseUrl.isBlank()) {
      return new ErpNextResponse(false, null, "ERPNext base URL is not configured", 400);
    }
    String url = cleanBaseUrl + "/api/resource/Journal%20Entry";

    try {
      // ERPNext-side idempotency lookup: prevent duplicate Journal Entries if an earlier request timed out after commit
      if (journalEntry.referenceKey() != null && !journalEntry.referenceKey().isBlank()) {
        Optional<String> existing = findExistingByField(
            cleanBaseUrl, apiKey, apiSecret, "Journal%20Entry", "cheque_no", journalEntry.referenceKey()
        );
        if (existing.isPresent()) {
          log.info("Idempotency hit in ERPNext: Journal Entry {} already exists for ref {}",
              existing.get(), journalEntry.referenceKey());
          return new ErpNextResponse(true, existing.get(), null, 200);
        }
      }

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("doctype", "Journal Entry");
      payload.put("voucher_type", journalEntry.voucherType());
      payload.put("posting_date", journalEntry.postingDate().toString());
      payload.put("company", journalEntry.company());
      payload.put("user_remark", journalEntry.userRemark());
      if (journalEntry.referenceKey() != null && !journalEntry.referenceKey().isBlank()) {
        payload.put("cheque_no", journalEntry.referenceKey());
        payload.put("cheque_date", journalEntry.postingDate().toString());
      }
      payload.put("docstatus", submitOnPost ? 1 : 0);

      List<Map<String, Object>> accountLines = new ArrayList<>();
      for (JournalEntryDto.JournalAccountLineDto line : journalEntry.accounts()) {
        Map<String, Object> lineMap = new LinkedHashMap<>();
        lineMap.put("account", line.account());
        lineMap.put("debit_in_account_currency", line.debitInAccountCurrency().doubleValue());
        lineMap.put("credit_in_account_currency", line.creditInAccountCurrency().doubleValue());
        if (line.userRemark() != null) {
          lineMap.put("user_remark", line.userRemark());
        }
        if (line.costCenter() != null) {
          lineMap.put("cost_center", line.costCenter());
        }
        accountLines.add(lineMap);
      }
      payload.put("accounts", accountLines);

      String requestJson = objectMapper.writeValueAsString(payload);

      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(20))
          .POST(HttpRequest.BodyPublishers.ofString(requestJson));

      if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
        reqBuilder.header("Authorization", "token " + apiKey.trim() + ":" + apiSecret.trim());
      }

      HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();

      if (status >= 200 && status < 300) {
        JsonNode root = objectMapper.readTree(response.body());
        String name = root.path("data").path("name").asText();
        if (name == null || name.isBlank()) {
          name = "ACC-JV-" + journalEntry.postingDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        log.info("Successfully posted Journal Entry to ERPNext: {}", name);
        return new ErpNextResponse(true, name, null, status);
      } else {
        String errDetail = response.body();
        log.warn("ERPNext API rejected Journal Entry (status {}): {}", status, errDetail);
        return new ErpNextResponse(false, null, "ERPNext HTTP " + status + ": " + errDetail, status);
      }
    } catch (Exception e) {
      String detail = describeException(e, cleanBaseUrl);
      log.warn("Failed to communicate with ERPNext server at {}: {}", url, detail);
      return new ErpNextResponse(false, null, "Network or server connection failed: " + detail, 503);
    }
  }

  /**
   * Tests connection to ERPNext server using current credentials.
   */
  public ConnectionTestResult testConnection(String baseUrl, String apiKey, String apiSecret) {
    String cleanBaseUrl = normalizeBaseUrl(baseUrl);
    if (cleanBaseUrl.isBlank()) {
      return new ConnectionTestResult(false, "ERPNext Base URL must be provided", 400);
    }

    String url = cleanBaseUrl + "/api/method/frappe.auth.get_logged_user";

    try {
      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(12))
          .GET();

      if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
        reqBuilder.header("Authorization", "token " + apiKey.trim() + ":" + apiSecret.trim());
      }

      HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonNode root = objectMapper.readTree(response.body());
        String user = root.path("message").asText("Connected User");
        return new ConnectionTestResult(true, "Connected to ERPNext (" + cleanBaseUrl + ") as: " + user, 200);
      } else if (response.statusCode() == 401 || response.statusCode() == 403) {
        return new ConnectionTestResult(
            false,
            "Authentication rejected by ERPNext (HTTP " + response.statusCode() + "). Verify API Key & API Secret belong to " + cleanBaseUrl,
            response.statusCode()
        );
      } else {
        return new ConnectionTestResult(false, "ERPNext responded with HTTP " + response.statusCode() + ": " + response.body(), response.statusCode());
      }
    } catch (Exception e) {
      return new ConnectionTestResult(false, "Connection error: " + describeException(e, cleanBaseUrl), 503);
    }
  }

  /**
   * Queries ERPNext Chart of Accounts for the configured company and reports missing mappings.
   */
  public AccountVerificationResult verifyCompanyAccounts(
      String baseUrl,
      String apiKey,
      String apiSecret,
      String companyName,
      List<String> requiredAccounts
  ) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return new AccountVerificationResult(false, List.of(), requiredAccounts, "ERPNext Base URL must be provided");
    }

    String cleanBaseUrl = normalizeBaseUrl(baseUrl);
    if (cleanBaseUrl.isBlank()) {
      return new AccountVerificationResult(false, List.of(), requiredAccounts, "ERPNext Base URL must be provided");
    }
    String encodedFilters = java.net.URLEncoder.encode(
        "[[\"company\",\"=\",\"" + companyName.replace("\"", "") + "\"]]",
        java.nio.charset.StandardCharsets.UTF_8
    );
    String encodedFields = java.net.URLEncoder.encode("[\"name\"]", java.nio.charset.StandardCharsets.UTF_8);
    String url = cleanBaseUrl + "/api/resource/Account?filters=" + encodedFilters + "&fields=" + encodedFields + "&limit_page_length=500";

    try {
      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(15))
          .GET();
      if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
        reqBuilder.header("Authorization", "token " + apiKey.trim() + ":" + apiSecret.trim());
      }

      HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return new AccountVerificationResult(
            false, List.of(), requiredAccounts,
            "ERPNext returned HTTP " + response.statusCode() + ": " + response.body()
        );
      }

      JsonNode root = objectMapper.readTree(response.body());
      Set<String> erpAccounts = new HashSet<>();
      for (JsonNode item : root.path("data")) {
        String name = item.path("name").asText("");
        if (!name.isBlank()) erpAccounts.add(name);
      }

      List<String> matched = new ArrayList<>();
      List<String> missing = new ArrayList<>();
      for (String req : requiredAccounts) {
        if (req != null && !req.isBlank()) {
          if (erpAccounts.contains(req)) matched.add(req);
          else missing.add(req);
        }
      }

      String msg = missing.isEmpty()
          ? "All " + matched.size() + " mapped accounts verified in ERPNext under '" + companyName + "'."
          : matched.size() + " matched, " + missing.size() + " account(s) missing in ERPNext: " + String.join(", ", missing);

      return new AccountVerificationResult(true, matched, missing, msg);
    } catch (Exception e) {
      return new AccountVerificationResult(false, List.of(), requiredAccounts,
          "Could not reach ERPNext at " + cleanBaseUrl + ": " + describeException(e, cleanBaseUrl));
    }
  }

  /**
   * Posts a Supplier Purchase Invoice / Stock Receipt to ERPNext (GL accounting with Input VAT taxes).
   */
  public ErpNextResponse postPurchaseInvoice(
      String baseUrl,
      String apiKey,
      String apiSecret,
      PurchaseInvoicePayload invoice
  ) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return new ErpNextResponse(false, null, "ERPNext base URL is not configured", 400);
    }
    String cleanBaseUrl = normalizeBaseUrl(baseUrl);
    if (cleanBaseUrl.isBlank()) {
      return new ErpNextResponse(false, null, "ERPNext base URL is not configured", 400);
    }
    String url = cleanBaseUrl + "/api/resource/Purchase%20Invoice";

    try {
      // ERPNext-side idempotency lookup by supplier invoice / bill_no
      if (invoice.supplierInvoiceNo() != null && !invoice.supplierInvoiceNo().isBlank()) {
        Optional<String> existing = findExistingByField(
            cleanBaseUrl, apiKey, apiSecret, "Purchase%20Invoice", "bill_no", invoice.supplierInvoiceNo().trim()
        );
        if (existing.isPresent()) {
          log.info("Idempotency hit in ERPNext: Purchase Invoice {} already exists for bill_no {}",
              existing.get(), invoice.supplierInvoiceNo());
          return new ErpNextResponse(true, existing.get(), null, 200);
        }
      }

      Map<String, Object> itemRow = new LinkedHashMap<>();
      itemRow.put("item_name", invoice.itemName());
      itemRow.put("qty", invoice.qty());
      itemRow.put("rate", invoice.rate());
      itemRow.put("expense_account", invoice.expenseOrInventoryAccount());
      itemRow.put("cost_center", invoice.costCenter());

      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("doctype", "Purchase Invoice");
      payload.put("supplier", invoice.supplierName());
      if (invoice.supplierInvoiceNo() != null && !invoice.supplierInvoiceNo().isBlank()) {
        payload.put("bill_no", invoice.supplierInvoiceNo().trim());
      }
      payload.put("posting_date", invoice.postingDate());
      payload.put("company", invoice.company());
      payload.put("credit_to", invoice.payableAccount());
      payload.put("docstatus", invoice.submitOnPost() ? 1 : 0);
      payload.put("items", List.of(itemRow));

      if (invoice.vatRate() > 0 && invoice.inputVatAccount() != null && !invoice.inputVatAccount().isBlank()) {
        double pct = Math.round(invoice.vatRate() * 10000.0) / 100.0;
        Map<String, Object> taxRow = new LinkedHashMap<>();
        taxRow.put("charge_type", "On Net Total");
        taxRow.put("account_head", invoice.inputVatAccount());
        taxRow.put("rate", pct);
        taxRow.put("description", "Input VAT " + pct + "%");
        payload.put("taxes", List.of(taxRow));
      }

      String requestJson = objectMapper.writeValueAsString(payload);
      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(20))
          .POST(HttpRequest.BodyPublishers.ofString(requestJson));

      if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
        reqBuilder.header("Authorization", "token " + apiKey.trim() + ":" + apiSecret.trim());
      }

      HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        JsonNode root = objectMapper.readTree(response.body());
        String name = root.path("data").path("name").asText();
        return new ErpNextResponse(true, name, null, status);
      } else {
        return new ErpNextResponse(false, null, "ERPNext HTTP " + status + ": " + response.body(), status);
      }
    } catch (Exception e) {
      return new ErpNextResponse(false, null, "Failed to post Purchase Invoice to ERPNext: " + describeException(e, cleanBaseUrl), 503);
    }
  }

  private Optional<String> findExistingByField(
      String cleanBaseUrl,
      String apiKey,
      String apiSecret,
      String docTypePath,
      String fieldName,
      String value
  ) {
    try {
      String sanitizedVal = value.replace("\"", "").trim();
      String encodedFilters = java.net.URLEncoder.encode(
          "[[\"" + fieldName + "\",\"=\",\"" + sanitizedVal + "\"],[\"docstatus\",\"!=\",2]]",
          java.nio.charset.StandardCharsets.UTF_8
      );
      String encodedFields = java.net.URLEncoder.encode("[\"name\"]", java.nio.charset.StandardCharsets.UTF_8);
      String url = cleanBaseUrl + "/api/resource/" + docTypePath
          + "?filters=" + encodedFilters + "&fields=" + encodedFields + "&limit_page_length=1";

      HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Accept", "application/json")
          .timeout(Duration.ofSeconds(5))
          .GET();
      if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
        reqBuilder.header("Authorization", "token " + apiKey.trim() + ":" + apiSecret.trim());
      }

      HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonNode data = objectMapper.readTree(response.body()).path("data");
        if (data.isArray() && !data.isEmpty()) {
          String name = data.get(0).path("name").asText("");
          if (!name.isBlank()) {
            return Optional.of(name);
          }
        }
      }
    } catch (Exception ignored) {
      // Let the primary POST attempt surface connectivity errors
    }
    return Optional.empty();
  }

  private static String describeException(Exception e, String cleanBaseUrl) {
    Throwable cause = e.getCause() != null ? e.getCause() : e;
    if (e instanceof java.net.ConnectException || cause instanceof java.net.ConnectException) {
      return "Connection refused — ensure ERPNext is running at " + cleanBaseUrl;
    }
    if (e instanceof java.net.UnknownHostException || cause instanceof java.net.UnknownHostException) {
      return "DNS lookup failed — check the ERPNext domain spelling (" + cleanBaseUrl + ") and internet connectivity";
    }
    if (e instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
      return "Request timed out contacting " + cleanBaseUrl + " — check server availability or firewall";
    }
    if (e instanceof javax.net.ssl.SSLException || cause instanceof javax.net.ssl.SSLException) {
      return "SSL/TLS certificate error connecting to " + cleanBaseUrl + ": " + cause.getMessage();
    }
    if (e instanceof IllegalArgumentException) {
      return "Invalid ERPNext Base URL (" + cleanBaseUrl + "): " + e.getMessage();
    }
    if (e.getMessage() != null && !e.getMessage().isBlank()) {
      return e.getMessage();
    }
    return cause.getClass().getSimpleName() + " contacting " + cleanBaseUrl;
  }
}
