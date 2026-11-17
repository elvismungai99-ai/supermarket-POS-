package ke.retailpos.pos_backend.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

  private final TotpService totp = new TotpService();

  @Test
  void testGenerateAndVerifyCurrentCode() {
    String secret = totp.generateSecret();
    assertNotNull(secret);
    assertEquals(32, secret.length());

    String code = totp.generateCurrentCode(secret);
    assertEquals(6, code.length());
    assertTrue(totp.verifyCode(secret, code));
    assertFalse(totp.verifyCode(secret, "000000"));
    assertFalse(totp.verifyCode(secret, "invalid"));
  }

  @Test
  void testOtpAuthUriContainsIssuerAndAccount() {
    String uri = totp.buildOtpAuthUri("KenyaRetailPOS", "chief_admin", "JBSWY3DPEHPK3PXP");
    assertTrue(uri.startsWith("otpauth://totp/"));
    assertTrue(uri.contains("KenyaRetailPOS"));
    assertTrue(uri.contains("chief_admin"));
    assertTrue(uri.contains("secret=JBSWY3DPEHPK3PXP"));
  }
}
