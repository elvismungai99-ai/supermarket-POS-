import { useEffect, useState } from 'react';
import { apiFetch, downloadAuthenticatedFile } from '../utils/apiClient';
import { useToast } from '../utils/toast';

type ManagerTab = 'CASHIERS' | 'INVENTORY' | 'RETURNS';

interface ManagerDashboardHeaderProps {
  activeTab: ManagerTab;
  onSelectTab: (tab: ManagerTab) => void;
  authToken?: string;
}

export function ManagerDashboardHeader({
  activeTab,
  onSelectTab,
  authToken,
}: ManagerDashboardHeaderProps) {
  const { showToast } = useToast();
  const [grossSalesToday, setGrossSalesToday] = useState<number>(0);
  const [transactionsToday, setTransactionsToday] = useState<number>(0);
  const [pendingApprovals, setPendingApprovals] = useState<number>(0);
  const [lowStockCount, setLowStockCount] = useState<number>(0);
  const [activeShifts, setActiveShifts] = useState<number>(0);
  const [monthlyTaxDue, setMonthlyTaxDue] = useState<number>(0);
  const [totalTaxPaid, setTotalTaxPaid] = useState<number>(0);
  const [taxPeriodLabel, setTaxPeriodLabel] = useState<string>('');

  const loadKpis = async () => {
    const headers: Record<string, string> = {};
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

    try {
      const [cashiersRes, lowStockRes, salesRes, taxRes] = await Promise.all([
        apiFetch('/api/cashiers', { headers }),
        apiFetch('/api/products/low-stock', { headers }),
        apiFetch('/api/reports/daily-sales', { headers }),
        apiFetch('/api/reports/monthly-tax', { headers }),
      ]);

      if (cashiersRes.ok) {
        const cashiers = await cashiersRes.json();
        if (Array.isArray(cashiers)) {
          setPendingApprovals(cashiers.filter((c: any) => c.status === 'PENDING_APPROVAL').length);
          setActiveShifts(cashiers.filter((c: any) => c.isOnline).length);
        }
      }

      if (lowStockRes.ok) {
        const low = await lowStockRes.json();
        if (Array.isArray(low)) setLowStockCount(low.length);
      }

      if (salesRes.ok) {
        const days = await salesRes.json();
        if (Array.isArray(days) && days.length > 0) {
          const todayIso = new Date().toISOString().slice(0, 10);
          const todayEntry = days.find((d: any) => d.date === todayIso) || days[0];
          setGrossSalesToday(Number(todayEntry.totalRevenueKes || 0));
          setTransactionsToday(Number(todayEntry.totalSalesCount || 0));
        }
      }

      if (taxRes.ok) {
        const t = await taxRes.json();
        setMonthlyTaxDue(Number(t?.currentMonth?.netTaxPayableKes || 0));
        setTotalTaxPaid(Number(t?.cumulative?.totalTaxPaidKes || 0));
        setTaxPeriodLabel(t?.currentMonth?.monthLabel || '');
      }
    } catch {
      // Keep previous values on transient failure
    }
  };

  useEffect(() => {
    loadKpis();
    const timer = setInterval(loadKpis, 15000);
    return () => clearInterval(timer);
  }, [authToken]);

  const avgBasket = transactionsToday > 0 ? grossSalesToday / transactionsToday : 0;

  return (
    <div className="space-y-5 mb-6">
      {/* Store Manager Portal Header Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-xl">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2.5">
            <span className="w-8 h-8 rounded-lg bg-blue-500/15 border border-blue-500/30 text-blue-400 flex items-center justify-center text-sm">
              🏬
            </span>
            <span>Store Manager Oversight Portal</span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Real-time cashier supervision, approvals, stock receiving (GRN), and customer returns.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="px-3 py-1.5 bg-amber-500/10 text-amber-300 border border-amber-500/20 rounded-xl text-xs font-semibold font-mono">
            {taxPeriodLabel || 'Month'} Tax: KES {monthlyTaxDue.toLocaleString('en-US', { minimumFractionDigits: 2 })} | Paid: KES {totalTaxPaid.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </span>
          <span className="px-3 py-1.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-xl text-xs font-semibold">
            Active Tills: {activeShifts}
          </span>
          <button
            type="button"
            onClick={() => {
              downloadAuthenticatedFile('/api/reports/tax-invoices/export', 'daily_sales_tax_export.csv')
                .then(() => showToast('Sales CSV exported.', 'success'))
                .catch((err) => showToast(err.message, 'error'));
            }}
            className="bg-indigo-600 hover:bg-indigo-500 text-white px-4 py-2 rounded-xl text-xs font-semibold shadow transition"
          >
            ⬇ Export Sales CSV
          </button>
        </div>
      </div>

      {/* Live KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-xl flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Gross Sales (Latest Day)
            </p>
            <h3 className="text-2xl font-black text-white mt-1 font-mono">
              KES {grossSalesToday.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </h3>
            <span className="text-xs text-emerald-400 font-medium mt-1 inline-block">
              Live POS Ledger Total
            </span>
          </div>
          <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 text-xl">
            💰
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-xl flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Total Transactions
            </p>
            <h3 className="text-2xl font-black text-white mt-1 font-mono">
              {transactionsToday}
            </h3>
            <span className="text-xs text-indigo-400 font-medium mt-1 inline-block">
              Avg basket: KES {avgBasket.toFixed(0)}
            </span>
          </div>
          <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 text-xl">
            🧾
          </div>
        </div>

        <button
          type="button"
          onClick={() => onSelectTab('CASHIERS')}
          className={`text-left bg-slate-900 border p-5 rounded-2xl shadow-xl flex items-center justify-between transition hover:border-amber-500/50 ${
            activeTab === 'CASHIERS' ? 'border-amber-500/40' : 'border-slate-800'
          }`}
        >
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Pending Approvals
            </p>
            <h3 className="text-2xl font-black text-amber-400 mt-1 font-mono">
              {pendingApprovals}
            </h3>
            <span className="text-xs text-amber-400 font-medium mt-1 inline-block">
              {pendingApprovals > 0 ? 'Click to review cashiers' : 'All cashiers authorized'}
            </span>
          </div>
          <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 text-xl">
            🔐
          </div>
        </button>

        <button
          type="button"
          onClick={() => onSelectTab('INVENTORY')}
          className={`text-left bg-slate-900 border p-5 rounded-2xl shadow-xl flex items-center justify-between transition hover:border-rose-500/50 ${
            activeTab === 'INVENTORY' ? 'border-rose-500/40' : 'border-slate-800'
          }`}
        >
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Low Stock Alerts
            </p>
            <h3 className="text-2xl font-black text-rose-400 mt-1 font-mono">
              {lowStockCount} Items
            </h3>
            <span className="text-xs text-rose-400 font-medium mt-1 inline-block">
              {lowStockCount > 0 ? 'Click to restock / receive GRN' : 'Stock levels healthy'}
            </span>
          </div>
          <div className="w-12 h-12 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400 text-xl">
            📦
          </div>
        </button>
      </div>
    </div>
  );
}
