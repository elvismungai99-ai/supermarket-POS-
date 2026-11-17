package ke.retailpos.pos_backend.accounting;

public interface AccountingExporter {
  byte[] exportToCsv(DailyAccountingSummary summary, JournalEntryDto journalEntry);
  ErpNextClient.ErpNextResponse postToErpNext(DailyAccountingSummary summary, JournalEntryDto journalEntry, AccountingConfig config);
}
