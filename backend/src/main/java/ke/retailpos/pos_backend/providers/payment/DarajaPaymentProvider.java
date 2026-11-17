package ke.retailpos.pos_backend.providers.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real Safaricom Daraja Lipa na M-Pesa Online (STK Push) client.
 * Activated when pos.mpesa.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "pos.mpesa.enabled", havingValue = "true")
public class DarajaPaymentProvider implements PaymentProvider {

  private static final Logger log = LoggerFactory.getLogger(DarajaPaymentProvider.class);
  private static final DateTimeFormatter DARAJA_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final DarajaProperties props;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public DarajaPaymentProvider(DarajaProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public PaymentResult initiatePayment(PaymentRequest request) {
    if (request.type() == PaymentType.CASH) {
      return new PaymentResult(PaymentStatus.SUCCESS, "CASH-" + UUID.randomUUID().toString().substring(0, 8), "Cash payment accepted", false);
    }
    if (request.type() == PaymentType.BANK) {
      String ref = (request.reference() != null && !request.reference().isBlank())
          ? request.reference()
          : "BANK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      return new PaymentResult(PaymentStatus.SUCCESS, ref, "Bank PDQ reference recorded", false);
    }

    try {
      String msisdn = normalizeKenyanMsisdn(request.customerPhone());
      String token = fetchAccessToken();
      String timestamp = ZonedDateTime.now(ZoneId.of("Africa/Nairobi")).format(DARAJA_TS);
      String password = Base64.getEncoder().encodeToString(
          (props.getShortCode() + props.getPasskey() + timestamp).getBytes(StandardCharsets.UTF_8)
      );

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("BusinessShortCode", props.getShortCode());
      body.put("Password", password);
      body.put("Timestamp", timestamp);
      body.put("TransactionType", props.getTransactionType());
      body.put("Amount", request.amountKes().intValue());
      body.put("PartyA", msisdn);
      body.put("PartyB", props.getShortCode());
      body.put("PhoneNumber", msisdn);
      body.put("CallBackURL", props.getCallbackUrl());
      body.put("AccountReference", request.reference() != null ? request.reference() : "RETAIL-POS");
      body.put("TransactionDesc", "Supermarket Purchase");

      HttpRequest stkReq = HttpRequest.newBuilder()
          .uri(URI.create(cleanBaseUrl() + "/mpesa/stkpush/v1/processrequest"))
          .header("Authorization", "Bearer " + token)
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(15))
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = httpClient.send(stkReq, HttpResponse.BodyHandlers.ofString());
      JsonNode root = objectMapper.readTree(resp.body());

      if (resp.statusCode() >= 200 && resp.statusCode() < 300 && "0".equals(root.path("ResponseCode").asText())) {
        String checkoutId = root.path("CheckoutRequestID").asText();
        String msg = root.path("CustomerMessage").asText("STK push sent to customer phone");
        log.info("Daraja STK Push accepted: CheckoutRequestID={}", checkoutId);
        return new PaymentResult(PaymentStatus.PENDING, checkoutId, msg, false);
      } else {
        String err = root.path("errorMessage").asText(root.path("ResponseDescription").asText(resp.body()));
        log.warn("Daraja STK Push failed ({}): {}", resp.statusCode(), err);
        return new PaymentResult(PaymentStatus.FAILED, null, "Daraja error: " + err, false);
      }
    } catch (Exception e) {
      log.error("Daraja STK Push exception: {}", e.getMessage());
      return new PaymentResult(PaymentStatus.FAILED, null, "Daraja communication failed: " + e.getMessage(), false);
    }
  }

  @Override
  public PaymentResult checkStatus(String transactionReference) {
    try {
      String token = fetchAccessToken();
      String timestamp = ZonedDateTime.now(ZoneId.of("Africa/Nairobi")).format(DARAJA_TS);
      String password = Base64.getEncoder().encodeToString(
          (props.getShortCode() + props.getPasskey() + timestamp).getBytes(StandardCharsets.UTF_8)
      );

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("BusinessShortCode", props.getShortCode());
      body.put("Password", password);
      body.put("Timestamp", timestamp);
      body.put("CheckoutRequestID", transactionReference);

      HttpRequest queryReq = HttpRequest.newBuilder()
          .uri(URI.create(cleanBaseUrl() + "/mpesa/stkpushquery/v1/query"))
          .header("Authorization", "Bearer " + token)
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(10))
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();

      HttpResponse<String> resp = httpClient.send(queryReq, HttpResponse.BodyHandlers.ofString());
      JsonNode root = objectMapper.readTree(resp.body());
      String resultCode = root.path("ResultCode").asText("");
      if ("0".equals(resultCode)) {
        return new PaymentResult(PaymentStatus.SUCCESS, transactionReference, root.path("ResultDesc").asText("Confirmed"), false);
      } else if (!resultCode.isBlank()) {
        return new PaymentResult(PaymentStatus.FAILED, transactionReference, root.path("ResultDesc").asText("Failed"), false);
      }
      return new PaymentResult(PaymentStatus.PENDING, transactionReference, "Awaiting M-Pesa PIN confirmation", false);
    } catch (Exception e) {
      return new PaymentResult(PaymentStatus.PENDING, transactionReference, "Status check error: " + e.getMessage(), false);
    }
  }

  private String fetchAccessToken() throws Exception {
    String creds = Base64.getEncoder().encodeToString(
        (props.getConsumerKey() + ":" + props.getConsumerSecret()).getBytes(StandardCharsets.UTF_8)
    );
    HttpRequest authReq = HttpRequest.newBuilder()
        .uri(URI.create(cleanBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials"))
        .header("Authorization", "Basic " + creds)
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

    HttpResponse<String> resp = httpClient.send(authReq, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new IllegalStateException("Daraja OAuth failed with HTTP " + resp.statusCode());
    }
    JsonNode json = objectMapper.readTree(resp.body());
    String token = json.path("access_token").asText();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Daraja OAuth returned empty access_token");
    }
    return token;
  }

  private String cleanBaseUrl() {
    String u = props.getBaseUrl();
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }

  public static String normalizeKenyanMsisdn(String phone) {
    if (phone == null) throw new IllegalArgumentException("Phone number is required");
    String digits = phone.replaceAll("[^0-9]", "");
    if (digits.startsWith("0") && digits.length() == 10) {
      return "254" + digits.substring(1);
    }
    if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length() == 9) {
      return "254" + digits;
    }
    if (digits.startsWith("254") && digits.length() == 12) {
      return digits;
    }
    throw new IllegalArgumentException("Invalid Kenyan M-Pesa phone number: " + phone);
  }
}
