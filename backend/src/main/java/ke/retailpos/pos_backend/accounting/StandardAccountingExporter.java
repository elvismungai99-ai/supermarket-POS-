package ke.retailpos.pos_backend.accounting;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class StandardAccountingExporter implements AccountingExporter {

  private final ErpNextClient erpNextClient;

  public StandardAccountingExporter(ErpNextClient erpNextClient) {
    this.erpNextClient = erpNextClient;
  }

  @Override
  public byte[] exportToCsv(DailyAccountingSummary summary, JournalEntryDto journalEntry) {
    StringBuilder sb = new StringBuilder();
    // ERPNext / Standard GL CSV Header
    sb.append("Voucher Type,Posting Date,Company,Account,Debit,Credit,User Remark,Cost Center\n");

    for (JournalEntryDto.JournalAccountLineDto line : journalEntry.accounts()) {
      sb.append("\"").append(escape(journalEntry.voucherType())).append("\",");
      sb.append("\"").append(journalEntry.postingDate().toString()).append("\",");
      sb.append("\"").append(escape(journalEntry.company())).append("\",");
      sb.append("\"").append(escape(line.account())).append("\",");
      sb.append(line.debitInAccountCurrency() != null ? line.debitInAccountCurrency().toPlainString() : "0.00").append(",");
      sb.append(line.creditInAccountCurrency() != null ? line.creditInAccountCurrency().toPlainString() : "0.00").append(",");
      sb.append("\"").append(escape(line.userRemark() != null ? line.userRemark() : journalEntry.userRemark())).append("\",");
      sb.append("\"").append(escape(line.costCenter() != null ? line.costCenter() : "")).append("\"\n");
    }

    // Totals line
    sb.append("\"TOTAL\",");
    sb.append("\"").append(journalEntry.postingDate().toString()).append("\",");
    sb.append("\"").append(escape(journalEntry.company())).append("\",");
    sb.append("\"SUMMARY TOTALS\",");
    sb.append(journalEntry.totalDebit().toPlainString()).append(",");
    sb.append(journalEntry.totalCredit().toPlainString()).append(",");
    sb.append("\"Balanced: ").append(journalEntry.isBalanced() ? "YES (Difference 0.00)" : "NO").append("\",");
    sb.append("\"\"\n");

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public ErpNextClient.ErpNextResponse postToErpNext(
      DailyAccountingSummary summary,
      JournalEntryDto journalEntry,
      AccountingConfig config
  ) {
    return erpNextClient.postJournalEntry(
        config.getErpnextBaseUrl(),
        config.getErpnextApiKey(),
        config.getErpnextApiSecret(),
        journalEntry,
        config.isSubmitOnPost()
    );
  }

  private String escape(String s) {
    if (s == null) return "";
    String safe = s;
    if (!safe.isEmpty()) {
      char first = safe.charAt(0);
      if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
        safe = "'" + safe;
      }
    }
    return safe.replace("\"", "\"\"");
  }
}
