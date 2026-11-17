package ke.retailpos.pos_backend.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts sensitive integration credentials (e.g. ERPNext API secret) at rest using AES-256-GCM.
 */
@Service
public class SecretEncryptionService {

  private static final String PREFIX = "ENC:v1:";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_BITS = 128;

  private final SecretKeySpec keySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public SecretEncryptionService(String rawKey) {
    this(rawKey, false);
  }

  @Autowired
  public SecretEncryptionService(
      @Value("${pos.security.encryption-key:${pos.security.jwt-secret:" + JwtService.DEV_DEFAULT_SECRET + "}}")
      String rawKey,
      @Value("${pos.security.enforce-strong-keys:false}") boolean enforceStrongKeys
  ) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new IllegalStateException("POS_ENCRYPTION_KEY must not be blank");
    }
    if (enforceStrongKeys && (JwtService.DEV_DEFAULT_SECRET.equals(rawKey) || rawKey.length() < 32)) {
      throw new IllegalStateException(
          "Security startup guard: POS_ENCRYPTION_KEY must be >=32 random chars and non-default when pos.security.enforce-strong-keys=true"
      );
    }
    try {
      byte[] sha = MessageDigest.getInstance("SHA-256")
          .digest(rawKey.getBytes(StandardCharsets.UTF_8));
      this.keySpec = new SecretKeySpec(sha, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize AES key", e);
    }
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return plaintext;
    }
    if (plaintext.startsWith(PREFIX)) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      buffer.put(iv);
      buffer.put(ciphertext);
      return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt secret", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null || stored.isBlank() || !stored.startsWith(PREFIX)) {
      return stored;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      ByteBuffer buffer = ByteBuffer.wrap(combined);
      byte[] iv = new byte[GCM_IV_LENGTH];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] plain = cipher.doFinal(ciphertext);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt stored secret", e);
    }
  }

  public static String mask(String secret) {
    if (secret == null || secret.isBlank()) {
      return "";
    }
    return "********";
  }

  public static boolean isMasked(String value) {
    return value != null && value.trim().matches("^\\*+$");
  }
}
