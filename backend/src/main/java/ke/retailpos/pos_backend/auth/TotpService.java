package ke.retailpos.pos_backend.auth;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Pure-Java RFC 6238 TOTP (6-digit, 30-second window, Base32 secret) service for Admin/Manager MFA.
 */
@Service
public class TotpService {

  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private static final int SECRET_BYTES = 20;
  private final SecureRandom random = new SecureRandom();

  public String generateSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    random.nextBytes(bytes);
    return base32Encode(bytes);
  }

  public String buildOtpAuthUri(String issuer, String username, String base32Secret) {
    return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(username)
        + "?secret=" + base32Secret + "&issuer=" + urlEncode(issuer) + "&digits=6&period=30";
  }

  public boolean verifyCode(String base32Secret, String code) {
    if (base32Secret == null || base32Secret.isBlank() || code == null) {
      return false;
    }
    String clean = code.trim();
    if (!clean.matches("^\\d{6}$")) {
      return false;
    }
    long currentStep = Instant.now().getEpochSecond() / 30L;
    byte[] key = base32Decode(base32Secret);
    // Allow +-1 step (30s) clock skew
    for (long offset = -1; offset <= 1; offset++) {
      String expected = generateTotpAtStep(key, currentStep + offset);
      if (expected.equals(clean)) {
        return true;
      }
    }
    return false;
  }

  public String generateCurrentCode(String base32Secret) {
    long step = Instant.now().getEpochSecond() / 30L;
    return generateTotpAtStep(base32Decode(base32Secret), step);
  }

  private String generateTotpAtStep(byte[] key, long step) {
    try {
      byte[] msg = ByteBuffer.allocate(8).putLong(step).array();
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(msg);
      int offset = hash[hash.length - 1] & 0x0F;
      int binary = ((hash[offset] & 0x7F) << 24)
          | ((hash[offset + 1] & 0xFF) << 16)
          | ((hash[offset + 2] & 0xFF) << 8)
          | (hash[offset + 3] & 0xFF);
      int otp = binary % 1_000_000;
      return String.format("%06d", otp);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute TOTP", e);
    }
  }

  private static String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
        bitsLeft -= 5;
      }
    }
    if (bitsLeft > 0) {
      out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
    }
    return out.toString();
  }

  private static byte[] base32Decode(String input) {
    String clean = input.toUpperCase().replaceAll("[^A-Z2-7]", "");
    byte[] out = new byte[clean.length() * 5 / 8];
    int buffer = 0;
    int bitsLeft = 0;
    int idx = 0;
    for (int i = 0; i < clean.length(); i++) {
      int val = BASE32_ALPHABET.indexOf(clean.charAt(i));
      if (val < 0) continue;
      buffer = (buffer << 5) | val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        out[idx++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
        bitsLeft -= 8;
      }
    }
    return out;
  }

  private static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
  }
}
