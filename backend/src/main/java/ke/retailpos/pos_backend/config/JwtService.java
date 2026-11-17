package ke.retailpos.pos_backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  public static final String DEV_DEFAULT_SECRET = "kenya-retail-pos-dev-secret-change-in-production-2026";
  private static final String HEADER_B64 = Base64.getUrlEncoder().withoutPadding()
      .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

  private final byte[] secretBytes;
  private final long ttlSeconds;
  private final ObjectMapper objectMapper;

  public JwtService(String secret, long ttlSeconds, ObjectMapper objectMapper) {
    this(secret, ttlSeconds, false, objectMapper);
  }

  @Autowired
  public JwtService(
      @Value("${pos.security.jwt-secret:" + DEV_DEFAULT_SECRET + "}") String secret,
      @Value("${pos.security.jwt-ttl-seconds:3600}") long ttlSeconds,
      @Value("${pos.security.enforce-strong-keys:false}") boolean enforceStrongKeys,
      ObjectMapper objectMapper
  ) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("POS_JWT_SECRET must not be blank");
    }
    if (DEV_DEFAULT_SECRET.equals(secret) || secret.length() < 32) {
      if (enforceStrongKeys) {
        throw new IllegalStateException(
            "Security startup guard: POS_JWT_SECRET must be at least 32 characters and not the default dev key when pos.security.enforce-strong-keys=true"
        );
      }
      log.warn("SECURITY NOTICE: Using default or short JWT secret. Set POS_JWT_SECRET (>=32 random chars) and POS_SECURITY_ENFORCE_STRONG_KEYS=true in production.");
    }
    this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    this.ttlSeconds = ttlSeconds;
    this.objectMapper = objectMapper;
  }

  public record TokenClaims(UUID cashierId, String username, String role, String jti, Instant expiresAt) {
    public TokenClaims(UUID cashierId, String username, String role) {
      this(cashierId, username, role, null, null);
    }
  }

  public String generateToken(UUID cashierId, String username, String role) {
    try {
      long now = Instant.now().getEpochSecond();
      String jti = UUID.randomUUID().toString();
      Map<String, Object> claims = Map.of(
          "jti", jti,
          "sub", cashierId.toString(),
          "username", username,
          "role", role.toUpperCase(),
          "iat", now,
          "exp", now + ttlSeconds
      );
      String payloadB64 = Base64.getUrlEncoder().withoutPadding()
          .encodeToString(objectMapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
      String signingInput = HEADER_B64 + "." + payloadB64;
      String sigB64 = sign(signingInput);
      return signingInput + "." + sigB64;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign JWT token", e);
    }
  }

  public TokenClaims verifyToken(String token) {
    if (token == null || token.isBlank()) return null;
    String[] parts = token.split("\\.");
    if (parts.length != 3) return null;

    try {
      String signingInput = parts[0] + "." + parts[1];
      String expectedSig = sign(signingInput);
      if (!MessageDigest.isEqual(
          expectedSig.getBytes(StandardCharsets.UTF_8),
          parts[2].getBytes(StandardCharsets.UTF_8))) {
        return null;
      }

      byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
      JsonNode node = objectMapper.readTree(payloadBytes);
      long exp = node.path("exp").asLong(0);
      if (exp < Instant.now().getEpochSecond()) {
        return null;
      }

      String jti = node.path("jti").asText("");
      return new TokenClaims(
          UUID.fromString(node.path("sub").asText()),
          node.path("username").asText(),
          node.path("role").asText(),
          jti.isBlank() ? null : jti,
          Instant.ofEpochSecond(exp)
      );
    } catch (Exception e) {
      return null;
    }
  }

  private String sign(String input) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
    byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }
}
