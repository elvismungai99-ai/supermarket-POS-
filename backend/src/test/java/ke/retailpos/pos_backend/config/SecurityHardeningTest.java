package ke.retailpos.pos_backend.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.providers.payment.DarajaProperties;
import ke.retailpos.pos_backend.providers.payment.MpesaCallbackController;
import ke.retailpos.pos_backend.providers.payment.PaymentProvider;
import ke.retailpos.pos_backend.sales.Payment;
import ke.retailpos.pos_backend.sales.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class SecurityHardeningTest {

  @Test
  void testEncryptionKeyMustBeDistinctFromJwtSecretWhenEnforced() {
    String shared = "a-very-strong-32-character-secret-key-value";
    assertThrows(IllegalStateException.class, () -> new SecretEncryptionService(shared, shared, true));

    String distinct = "another-distinct-32-character-aes-key-value";
    SecretEncryptionService svc = new SecretEncryptionService(distinct, shared, true);
    String enc = svc.encrypt("my-totp-secret");
    assertTrue(enc.startsWith("ENC:v1:"));
    assertEquals("my-totp-secret", svc.decrypt(enc));
  }

  @Test
  void testMpesaCallbackEnforcesTokenFailClosedAmountAndPendingStkState() {
    PaymentRepository paymentRepo = mock(PaymentRepository.class);
    PaymentProvider provider = mock(PaymentProvider.class);
    DarajaProperties props = new DarajaProperties();
    props.setCallbackSecret("super-secret-callback-token");

    MpesaCallbackController controller = new MpesaCallbackController(
        paymentRepo, provider, new ObjectMapper(), props, null
    );

    String validPayload = """
        {
          "Body": {
            "stkCallback": {
              "CheckoutRequestID": "ws_CO_123",
              "ResultCode": 0,
              "ResultDesc": "Success",
              "CallbackMetadata": {
                "Item": [
                  {"Name": "Amount", "Value": 250.00},
                  {"Name": "MpesaReceiptNumber", "Value": "SJL9ABC123"}
                ]
              }
            }
          }
        }
        """;

    // Wrong callback token -> 403 Forbidden
    var rejected = controller.handleDarajaCallback(validPayload, "wrong-token");
    assertEquals(HttpStatus.FORBIDDEN, rejected.getStatusCode());

    // Valid token + PENDING_STK payment -> PAID with receipt reference
    Payment pendingStk = new Payment(UUID.randomUUID(), UUID.randomUUID(), "MPESA", new BigDecimal("250.00"), "ws_CO_123", "PENDING_STK");
    when(paymentRepo.findByReference("ws_CO_123")).thenReturn(Optional.of(pendingStk));

    var accepted = controller.handleDarajaCallback(validPayload, "super-secret-callback-token");
    assertEquals(HttpStatus.OK, accepted.getStatusCode());
    assertEquals("PAID", pendingStk.getState());
    assertEquals("SJL9ABC123", pendingStk.getReference());

    // Missing Amount in callback payload -> fail-closed FAILED
    String missingAmountPayload = """
        {
          "Body": {
            "stkCallback": {
              "CheckoutRequestID": "ws_CO_999",
              "ResultCode": 0,
              "ResultDesc": "Success",
              "CallbackMetadata": { "Item": [] }
            }
          }
        }
        """;
    Payment pending2 = new Payment(UUID.randomUUID(), UUID.randomUUID(), "MPESA", new BigDecimal("500.00"), "ws_CO_999", "PENDING_STK");
    when(paymentRepo.findByReference("ws_CO_999")).thenReturn(Optional.of(pending2));

    controller.handleDarajaCallback(missingAmountPayload, "super-secret-callback-token");
    assertEquals("FAILED", pending2.getState());
  }

  @Test
  void testTokenCleanupSchedulerPurgesExpiredTokens() {
    RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
    TokenCleanupScheduler scheduler = new TokenCleanupScheduler(repo);
    scheduler.purgeExpiredRevokedTokens();
    verify(repo, times(1)).deleteByExpiresAtBefore(any());
  }
}
