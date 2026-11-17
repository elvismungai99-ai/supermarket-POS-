import { useState } from 'react';

export interface UserSession {
  cashierId: string;
  username: string;
  name: string;
  role: 'CASHIER' | 'SUPERVISOR' | 'SUPER_ADMIN';
  status: string;
  token?: string;
}

interface RoleAuthPortalProps {
  onLoginSuccess: (user: UserSession) => void;
}

export function RoleAuthPortal({ onLoginSuccess }: RoleAuthPortalProps) {
  const [selectedRole, setSelectedRole] = useState<'CASHIER' | 'SUPERVISOR' | 'SUPER_ADMIN'>('CASHIER');
  const [mode, setMode] = useState<'SIGN_IN' | 'CREATE_ACCOUNT'>('SIGN_IN');

  // Sign in form state
  const [username, setUsername] = useState('');
  const [pin, setPin] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [mfaRequired, setMfaRequired] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lockoutSec, setLockoutSec] = useState<number | null>(null);
  const [attemptsLeft, setAttemptsLeft] = useState<number | null>(null);

  // Register form state
  const [regName, setRegName] = useState('');
  const [regUsername, setRegUsername] = useState('');
  const [regPin, setRegPin] = useState('');
  const [regSuccessMsg, setRegSuccessMsg] = useState<string | null>(null);

  const handleRoleSelect = (role: 'CASHIER' | 'SUPERVISOR' | 'SUPER_ADMIN') => {
    setSelectedRole(role);
    setError(null);
    setRegSuccessMsg(null);
    setUsername('');
    setPin('');
    setTotpCode('');
    setMfaRequired(false);
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !pin.trim()) {
      setError('Username and PIN are required');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          pin: pin.trim(),
          ...(totpCode.trim() ? { totpCode: totpCode.trim() } : {}),
        }),
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        if (typeof data.message === 'string' && data.message.startsWith('MFA_REQUIRED')) {
          setMfaRequired(true);
          setError('Two-factor authentication enabled: enter your 6-digit authenticator code.');
          return;
        }
        if (res.status === 429) {
          setLockoutSec(data.remainingLockSeconds || 60);
          setError(data.message || 'Account temporarily locked due to failed attempts.');
        } else if (res.status === 403) {
          setError(data.message || 'Account pending manager authorization.');
        } else {
          setAttemptsLeft(data.remainingAttempts ?? null);
          setError(data.message || 'Invalid username or PIN');
        }
        return;
      }

      onLoginSuccess(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Network error connecting to auth service');
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!regName.trim() || !regUsername.trim() || !regPin.trim()) {
      setError('All fields are required');
      return;
    }

    setLoading(true);
    setError(null);
    setRegSuccessMsg(null);

    try {
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: regName.trim(),
          username: regUsername.trim(),
          pin: regPin.trim(),
          role: selectedRole,
        }),
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        throw new Error(data.message || 'Registration failed');
      }

      setRegSuccessMsg(
        selectedRole === 'SUPER_ADMIN'
          ? `Super Admin account for ${regName} created successfully! You can now sign in.`
          : selectedRole === 'CASHIER'
          ? `Cashier account for ${regName} (@${regUsername}) created! A Store Manager must authorize your account before you can log in.`
          : `Manager account for ${regName} created successfully! You can now sign in.`
      );
      setMode('SIGN_IN');
      setUsername(regUsername.trim());
      setPin('');
      setRegName('');
      setRegUsername('');
      setRegPin('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to register account');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-linear-to-br from-zinc-900 via-zinc-950 to-zinc-900 text-zinc-100 flex flex-col justify-center items-center p-4 sm:p-6 font-sans">
      <div className="max-w-xl w-full space-y-6">
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-emerald-600 text-white font-extrabold text-2xl shadow-lg shadow-emerald-950/40">
            POS
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">Kenya Retail Supermarket POS</h1>
          <p className="text-xs sm:text-sm text-zinc-400">
            Choose your role to access role-specific workspaces and security oversight
          </p>
        </div>

        {/* Role Cards Selector */}
        <div className="grid grid-cols-3 gap-2 sm:gap-3">
          {/* Cashier Role */}
          <button
            type="button"
            onClick={() => handleRoleSelect('CASHIER')}
            className={`p-3 sm:p-4 rounded-2xl border text-left transition flex flex-col justify-between ${
              selectedRole === 'CASHIER'
                ? 'bg-emerald-600/10 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/20'
                : 'bg-zinc-900/60 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
            }`}
          >
            <div className="text-2xl mb-1">🛒</div>
            <div>
              <p className="font-bold text-xs sm:text-sm text-zinc-100">Cashier</p>
              <p className="text-[10px] text-zinc-400 line-clamp-2 mt-0.5">
                Barcode scan, cart, cash & M-Pesa, shifts
              </p>
            </div>
          </button>

          {/* Admin / Manager Role */}
          <button
            type="button"
            onClick={() => handleRoleSelect('SUPERVISOR')}
            className={`p-3 sm:p-4 rounded-2xl border text-left transition flex flex-col justify-between ${
              selectedRole === 'SUPERVISOR'
                ? 'bg-blue-600/10 border-blue-500 text-blue-400 ring-2 ring-blue-500/20'
                : 'bg-zinc-900/60 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
            }`}
          >
            <div className="text-2xl mb-1">👔</div>
            <div>
              <p className="font-bold text-xs sm:text-sm text-zinc-100">Admin / Manager</p>
              <p className="text-[10px] text-zinc-400 line-clamp-2 mt-0.5">
                Cashier authorizations & monitoring, PIN resets, inventory & KRA tax, returns
              </p>
            </div>
          </button>

          {/* Super Admin Role */}
          <button
            type="button"
            onClick={() => handleRoleSelect('SUPER_ADMIN')}
            className={`p-3 sm:p-4 rounded-2xl border text-left transition flex flex-col justify-between ${
              selectedRole === 'SUPER_ADMIN'
                ? 'bg-purple-600/10 border-purple-500 text-purple-400 ring-2 ring-purple-500/20'
                : 'bg-zinc-900/60 border-zinc-800 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200'
            }`}
          >
            <div className="text-2xl mb-1">🏛️</div>
            <div>
              <p className="font-bold text-xs sm:text-sm text-zinc-100">Super Admin</p>
              <p className="text-[10px] text-zinc-400 line-clamp-2 mt-0.5">
                Overall system oversight, daily sales by till, eTIMS tax archive & CSV
              </p>
            </div>
          </button>
        </div>

        {/* Main Card with Sign In / Create Account Toggle */}
        <div className="bg-zinc-900/90 border border-zinc-800 rounded-3xl p-6 sm:p-8 shadow-2xl backdrop-blur-md">
          {/* Toggle Buttons */}
          <div className="grid grid-cols-2 p-1 bg-zinc-800/80 rounded-2xl mb-6">
            <button
              type="button"
              onClick={() => {
                setMode('SIGN_IN');
                setError(null);
              }}
              className={`py-2 text-xs sm:text-sm font-bold rounded-xl transition ${
                mode === 'SIGN_IN'
                  ? 'bg-emerald-600 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => {
                setMode('CREATE_ACCOUNT');
                setError(null);
                setRegSuccessMsg(null);
              }}
              className={`py-2 text-xs sm:text-sm font-bold rounded-xl transition ${
                mode === 'CREATE_ACCOUNT'
                  ? 'bg-emerald-600 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Create Account
            </button>
          </div>

          {/* Success Message Banner */}
          {regSuccessMsg && (
            <div className="mb-4 p-4 rounded-2xl bg-emerald-950/50 border border-emerald-800 text-emerald-300 text-xs flex items-start gap-2">
              <span className="text-base">✓</span>
              <span>{regSuccessMsg}</span>
            </div>
          )}

          {/* Error Message Banner */}
          {error && (
            <div className="mb-4 p-4 rounded-2xl bg-red-950/50 border border-red-900 text-red-300 text-xs flex items-start gap-2">
              <span className="text-base">⚠️</span>
              <div className="space-y-1">
                <p className="font-semibold">{error}</p>
                {attemptsLeft !== null && attemptsLeft > 0 && (
                  <p className="text-[11px] text-red-400">
                    {attemptsLeft} attempt(s) left before account lockout.
                  </p>
                )}
                {lockoutSec !== null && (
                  <p className="text-[11px] text-amber-300 font-mono">
                    Rate limiting active: Locked for {lockoutSec}s. Managers can clear lock via Reset PIN.
                  </p>
                )}
              </div>
            </div>
          )}

          {/* SIGN IN FORM */}
          {mode === 'SIGN_IN' ? (
            <form onSubmit={handleLoginSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-400 uppercase mb-1">
                  Username
                </label>
                <input
                  type="text"
                  required
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-400 uppercase mb-1">
                  Security PIN / Password
                </label>
                <input
                  type="password"
                  required
                  placeholder="••••••"
                  value={pin}
                  onChange={(e) => setPin(e.target.value)}
                  className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-center text-lg tracking-widest"
                />
              </div>

              {mfaRequired && (
                <div>
                  <label className="block text-xs font-semibold text-emerald-400 uppercase mb-1">
                    Authenticator Code (6 digits)
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    required
                    placeholder="123456"
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value)}
                    className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-emerald-500 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-center text-lg tracking-widest"
                  />
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full py-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-bold text-base rounded-2xl shadow-lg transition mt-4 flex items-center justify-center gap-2"
              >
                {loading ? 'Authenticating...' : `Sign In as ${selectedRole === 'CASHIER' ? 'Cashier' : selectedRole === 'SUPERVISOR' ? 'Admin / Manager' : 'Super Admin'}`}
              </button>
            </form>
          ) : (
            /* CREATE ACCOUNT FORM (No Role Selection Dropdown: Scoped Directly to Selected Role) */
            <form onSubmit={handleRegisterSubmit} className="space-y-4">
              {/* Contextual Role Banner */}
              {selectedRole === 'SUPER_ADMIN' && (
                <div className="p-3.5 rounded-2xl bg-purple-950/40 border border-purple-800/60 text-xs text-purple-200 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-purple-300">
                    <span>🏛️</span>
                    <span>Creating Super Admin Account (Overall System Oversight)</span>
                  </div>
                  <p className="text-[11px] text-purple-300/80">
                    The Super Admin provides overarching oversight for daily store sales, KRA eTIMS electronic tax compliance, and system telemetry across all tills. Super Admins do not operate till registers.
                  </p>
                </div>
              )}

              {selectedRole === 'SUPERVISOR' && (
                <div className="p-3.5 rounded-2xl bg-blue-950/40 border border-blue-800/60 text-xs text-blue-200 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-blue-300">
                    <span>👔</span>
                    <span>Creating Admin / Manager Account</span>
                  </div>
                  <p className="text-[11px] text-blue-300/80">
                    Managers oversee cashier staff, monitor shifts, reset PINs, manage product prices and KRA tax categories, and handle returns. Managers do not work as cashiers and do not access the till.
                  </p>
                </div>
              )}

              {selectedRole === 'CASHIER' && (
                <div className="p-3.5 rounded-2xl bg-emerald-950/40 border border-emerald-800/60 text-xs text-emerald-200 space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-emerald-300">
                    <span>🛒</span>
                    <span>Creating Cashier Account (Till Operator)</span>
                  </div>
                  <p className="text-[11px] text-emerald-300/80">
                    Cashier accounts operate point-of-sale till registers. Newly created cashiers must be authorized by an Admin/Manager before signing in.
                  </p>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-zinc-400 uppercase mb-1">
                  Full Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. John Mwangi"
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-zinc-400 uppercase mb-1">
                    Desired Username
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. jmwangi"
                    value={regUsername}
                    onChange={(e) => setRegUsername(e.target.value)}
                    className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-zinc-400 uppercase mb-1">
                    Security PIN (min 6 chars)
                  </label>
                  <input
                    type="password"
                    required
                    minLength={6}
                    placeholder="••••••"
                    value={regPin}
                    onChange={(e) => setRegPin(e.target.value)}
                    className="w-full px-4 py-3 rounded-2xl bg-zinc-800/80 border border-zinc-700 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-center text-sm"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-4 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-bold text-base rounded-2xl shadow-lg transition mt-4"
              >
                {loading
                  ? 'Creating Profile...'
                  : `Create ${selectedRole === 'CASHIER' ? 'Cashier' : selectedRole === 'SUPERVISOR' ? 'Admin / Manager' : 'Super Admin'} Account`}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
