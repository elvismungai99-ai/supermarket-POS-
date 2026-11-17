package ke.retailpos.pos_backend.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Parses standard supermarket scale-printed EAN-13 variable-weight barcodes:
 * Format: 20XXXXXWWWWWC or 21XXXXXWWWWWC
 * - Prefix: 20 or 21 (2 digits)
 * - Item PLU/SKU code: 5 digits (lookup barcode matches prefix+PLU or PLU, e.g. 2000123 or 00123)
 * - Weight in grams: 5 digits (e.g. 01250 -> 1.250 kg)
 * - Check digit: 1 digit
 */
@Service
public class BarcodeParserService {

  public record ParsedScaleBarcode(
      String rawBarcode,
      String prefixPluBarcode,
      String shortPlu,
      BigDecimal weightKg
  ) {}

  public Optional<ParsedScaleBarcode> parseWeightedEan13(String barcode) {
    if (barcode == null) return Optional.empty();
    String clean = barcode.trim();
    if (clean.length() != 13 || !clean.chars().allMatch(Character::isDigit)) {
      return Optional.empty();
    }
    if (!clean.startsWith("20") && !clean.startsWith("21")) {
      return Optional.empty();
    }

    String prefixAndPlu = clean.substring(0, 7);
    String shortPlu = clean.substring(2, 7);
    int grams = Integer.parseInt(clean.substring(7, 12));
    if (grams <= 0) {
      return Optional.empty();
    }

    BigDecimal weightKg = BigDecimal.valueOf(grams)
        .divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
    return Optional.of(new ParsedScaleBarcode(clean, prefixAndPlu, shortPlu, weightKg));
  }
}
