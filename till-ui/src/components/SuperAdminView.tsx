import { useState, useEffect } from 'react';
import { AccountingErpNextView } from './AccountingErpNextView';
import { apiFetch, downloadAuthenticatedFile } from '../utils/apiClient';
import { useToast } from '../utils/toast';

export interface DailySalesSummary {
  date: string;
  totalSalesCount: number;
  totalRevenueKes: number;
  tillSummaries: {
    tillId: string;
    salesCount: number;
    revenueKes: number;
  }[];
}

export interface TaxInvoiceItem {
  saleId: string;
  receiptNumber: string;
  date: string;
  tillId: string;
  totalAmountKes: number;
  taxAmountKes: number;
  taxableAmountKes: number;
  kraControlUnitNumber: string;
  status: string;
  qrCodeUrl: string;
}

export interface OutboxStatus {
  pendingCount: number;
  sentCount: number;
  totalCount: number;
  status: string;
}

interface AuditLogItem {
  id: string;
  actorUsername: string | null;
  actorRole: string | null;
  action: string;
  targetEntity: string | null;
  targetId: string | null;
  ipAddress: string | null;
  details: string | null;
  createdAt: string;
}

interface BranchShopItem {
  id: string;
  code: string;
  name: string;
  location: string | null;
  costCenter: string;
}

interface BranchTillItem {
  id: string;
  shopId: string;
  tillNumber: string;
  label: string;
}

interface SuperAdminViewProps {
  authToken?: string;
}

export function SuperAdminView({ authToken }: SuperAdminViewProps) {
  const { showToast } = useToast();
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'ACCOUNTING' | 'TAX_INVOICES' | 'SECURITY'>('OVERVIEW');
  const [dailySales, setDailySales] = useState<DailySalesSummary[]>([]);
  const [taxInvoices, setTaxInvoices] = useState<TaxInvoiceItem[]>([]);
  const [outboxStatus, setOutboxStatus] = useState<OutboxStatus | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
  const [shops, setShops] = useState<BranchShopItem[]>([]);
  const [tills, setTills] = useState<BranchTillItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [backendHealthy, setBackendHealthy] = useState<boolean>(true);
  const [erpConnected, setErpConnected] = useState<boolean | null>(null);
  const [pinging, setPinging] = useState(false);

  // Admin user provisioning form
  const [provName, setProvName] = useState('');
  const [provUsername, setProvUsername] = useState('');
  const [provPin, setProvPin] = useState('');
  const [provRole, setProvRole] = useState<'SUPERVISOR' | 'SUPER_ADMIN' | 'CASHIER'>('SUPERVISOR');

  // Branch shop form
  const [shopCode, setShopCode] = useState('');
  const [shopName, setShopName] = useState('');
  const [shopLocation, setShopLocation] = useState('');

  // TOTP 2FA configuration state
  const [mfaSetup, setMfaSetup] = useState<{ secret: string; otpAuthUri: string } | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [mfaDisablePin, setMfaDisablePin] = useState('');
  const [mfaEnabledLocal, setMfaEnabledLocal] = useState(false);
  const [mfaBusy, setMfaBusy] = useState(false);

  const handleStartMfaSetup = async () => {
    setMfaBusy(true);
    try {
      const res = await apiFetch('/api/auth/mfa/setup', { method: 'POST' });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Failed to initiate MFA setup');
      setMfaSetup({ secret: data.secret, otpAuthUri: data.otpAuthUri });
      showToast('Scan or copy the TOTP secret into your authenticator app, then verify.', 'info');
    } catch (err: any) {
      showToast(err.message || 'Failed to setup MFA', 'error');
    } finally {
      setMfaBusy(false);
    }
  };

  const handleEnableMfa = async (e: React.FormEvent) => {
    e.preventDefault();
    setMfaBusy(true);
    try {
      const res = await apiFetch('/api/auth/mfa/enable', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: mfaCode.trim() }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Invalid verification code');
      setMfaEnabledLocal(true);
      setMfaSetup(null);
      setMfaCode('');
      showToast('Two-Factor Authentication enabled for your account.', 'success');
    } catch (err: any) {
      showToast(err.message || 'MFA activation failed', 'error');
    } finally {
      setMfaBusy(false);
    }
  };

  const handleDisableMfa = async (e: React.FormEvent) => {
    e.preventDefault();
    setMfaBusy(true);
    try {
      const res = await apiFetch('/api/auth/mfa/disable', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin: mfaDisablePin.trim() }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || 'Failed to disable MFA');
      setMfaEnabledLocal(false);
      setMfaDisablePin('');
      showToast('Two-Factor Authentication disabled.', 'info');
    } catch (err: any) {
      showToast(err.message || 'Could not disable MFA', 'error');
    } finally {
      setMfaBusy(false);
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      const [salesRes, invRes, outboxRes, auditRes, branchRes] = await Promise.all([
        apiFetch('/api/reports/daily-sales'),
        apiFetch('/api/reports/tax-invoices'),
        apiFetch('/api/reports/outbox-status'),
        apiFetch('/api/admin/audit-logs?limit=50'),
        apiFetch('/api/branches'),
      ]);

      if (salesRes.ok) setDailySales(await salesRes.json());
      if (invRes.ok) setTaxInvoices(await invRes.json());
      if (outboxRes.ok) setOutboxStatus(await outboxRes.json());
      if (auditRes.ok) setAuditLogs(await auditRes.json());
      if (branchRes.ok) {
        const b = await branchRes.json();
        setShops(b.shops || []);
        setTills(b.tills || []);
      }
    } catch {
      console.error('Failed to load super admin data');
    } finally {
      setLoading(false);
    }
  };

  const handleProvisionUser = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch('/api/auth/admin/create-user', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: provName.trim(),
          username: provUsername.trim(),
          pin: provPin.trim(),
          role: provRole,
        }),
      });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || 'Provisioning failed');
      showToast(body.message || `Created @${provUsername}`, 'success', 'User Provisioned');
      setProvName('');
      setProvUsername('');
      setProvPin('');
      loadData();
    } catch (err: any) {
      showToast(err.message || 'Failed to provision user', 'error');
    }
  };

  const handleCreateBranch = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch('/api/branches/shops', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: shopCode.trim(),
          name: shopName.trim(),
          location: shopLocation.trim(),
        }),
      });
      if (!res.ok) throw new Error('Failed to create branch');
      showToast(`Created branch ${shopName}`, 'success');
      setShopCode('');
      setShopName('');
      setShopLocation('');
      loadData();
    } catch (err: any) {
      showToast(err.message || 'Branch creation failed', 'error');
    }
  };

  const handlePingAll = async (notify = false) => {
    setPinging(true);
    const t0 = performance.now();
    try {
      const [healthRes, erpRes] = await Promise.all([
        fetch('/actuator/health'),
        apiFetch('/api/accounting/test-connection', { method: 'POST' }),
      ]);
      const hData = healthRes.ok ? await healthRes.json() : null;
      setBackendHealthy(hData?.status === 'UP');

      if (erpRes.ok) {
        const erpData = await erpRes.json();
        setErpConnected(Boolean(erpData.connected));
      } else {
        setErpConnected(false);
      }

      const latency = Math.round(performance.now() - t0);
      if (notify) {
        showToast(`Health check finished (${latency}ms)`, 'success', 'Integration Ping');
      }
    } catch {
      setBackendHealthy(false);
      setErpConnected(false);
      if (notify) {
        showToast('Failed to reach backend health endpoint', 'error');
      }
    } finally {
      setPinging(false);
    }
  };

  useEffect(() => {
    loadData();
    handlePingAll(false);
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [authToken]);

  return (
    <div className="space-y-6">
      {/* Enterprise Control Panel Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-xl">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2.5">
            <span className="w-8 h-8 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 flex items-center justify-center text-sm">
              🛡️
            </span>
            <span>Super Admin Enterprise Control Panel</span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Multi-till audit overview, ERPNext General Ledger, RBAC security matrix, and integration health.
          </p>
        </div>

        {/* Sub-navigation pills */}
        <div className="flex flex-wrap items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('OVERVIEW')}
            className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'OVERVIEW'
                ? 'bg-emerald-600 text-white shadow'
                : 'text-slate-300 hover:text-white hover:bg-slate-800'
            }`}
          >
            <span>📊</span> System &amp; Sales Overview
          </button>

          <button
            onClick={() => setActiveTab('ACCOUNTING')}
            className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'ACCOUNTING'
                ? 'bg-emerald-600 text-white shadow'
                : 'text-slate-300 hover:text-white hover:bg-slate-800'
            }`}
          >
            <span>📑</span> General Ledger &amp; ERPNext
          </button>

          <button
            onClick={() => setActiveTab('TAX_INVOICES')}
            className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'TAX_INVOICES'
                ? 'bg-emerald-600 text-white shadow'
                : 'text-slate-300 hover:text-white hover:bg-slate-800'
            }`}
          >
            <span>🧾</span> Invoice Archive
          </button>

          <button
            onClick={() => setActiveTab('SECURITY')}
            className={`px-3.5 py-2 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'SECURITY'
                ? 'bg-emerald-600 text-white shadow'
                : 'text-slate-300 hover:text-white hover:bg-slate-800'
            }`}
          >
            <span>🔐</span> Audit, Users &amp; Branches
          </button>
        </div>
      </div>

      {activeTab === 'ACCOUNTING' ? (
        <AccountingErpNextView />
      ) : activeTab === 'SECURITY' ? (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Provision Privileged User */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
              <h3 className="font-bold text-sm text-white mb-1">
                🔐 Provision Manager / Admin / Cashier Account
              </h3>
              <p className="text-xs text-slate-400 mb-4">
                Self-registration of privileged roles is locked. Provision Managers &amp; Admins here.
              </p>
              <form onSubmit={handleProvisionUser} className="space-y-3 text-xs">
                <div className="grid grid-cols-2 gap-3">
                  <input
                    type="text"
                    required
                    placeholder="Full Name"
                    value={provName}
                    onChange={(e) => setProvName(e.target.value)}
                    className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                  />
                  <input
                    type="text"
                    required
                    placeholder="Username"
                    value={provUsername}
                    onChange={(e) => setProvUsername(e.target.value)}
                    className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <input
                    type="password"
                    required
                    placeholder="Initial PIN / Password"
                    value={provPin}
                    onChange={(e) => setProvPin(e.target.value)}
                    className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                  />
                  <select
                    value={provRole}
                    onChange={(e) => setProvRole(e.target.value as any)}
                    className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                  >
                    <option value="SUPERVISOR">Store Manager (SUPERVISOR)</option>
                    <option value="SUPER_ADMIN">Super Admin (SUPER_ADMIN)</option>
                    <option value="CASHIER">Pre-Approved Cashier</option>
                  </select>
                </div>
                <button
                  type="submit"
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl transition"
                >
                  Create Active Account
                </button>
              </form>
            </div>

            {/* Multi-Branch Shops & Tills */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
              <div>
                <h3 className="font-bold text-sm text-white mb-1">🏬 Branches &amp; Tills Registry</h3>
                <p className="text-xs text-slate-400">
                  {shops.length} active branch(es) · {tills.length} active till lane(s)
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {shops.map((s) => (
                  <span
                    key={s.id}
                    className="px-3 py-1.5 rounded-xl bg-slate-950 border border-slate-700 text-xs text-slate-200"
                  >
                    <strong className="text-emerald-400">{s.code}</strong> · {s.name} ({s.costCenter})
                  </span>
                ))}
              </div>
              <form onSubmit={handleCreateBranch} className="grid grid-cols-3 gap-2 text-xs pt-2 border-t border-slate-800">
                <input
                  type="text"
                  required
                  placeholder="Code (e.g. WEST-02)"
                  value={shopCode}
                  onChange={(e) => setShopCode(e.target.value)}
                  className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                />
                <input
                  type="text"
                  required
                  placeholder="Branch Name"
                  value={shopName}
                  onChange={(e) => setShopName(e.target.value)}
                  className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                />
                <div className="flex gap-2">
                  <input
                    type="text"
                    placeholder="Location"
                    value={shopLocation}
                    onChange={(e) => setShopLocation(e.target.value)}
                    className="flex-1 min-w-0 px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                  />
                  <button
                    type="submit"
                    className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-xl shrink-0"
                  >
                    + Add
                  </button>
                </div>
              </form>
            </div>
          </div>

          {/* Personal TOTP Two-Factor Authentication Card */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h3 className="font-bold text-sm text-white">📱 Two-Factor Authentication (TOTP MFA)</h3>
                <p className="text-xs text-slate-400">
                  Protect your Super Admin account with RFC 6238 authenticator codes (Google Authenticator, Authy, 1Password).
                </p>
              </div>
              {!mfaSetup && (
                <button
                  type="button"
                  disabled={mfaBusy}
                  onClick={handleStartMfaSetup}
                  className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold transition"
                >
                  {mfaEnabledLocal ? 'Rotate MFA Secret' : 'Enable TOTP 2FA'}
                </button>
              )}
            </div>

            {mfaSetup && (
              <form onSubmit={handleEnableMfa} className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-3 border-t border-slate-800 text-xs">
                <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
                  <p className="text-slate-400 uppercase font-semibold">1. Add Secret to Authenticator</p>
                  <p className="font-mono text-emerald-400 break-all select-all">{mfaSetup.secret}</p>
                  <p className="text-[11px] text-slate-500 break-all">{mfaSetup.otpAuthUri}</p>
                </div>
                <div className="flex flex-col justify-between gap-2">
                  <div>
                    <label className="block text-slate-400 uppercase font-semibold mb-1">2. Enter 6-digit Code</label>
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      required
                      placeholder="123456"
                      value={mfaCode}
                      onChange={(e) => setMfaCode(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-center tracking-widest"
                    />
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="submit"
                      disabled={mfaBusy}
                      className="flex-1 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold transition"
                    >
                      Verify &amp; Activate
                    </button>
                    <button
                      type="button"
                      onClick={() => setMfaSetup(null)}
                      className="px-3 py-2 rounded-xl bg-slate-800 text-slate-300 font-semibold"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </form>
            )}

            <form onSubmit={handleDisableMfa} className="flex flex-wrap items-center gap-2 pt-2 border-t border-slate-800/70 text-xs">
              <span className="text-slate-400">Disable active MFA:</span>
              <input
                type="password"
                required
                placeholder="Confirm PIN"
                value={mfaDisablePin}
                onChange={(e) => setMfaDisablePin(e.target.value)}
                className="px-3 py-1.5 rounded-lg bg-slate-950 border border-slate-700 text-white font-mono w-36"
              />
              <button
                type="submit"
                disabled={mfaBusy}
                className="px-3 py-1.5 rounded-lg bg-rose-950/60 hover:bg-rose-900/70 border border-rose-800 text-rose-200 font-semibold"
              >
                Disable 2FA
              </button>
            </form>
          </div>

          {/* Immutable Audit Trail */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            <div className="p-5 border-b border-slate-800 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-base text-white">📜 Immutable Security Audit Trail</h3>
                <p className="text-xs text-slate-400">
                  Latest logins, failed attempts, manager overrides, stock receipts, and ERPNext actions.
                </p>
              </div>
              <button
                type="button"
                onClick={loadData}
                className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-700 text-xs"
              >
                ↻ Refresh
              </button>
            </div>
            <div className="overflow-x-auto max-h-[420px]">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-800/50 text-slate-400 border-b border-slate-800 sticky top-0 uppercase">
                  <tr>
                    <th className="py-2.5 px-4">Timestamp</th>
                    <th className="py-2.5 px-4">Action</th>
                    <th className="py-2.5 px-4">Actor</th>
                    <th className="py-2.5 px-4">IP</th>
                    <th className="py-2.5 px-4">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {auditLogs.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="py-6 text-center text-slate-500">
                        No audit events recorded yet.
                      </td>
                    </tr>
                  ) : (
                    auditLogs.map((a) => (
                      <tr key={a.id} className="hover:bg-slate-800/30">
                        <td className="py-2.5 px-4 font-mono text-slate-400">
                          {new Date(a.createdAt).toLocaleString()}
                        </td>
                        <td className="py-2.5 px-4 font-mono font-bold text-emerald-400">{a.action}</td>
                        <td className="py-2.5 px-4 text-slate-200">
                          {a.actorUsername || 'system'}{' '}
                          {a.actorRole && <span className="text-slate-500">({a.actorRole})</span>}
                        </td>
                        <td className="py-2.5 px-4 font-mono text-slate-400">{a.ipAddress || '—'}</td>
                        <td className="py-2.5 px-4 text-slate-300">{a.details || '—'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : activeTab === 'TAX_INVOICES' ? (
        <div className="bg-slate-900 rounded-2xl border border-slate-800 overflow-hidden shadow-xl">
          <div className="p-5 border-b border-slate-800 flex items-center justify-between">
            <div>
              <h2 className="font-bold text-base text-white">POS Tax Invoice &amp; Outbox Archive</h2>
              <p className="text-xs text-slate-400">
                Inspect queued electronic invoices and download audit CSV reports.
              </p>
            </div>
            <button
              type="button"
              onClick={() => {
                downloadAuthenticatedFile('/api/reports/tax-invoices/export', 'kra_tax_invoices.csv')
                  .then(() => showToast('Tax invoice CSV exported.', 'success'))
                  .catch((err) => showToast(err.message, 'error'));
              }}
              className="px-3.5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-xs font-semibold transition shadow"
            >
              ⬇ Export CSV
            </button>
          </div>

          {loading ? (
            <div className="p-8 text-center text-xs text-slate-400">Loading tax invoices...</div>
          ) : taxInvoices.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">No invoices generated yet.</div>
          ) : (
            <div className="overflow-x-auto max-h-[600px]">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-800/50 text-slate-400 border-b border-slate-800 sticky top-0 uppercase tracking-wider">
                  <tr>
                    <th className="py-3 px-4">Date &amp; Time</th>
                    <th className="py-3 px-4">Receipt Number</th>
                    <th className="py-3 px-4">Control Unit Ref</th>
                    <th className="py-3 px-4 text-right">Taxable KES</th>
                    <th className="py-3 px-4 text-right">VAT KES</th>
                    <th className="py-3 px-4 text-right">Gross Total KES</th>
                    <th className="py-3 px-4 text-center">Outbox Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {taxInvoices.map((inv) => (
                    <tr key={inv.saleId} className="hover:bg-slate-800/40">
                      <td className="py-3 px-4 text-slate-400">{inv.date}</td>
                      <td className="py-3 px-4 font-mono font-semibold text-white">{inv.receiptNumber}</td>
                      <td className="py-3 px-4 font-mono text-emerald-400 font-bold">
                        {inv.kraControlUnitNumber}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-slate-300">
                        {Number(inv.taxableAmountKes).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-slate-300">
                        {Number(inv.taxAmountKes).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right font-mono font-bold text-white">
                        {Number(inv.totalAmountKes).toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                          {inv.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        <>
          {/* Top KPI Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-slate-900 p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center justify-between">
              <div>
                <p className="text-xs uppercase text-slate-400 font-semibold tracking-wider">
                  Offline Outbox Queue
                </p>
                <p className="text-xl font-black font-mono text-emerald-400 mt-1">
                  {outboxStatus?.status || 'CONNECTED'}
                </p>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  Processed: {outboxStatus?.sentCount ?? 0} | Pending: {outboxStatus?.pendingCount ?? 0}
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center text-2xl">
                📡
              </div>
            </div>

            <div className="bg-slate-900 p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center justify-between">
              <div>
                <p className="text-xs uppercase text-slate-400 font-semibold tracking-wider">
                  Recorded Invoices
                </p>
                <p className="text-xl font-black font-mono text-white mt-1">
                  {taxInvoices.length} Total
                </p>
                <p className="text-[11px] text-slate-400 mt-0.5">
                  Local VSCU outbox (eTIMS registration pending)
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center text-2xl">
                📑
              </div>
            </div>

            <div className="bg-slate-900 p-5 rounded-2xl border border-slate-800 shadow-xl flex items-center justify-between">
              <div>
                <p className="text-xs uppercase text-slate-400 font-semibold tracking-wider">
                  Audit &amp; GL Export
                </p>
                <div className="mt-2 flex gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      downloadAuthenticatedFile('/api/reports/tax-invoices/export', 'kra_tax_invoices.csv')
                        .then(() => showToast('Tax & sales CSV exported.', 'success'))
                        .catch((err) => showToast(err.message, 'error'));
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-bold transition shadow"
                  >
                    <span>📥</span> CSV Export
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveTab('ACCOUNTING')}
                    className="px-3 py-1.5 bg-indigo-600/20 hover:bg-indigo-600/35 text-indigo-300 border border-indigo-500/30 rounded-xl text-xs font-bold transition"
                  >
                    ERPNext GL ➔
                  </button>
                </div>
              </div>
              <div className="w-12 h-12 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400 flex items-center justify-center text-2xl">
                🏛️
              </div>
            </div>
          </div>

          {/* RBAC Matrix + Integration Health (2:1 Split from Enterprise System Design) */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* RBAC Security Matrix */}
            <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl shadow-xl flex flex-col overflow-hidden">
              <div className="bg-slate-800/80 px-5 py-3.5 border-b border-slate-700 flex items-center justify-between">
                <h3 className="font-bold text-white text-sm flex items-center gap-2">
                  <span className="text-emerald-400">🔑</span>
                  <span>Role-Based Access Control (RBAC) Policy</span>
                </h3>
                <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-semibold">
                  Enforced
                </span>
              </div>
              <div className="p-4 overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead className="bg-slate-800/40 uppercase text-slate-400 tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-4">Role</th>
                      <th className="py-3 px-4 text-center">POS Tills</th>
                      <th className="py-3 px-4 text-center">Staff &amp; Approvals</th>
                      <th className="py-3 px-4 text-center">Inventory &amp; GRN</th>
                      <th className="py-3 px-4 text-center">ERPNext &amp; GL</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60">
                    <tr>
                      <td className="py-3 px-4 font-semibold text-white flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-emerald-500" />
                        <span>Cashier</span>
                      </td>
                      <td className="py-3 px-4 text-center text-emerald-400 font-semibold">Full Access</td>
                      <td className="py-3 px-4 text-center text-slate-500">No Access</td>
                      <td className="py-3 px-4 text-center text-slate-500">Read Catalog</td>
                      <td className="py-3 px-4 text-center text-slate-500">No Access</td>
                    </tr>
                    <tr>
                      <td className="py-3 px-4 font-semibold text-white flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-blue-500" />
                        <span>Store Manager (Supervisor)</span>
                      </td>
                      <td className="py-3 px-4 text-center text-rose-400 font-medium">Restricted (No Till)</td>
                      <td className="py-3 px-4 text-center text-emerald-400 font-semibold">Authorize / Reset</td>
                      <td className="py-3 px-4 text-center text-emerald-400 font-semibold">Full Access</td>
                      <td className="py-3 px-4 text-center text-slate-500">No Access</td>
                    </tr>
                    <tr>
                      <td className="py-3 px-4 font-semibold text-white flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full bg-purple-500" />
                        <span>Super Administrator</span>
                      </td>
                      <td className="py-3 px-4 text-center text-rose-400 font-medium">Restricted (No Till)</td>
                      <td className="py-3 px-4 text-center text-amber-400">Oversight</td>
                      <td className="py-3 px-4 text-center text-emerald-400 font-semibold">Audit &amp; Reports</td>
                      <td className="py-3 px-4 text-center text-emerald-400 font-semibold">Full Access</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* Live Integration Health */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl flex flex-col overflow-hidden">
              <div className="bg-slate-800/80 px-5 py-3.5 border-b border-slate-700 flex items-center justify-between">
                <h3 className="font-bold text-white text-sm flex items-center gap-2">
                  <span className="text-emerald-400">🖥️</span>
                  <span>Integration Health</span>
                </h3>
                <button
                  type="button"
                  onClick={() => handlePingAll(true)}
                  disabled={pinging}
                  className="text-xs text-indigo-400 hover:text-indigo-300 font-semibold"
                >
                  {pinging ? 'Pinging...' : 'Ping All'}
                </button>
              </div>
              <div className="p-4 space-y-3">
                <div className="bg-slate-950 border border-slate-800 p-3 rounded-xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span
                      className={`w-2.5 h-2.5 rounded-full ${
                        backendHealthy ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'
                      }`}
                    />
                    <div>
                      <p className="text-xs font-semibold text-white">PostgreSQL &amp; POS API</p>
                      <p className="text-[10px] text-slate-400">Flyway v7 · Port 8080</p>
                    </div>
                  </div>
                  <span
                    className={`text-[11px] font-bold ${
                      backendHealthy ? 'text-emerald-400' : 'text-rose-400'
                    }`}
                  >
                    {backendHealthy ? 'HEALTHY' : 'DOWN'}
                  </span>
                </div>

                <div className="bg-slate-950 border border-slate-800 p-3 rounded-xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span
                      className={`w-2.5 h-2.5 rounded-full ${
                        erpConnected === true
                          ? 'bg-emerald-500 animate-pulse'
                          : erpConnected === false
                          ? 'bg-amber-400'
                          : 'bg-slate-500'
                      }`}
                    />
                    <div>
                      <p className="text-xs font-semibold text-white">ERPNext Accounting API</p>
                      <p className="text-[10px] text-slate-400">GL Journal &amp; Purchase Invoices</p>
                    </div>
                  </div>
                  <span
                    className={`text-[11px] font-bold ${
                      erpConnected ? 'text-emerald-400' : 'text-amber-400'
                    }`}
                  >
                    {erpConnected ? 'CONNECTED' : 'STANDBY'}
                  </span>
                </div>

                <div className="bg-slate-950 border border-slate-800 p-3 rounded-xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="w-2.5 h-2.5 rounded-full bg-indigo-400" />
                    <div>
                      <p className="text-xs font-semibold text-white">M-Pesa Daraja Gateway</p>
                      <p className="text-[10px] text-slate-400">STK Push &amp; Callback Ready</p>
                    </div>
                  </div>
                  <span className="text-[11px] font-bold text-indigo-400">CONFIGURABLE</span>
                </div>

                <div className="bg-slate-950 border border-slate-800 p-3 rounded-xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-500" />
                    <div>
                      <p className="text-xs font-semibold text-white">KRA eTIMS / VSCU</p>
                      <p className="text-[10px] text-slate-400">Queued in Outbox (Not registered)</p>
                    </div>
                  </div>
                  <span className="text-[11px] font-bold text-slate-400">OFFLINE</span>
                </div>
              </div>
            </div>
          </div>

          {/* Daily Sales by Day & Till */}
          <div className="bg-slate-900 rounded-2xl border border-slate-800 overflow-hidden shadow-xl">
            <div className="p-5 border-b border-slate-800 flex items-center justify-between">
              <div>
                <h2 className="font-bold text-base text-white">Daily Sales by Till &amp; Date</h2>
                <p className="text-xs text-slate-400">
                  Aggregated daily sales and individual till breakdown across all shop registers.
                </p>
              </div>
              <button
                onClick={loadData}
                className="text-xs px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-700 transition"
              >
                ↻ Refresh
              </button>
            </div>

            {loading ? (
              <div className="p-8 text-center text-xs text-slate-400">Loading daily sales report...</div>
            ) : dailySales.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-500">No sales transactions recorded yet.</div>
            ) : (
              <div className="divide-y divide-slate-800/60">
                {dailySales.map((day) => (
                  <div key={day.date} className="p-5 space-y-3">
                    <div className="flex justify-between items-baseline">
                      <div>
                        <span className="font-bold text-sm text-white">📅 {day.date}</span>
                        <span className="ml-3 text-xs text-slate-400 font-medium">
                          ({day.totalSalesCount} sales)
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="text-xs text-slate-400 mr-2 uppercase">Day Total:</span>
                        <span className="text-lg font-mono font-extrabold text-emerald-400">
                          KES {Number(day.totalRevenueKes).toFixed(2)}
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-2.5 pt-1">
                      {day.tillSummaries.map((till) => (
                        <div
                          key={till.tillId}
                          className="p-3 bg-slate-950 rounded-xl border border-slate-800 flex justify-between items-center text-xs"
                        >
                          <div>
                            <p className="font-bold font-mono text-white">Till: {till.tillId.slice(0, 8)}</p>
                            <p className="text-[11px] text-slate-400">{till.salesCount} transactions</p>
                          </div>
                          <span className="font-mono font-bold text-slate-200">
                            KES {Number(till.revenueKes).toFixed(2)}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
