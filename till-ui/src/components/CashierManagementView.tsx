import { useState, useEffect } from 'react';
import { apiFetch } from '../utils/apiClient';

export interface CashierDetails {
  id: string;
  username: string;
  name: string;
  role: string;
  status: string;
  active: boolean;
  createdAt: string;
  isOnline: boolean;
  activeTillId: string | null;
  shiftOpenedAt: string | null;
  shiftFloatKes: number;
  totalSalesCount: number;
  totalRevenueKes: number;
}

export function CashierManagementView() {
  const [cashiers, setCashiers] = useState<CashierDetails[]>([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Reset PIN modal state
  const [resetTarget, setResetTarget] = useState<CashierDetails | null>(null);
  const [newPin, setNewPin] = useState('');
  const [resetting, setResetting] = useState(false);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<CashierDetails | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadCashiers = async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/api/cashiers');
      if (res.ok) {
        setCashiers(await res.json());
      }
    } catch {
      setMsg({ type: 'error', text: 'Failed to load cashiers list' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCashiers();
    const interval = setInterval(loadCashiers, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleAuthorize = async (cashier: CashierDetails) => {
    try {
      const res = await apiFetch(`/api/cashiers/${cashier.id}/authorize`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error('Failed to authorize cashier');
      setMsg({
        type: 'success',
        text: `Cashier "${cashier.name}" (@${cashier.username}) has been authorized! They can now log in.`,
      });
      loadCashiers();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error authorizing cashier' });
    }
  };

  const handleResetPinSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resetTarget || !newPin.trim()) return;

    setResetting(true);
    try {
      const res = await apiFetch(`/api/cashiers/${resetTarget.id}/reset-pin`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPin: newPin.trim() }),
      });

      if (!res.ok) throw new Error('Failed to reset PIN');
      setMsg({
        type: 'success',
        text: `PIN successfully reset for ${resetTarget.name}. Any rate-limiting lockouts have been cleared.`,
      });
      setResetTarget(null);
      setNewPin('');
      loadCashiers();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error resetting PIN' });
    } finally {
      setResetting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;

    setDeleting(true);
    try {
      const res = await apiFetch(`/api/cashiers/${deleteTarget.id}`, {
        method: 'DELETE',
      });

      if (!res.ok) throw new Error('Failed to delete cashier');
      const data = await res.json();
      setMsg({
        type: 'success',
        text: data.message || `Cashier ${deleteTarget.name} removed successfully.`,
      });
      setDeleteTarget(null);
      loadCashiers();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error deleting cashier' });
    } finally {
      setDeleting(false);
    }
  };

  const pendingCashiers = cashiers.filter((c) => c.status === 'PENDING_APPROVAL');
  const activeCashiers = cashiers.filter((c) => c.status !== 'PENDING_APPROVAL' && c.status !== 'DELETED');

  return (
    <div className="space-y-6">
      {/* Alert Banner */}
      {msg && (
        <div
          className={`p-4 rounded-xl flex items-center justify-between text-xs font-semibold ${
            msg.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800'
              : 'bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-300 border border-red-200 dark:border-red-800'
          }`}
        >
          <span>{msg.text}</span>
          <button onClick={() => setMsg(null)} className="font-bold text-sm">
            &times;
          </button>
        </div>
      )}

      {/* Pending Authorizations Section */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 p-5 shadow-xs">
        <div className="flex items-center justify-between pb-3 border-b border-zinc-200 dark:border-zinc-800">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-bold text-base">Pending Cashier Authorizations</h2>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">
                {pendingCashiers.length} Awaiting Manager Approval
              </span>
            </div>
            <p className="text-xs text-zinc-500">
              When a new cashier signs up, the store manager must authorize their profile before they can access the till.
            </p>
          </div>
          <button
            onClick={loadCashiers}
            className="text-xs px-3 py-1 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 rounded-lg transition"
          >
            ↻ Refresh
          </button>
        </div>

        {pendingCashiers.length === 0 ? (
          <div className="py-8 text-center text-xs text-zinc-400">
            ✓ No pending cashier registrations. All cashiers are up to date.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 pt-4">
            {pendingCashiers.map((c) => (
              <div
                key={c.id}
                className="p-4 rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/40 dark:bg-amber-950/20 flex flex-col justify-between gap-3 text-xs"
              >
                <div>
                  <div className="flex justify-between items-start">
                    <p className="font-bold text-sm text-zinc-900 dark:text-zinc-100">{c.name}</p>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-200 text-amber-900 dark:bg-amber-900 dark:text-amber-100">
                      Pending
                    </span>
                  </div>
                  <p className="font-mono text-zinc-500 text-[11px] mt-0.5">@{c.username}</p>
                  <p className="text-[11px] text-zinc-400 mt-1">
                    Registered: {new Date(c.createdAt).toLocaleString('en-KE', { dateStyle: 'short', timeStyle: 'short' })}
                  </p>
                </div>

                <div className="flex gap-2 pt-2 border-t border-amber-200 dark:border-amber-900/50">
                  <button
                    onClick={() => handleAuthorize(c)}
                    className="flex-1 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-lg shadow-xs transition text-center"
                  >
                    ✓ Authorize Cashier
                  </button>
                  <button
                    onClick={() => setDeleteTarget(c)}
                    className="py-2 px-3 bg-red-100 hover:bg-red-200 text-red-700 rounded-lg font-semibold transition"
                    title="Reject and Delete"
                  >
                    Reject
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Cashier Monitoring & Management Table */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
          <div>
            <h2 className="font-bold text-base">Cashier Shift Monitoring & Access Control</h2>
            <p className="text-xs text-zinc-500">
              Live monitoring of active till shifts, transaction volume, PIN resets, and role assignments.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="p-8 text-center text-xs text-zinc-500">Loading cashiers data...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/50 text-zinc-600 dark:text-zinc-400 border-b border-zinc-200 dark:border-zinc-800">
                <tr>
                  <th className="py-3 px-4">Cashier Name</th>
                  <th className="py-3 px-4">Username</th>
                  <th className="py-3 px-4 text-center">Role</th>
                  <th className="py-3 px-4 text-center">Live Status</th>
                  <th className="py-3 px-4 text-right">Shift Float</th>
                  <th className="py-3 px-4 text-right">Sales Count</th>
                  <th className="py-3 px-4 text-right">Total Revenue</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {activeCashiers.map((c) => (
                  <tr key={c.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                    <td className="py-3 px-4 font-semibold text-zinc-900 dark:text-zinc-100">
                      {c.name}
                    </td>
                    <td className="py-3 px-4 font-mono text-zinc-500">@{c.username}</td>
                    <td className="py-3 px-4 text-center">
                      <span className="px-2 py-0.5 rounded font-bold text-[10px] bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                        {c.role}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-center">
                      {c.isOnline ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                          Online (Till {c.activeTillId?.slice(0, 4)})
                        </span>
                      ) : (
                        <span className="text-zinc-400 text-[11px]">Shift Closed</span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right font-mono">
                      {c.isOnline ? `KES ${Number(c.shiftFloatKes).toFixed(2)}` : '—'}
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-semibold">
                      {c.totalSalesCount}
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400">
                      KES {Number(c.totalRevenueKes).toFixed(2)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => {
                            setResetTarget(c);
                            setNewPin('');
                          }}
                          className="px-2.5 py-1 text-xs font-semibold bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 rounded border border-zinc-200 dark:border-zinc-700 transition"
                          title="Reset PIN / Clear Rate Limit Lockout"
                        >
                          🔑 Reset PIN
                        </button>
                        <button
                          type="button"
                          onClick={() => setDeleteTarget(c)}
                          className="px-2 py-1 text-xs font-semibold text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 rounded transition"
                          title="Delete Cashier"
                        >
                          🗑️
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Reset PIN Modal */}
      {resetTarget && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-3xl shadow-2xl max-w-sm w-full overflow-hidden border border-zinc-200 dark:border-zinc-800">
            <div className="bg-emerald-600 text-white px-6 py-4 flex items-center justify-between">
              <h3 className="font-bold text-base">Reset Cashier PIN</h3>
              <button onClick={() => setResetTarget(null)} className="text-2xl leading-none">
                &times;
              </button>
            </div>
            <form onSubmit={handleResetPinSubmit} className="p-6 space-y-4 text-xs">
              <p className="text-zinc-600 dark:text-zinc-400">
                Enter a new PIN for <strong>{resetTarget.name}</strong> (@{resetTarget.username}). This will immediately clear any failed login lockouts.
              </p>
              <div>
                <label className="block font-semibold mb-1">New PIN / Password</label>
                <input
                  type="password"
                  required
                  autoFocus
                  placeholder="••••"
                  value={newPin}
                  onChange={(e) => setNewPin(e.target.value)}
                  className="w-full px-4 py-2.5 text-center text-lg font-mono font-bold rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                />
              </div>
              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setResetTarget(null)}
                  className="flex-1 py-2.5 rounded-xl border border-zinc-300 dark:border-zinc-700 font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={resetting || !newPin.trim()}
                  className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-white font-bold rounded-xl shadow-xs transition"
                >
                  {resetting ? 'Resetting...' : 'Save New PIN'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Cashier Confirmation Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-3xl shadow-2xl max-w-sm w-full overflow-hidden border border-zinc-200 dark:border-zinc-800 p-6 space-y-4 text-xs">
            <h3 className="font-bold text-base text-red-600">Delete Cashier Account?</h3>
            <p className="text-zinc-600 dark:text-zinc-400">
              Are you sure you want to delete <strong>{deleteTarget.name}</strong> (@{deleteTarget.username})?
              If the cashier has past sales records, their account will be deactivated and archived to preserve transaction audit integrity.
            </p>
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="flex-1 py-2.5 rounded-xl border border-zinc-300 dark:border-zinc-700 font-semibold"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={deleting}
                onClick={handleDeleteConfirm}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 text-white font-bold rounded-xl shadow-xs transition"
              >
                {deleting ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
