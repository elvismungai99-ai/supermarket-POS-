package ke.retailpos.pos_backend.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

  public static final BigDecimal VAT_RATE_16 = new BigDecimal("0.16");
  public static final BigDecimal VAT_RATE_8  = new BigDecimal("0.08");
  public static final BigDecimal VAT_RATE_0  = new BigDecimal("0.00");

  public record LinePricingRequest(
      UUID productId,
      String name,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      String taxCategory
  ) {}

  public record LinePricingResult(
      UUID productId,
      String name,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      BigDecimal lineTotalKes,
      String taxCategory,
      BigDecimal taxRate,
      BigDecimal taxableAmountKes,
      BigDecimal taxAmountKes
  ) {}

  public record TaxBreakdown(
      String category,
      BigDecimal rate,
      BigDecimal taxableKes,
      BigDecimal taxKes,
      BigDecimal grossKes
  ) {}

  public record CartPricingSummary(
      List<LinePricingResult> lines,
      BigDecimal subtotalKes,
      BigDecimal totalTaxKes,
      BigDecimal totalKes,
      List<TaxBreakdown> taxBreakdowns
  ) {}

  public record CashPaymentResult(
      BigDecimal totalKes,
      BigDecimal amountTenderedKes,
      BigDecimal changeKes
  ) {}

  public BigDecimal resolveTaxRate(String taxCategory) {
    if (taxCategory == null) return VAT_RATE_16;
    return switch (taxCategory.trim().toUpperCase()) {
      case "A", "16", "VAT16" -> VAT_RATE_16;
      case "B", "0", "ZERO_RATED" -> VAT_RATE_0;
      case "C", "8", "VAT8" -> VAT_RATE_8;
      case "E", "EXEMPT" -> VAT_RATE_0;
      default -> VAT_RATE_16;
    };
  }

  public LinePricingResult calculateLine(
      UUID productId,
      String name,
      BigDecimal quantity,
      BigDecimal unitPriceKes,
      String taxCategory
  ) {
    Objects.requireNonNull(quantity, "quantity must not be null");
    Objects.requireNonNull(unitPriceKes, "unitPriceKes must not be null");

    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Quantity must be positive: " + quantity);
    }
    if (unitPriceKes.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Unit price must not be negative: " + unitPriceKes);
    }

    BigDecimal qtyNormalized = quantity.setScale(3, RoundingMode.HALF_UP);
    BigDecimal priceNormalized = unitPriceKes.setScale(2, RoundingMode.HALF_UP);
    BigDecimal lineTotal = qtyNormalized.multiply(priceNormalized).setScale(2, RoundingMode.HALF_UP);

    String normalizedCat = taxCategory != null ? taxCategory.trim().toUpperCase() : "A";
    BigDecimal rate = resolveTaxRate(normalizedCat);

    BigDecimal taxable;
    BigDecimal tax;
    if (rate.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal divisor = BigDecimal.ONE.add(rate);
      taxable = lineTotal.divide(divisor, 2, RoundingMode.HALF_UP);
      tax = lineTotal.subtract(taxable);
    } else {
      taxable = lineTotal;
      tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    return new LinePricingResult(
        productId,
        name,
        qtyNormalized,
        priceNormalized,
        lineTotal,
        normalizedCat,
        rate,
        taxable,
        tax
    );
  }

  public CartPricingSummary calculateCart(List<LinePricingRequest> items) {
    if (items == null || items.isEmpty()) {
      return new CartPricingSummary(
          Collections.emptyList(),
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
          Collections.emptyList()
      );
    }

    List<LinePricingResult> lines = new ArrayList<>();
    Map<String, BigDecimal[]> categoryTotals = new TreeMap<>(); // category -> [gross, taxable, tax, rate]

    for (LinePricingRequest req : items) {
      LinePricingResult line = calculateLine(
          req.productId(),
          req.name(),
          req.quantity(),
          req.unitPriceKes(),
          req.taxCategory()
      );
      lines.add(line);

      categoryTotals.compute(line.taxCategory(), (cat, existing) -> {
        if (existing == null) {
          return new BigDecimal[]{
              line.lineTotalKes(),
              line.taxableAmountKes(),
              line.taxAmountKes(),
              line.taxRate()
          };
        } else {
          existing[0] = existing[0].add(line.lineTotalKes());
          existing[1] = existing[1].add(line.taxableAmountKes());
          existing[2] = existing[2].add(line.taxAmountKes());
          return existing;
        }
      });
    }

    BigDecimal totalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalTaxable = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalTax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    List<TaxBreakdown> breakdowns = new ArrayList<>();
    for (Map.Entry<String, BigDecimal[]> entry : categoryTotals.entrySet()) {
      String cat = entry.getKey();
      BigDecimal[] totals = entry.getValue();
      breakdowns.add(new TaxBreakdown(
          cat,
          totals[3],
          totals[1],
          totals[2],
          totals[0]
      ));
      totalGross = totalGross.add(totals[0]);
      totalTaxable = totalTaxable.add(totals[1]);
      totalTax = totalTax.add(totals[2]);
    }

    return new CartPricingSummary(lines, totalTaxable, totalTax, totalGross, breakdowns);
  }

  public CashPaymentResult calculateCashPayment(BigDecimal totalKes, BigDecimal amountTenderedKes) {
    Objects.requireNonNull(totalKes, "totalKes must not be null");
    Objects.requireNonNull(amountTenderedKes, "amountTenderedKes must not be null");

    BigDecimal total = totalKes.setScale(2, RoundingMode.HALF_UP);
    BigDecimal tendered = amountTenderedKes.setScale(2, RoundingMode.HALF_UP);

    if (tendered.compareTo(total) < 0) {
      throw new IllegalArgumentException(
          String.format("Tendered amount KES %s is less than total KES %s", tendered, total)
      );
    }

    BigDecimal change = tendered.subtract(total).setScale(2, RoundingMode.HALF_UP);
    return new CashPaymentResult(total, tendered, change);
  }
}
