package ke.retailpos.pos_backend.pricing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

  private PricingService pricingService;

  @BeforeEach
  void setUp() {
    pricingService = new PricingService();
  }

  @Test
  @DisplayName("Standard Rate (16% VAT): Accurate inclusive tax calculation for single item")
  void testStandardVat16SingleItem() {
    UUID id = UUID.randomUUID();
    // 1 item @ KES 65.00 inclusive of 16% VAT
    var result = pricingService.calculateLine(id, "Brookside Milk 500ml", new BigDecimal("1.000"), new BigDecimal("65.00"), "A");

    assertEquals(new BigDecimal("65.00"), result.lineTotalKes());
    assertEquals(new BigDecimal("56.03"), result.taxableAmountKes());
    assertEquals(new BigDecimal("8.97"), result.taxAmountKes());
    assertEquals(result.lineTotalKes(), result.taxableAmountKes().add(result.taxAmountKes()),
        "Taxable + Tax must equal line total exactly");
  }

  @Test
  @DisplayName("Zero Rated (0% VAT, Category B): Line total equals taxable with 0 tax")
  void testZeroRatedItem() {
    UUID id = UUID.randomUUID();
    var result = pricingService.calculateLine(id, "Jogoo Maize Meal 2kg", new BigDecimal("2.000"), new BigDecimal("170.00"), "B");

    assertEquals(new BigDecimal("340.00"), result.lineTotalKes());
    assertEquals(new BigDecimal("340.00"), result.taxableAmountKes());
    assertEquals(new BigDecimal("0.00"), result.taxAmountKes());
  }

  @Test
  @DisplayName("Exempt (Category E): Line total equals taxable with 0 tax")
  void testExemptItem() {
    UUID id = UUID.randomUUID();
    var result = pricingService.calculateLine(id, "Panadol Extra", new BigDecimal("1.000"), new BigDecimal("120.00"), "E");

    assertEquals(new BigDecimal("120.00"), result.lineTotalKes());
    assertEquals(new BigDecimal("120.00"), result.taxableAmountKes());
    assertEquals(new BigDecimal("0.00"), result.taxAmountKes());
  }

  @Test
  @DisplayName("Special Rate (Category C, 8%): Correctly extracts 8% VAT")
  void testSpecialRate8Percent() {
    UUID id = UUID.randomUUID();
    var result = pricingService.calculateLine(id, "Domestic Fuel 1L", new BigDecimal("1.000"), new BigDecimal("108.00"), "C");

    assertEquals(new BigDecimal("108.00"), result.lineTotalKes());
    assertEquals(new BigDecimal("100.00"), result.taxableAmountKes());
    assertEquals(new BigDecimal("8.00"), result.taxAmountKes());
  }

  @Test
  @DisplayName("Weighed Item (3 Decimals): 0.345 kg @ KES 450.00/kg")
  void testWeighedItemCleanRounding() {
    UUID id = UUID.randomUUID();
    var result = pricingService.calculateLine(id, "Beef Steak", new BigDecimal("0.345"), new BigDecimal("450.00"), "A");

    // 0.345 * 450.00 = 155.25
    assertEquals(new BigDecimal("155.25"), result.lineTotalKes());
    // 155.25 / 1.16 = 133.836... -> 133.84
    assertEquals(new BigDecimal("133.84"), result.taxableAmountKes());
    assertEquals(new BigDecimal("21.41"), result.taxAmountKes());
    assertEquals(result.lineTotalKes(), result.taxableAmountKes().add(result.taxAmountKes()));
  }

  @Test
  @DisplayName("Weighed Item Fractional Half-Up Rounding: 1.785 kg @ KES 85.50/kg")
  void testWeighedItemFractionalRounding() {
    UUID id = UUID.randomUUID();
    // 1.785 * 85.50 = 152.6175 -> rounds HALF_UP to 152.62
    var result = pricingService.calculateLine(id, "Tomatoes Local", new BigDecimal("1.785"), new BigDecimal("85.50"), "B");

    assertEquals(new BigDecimal("152.62"), result.lineTotalKes());
    assertEquals(new BigDecimal("152.62"), result.taxableAmountKes());
    assertEquals(new BigDecimal("0.00"), result.taxAmountKes());
  }

  @Test
  @DisplayName("Tricky Repeating Decimal: 0.333 kg @ KES 100.00/kg")
  void testTrickyRepeatingDecimal() {
    UUID id = UUID.randomUUID();
    // 0.333 * 100.00 = 33.30
    var result = pricingService.calculateLine(id, "Sugar", new BigDecimal("0.333"), new BigDecimal("100.00"), "A");

    assertEquals(new BigDecimal("33.30"), result.lineTotalKes());
    // 33.30 / 1.16 = 28.7068... -> 28.71
    assertEquals(new BigDecimal("28.71"), result.taxableAmountKes());
    assertEquals(new BigDecimal("4.59"), result.taxAmountKes());
    assertEquals(new BigDecimal("33.30"), result.taxableAmountKes().add(result.taxAmountKes()));
  }

  @Test
  @DisplayName("Cart Pricing: Mixed categories (16% VAT, Zero-Rated, Exempt) sum correctly")
  void testMixedCartPricingSummary() {
    List<PricingService.LinePricingRequest> items = List.of(
        new PricingService.LinePricingRequest(UUID.randomUUID(), "Brookside Milk", new BigDecimal("2.000"), new BigDecimal("65.00"), "A"),
        new PricingService.LinePricingRequest(UUID.randomUUID(), "Jogoo Maize Meal", new BigDecimal("1.000"), new BigDecimal("170.00"), "B"),
        new PricingService.LinePricingRequest(UUID.randomUUID(), "Panadol Extra", new BigDecimal("1.000"), new BigDecimal("120.00"), "E")
    );

    var cart = pricingService.calculateCart(items);

    // Line 1: 130.00 (A, 16%) -> taxable 112.07, tax 17.93
    // Line 2: 170.00 (B, 0%)  -> taxable 170.00, tax 0.00
    // Line 3: 120.00 (E, 0%)  -> taxable 120.00, tax 0.00
    // Total gross = 130 + 170 + 120 = 420.00
    // Total taxable = 112.07 + 170.00 + 120.00 = 402.07
    // Total tax = 17.93
    assertEquals(new BigDecimal("420.00"), cart.totalKes());
    assertEquals(new BigDecimal("402.07"), cart.subtotalKes());
    assertEquals(new BigDecimal("17.93"), cart.totalTaxKes());
    assertEquals(cart.totalKes(), cart.subtotalKes().add(cart.totalTaxKes()));

    assertEquals(3, cart.taxBreakdowns().size());
  }

  @Test
  @DisplayName("Cash Payment: Exact change calculation and validation")
  void testCashPaymentCalculation() {
    BigDecimal total = new BigDecimal("420.00");
    BigDecimal tendered = new BigDecimal("500.00");

    var result = pricingService.calculateCashPayment(total, tendered);

    assertEquals(new BigDecimal("420.00"), result.totalKes());
    assertEquals(new BigDecimal("500.00"), result.amountTenderedKes());
    assertEquals(new BigDecimal("80.00"), result.changeKes());
  }

  @Test
  @DisplayName("Cash Payment: Exact payment results in zero change")
  void testCashPaymentExact() {
    BigDecimal total = new BigDecimal("420.00");
    var result = pricingService.calculateCashPayment(total, total);

    assertEquals(new BigDecimal("0.00"), result.changeKes());
  }

  @Test
  @DisplayName("Cash Payment: Underpayment throws IllegalArgumentException")
  void testCashPaymentUnderpaymentThrows() {
    BigDecimal total = new BigDecimal("420.00");
    BigDecimal tendered = new BigDecimal("400.00");

    assertThrows(IllegalArgumentException.class, () ->
        pricingService.calculateCashPayment(total, tendered)
    );
  }

  @Test
  @DisplayName("Validation: Non-positive quantity throws IllegalArgumentException")
  void testInvalidQuantityThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        pricingService.calculateLine(UUID.randomUUID(), "Item", BigDecimal.ZERO, new BigDecimal("10.00"), "A")
    );
    assertThrows(IllegalArgumentException.class, () ->
        pricingService.calculateLine(UUID.randomUUID(), "Item", new BigDecimal("-1.000"), new BigDecimal("10.00"), "A")
    );
  }

  @Test
  @DisplayName("Validation: Negative price throws IllegalArgumentException")
  void testNegativePriceThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        pricingService.calculateLine(UUID.randomUUID(), "Item", new BigDecimal("1.000"), new BigDecimal("-10.00"), "A")
    );
  }

  @Test
  @DisplayName("Empty cart returns zero totals")
  void testEmptyCart() {
    var cart = pricingService.calculateCart(List.of());
    assertEquals(new BigDecimal("0.00"), cart.totalKes());
    assertEquals(new BigDecimal("0.00"), cart.subtotalKes());
    assertEquals(new BigDecimal("0.00"), cart.totalTaxKes());
    assertTrue(cart.lines().isEmpty());
    assertTrue(cart.taxBreakdowns().isEmpty());
  }
}
