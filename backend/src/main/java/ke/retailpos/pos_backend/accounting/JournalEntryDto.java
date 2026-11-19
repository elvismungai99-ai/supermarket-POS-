package ke.retailpos.pos_backend.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record JournalEntryDto(
    String doctype,
    String voucherType,
    LocalDate postingDate,
    String company,
    String userRemark,
    List<JournalAccountLineDto> accounts,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    BigDecimal difference,
    boolean isBalanced,
    String referenceKey
) {

  public JournalEntryDto(
      String doctype,
      String voucherType,
      LocalDate postingDate,
      String company,
      String userRemark,
      List<JournalAccountLineDto> accounts,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      BigDecimal difference,
      boolean isBalanced
  ) {
    this(doctype, voucherType, postingDate, company, userRemark, accounts, totalDebit, totalCredit, difference, isBalanced, null);
  }

  public record JournalAccountLineDto(
      String account,
      BigDecimal debitInAccountCurrency,
      BigDecimal creditInAccountCurrency,
      String userRemark,
      String costCenter
  ) {}

  public static JournalEntryDto of(
      String company,
      LocalDate postingDate,
      String userRemark,
      List<JournalAccountLineDto> lines
  ) {
    return of(company, postingDate, userRemark, lines, null);
  }

  public static JournalEntryDto of(
      String company,
      LocalDate postingDate,
      String userRemark,
      List<JournalAccountLineDto> lines,
      String referenceKey
  ) {
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;

    for (JournalAccountLineDto line : lines) {
      if (line.debitInAccountCurrency() != null) {
        totalDebit = totalDebit.add(line.debitInAccountCurrency());
      }
      if (line.creditInAccountCurrency() != null) {
        totalCredit = totalCredit.add(line.creditInAccountCurrency());
      }
    }

    BigDecimal diff = totalDebit.subtract(totalCredit).abs();
    boolean balanced = diff.compareTo(new BigDecimal("0.005")) < 0;

    return new JournalEntryDto(
        "Journal Entry",
        "Journal Entry",
        postingDate,
        company,
        userRemark,
        lines,
        totalDebit,
        totalCredit,
        totalDebit.subtract(totalCredit),
        balanced,
        referenceKey
    );
  }
}
