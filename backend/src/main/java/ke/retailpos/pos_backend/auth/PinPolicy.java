package ke.retailpos.pos_backend.auth;

import java.util.Optional;
import java.util.Set;

/**
 * Enforces baseline PIN/password complexity on new account creation and PIN resets
 * while keeping legacy accounts working until rotated.
 */
public final class PinPolicy {

  public static final int MIN_LENGTH = 6;
  private static final Set<String> COMMON_WEAK = Set.of(
      "012345", "123456", "234567", "345678", "456789", "567890",
      "654321", "987654", "876543", "765432", "098765",
      "123123", "112233", "121212", "password", "admin123", "12345678"
  );

  private PinPolicy() {}

  public static Optional<String> validate(String rawPin) {
    if (rawPin == null || rawPin.trim().length() < MIN_LENGTH) {
      return Optional.of("PIN/password must be at least " + MIN_LENGTH + " characters long.");
    }
    String trimmed = rawPin.trim();
    if (trimmed.chars().distinct().count() <= 1) {
      return Optional.of("PIN/password cannot consist of a single repeated character (e.g. 000000).");
    }
    if (COMMON_WEAK.contains(trimmed.toLowerCase())) {
      return Optional.of("PIN/password is too common or sequential. Choose a less predictable PIN/password.");
    }
    return Optional.empty();
  }
}
