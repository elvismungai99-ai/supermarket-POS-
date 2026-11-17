import { useEffect, useState } from 'react';
import { apiFetch, downloadAuthenticatedFile } from '../utils/apiClient';
import { useToast } from '../utils/toast';

export interface MonthlyTaxSummary {
  month: string;
  monthLabel: string;
  salesCount: number;
  grossSalesKes: number;
  taxableSalesKes: number;
  outputVatKes: number;
  returnsTotalKes: number;
  returnsVatKes: number;
  netOutputVatKes: number;
  purchasesTotalKes: number;
  inputVatKes: number;
  netTaxPayableKes: number;
  vatCreditCarryoverKes: number;
  taxPaidKes: number;
  balanceDueKes: number;
  status: 'PAID' | 'PARTIALLY_PAID' | 'UNPAID' | 'NIL_RETURN' | 'CREDIT_CARRYOVER';
}

export interface CategoryTaxBreakdown {
  taxCategory: string;
  categoryLabel: string;
  vatRateLabel: string;
  grossSalesKes: number;
  taxableSalesKes: number;
  outputVatKes: number;
  returnsTotalKes: number;
  returnsVatKes: number;
  netVatKes: number;
}

export interface CumulativeTaxSummary {
  totalOutputVatKes: number;
  totalReturnsVatKes: number;
  totalInputVatKes: number;
  totalNetTaxPayableKes: number;
  totalTaxPaidKes: number;
  totalBalanceDueKes: number;
}

export interface TaxPaymentItem {
  id: string;
  taxPeriod: string;
  taxType: string;
  amountPaidKes: number;
  paymentDate: string;
  paymentMethod: string;
  kraPrn: string;
  notes?: string | null;
  recordedByUsername?: string | null;
  createdAt: string;
}

export interface MonthlyTaxReportResponse {
  selectedMonth: string;
  currentMonth: MonthlyTaxSummary;
  cumulative: CumulativeTaxSummary;
  monthlySummaries: MonthlyTaxSummary[];
  categoryBreakdown: CategoryTaxBreakdown[];
  payments: TaxPaymentItem[];
}

function defaultCurrentMonth(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

function fmtKes(val: number | undefined | null): string {
  return Number(val ?? 0).toLocaleString('en-KE', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function statusBadgeClass(status: MonthlyTaxSummary['status']): string {
  switch (status) {
    case 'PAID':
      return 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30';
    case 'PARTIALLY_PAID':
      return 'bg-amber-500/15 text-amber-300 border-amber-500/30';
    case 'UNPAID':
      return 'bg-rose-500/15 text-rose-300 border-rose-500/30';
    case 'CREDIT_CARRYOVER':
      return 'bg-sky-500/15 text-sky-300 border-sky-500/30';
    default:
      return 'bg-slate-700/40 text-slate-300 border-slate-600/40';
  }
}

export function MonthlyTaxView({ canRecordPayment = true }: { canRecordPayment?: boolean }) {
  const { showToast } = useToast();
  const [selectedMonth, setSelectedMonth] = useState<string>(defaultCurrentMonth);
  const [report, setReport] = useState<MonthlyTaxReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Record KRA Tax Payment form
  const [payPeriod, setPayPeriod] = useState<string>(defaultCurrentMonth);
  const [payType, setPayType] = useState<string>('VAT');
  const [payAmount, setPayAmount] = useState<string>('');
  const [payDate, setPayDate] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [payMethod, setPayMethod] = useState<string>('KRA_ITAX_MPESA');
  const [payPrn, setPayPrn] = useState<string>('');
  const [payNotes, setPayNotes] = useState<string>('');

  const loadReport = async (month: string) => {
    setLoading(true);
    try {
      const res = await apiFetch(`/api/reports/monthly-tax?month=${encodeURIComponent(month)}`);
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Failed to load monthly tax calculation');
      }
      const data: MonthlyTaxReportResponse = await res.json();
      setReport(data);
    } catch (err: any) {
      showToast(err.message || 'Could not load monthly tax report', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReport(selectedMonth);
  }, [selectedMonth]);

  const handleMonthChange = (m: string) => {
    if (!m) return;
    setSelectedMonth(m);
    setPayPeriod(m);
  };

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    const amt = parseFloat(payAmount);
    if (!amt || amt <= 0) {
      showToast('Enter a positive payment amount in KES.', 'error');
      return;
    }
    setSubmitting(true);
    try {
      const res = await apiFetch('/api/reports/tax-payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          taxPeriod: payPeriod,
          taxType: payType,
          amountPaidKes: amt,
          paymentDate: payDate,
          paymentMethod: payMethod,
          kraPrn: payPrn.trim(),
          notes: payNotes.trim() || null,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Failed to record KRA tax payment');
      showToast(`Recorded KES ${fmtKes(amt)} tax payment for ${payPeriod} (PRN: ${payPrn.trim()}).`, 'success');
      setPayAmount('');
      setPayPrn('');
      setPayNotes('');
      await loadReport(selectedMonth);
    } catch (err: any) {
      showToast(err.message || 'Error saving tax remittance', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const cur = report?.currentMonth;
  const cum = report?.cumulative;

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <span>🏛️</span> Monthly Tax Calculation &amp; KRA Payments Paid
          </h3>
          <p className="text-xs text-slate-400 mt-0.5">
            Calculates monthly Output VAT (sales minus returns), subtracts claimable Supplier Input VAT, and tracks KRA tax remittances paid by the supermarket.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <label className="text-xs font-semibold text-slate-400">Tax Month:</label>
          <input
            type="month"
            value={selectedMonth}
            onChange={(e) => handleMonthChange(e.target.value)}
            className="px-3 py-1.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-xs font-mono"
          />
          <button
            type="button"
            onClick={() => loadReport(selectedMonth)}
            className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition"
          >
            ↻ Recalculate
          </button>
          <button
            type="button"
            onClick={() => {
              downloadAuthenticatedFile(
                `/api/reports/monthly-tax/export?month=${encodeURIComponent(selectedMonth)}`,
                `kra_monthly_tax_${selectedMonth}.csv`
              )
                .then(() => showToast('Monthly KRA tax summary exported.', 'success'))
                .catch((err) => showToast(err.message, 'error'));
            }}
            className="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition shadow"
          >
            ⬇ Export Monthly CSV
          </button>
        </div>
      </div>

      {/* Selected Month KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
          <p className="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">
            Net Output VAT ({cur?.monthLabel || selectedMonth})
          </p>
          <p className="text-2xl font-black font-mono text-white mt-1">
            KES {fmtKes(cur?.netOutputVatKes)}
          </p>
          <p className="text-[11px] text-slate-400 mt-1">
            Sales VAT {fmtKes(cur?.outputVatKes)} − Returns {fmtKes(cur?.returnsVatKes)}
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
          <p className="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">
            Input VAT (Supplier GRN)
          </p>
          <p className="text-2xl font-black font-mono text-sky-400 mt-1">
            KES {fmtKes(cur?.inputVatKes)}
          </p>
          <p className="text-[11px] text-slate-400 mt-1">
            On KES {fmtKes(cur?.purchasesTotalKes)} stock receipts
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
          <p className="text-[11px] uppercase tracking-wider text-slate-400 font-semibold">
            Monthly Tax Calculated (Due)
          </p>
          <p className="text-2xl font-black font-mono text-amber-400 mt-1">
            KES {fmtKes(cur?.netTaxPayableKes)}
          </p>
          <p className="text-[11px] text-slate-400 mt-1">
            Remaining balance: <strong className="text-white">KES {fmtKes(cur?.balanceDueKes)}</strong>
          </p>
        </div>

        <div className="bg-slate-900 border border-emerald-500/30 rounded-2xl p-5 shadow-xl">
          <div className="flex items-center justify-between gap-2">
            <p className="text-[11px] uppercase tracking-wider text-emerald-400 font-semibold">
              Tax Paid ({cur?.month || selectedMonth})
            </p>
            {cur && (
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${statusBadgeClass(cur.status)}`}>
                {cur.status}
              </span>
            )}
          </div>
          <p className="text-2xl font-black font-mono text-emerald-400 mt-1">
            KES {fmtKes(cur?.taxPaidKes)}
          </p>
          <p className="text-[11px] text-slate-300 mt-1">
            All-Time Total Paid: <strong className="font-mono text-emerald-300">KES {fmtKes(cum?.totalTaxPaidKes)}</strong>
          </p>
        </div>
      </div>

      {/* Category Breakdown + Record Payment Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* KRA Category Breakdown for Selected Month */}
        <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <div className="p-4 border-b border-slate-800 flex items-center justify-between">
            <div>
              <h4 className="font-bold text-sm text-white">
                KRA VAT Breakdown — {cur?.monthLabel || selectedMonth}
              </h4>
              <p className="text-xs text-slate-400">
                {cur?.salesCount ?? 0} sales · Gross KES {fmtKes(cur?.grossSalesKes)} · Net Taxable KES {fmtKes(cur?.taxableSalesKes)}
              </p>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-800/50 text-slate-400 uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="py-2.5 px-4">KRA Category</th>
                  <th className="py-2.5 px-4">Rate</th>
                  <th className="py-2.5 px-4 text-right">Taxable Sales</th>
                  <th className="py-2.5 px-4 text-right">Output VAT</th>
                  <th className="py-2.5 px-4 text-right">Returns VAT</th>
                  <th className="py-2.5 px-4 text-right">Net VAT</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {(report?.categoryBreakdown || []).map((c) => (
                  <tr key={c.taxCategory} className="hover:bg-slate-800/40">
                    <td className="py-2.5 px-4 font-semibold text-white">{c.categoryLabel}</td>
                    <td className="py-2.5 px-4 font-mono text-slate-300">{c.vatRateLabel}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-slate-300">{fmtKes(c.taxableSalesKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-slate-300">{fmtKes(c.outputVatKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-rose-400">-{fmtKes(c.returnsVatKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold text-emerald-400">{fmtKes(c.netVatKes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Record KRA Tax Payment Form */}
        {canRecordPayment && (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
            <h4 className="font-bold text-sm text-white mb-1">💳 Record KRA Tax Payment</h4>
            <p className="text-xs text-slate-400 mb-3">
              Log tax remitted by the supermarket via iTax PRN so paid vs. due balances stay accurate.
            </p>
            <form onSubmit={handleRecordPayment} className="space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">Tax Period</label>
                  <input
                    type="month"
                    required
                    value={payPeriod}
                    onChange={(e) => setPayPeriod(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                </div>
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">Tax Type</label>
                  <select
                    value={payType}
                    onChange={(e) => setPayType(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                  >
                    <option value="VAT">Monthly VAT (VAT3)</option>
                    <option value="TURNOVER_TAX">Turnover Tax (TOT)</option>
                    <option value="INSTALMENT_TAX">Corporate Instalment Tax</option>
                    <option value="PAYE">PAYE / Statutory</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">Amount Paid (KES)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    placeholder={cur?.balanceDueKes ? cur.balanceDueKes.toFixed(2) : '0.00'}
                    value={payAmount}
                    onChange={(e) => setPayAmount(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                </div>
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">Payment Date</label>
                  <input
                    type="date"
                    required
                    value={payDate}
                    onChange={(e) => setPayDate(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">KRA PRN / eSlip Ref</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. 20261117000123"
                    value={payPrn}
                    onChange={(e) => setPayPrn(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                </div>
                <div>
                  <label className="block text-slate-400 font-semibold mb-1">Payment Channel</label>
                  <select
                    value={payMethod}
                    onChange={(e) => setPayMethod(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                  >
                    <option value="KRA_ITAX_MPESA">M-Pesa Paybill 222222</option>
                    <option value="KRA_ITAX_BANK">Bank RTGS / EFT</option>
                    <option value="ITAX_CREDIT_OFFSET">iTax Credit Offset</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-slate-400 font-semibold mb-1">Notes (optional)</label>
                <input
                  type="text"
                  placeholder="e.g. November VAT3 return settled"
                  value={payNotes}
                  onChange={(e) => setPayNotes(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                />
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-bold transition shadow"
              >
                {submitting ? 'Saving Payment...' : 'Record Tax Payment'}
              </button>
            </form>
          </div>
        )}
      </div>

      {/* All Months Tax Summary Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-4 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h4 className="font-bold text-sm text-white">Monthly Tax Ledger (Calculated vs. Paid)</h4>
            <p className="text-xs text-slate-400">
              Click any month to inspect its category breakdown. Cumulative Payable: KES {fmtKes(cum?.totalNetTaxPayableKes)} · Cumulative Paid: KES {fmtKes(cum?.totalTaxPaidKes)} · Balance Due: KES {fmtKes(cum?.totalBalanceDueKes)}
            </p>
          </div>
        </div>
        {loading ? (
          <div className="p-8 text-center text-xs text-slate-400">Calculating monthly tax schedules...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-800/50 text-slate-400 uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="py-2.5 px-4">Period</th>
                  <th className="py-2.5 px-4 text-right">Sales</th>
                  <th className="py-2.5 px-4 text-right">Gross Sales</th>
                  <th className="py-2.5 px-4 text-right">Net Output VAT</th>
                  <th className="py-2.5 px-4 text-right">Input VAT</th>
                  <th className="py-2.5 px-4 text-right">Tax Calculated</th>
                  <th className="py-2.5 px-4 text-right">Tax Paid</th>
                  <th className="py-2.5 px-4 text-right">Balance Due</th>
                  <th className="py-2.5 px-4 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {(report?.monthlySummaries || []).map((m) => (
                  <tr
                    key={m.month}
                    onClick={() => handleMonthChange(m.month)}
                    className={`cursor-pointer transition ${
                      m.month === selectedMonth ? 'bg-emerald-950/30' : 'hover:bg-slate-800/40'
                    }`}
                  >
                    <td className="py-2.5 px-4 font-semibold text-white">
                      <span className="font-mono text-emerald-400 mr-1.5">{m.month}</span>
                      <span className="text-slate-400 font-normal">{m.monthLabel}</span>
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono text-slate-300">{m.salesCount}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-slate-300">{fmtKes(m.grossSalesKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-slate-200">{fmtKes(m.netOutputVatKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-sky-400">{fmtKes(m.inputVatKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold text-amber-300">{fmtKes(m.netTaxPayableKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold text-emerald-400">{fmtKes(m.taxPaidKes)}</td>
                    <td className="py-2.5 px-4 text-right font-mono text-white">{fmtKes(m.balanceDueKes)}</td>
                    <td className="py-2.5 px-4 text-center">
                      <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${statusBadgeClass(m.status)}`}>
                        {m.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Supermarket Tax Remittance History */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-4 border-b border-slate-800">
          <h4 className="font-bold text-sm text-white">Supermarket KRA Tax Payments History</h4>
          <p className="text-xs text-slate-400">
            All recorded KRA iTax remittances paid by the supermarket ({report?.payments.length ?? 0} payment(s) · Total KES {fmtKes(cum?.totalTaxPaidKes)})
          </p>
        </div>
        {!report || report.payments.length === 0 ? (
          <div className="p-6 text-center text-xs text-slate-500">
            No KRA tax payments recorded yet. Use &ldquo;Record KRA Tax Payment&rdquo; when remitting monthly tax on iTax.
          </div>
        ) : (
          <div className="overflow-x-auto max-h-80">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-800/50 text-slate-400 uppercase tracking-wider border-b border-slate-800 sticky top-0">
                <tr>
                  <th className="py-2.5 px-4">Payment Date</th>
                  <th className="py-2.5 px-4">Tax Period</th>
                  <th className="py-2.5 px-4">Type</th>
                  <th className="py-2.5 px-4">KRA PRN / Ref</th>
                  <th className="py-2.5 px-4">Method</th>
                  <th className="py-2.5 px-4 text-right">Amount Paid (KES)</th>
                  <th className="py-2.5 px-4">Recorded By</th>
                  <th className="py-2.5 px-4">Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {report.payments.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-800/40">
                    <td className="py-2.5 px-4 font-mono text-slate-300">{p.paymentDate}</td>
                    <td className="py-2.5 px-4 font-mono font-bold text-emerald-400">{p.taxPeriod}</td>
                    <td className="py-2.5 px-4 text-slate-200">{p.taxType}</td>
                    <td className="py-2.5 px-4 font-mono text-white">{p.kraPrn}</td>
                    <td className="py-2.5 px-4 text-slate-300">{p.paymentMethod}</td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold text-emerald-400">
                      KES {fmtKes(p.amountPaidKes)}
                    </td>
                    <td className="py-2.5 px-4 text-slate-400">{p.recordedByUsername || 'admin'}</td>
                    <td className="py-2.5 px-4 text-slate-400">{p.notes || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
