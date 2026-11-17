package ke.retailpos.pos_backend.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SecretEncryptionServiceTest {

  @Test
  void testEncryptDecryptRoundTripAndMasking() {
    SecretEncryptionService svc = new SecretEncryptionService("super-strong-test-key-at-least-32-chars");
    String raw = "erpnext_api_secret_9988";

    String encrypted = svc.encrypt(raw);
    assertNotEquals(raw, encrypted);
    assertTrue(encrypted.startsWith("ENC:v1:"));
    assertEquals(raw, svc.decrypt(encrypted));

    // Legacy plaintext passes through safely
    assertEquals("legacy_plain", svc.decrypt("legacy_plain"));

    // Mask check
    assertEquals("********", SecretEncryptionService.mask(encrypted));
    assertTrue(SecretEncryptionService.isMasked("********"));
    assertFalse(SecretEncryptionService.isMasked("new_secret"));
  }
}
