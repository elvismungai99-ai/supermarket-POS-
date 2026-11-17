package ke.retailpos.pos_backend.product;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BarcodeParserServiceTest {

  private final BarcodeParserService parser = new BarcodeParserService();

  @Test
  void testParsesWeightedEan13LabelWithPrefix20() {
    // Prefix 20, PLU 00123, weight 01250g (1.250 kg), check digit 4
    var parsed = parser.parseWeightedEan13("2000123012504");
    assertTrue(parsed.isPresent());
    assertEquals("2000123", parsed.get().prefixPluBarcode());
    assertEquals("00123", parsed.get().shortPlu());
    assertEquals(new BigDecimal("1.250"), parsed.get().weightKg());
  }

  @Test
  void testIgnoresStandardRetailEan13Barcodes() {
    assertTrue(parser.parseWeightedEan13("6161100001234").isEmpty());
    assertTrue(parser.parseWeightedEan13("12345").isEmpty());
    assertTrue(parser.parseWeightedEan13(null).isEmpty());
  }
}
