import { useState, useEffect, useMemo } from 'react';
import { ReceiptModal } from './components/ReceiptModal';
import type { ReceiptDto } from './components/ReceiptModal';
import { CashierShiftModal } from './components/CashierShiftModal';
import type { CashierProfile, ShiftInfo } from './components/CashierShiftModal';
import { CheckoutModal } from './components/CheckoutModal';
import type { CartItem } from './components/CheckoutModal';
import { ManagerInventoryView } from './components/ManagerInventoryView';
import { ManagerDashboardHeader } from './components/ManagerDashboardHeader';
import { ReturnsView } from './components/ReturnsView';
import { SuperAdminView } from './components/SuperAdminView';
import { CashierManagementView } from './components/CashierManagementView';
import { CashierPosTerminal } from './components/CashierPosTerminal';
import { CustomerPoleDisplay } from './components/CustomerPoleDisplay';
import { RoleAuthPortal } from './components/RoleAuthPortal';
import type { UserSession } from './components/RoleAuthPortal';
import { apiFetch, setAuthToken, setOnUnauthorized } from './utils/apiClient';
import { playPosBeep } from './utils/sound';
import { ToastProvider, useToast } from './utils/toast';
import {
  checkHardwareHealth,
  findCachedProductByBarcode,
  getOfflineQueue,
  readScaleWeight,
  syncOfflineQueue,
} from './utils/offlineQueue';

export interface ProductDto {
  id: string;
  name: string;
  priceKes: number;
  taxCategory: string;
  stockQuantity?: number;
  reorderLevel?: number;
  barcode?: string;
  detectedWeightKg?: number | null;
}

const DEFAULT_SHOP_ID = '00000000-0000-0000-0000-000000000001';
const DEFAULT_TILL_ID = '00000000-0000-0000-0000-000000000001';
const TERMINAL_TILL_STORAGE_KEY = 'pos_terminal_till_id';
const TERMINAL_SHOP_STORAGE_KEY = 'pos_terminal_shop_id';

interface TerminalTillOption {
  id: string;
  shopId: string;
  tillNumber: string;
  label: string;
}

interface TerminalShopOption {
  id: string;
  code: string;
  name: string;
}

type ManagerTab = 'CASHIERS' | 'INVENTORY' | 'RETURNS';

function getInitials(name: string): string {
  const parts = (name || 'U').trim().split(/\s+/);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return parts[0].slice(0, 2).toUpperCase();
}

function AppContent() {
  const { showToast } = useToast();

  // Global User Session (Null -> Shows Role Selection & Sign In / Create Account Portal)
  const [session, setSession] = useState<UserSession | null>(null);

  // Tab states for Manager role (Cashier approvals & monitoring, inventory & tax, returns - tills are strictly for cashiers)
  const [managerTab, setManagerTab] = useState<ManagerTab>('CASHIERS');

  // POS Till state (strictly Cashier)
  const [cart, setCart] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastScanned, setLastScanned] = useState<string | null>(null);
  const [backendUp, setBackendUp] = useState<boolean | null>(null);
  const [hardwareUp, setHardwareUp] = useState<boolean>(false);
  const [offlineCount, setOfflineCount] = useState<number>(() => getOfflineQueue().length);
  const [syncingOffline, setSyncingOffline] = useState(false);

  // Cashier / Till Shift state (multi-till workstation binding; defaults seamlessly to Till 1)
  const [selectedTillId, setSelectedTillId] = useState<string>(() => {
    try {
      return localStorage.getItem(TERMINAL_TILL_STORAGE_KEY) || DEFAULT_TILL_ID;
    } catch {
      return DEFAULT_TILL_ID;
    }
  });
  const [selectedShopId, setSelectedShopId] = useState<string>(() => {
    try {
      return localStorage.getItem(TERMINAL_SHOP_STORAGE_KEY) || DEFAULT_SHOP_ID;
    } catch {
      return DEFAULT_SHOP_ID;
    }
  });
  const [availableTills, setAvailableTills] = useState<TerminalTillOption[]>([]);
  const [availableShops, setAvailableShops] = useState<TerminalShopOption[]>([]);
  const [shift, setShift] = useState<ShiftInfo | null>(null);
  const [showShiftModal, setShowShiftModal] = useState(false);

  // Checkout & Receipt state
  const [showCheckout, setShowCheckout] = useState(false);
  const [activeReceipt, setActiveReceipt] = useState<ReceiptDto | null>(null);
  const [overrideBy, setOverrideBy] = useState<string | null>(null);
  const [overrideToken, setOverrideToken] = useState<string | null>(null);

  // Sync JWT token globally into apiFetch
  useEffect(() => {
    setAuthToken(session?.token ?? null);
    setOnUnauthorized(() => {
      setAuthToken(null);
      setSession(null);
      setCart([]);
      showToast('Session expired or revoked. Please sign in again.', 'error', 'Authentication');
    });
    return () => setOnUnauthorized(null);
  }, [session?.token, showToast]);

  // Poll backend & local hardware agent health, auto-syncing offline queue when online
  useEffect(() => {
    const checkAll = async () => {
      try {
        const res = await fetch('/actuator/health');
        const data = res.ok ? await res.json() : null;
        const isUp = data?.status === 'UP';
        setBackendUp(isUp);
        if (isUp && getOfflineQueue().length > 0) {
          const synced = await syncOfflineQueue(session?.token);
          setOfflineCount(synced.remaining);
          if (synced.synced > 0) {
            showToast(`Synced ${synced.synced} offline sale(s) to server.`, 'success', 'Offline Sync');
          }
        }
      } catch {
        setBackendUp(false);
      }

      if (session?.role === 'CASHIER') {
        const hw = await checkHardwareHealth();
        setHardwareUp(hw.online);
      } else {
        setHardwareUp(false);
      }
    };
    checkAll();
    const timer = setInterval(checkAll, 15000);
    return () => clearInterval(timer);
  }, [session?.token, session?.role, showToast]);

  const handleManualOfflineSync = async () => {
    setSyncingOffline(true);
    try {
      const res = await syncOfflineQueue(session?.token);
      setOfflineCount(res.remaining);
      showToast(
        res.synced > 0 ? `Synced ${res.synced} queued sale(s).` : 'No queued sales could be synced yet.',
        res.synced > 0 ? 'success' : 'info'
      );
    } finally {
      setSyncingOffline(false);
    }
  };

  const handleApplyScaleWeight = async (productId: string) => {
    try {
      const w = await readScaleWeight();
      playPosBeep();
      setCart((prev) =>
        prev.map((item) => (item.id === productId ? { ...item, quantity: w.weightKg } : item))
      );
      setError(null);
      showToast(`Scale weight applied: ${w.weightKg} kg`, 'success');
    } catch {
      setError('Scale hardware agent (:9001) not reachable.');
      showToast('Scale hardware agent (:9001) not reachable.', 'error');
    }
  };

  // Load available branches/tills when Cashier logs in (enables simultaneous multi-till terminals)
  useEffect(() => {
    if (session?.role === 'CASHIER') {
      apiFetch('/api/branches')
        .then((res) => (res.ok ? res.json().catch(() => null) : null))
        .then((data: { shops?: TerminalShopOption[]; tills?: TerminalTillOption[] } | null) => {
          if (!data) return;
          if (Array.isArray(data.shops)) setAvailableShops(data.shops);
          if (Array.isArray(data.tills) && data.tills.length > 0) {
            setAvailableTills(data.tills);
            const matched = data.tills.find((t) => t.id === selectedTillId);
            if (matched) {
              setSelectedShopId(matched.shopId);
            } else {
              setSelectedTillId(data.tills[0].id);
              setSelectedShopId(data.tills[0].shopId);
            }
          }
        })
        .catch(() => {});
    }
  }, [session?.role, selectedTillId]);

  // Sync active till shift whenever Cashier logs in or switches workstation till
  useEffect(() => {
    if (session?.role === 'CASHIER') {
      apiFetch(`/api/shifts/current?tillId=${encodeURIComponent(selectedTillId)}`)
        .then((res) => {
          if (res.status === 204) return null;
          return res.ok ? res.json().catch(() => null) : null;
        })
        .then((data) => {
          if (data && data.shiftId) {
            setShift(data);
          } else {
            setShift(null);
          }
        })
        .catch(() => setShift(null));
    } else {
      setShift(null);
    }
  }, [session, selectedTillId]);

  const handleSelectTerminalTill = (tillId: string) => {
    const found = availableTills.find((t) => t.id === tillId);
    const shopId = found ? found.shopId : DEFAULT_SHOP_ID;
    setSelectedTillId(tillId);
    setSelectedShopId(shopId);
    try {
      localStorage.setItem(TERMINAL_TILL_STORAGE_KEY, tillId);
      localStorage.setItem(TERMINAL_SHOP_STORAGE_KEY, shopId);
    } catch {
      // Ignore storage quota restrictions
    }
  };

  const handleSelectProduct = (product: ProductDto) => {
    playPosBeep();
    const qtyToAdd = product.detectedWeightKg && product.detectedWeightKg > 0
      ? Number(product.detectedWeightKg)
      : 1;
    setCart((prev) => {
      const existingIdx = prev.findIndex((item) => item.id === product.id);
      if (existingIdx >= 0) {
        const next = [...prev];
        next[existingIdx] = {
          ...next[existingIdx],
          quantity: Number((next[existingIdx].quantity + qtyToAdd).toFixed(3)),
        };
        return next;
      }
      return [
        ...prev,
        {
          id: product.id,
          barcode: product.barcode || '',
          name: product.name,
          priceKes: Number(product.priceKes),
          quantity: qtyToAdd,
          taxCategory: product.taxCategory || 'A',
        },
      ];
    });
    setLastScanned(product.barcode || product.name);
    setError(null);
  };

  const handleScan = async (barcode: string) => {
    setLoading(true);
    setError(null);
    setLastScanned(barcode);

    try {
      const res = await apiFetch(`/api/products/by-barcode/${encodeURIComponent(barcode)}`);
      if (res.status === 404) {
        setError(`Barcode not found: ${barcode}`);
        showToast(`Barcode not found: ${barcode}`, 'error');
        return;
      }
      if (!res.ok) {
        throw new Error(`Server returned error status ${res.status}`);
      }

      const product: ProductDto = await res.json();
      handleSelectProduct(product);
      showToast(
        product.detectedWeightKg
          ? `Added ${product.name} (${product.detectedWeightKg.toFixed(3)} kg)`
          : `Added ${product.name}`,
        'success'
      );
    } catch (err: unknown) {
      // Offline IndexedDB / localStorage catalog fallback
      const cached = findCachedProductByBarcode(barcode);
      if (cached) {
        handleSelectProduct(cached);
        showToast(`Offline catalog hit: ${cached.name}`, 'info');
      } else {
        setError(err instanceof Error ? err.message : 'Failed to connect to backend server');
      }
    } finally {
      setLoading(false);
    }
  };

  const updateQuantity = (id: string, delta: number) => {
    setCart((prev) =>
      prev
        .map((item) => {
          if (item.id === id) {
            const newQty = Number((item.quantity + delta).toFixed(3));
            return newQty > 0 ? { ...item, quantity: newQty } : null;
          }
          return item;
        })
        .filter(Boolean) as CartItem[]
    );
  };

  const removeItem = (id: string) => {
    setCart((prev) => prev.filter((item) => item.id !== id));
  };

  const clearCart = () => {
    setCart([]);
    setError(null);
    setLastScanned(null);
    setOverrideBy(null);
  };

  const { totalKes, subtotalKes, totalTaxKes, taxBreakdown } = useMemo(() => {
    let grossTotal = 0;
    let taxableTotal = 0;
    let taxTotal = 0;
    const catMap: Record<string, { gross: number; taxable: number; tax: number; rate: number }> = {};

    for (const item of cart) {
      const lineGross = Number(
        Math.max(0, item.priceKes * item.quantity - (item.discountKes || 0)).toFixed(2)
      );
      grossTotal += lineGross;

      const cat = (item.taxCategory || 'A').toUpperCase();
      const rate = cat === 'A' ? 0.16 : cat === 'C' ? 0.08 : 0.0;
      let taxable = lineGross;
      let tax = 0;

      if (rate > 0) {
        taxable = Number((lineGross / (1 + rate)).toFixed(2));
        tax = Number((lineGross - taxable).toFixed(2));
      }

      taxableTotal += taxable;
      taxTotal += tax;

      if (!catMap[cat]) {
        catMap[cat] = { gross: 0, taxable: 0, tax: 0, rate };
      }
      catMap[cat].gross += lineGross;
      catMap[cat].taxable += taxable;
      catMap[cat].tax += tax;
    }

    return {
      totalKes: Number(grossTotal.toFixed(2)),
      subtotalKes: Number(taxableTotal.toFixed(2)),
      totalTaxKes: Number(taxTotal.toFixed(2)),
      taxBreakdown: Object.entries(catMap).map(([category, vals]) => ({
        category,
        rate: vals.rate,
        taxable: vals.taxable,
        tax: vals.tax,
        gross: vals.gross,
      })),
    };
  }, [cart]);

  const totalItems = Number(cart.reduce((acc, item) => acc + item.quantity, 0).toFixed(3));

  const handleSaleSuccess = (receipt: ReceiptDto) => {
    setShowCheckout(false);
    setActiveReceipt(receipt);
    setCart([]);
    setOverrideBy(null);
    setOverrideToken(null);
    showToast(
      `Sale completed (${receipt.paymentMethod}): KES ${Number(receipt.totalKes).toFixed(2)}`,
      'success',
      'Transaction Complete'
    );
  };

  if (!session) {
    return (
      <RoleAuthPortal
        onLoginSuccess={(u) => {
          setAuthToken(u.token ?? null);
          setSession(u);
        }}
      />
    );
  }

  const cashierProfile: CashierProfile = {
    cashierId: session.cashierId,
    username: session.username,
    name: session.name,
    role: session.role,
  };

  const roleLabel =
    session.role === 'CASHIER'
      ? 'Cashier (Till #01)'
      : session.role === 'SUPERVISOR'
      ? 'Store Manager (Oversight)'
      : 'Super Administrator';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Enterprise Navigation Bar */}
      <header className="bg-slate-900 border-b border-slate-800 px-6 py-3 flex flex-wrap items-center justify-between gap-4 shadow-md z-20">
        <div className="flex items-center gap-3">
          <div className="bg-emerald-500 text-slate-950 w-10 h-10 rounded-xl font-black flex items-center justify-center shadow-lg text-sm">
            POS
          </div>
          <div>
            <h1 className="font-bold text-lg tracking-tight text-white leading-tight">
              SuperMart Enterprise
            </h1>
            <p className="text-xs text-slate-400">Retail Ecosystem &amp; POS Suite</p>
          </div>
        </div>

        {/* Center Role-Scoped Navigation (Strict RBAC Separation) */}
        {session.role === 'CASHIER' && (
          <div className="flex items-center gap-2 bg-slate-950 px-3.5 py-1.5 rounded-xl border border-slate-800 text-xs">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span className="font-semibold text-emerald-300">Cashier POS Terminal</span>
            <span className="text-slate-600">•</span>
            <span className="text-slate-400">Scan · Tap · Pay</span>
          </div>
        )}

        {session.role === 'SUPERVISOR' && (
          <nav className="flex items-center gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800">
            <button
              onClick={() => setManagerTab('CASHIERS')}
              className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                managerTab === 'CASHIERS'
                  ? 'bg-emerald-600 text-white shadow'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              👥 Cashiers &amp; Approvals
            </button>
            <button
              onClick={() => setManagerTab('INVENTORY')}
              className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                managerTab === 'INVENTORY'
                  ? 'bg-emerald-600 text-white shadow'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              📦 Inventory &amp; GRN
            </button>
            <button
              onClick={() => setManagerTab('RETURNS')}
              className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition ${
                managerTab === 'RETURNS'
                  ? 'bg-emerald-600 text-white shadow'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              🔄 Returns &amp; Refunds
            </button>
          </nav>
        )}

        {session.role === 'SUPER_ADMIN' && (
          <div className="flex items-center gap-2 bg-slate-950 px-3.5 py-1.5 rounded-xl border border-slate-800 text-xs">
            <span className="w-2 h-2 rounded-full bg-purple-400" />
            <span className="font-semibold text-purple-300">Super Admin Control Panel</span>
            <span className="text-slate-600">•</span>
            <span className="text-slate-400">System &amp; GL Oversight</span>
          </div>
        )}

        {/* Right: System Status, Shift & Profile */}
        <div className="flex items-center gap-3">
          <div className="hidden md:flex items-center gap-2 bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                backendUp === true
                  ? 'bg-emerald-500 animate-pulse'
                  : backendUp === false
                  ? 'bg-rose-500'
                  : 'bg-amber-500'
              }`}
            />
            <span className="text-slate-300 font-medium">
              {backendUp === true ? 'API Online :8080' : 'Offline Mode'}
            </span>
          </div>

          {session.role === 'CASHIER' && (
            <div
              className="hidden md:flex items-center gap-1.5 bg-slate-950 px-3 py-1.5 rounded-lg border border-slate-800 text-xs"
              title="Local Printer & Scale Agent (localhost:9001)"
            >
              <span className={`w-2 h-2 rounded-full ${hardwareUp ? 'bg-emerald-500' : 'bg-slate-500'}`} />
              <span className="text-slate-300">{hardwareUp ? 'HW Ready' : 'HW Off'}</span>
            </div>
          )}

          {offlineCount > 0 && (
            <button
              type="button"
              onClick={handleManualOfflineSync}
              disabled={syncingOffline}
              className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-amber-500/15 text-amber-300 border border-amber-500/30 font-bold"
            >
              <span>🔄</span>
              <span>{syncingOffline ? 'Syncing...' : `Sync Offline (${offlineCount})`}</span>
            </button>
          )}

          {session.role === 'CASHIER' && availableTills.length > 1 && (
            <select
              value={selectedTillId}
              disabled={Boolean(shift)}
              onChange={(e) => handleSelectTerminalTill(e.target.value)}
              title={
                shift
                  ? 'Close active shift before switching workstation till'
                  : 'Select physical workstation till'
              }
              className="text-xs px-2.5 py-1.5 rounded-lg bg-slate-950 text-slate-200 border border-slate-700 font-semibold disabled:opacity-60"
            >
              {availableTills.map((t) => {
                const branch = availableShops.find((s) => s.id === t.shopId);
                return (
                  <option key={t.id} value={t.id}>
                    {branch ? `${branch.code} · ` : ''}
                    {t.label || t.tillNumber}
                  </option>
                );
              })}
            </select>
          )}

          {session.role === 'CASHIER' && (
            <button
              onClick={() => setShowShiftModal(true)}
              className={`flex items-center gap-2 text-xs px-3 py-1.5 rounded-lg border font-semibold transition ${
                shift
                  ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30 hover:bg-emerald-500/20'
                  : 'bg-amber-500/15 text-amber-300 border-amber-500/30 hover:bg-amber-500/25'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${shift ? 'bg-emerald-400' : 'bg-amber-400'}`} />
              <span>{shift ? 'Shift Open' : 'Open Shift'}</span>
            </button>
          )}

          <div className="flex items-center gap-3 border-l border-slate-800 pl-3">
            <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center font-bold text-white text-xs shadow">
              {getInitials(session.name)}
            </div>
            <div className="hidden sm:block text-left">
              <p className="text-xs font-semibold text-white leading-tight">{session.name}</p>
              <p className="text-[11px] text-slate-400">{roleLabel}</p>
            </div>
            <button
              onClick={() => {
                void apiFetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
                setAuthToken(null);
                setSession(null);
                setCart([]);
                setOverrideBy(null);
                setOverrideToken(null);
              }}
              className="ml-1 px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-rose-500/20 text-slate-300 hover:text-rose-300 border border-slate-700 text-xs font-semibold transition"
              title="Sign Out / Switch Role"
            >
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Main Role-Scoped Workspace */}
      <main className="flex-1 max-w-[1600px] w-full mx-auto p-4 sm:p-6">
        {session.role === 'CASHIER' && (
          <CashierPosTerminal
            cart={cart}
            setCart={setCart}
            shift={shift}
            loading={loading}
            error={error}
            setError={setError}
            lastScanned={lastScanned}
            subtotalKes={subtotalKes}
            totalTaxKes={totalTaxKes}
            totalKes={totalKes}
            totalItems={totalItems}
            taxBreakdown={taxBreakdown}
            onScanBarcode={handleScan}
            onSelectProduct={handleSelectProduct}
            onUpdateQuantity={updateQuantity}
            onRemoveItem={removeItem}
            onClearCart={clearCart}
            onApplyScaleWeight={handleApplyScaleWeight}
            onOpenPayment={() => setShowCheckout(true)}
            onOpenShiftModal={() => setShowShiftModal(true)}
            onOverrideAuthorized={(supId, token) => {
              setOverrideBy(supId);
              setOverrideToken(token ?? null);
            }}
            authToken={session.token}
          />
        )}

        {session.role === 'SUPERVISOR' && (
          <div>
            <ManagerDashboardHeader
              activeTab={managerTab}
              onSelectTab={setManagerTab}
              authToken={session.token}
            />
            {managerTab === 'CASHIERS' && <CashierManagementView />}
            {managerTab === 'INVENTORY' && <ManagerInventoryView />}
            {managerTab === 'RETURNS' && (
              <ReturnsView cashierId={session.cashierId} cashierName={session.name} />
            )}
          </div>
        )}

        {session.role === 'SUPER_ADMIN' && (
          <SuperAdminView authToken={session.token} />
        )}
      </main>

      {/* Cashier Shift Modal */}
      {showShiftModal && (
        <CashierShiftModal
          currentCashier={cashierProfile}
          currentShift={shift}
          tillId={selectedTillId}
          onLoginSuccess={() => {}}
          onOpenShiftSuccess={(s) => {
            setShift(s);
            setShowShiftModal(false);
            showToast('Till shift opened. Ready for checkout.', 'success');
          }}
          onCloseShiftSuccess={() => {
            setShift(null);
            setShowShiftModal(false);
            showToast('Till shift closed and reconciled.', 'info');
          }}
          onCancel={() => setShowShiftModal(false)}
        />
      )}

      {/* Checkout Modal */}
      {showCheckout && (
        <CheckoutModal
          cart={cart}
          totalKes={totalKes}
          shopId={selectedShopId}
          tillId={selectedTillId}
          cashierId={session.cashierId}
          cashierName={session.name}
          authToken={session.token}
          overrideBy={overrideBy}
          overrideToken={overrideToken}
          onSuccess={handleSaleSuccess}
          onOfflineQueued={() => {
            setOfflineCount(getOfflineQueue().length);
            showToast('Sale saved offline. Will auto-sync when API reconnects.', 'info');
          }}
          onCancel={() => setShowCheckout(false)}
        />
      )}

      {/* Thermal Receipt Print Preview Modal */}
      {activeReceipt && (
        <ReceiptModal receipt={activeReceipt} onClose={() => setActiveReceipt(null)} />
      )}
    </div>
  );
}

export default function App() {
  if (typeof window !== 'undefined' && window.location.search.includes('display=1')) {
    return <CustomerPoleDisplay />;
  }
  return (
    <ToastProvider>
      <AppContent />
    </ToastProvider>
  );
}
