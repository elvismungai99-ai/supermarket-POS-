import { useState, useEffect } from 'react';
import { apiFetch, downloadAuthenticatedFile } from '../utils/apiClient';

export interface DailyAccountingSummary {
  id: string;
  shopId: string;
  businessDate: string;
  salesStandardRatedNetKes: number;
  salesSpecialRatedNetKes?: number;
  salesZeroRatedNetKes: number;
  salesExemptNetKes: number;
  outputVatKes: number;
  totalGrossSalesKes: number;
  cashTakingsKes: number;
  mpesaTakingsKes: number;
  bankTakingsKes: number;
  loyaltyRedeemedKes?: number;
  returnsTotalKes: number;
  returnsTaxKes: number;
  returnsNetKes: number;
  returnsCogsKes?: number;
  cashDifferenceKes: number;
  cogsTotalKes: number;
  totalDebitsKes: number;
  totalCreditsKes: number;
  isBalanced: boolean;
  status: 'DRAFT' | 'POSTED' | 'QUEUED' | 'FAILED';
  erpnextJournalEntryId?: string | null;
  erpnextCompany?: string | null;
  postedAt?: string | null;
  errorMessage?: string | null;
}

export interface JournalAccountLineDto {
  account: string;
  debitInAccountCurrency: number;
  creditInAccountCurrency: number;
  userRemark: string;
  costCenter: string;
}

export interface JournalEntryDto {
  doctype: string;
  voucherType: string;
  postingDate: string;
  company: string;
  userRemark: string;
  accounts: JournalAccountLineDto[];
  totalDebit: number;
  totalCredit: number;
  difference: number;
  isBalanced: boolean;
}

export interface DailySummaryResult {
  summary: DailyAccountingSummary;
  journalEntry: JournalEntryDto;
}

export interface AccountingConfig {
  id: string;
  companyName: string;
  erpnextBaseUrl: string;
  erpnextApiKey: string;
  erpnextApiSecret: string;
  autoPostOnEod: boolean;
  submitOnPost: boolean;
  accountCashOnHand: string;
  accountMpesaClearing: string;
  accountBankClearing: string;
  accountSalesStandard: string;
  accountSalesSpecial: string;
  accountSalesZeroRated: string;
  accountSalesExempt: string;
  accountOutputVat: string;
  accountSalesReturns: string;
  accountCashOverShort: string;
  accountCogs: string;
  accountInventory: string;
  accountCostCenter: string;
  accountLoyaltyDiscount: string;
  accountInputVat: string;
  accountAccountsPayable: string;
}

export function AccountingErpNextView() {
  const [selectedDate, setSelectedDate] = useState<string>(
    () => new Date().toISOString().split('T')[0]
  );
  const [data, setData] = useState<DailySummaryResult | null>(null);
  const [history, setHistory] = useState<DailyAccountingSummary[]>([]);
  const [config, setConfig] = useState<AccountingConfig | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [testResult, setTestResult] = useState<{ connected: boolean; message: string } | null>(null);
  const [verifyResult, setVerifyResult] = useState<{ reachable: boolean; missingAccounts: string[]; message: string } | null>(null);
  const [alertMsg, setAlertMsg] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  // Load summary for selected date
  const loadSummary = async (date: string) => {
    setLoading(true);
    setAlertMsg(null);
    try {
      const res = await apiFetch(`/api/accounting/summary?date=${date}`);
      if (res.ok) {
        const json: DailySummaryResult = await res.json();
        setData(json);
      } else {
        setAlertMsg({ type: 'error', text: 'Failed to load accounting summary for ' + date });
      }
    } catch {
      setAlertMsg({ type: 'error', text: 'Network error connecting to accounting backend' });
    } finally {
      setLoading(false);
    }
  };

  // Load history & config
  const loadMeta = async () => {
    try {
      const [histRes, cfgRes] = await Promise.all([
        apiFetch('/api/accounting/history'),
        apiFetch('/api/accounting/config'),
      ]);
      if (histRes.ok) setHistory(await histRes.json());
      if (cfgRes.ok) setConfig(await cfgRes.json());
    } catch {
      // Ignored
    }
  };

  useEffect(() => {
    loadSummary(selectedDate);
    loadMeta();
  }, [selectedDate]);

  // Handle post to ERPNext
  const handlePostToErpNext = async (force = false) => {
    setActionLoading(true);
    setAlertMsg(null);
    try {
      const res = await apiFetch(`/api/accounting/post?date=${selectedDate}&force=${force}`, { method: 'POST' });
      const result = await res.json();
      if (result.success) {
        setAlertMsg({ type: 'success', text: result.message || 'Successfully posted to ERPNext!' });
      } else if (result.status === 'QUEUED') {
        setAlertMsg({ type: 'info', text: result.message || 'Queued in Outbox for sync when ERPNext is reachable.' });
      } else {
        setAlertMsg({ type: 'error', text: result.message || 'Posting failed.' });
      }
      loadSummary(selectedDate);
      loadMeta();
    } catch (err: unknown) {
      setAlertMsg({ type: 'error', text: err instanceof Error ? err.message : 'Failed to trigger ERPNext posting' });
    } finally {
      setActionLoading(false);
    }
  };

  // Handle recalculate
  const handleRecalculate = async () => {
    setActionLoading(true);
    try {
      const res = await apiFetch(`/api/accounting/generate?date=${selectedDate}`, { method: 'POST' });
      if (res.ok) {
        setData(await res.json());
        setAlertMsg({ type: 'success', text: 'Daily accounting summary recalculated successfully.' });
        loadMeta();
      }
    } catch {
      setAlertMsg({ type: 'error', text: 'Recalculation failed.' });
    } finally {
      setActionLoading(false);
    }
  };

  // Handle test connection
  const handleTestConnection = async () => {
    setTestResult(null);
    try {
      const res = await apiFetch('/api/accounting/test-connection', { method: 'POST' });
      const json = await res.json();
      setTestResult(json);
    } catch (err: unknown) {
      setTestResult({ connected: false, message: err instanceof Error ? err.message : 'Connection ping failed' });
    }
  };

  const handleVerifyAccounts = async () => {
    setVerifyResult(null);
    try {
      const res = await apiFetch('/api/accounting/verify-accounts', { method: 'POST' });
      const json = await res.json();
      setVerifyResult(json);
    } catch (err: unknown) {
      setVerifyResult({
        reachable: false,
        missingAccounts: [],
        message: err instanceof Error ? err.message : 'COA verification failed',
      });
    }
  };

  // Handle save config
  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!config) return;
    try {
      const res = await apiFetch('/api/accounting/config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      });
      if (res.ok) {
        const saved = await res.json();
        setConfig(saved);
        setShowConfigModal(false);
        setAlertMsg({ type: 'success', text: 'ERPNext & Chart of Accounts configuration saved.' });
        loadSummary(selectedDate);
      }
    } catch {
      setAlertMsg({ type: 'error', text: 'Failed to update configuration.' });
    }
  };

  const summary = data?.summary;
  const journal = data?.journalEntry;

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-white dark:bg-zinc-900 p-5 rounded-2xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-2xl">📑</span>
            <h1 className="text-xl font-bold">General Ledger & ERPNext Accounting</h1>
          </div>
          <p className="text-xs text-zinc-500 mt-1">
            Automated double-entry daily sales summaries, VAT breakdown, trial balance, and direct ERPNext posting.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-1.5 bg-zinc-100 dark:bg-zinc-800 px-3 py-1.5 rounded-xl border border-zinc-200 dark:border-zinc-700">
            <label className="text-xs font-semibold text-zinc-500">Business Date:</label>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="bg-transparent text-xs font-mono font-bold focus:outline-hidden text-zinc-800 dark:text-zinc-200"
            />
          </div>

          <button
            onClick={handleRecalculate}
            disabled={actionLoading}
            className="px-3 py-2 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 text-xs font-bold rounded-xl transition flex items-center gap-1.5"
            title="Recalculate today's totals from till sales"
          >
            <span>🔄</span> Recalculate
          </button>

          <button
            type="button"
            onClick={() => {
              downloadAuthenticatedFile(`/api/accounting/export/csv?date=${selectedDate}`, `gl_summary_${selectedDate}.csv`)
                .catch((err) => setAlertMsg({ type: 'error', text: err.message }));
            }}
            className="px-3.5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl transition shadow-xs flex items-center gap-1.5"
          >
            <span>📥</span> Export GL CSV
          </button>

          <button
            onClick={() => setShowConfigModal(true)}
            className="px-3 py-2 bg-purple-50 dark:bg-purple-950/40 text-purple-700 dark:text-purple-300 hover:bg-purple-100 dark:hover:bg-purple-900/50 text-xs font-bold rounded-xl border border-purple-200 dark:border-purple-800 transition flex items-center gap-1.5"
          >
            <span>⚙️</span> ERPNext Settings
          </button>
        </div>
      </div>

      {/* Alert banner */}
      {alertMsg && (
        <div
          className={`p-4 rounded-xl text-xs font-medium border flex items-center justify-between ${
            alertMsg.type === 'success'
              ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800'
              : alertMsg.type === 'info'
              ? 'bg-blue-50 dark:bg-blue-950/40 text-blue-800 dark:text-blue-300 border-blue-200 dark:border-blue-800'
              : 'bg-red-50 dark:bg-red-950/40 text-red-800 dark:text-red-300 border-red-200 dark:border-red-800'
          }`}
        >
          <span>{alertMsg.text}</span>
          <button onClick={() => setAlertMsg(null)} className="font-bold ml-2">×</button>
        </div>
      )}

      {/* ERPNext Posting Status Banner */}
      <div className="bg-white dark:bg-zinc-900 p-5 rounded-2xl border border-zinc-200 dark:border-zinc-800 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div
            className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-lg ${
              summary?.status === 'POSTED'
                ? 'bg-emerald-100 dark:bg-emerald-900/50 text-emerald-600'
                : summary?.status === 'QUEUED'
                ? 'bg-amber-100 dark:bg-amber-900/50 text-amber-600'
                : 'bg-zinc-100 dark:bg-zinc-800 text-zinc-500'
            }`}
          >
            {summary?.status === 'POSTED' ? '✓' : summary?.status === 'QUEUED' ? '⏳' : '📝'}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase font-bold tracking-wider text-zinc-500">ERPNext Sync Status:</span>
              <span
                className={`text-xs font-mono font-bold px-2.5 py-0.5 rounded-full ${
                  summary?.status === 'POSTED'
                    ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                    : summary?.status === 'QUEUED'
                    ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                    : 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300'
                }`}
              >
                {summary?.status || 'DRAFT'}
              </span>
            </div>
            {summary?.erpnextJournalEntryId ? (
              <p className="text-xs font-mono text-emerald-600 dark:text-emerald-400 mt-0.5">
                Journal Voucher: <strong>{summary.erpnextJournalEntryId}</strong> (Company: {summary.erpnextCompany || config?.companyName})
              </p>
            ) : summary?.errorMessage ? (
              <p className="text-xs text-amber-600 dark:text-amber-400 mt-0.5">
                Note: {summary.errorMessage}
              </p>
            ) : (
              <p className="text-xs text-zinc-400 mt-0.5">
                Ready to post to ERPNext general ledger as Journal Entry.
              </p>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => handlePostToErpNext(false)}
            disabled={actionLoading || summary?.status === 'POSTED'}
            className="w-full md:w-auto px-5 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs font-bold rounded-xl shadow-xs transition flex items-center justify-center gap-2"
          >
            <span>🚀</span>
            {summary?.status === 'POSTED' ? 'Already Posted to ERPNext' : actionLoading ? 'Posting...' : 'Post to ERPNext'}
          </button>
          {summary?.status === 'POSTED' && (
            <button
              onClick={() => handlePostToErpNext(true)}
              disabled={actionLoading}
              className="px-3 py-2.5 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 text-xs font-semibold rounded-xl border border-zinc-200 dark:border-zinc-700 transition"
              title="Re-post if late sales or returns amended this business day"
            >
              ↻ Re-post (Amended)
            </button>
          )}
        </div>
      </div>

      {/* Summary KPI Ribbon */}
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Total Gross Sales</p>
          <p className="text-lg font-mono font-extrabold text-zinc-900 dark:text-zinc-100 mt-1">
            KES {summary?.totalGrossSalesKes?.toFixed(2) || '0.00'}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Std Rate Net (16%)</p>
          <p className="text-lg font-mono font-extrabold text-emerald-600 dark:text-emerald-400 mt-1">
            KES {summary?.salesStandardRatedNetKes?.toFixed(2) || '0.00'}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Output VAT (16%)</p>
          <p className="text-lg font-mono font-extrabold text-purple-600 dark:text-purple-400 mt-1">
            KES {summary?.outputVatKes?.toFixed(2) || '0.00'}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Zero & Exempt</p>
          <p className="text-lg font-mono font-extrabold text-blue-600 dark:text-blue-400 mt-1">
            KES {((summary?.salesZeroRatedNetKes || 0) + (summary?.salesExemptNetKes || 0)).toFixed(2)}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Cash Takings</p>
          <p className="text-lg font-mono font-extrabold text-amber-600 dark:text-amber-400 mt-1">
            KES {summary?.cashTakingsKes?.toFixed(2) || '0.00'}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">M-Pesa Clearing</p>
          <p className="text-lg font-mono font-extrabold text-emerald-600 dark:text-emerald-400 mt-1">
            KES {summary?.mpesaTakingsKes?.toFixed(2) || '0.00'}
          </p>
        </div>

        <div className="bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
          <p className="text-[10px] uppercase font-bold text-zinc-400">Cost of Goods (COGS)</p>
          <p className="text-lg font-mono font-extrabold text-zinc-600 dark:text-zinc-300 mt-1">
            KES {summary?.cogsTotalKes?.toFixed(2) || '0.00'}
          </p>
        </div>
      </div>

      {/* Double-Entry Journal Entry Voucher Table */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-bold text-base">ERPNext Double-Entry Journal Voucher</h2>
              <span
                className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${
                  journal?.isBalanced
                    ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                    : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                }`}
              >
                {journal?.isBalanced ? '✓ Balanced (Debits = Credits)' : '⚠ Imbalanced'}
              </span>
            </div>
            <p className="text-xs text-zinc-500 mt-0.5 font-mono">
              Company: {config?.companyName} | Voucher Type: Journal Entry | Date: {selectedDate}
            </p>
          </div>

          <div className="flex items-center gap-4 text-xs font-mono">
            <span className="text-zinc-500">
              Total Debits: <strong className="text-zinc-900 dark:text-zinc-100">KES {journal?.totalDebit?.toFixed(2) || '0.00'}</strong>
            </span>
            <span className="text-zinc-500">
              Total Credits: <strong className="text-zinc-900 dark:text-zinc-100">KES {journal?.totalCredit?.toFixed(2) || '0.00'}</strong>
            </span>
          </div>
        </div>

        {loading ? (
          <div className="p-10 text-center text-xs text-zinc-500">Loading daily ledger entries...</div>
        ) : !journal || journal.accounts.length === 0 ? (
          <div className="p-10 text-center text-xs text-zinc-400">
            No sales or transactions recorded on {selectedDate}.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/50 border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 uppercase font-semibold">
                <tr>
                  <th className="py-3 px-4">Account Name (Chart of Accounts)</th>
                  <th className="py-3 px-4">Remark / Description</th>
                  <th className="py-3 px-4">Cost Center</th>
                  <th className="py-3 px-4 text-right">Debit (KES)</th>
                  <th className="py-3 px-4 text-right">Credit (KES)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800 font-mono">
                {journal.accounts.map((line, idx) => (
                  <tr key={idx} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50 transition">
                    <td className="py-3 px-4 font-semibold text-zinc-900 dark:text-zinc-100">
                      {line.account}
                    </td>
                    <td className="py-3 px-4 text-zinc-500 font-sans text-xs">
                      {line.userRemark}
                    </td>
                    <td className="py-3 px-4 text-zinc-400 text-xs">
                      {line.costCenter || 'Main - SP'}
                    </td>
                    <td className="py-3 px-4 text-right text-emerald-600 dark:text-emerald-400 font-bold">
                      {line.debitInAccountCurrency > 0 ? line.debitInAccountCurrency.toFixed(2) : '-'}
                    </td>
                    <td className="py-3 px-4 text-right text-blue-600 dark:text-blue-400 font-bold">
                      {line.creditInAccountCurrency > 0 ? line.creditInAccountCurrency.toFixed(2) : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="bg-zinc-100 dark:bg-zinc-800/80 border-t-2 border-zinc-300 dark:border-zinc-700 font-mono font-extrabold">
                <tr>
                  <td colSpan={3} className="py-3 px-4 uppercase text-zinc-600 dark:text-zinc-300">
                    Total Voucher Debits & Credits
                  </td>
                  <td className="py-3 px-4 text-right text-emerald-700 dark:text-emerald-300 text-sm">
                    KES {journal.totalDebit.toFixed(2)}
                  </td>
                  <td className="py-3 px-4 text-right text-blue-700 dark:text-blue-300 text-sm">
                    KES {journal.totalCredit.toFixed(2)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </div>

      {/* History & Past Summaries */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
          <div>
            <h3 className="font-bold text-base">Past Daily Summaries & ERPNext Postings</h3>
            <p className="text-xs text-zinc-500">Historical trial balances and ERPNext document numbers.</p>
          </div>
        </div>

        {history.length === 0 ? (
          <div className="p-8 text-center text-xs text-zinc-400">No summaries generated yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/50 border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 font-semibold uppercase">
                <tr>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">Gross Sales</th>
                  <th className="py-3 px-4">Output VAT</th>
                  <th className="py-3 px-4">Cash Takings</th>
                  <th className="py-3 px-4">M-Pesa</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">ERPNext Ref</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800 font-mono">
                {history.map((row) => (
                  <tr key={row.id} className="hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                    <td className="py-3 px-4 font-bold text-zinc-900 dark:text-zinc-100">
                      {row.businessDate}
                    </td>
                    <td className="py-3 px-4">KES {row.totalGrossSalesKes.toFixed(2)}</td>
                    <td className="py-3 px-4">KES {row.outputVatKes.toFixed(2)}</td>
                    <td className="py-3 px-4">KES {row.cashTakingsKes.toFixed(2)}</td>
                    <td className="py-3 px-4">KES {row.mpesaTakingsKes.toFixed(2)}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                          row.status === 'POSTED'
                            ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                            : row.status === 'QUEUED'
                            ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                            : 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300'
                        }`}
                      >
                        {row.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-zinc-500 font-bold">
                      {row.erpnextJournalEntryId || '-'}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => setSelectedDate(row.businessDate)}
                        className="text-xs px-2.5 py-1 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 rounded-lg font-sans font-semibold transition"
                      >
                        View Voucher
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Config Modal */}
      {showConfigModal && config && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div className="bg-white dark:bg-zinc-900 rounded-3xl border border-zinc-200 dark:border-zinc-800 max-w-2xl w-full p-6 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 pb-3">
              <div>
                <h3 className="font-bold text-lg">ERPNext & Chart of Accounts Settings</h3>
                <p className="text-xs text-zinc-500">Configure your ERPNext connection and GL account names.</p>
              </div>
              <button
                onClick={() => setShowConfigModal(false)}
                className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 font-bold text-xl px-2"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleSaveConfig} className="space-y-4">
              {/* Connection info */}
              <div className="bg-zinc-50 dark:bg-zinc-800/50 p-4 rounded-xl space-y-3">
                <p className="text-xs font-bold uppercase text-zinc-500">ERPNext REST API Connection</p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">Company Name</label>
                    <input
                      type="text"
                      value={config.companyName}
                      onChange={(e) => setConfig({ ...config, companyName: e.target.value })}
                      className="w-full mt-1 p-2 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg text-xs"
                      required
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">ERPNext Base URL</label>
                    <input
                      type="text"
                      value={config.erpnextBaseUrl}
                      onChange={(e) => setConfig({ ...config, erpnextBaseUrl: e.target.value })}
                      placeholder="http://localhost:8000"
                      className="w-full mt-1 p-2 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg text-xs"
                      required
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">API Key</label>
                    <input
                      type="text"
                      value={config.erpnextApiKey}
                      onChange={(e) => setConfig({ ...config, erpnextApiKey: e.target.value })}
                      className="w-full mt-1 p-2 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg text-xs font-mono"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">API Secret</label>
                    <input
                      type="password"
                      value={config.erpnextApiSecret}
                      onChange={(e) => setConfig({ ...config, erpnextApiSecret: e.target.value })}
                      className="w-full mt-1 p-2 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg text-xs font-mono"
                    />
                  </div>
                </div>

                <div className="pt-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={handleTestConnection}
                      className="px-3 py-1.5 bg-zinc-200 dark:bg-zinc-700 hover:bg-zinc-300 dark:hover:bg-zinc-600 text-xs font-bold rounded-lg transition"
                    >
                      🔌 Test ERPNext Connection
                    </button>
                    <button
                      type="button"
                      onClick={handleVerifyAccounts}
                      className="px-3 py-1.5 bg-blue-100 dark:bg-blue-900/50 text-blue-800 dark:text-blue-200 hover:bg-blue-200 text-xs font-bold rounded-lg transition"
                    >
                      ✓ Verify COA in ERPNext
                    </button>
                  </div>

                  {testResult && (
                    <span
                      className={`text-xs font-semibold ${
                        testResult.connected ? 'text-emerald-600' : 'text-red-500'
                      }`}
                    >
                      {testResult.connected ? '✓ ' : '✕ '} {testResult.message}
                    </span>
                  )}
                  {verifyResult && (
                    <span
                      className={`text-xs font-semibold w-full ${
                        verifyResult.reachable && verifyResult.missingAccounts.length === 0 ? 'text-emerald-600' : 'text-amber-600'
                      }`}
                    >
                      {verifyResult.message}
                    </span>
                  )}
                </div>

                <div className="pt-2 grid grid-cols-1 md:grid-cols-2 gap-3 text-xs border-t border-zinc-200 dark:border-zinc-700">
                  <label className="flex items-center gap-2 font-semibold text-zinc-700 dark:text-zinc-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={Boolean(config.autoPostOnEod)}
                      onChange={(e) => setConfig({ ...config, autoPostOnEod: e.target.checked })}
                      className="rounded"
                    />
                    <span>Auto-post to ERPNext at End of Day (23:55 EAT)</span>
                  </label>
                  <label className="flex items-center gap-2 font-semibold text-zinc-700 dark:text-zinc-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={config.submitOnPost !== false}
                      onChange={(e) => setConfig({ ...config, submitOnPost: e.target.checked })}
                      className="rounded"
                    />
                    <span>Auto-Submit Journal Entry (docstatus = 1)</span>
                  </label>
                </div>
              </div>

              {/* Chart of Accounts */}
              <div className="space-y-3">
                <p className="text-xs font-bold uppercase text-zinc-500">Chart of Accounts Mapping</p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Cash On Hand</label>
                    <input
                      type="text"
                      value={config.accountCashOnHand}
                      onChange={(e) => setConfig({ ...config, accountCashOnHand: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">M-Pesa Clearing</label>
                    <input
                      type="text"
                      value={config.accountMpesaClearing}
                      onChange={(e) => setConfig({ ...config, accountMpesaClearing: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Bank Clearing</label>
                    <input
                      type="text"
                      value={config.accountBankClearing}
                      onChange={(e) => setConfig({ ...config, accountBankClearing: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Sales - Standard Rated 16%</label>
                    <input
                      type="text"
                      value={config.accountSalesStandard}
                      onChange={(e) => setConfig({ ...config, accountSalesStandard: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Sales - Zero Rated (0%)</label>
                    <input
                      type="text"
                      value={config.accountSalesZeroRated}
                      onChange={(e) => setConfig({ ...config, accountSalesZeroRated: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Sales - Exempt</label>
                    <input
                      type="text"
                      value={config.accountSalesExempt}
                      onChange={(e) => setConfig({ ...config, accountSalesExempt: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Output VAT 16% Liability</label>
                    <input
                      type="text"
                      value={config.accountOutputVat}
                      onChange={(e) => setConfig({ ...config, accountOutputVat: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Sales Returns & Refunds</label>
                    <input
                      type="text"
                      value={config.accountSalesReturns}
                      onChange={(e) => setConfig({ ...config, accountSalesReturns: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Cash Over and Short</label>
                    <input
                      type="text"
                      value={config.accountCashOverShort}
                      onChange={(e) => setConfig({ ...config, accountCashOverShort: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Cost of Goods Sold (COGS)</label>
                    <input
                      type="text"
                      value={config.accountCogs}
                      onChange={(e) => setConfig({ ...config, accountCogs: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Stock in Hand (Inventory)</label>
                    <input
                      type="text"
                      value={config.accountInventory}
                      onChange={(e) => setConfig({ ...config, accountInventory: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Default Cost Center</label>
                    <input
                      type="text"
                      value={config.accountCostCenter}
                      onChange={(e) => setConfig({ ...config, accountCostCenter: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Sales - Special Rated (8%)</label>
                    <input
                      type="text"
                      value={config.accountSalesSpecial || ''}
                      onChange={(e) => setConfig({ ...config, accountSalesSpecial: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Loyalty Points Redeemed</label>
                    <input
                      type="text"
                      value={config.accountLoyaltyDiscount || ''}
                      onChange={(e) => setConfig({ ...config, accountLoyaltyDiscount: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>

                  <div>
                    <label className="font-semibold text-zinc-600 dark:text-zinc-400">Creditors / Accounts Payable</label>
                    <input
                      type="text"
                      value={config.accountAccountsPayable || ''}
                      onChange={(e) => setConfig({ ...config, accountAccountsPayable: e.target.value })}
                      className="w-full mt-1 p-2 bg-zinc-50 dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-lg"
                    />
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-zinc-200 dark:border-zinc-800 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowConfigModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl transition shadow-xs"
                >
                  Save Settings
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
