import { useState } from 'react';
import { apiFetch } from '../utils/apiClient';

export interface CashierProfile {
  cashierId: string;
  username: string;
  name: string;
  role: string;
}

export interface ShiftInfo {
  shiftId: string;
  tillId: string;
  cashierId: string;
  cashierName: string;
  openedAt: string;
  closedAt: string | null;
  openingFloatKes: number;
  closingCashKes: number | null;
  status: string;
}

export function CashierShiftModal({
  currentCashier,
  currentShift,
  tillId,
  onLoginSuccess,
  onOpenShiftSuccess,
  onCloseShiftSuccess,
  onCancel,
}: {
  currentCashier: CashierProfile | null;
  currentShift: ShiftInfo | null;
  tillId: string;
  onLoginSuccess: (cashier: CashierProfile) => void;
  onOpenShiftSuccess: (shift: ShiftInfo) => void;
  onCloseShiftSuccess: () => void;
  onCancel?: () => void;
}) {
  // Mode: 'LOGIN' | 'OPEN_SHIFT' | 'CLOSE_SHIFT'
  const [mode, setMode] = useState<'LOGIN' | 'OPEN_SHIFT' | 'CLOSE_SHIFT'>(
    !currentCashier ? 'LOGIN' : !currentShift ? 'OPEN_SHIFT' : 'CLOSE_SHIFT'
  );

  const [username, setUsername] = useState(currentCashier?.username || '');
  const [pin, setPin] = useState('');
  const [openingFloat, setOpeningFloat] = useState('');
  const [closingCash, setClosingCash] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), pin: pin.trim() }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || 'Invalid username or PIN');
      }
      const cashier: CashierProfile = await res.json();
      onLoginSuccess(cashier);
      if (!currentShift) {
        setMode('OPEN_SHIFT');
      }
    } catch (err: any) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenShift = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!currentCashier) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch('/api/shifts/open', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tillId,
          cashierId: currentCashier.cashierId,
          openingFloatKes: parseFloat(openingFloat) || 0,
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || 'Failed to open shift');
      }
      const shift: ShiftInfo = await res.json();
      onOpenShiftSuccess(shift);
    } catch (err: any) {
      setError(err.message || 'Failed to open shift');
    } finally {
      setLoading(false);
    }
  };

  const handleCloseShift = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!currentShift) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch('/api/shifts/close', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          shiftId: currentShift.shiftId,
          closingCashKes: parseFloat(closingCash) || 0,
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message || 'Failed to close shift');
      }
      onCloseShiftSuccess();
    } catch (err: any) {
      setError(err.message || 'Failed to close shift');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden border border-zinc-200 dark:border-zinc-800">
        {/* Header */}
        <div className="bg-zinc-900 text-white px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xl">
              {mode === 'LOGIN' ? '🔐' : mode === 'OPEN_SHIFT' ? '🟢' : '🛑'}
            </span>
            <h3 className="font-bold text-lg">
              {mode === 'LOGIN' && 'Cashier Login'}
              {mode === 'OPEN_SHIFT' && 'Open Shift'}
              {mode === 'CLOSE_SHIFT' && 'End Shift / Cash Drop'}
            </h3>
          </div>
          {onCancel && (
            <button onClick={onCancel} className="text-zinc-400 hover:text-white text-2xl font-bold">
              &times;
            </button>
          )}
        </div>

        {error && (
          <div className="mx-6 mt-4 p-3 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-red-700 dark:text-red-300 text-xs">
            {error}
          </div>
        )}

        {/* Mode: LOGIN */}
        {mode === 'LOGIN' && (
          <form onSubmit={handleLogin} className="p-6 space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                Cashier Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 font-medium"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                4-Digit PIN
              </label>
              <input
                type="password"
                maxLength={6}
                value={pin}
                autoFocus
                placeholder="••••"
                onChange={(e) => setPin(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-center tracking-widest text-2xl focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-bold rounded-xl transition shadow-sm mt-2"
            >
              {loading ? 'Authenticating...' : 'Sign In'}
            </button>
          </form>
        )}

        {/* Mode: OPEN SHIFT */}
        {mode === 'OPEN_SHIFT' && (
          <form onSubmit={handleOpenShift} className="p-6 space-y-4">
            <div className="bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 p-3 rounded-xl text-xs text-emerald-800 dark:text-emerald-200">
              Signed in as <span className="font-bold">{currentCashier?.name}</span> ({currentCashier?.role}).
              Enter the starting cash float for Till 01.
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                Opening Cash Float (KES)
              </label>
              <input
                type="number"
                step="0.50"
                value={openingFloat}
                autoFocus
                onChange={(e) => setOpeningFloat(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-lg font-mono focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-bold rounded-xl transition shadow-sm mt-2"
            >
              {loading ? 'Opening Shift...' : 'Open Shift & Activate Till'}
            </button>
          </form>
        )}

        {/* Mode: CLOSE SHIFT */}
        {mode === 'CLOSE_SHIFT' && (
          <form onSubmit={handleCloseShift} className="p-6 space-y-4">
            <div className="text-xs text-zinc-600 dark:text-zinc-400 space-y-1">
              <p>Cashier: <span className="font-bold text-zinc-800 dark:text-zinc-200">{currentShift?.cashierName}</span></p>
              <p>Shift Started: <span className="font-mono">{currentShift?.openedAt ? new Date(currentShift.openedAt).toLocaleTimeString() : ''}</span></p>
              <p>Opening Float: <span className="font-mono font-semibold">KES {currentShift?.openingFloatKes.toFixed(2)}</span></p>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                Closing Cash Count in Drawer (KES)
              </label>
              <input
                type="number"
                step="0.50"
                value={closingCash}
                placeholder="Enter physical cash counted"
                autoFocus
                onChange={(e) => setClosingCash(e.target.value)}
                required
                className="w-full px-4 py-3 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-zinc-50 dark:bg-zinc-800 text-lg font-mono focus:outline-none focus:ring-2 focus:ring-red-500"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-bold rounded-xl transition shadow-sm mt-2"
            >
              {loading ? 'Closing Shift...' : 'Confirm & Close Shift'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
