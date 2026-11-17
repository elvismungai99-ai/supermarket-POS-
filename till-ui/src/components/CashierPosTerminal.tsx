import { useEffect, useState } from 'react';
import type { ProductDto } from '../App';
import { BarcodeInput } from './BarcodeInput';
import { ProductSearchSelector } from './ProductSearchSelector';
import type { CartItem } from './CheckoutModal';
import type { ShiftInfo } from './CashierShiftModal';
import { apiFetch } from '../utils/apiClient';
import { cacheCatalogProducts } from '../utils/offlineQueue';
import { useToast } from '../utils/toast';
import { playPosBeep } from '../utils/sound';

interface HeldBasket {
  id: string;
  heldAt: string;
  items: CartItem[];
  totalKes: number;
}

interface TaxBreakdownSummary {
  category: string;
  rate: number;
  taxable: number;
  tax: number;
  gross: number;
}

interface CashierPosTerminalProps {
  cart: CartItem[];
  setCart: React.Dispatch<React.SetStateAction<CartItem[]>>;
  shift: ShiftInfo | null;
  loading: boolean;
  error: string | null;
  setError: (err: string | null) => void;
  lastScanned: string | null;
  subtotalKes: number;
  totalTaxKes: number;
  totalKes: number;
  totalItems: number;
  taxBreakdown: TaxBreakdownSummary[];
  onScanBarcode: (barcode: string) => void;
  onSelectProduct: (product: ProductDto) => void;
  onUpdateQuantity: (id: string, delta: number) => void;
  onRemoveItem: (id: string) => void;
  onClearCart: () => void;
  onApplyScaleWeight: (productId: string) => void;
  onOpenPayment: () => void;
  onOpenShiftModal: () => void;
  onOverrideAuthorized: (supervisorId: string, overrideToken?: string | null) => void;
  authToken?: string;
}

const HELD_STORAGE_KEY = 'supermart_held_baskets_v1';

export function CashierPosTerminal({
  cart,
  setCart,
  shift,
  loading,
  error,
  setError,
  lastScanned,
  subtotalKes,
  totalTaxKes,
  totalKes,
  totalItems,
  taxBreakdown,
  onScanBarcode,
  onSelectProduct,
  onUpdateQuantity,
  onRemoveItem,
  onClearCart,
  onApplyScaleWeight,
  onOpenPayment,
  onOpenShiftModal,
  onOverrideAuthorized,
  authToken,
}: CashierPosTerminalProps) {
  const { showToast } = useToast();
  const [tillInputMode, setTillInputMode] = useState<'SCAN' | 'SEARCH_NAME'>('SCAN');
  const [quickProducts, setQuickProducts] = useState<ProductDto[]>([]);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [keypadBuffer, setKeypadBuffer] = useState<string>('');
  const [showHeldDrawer, setShowHeldDrawer] = useState(false);
  const [overrideModal, setOverrideModal] = useState<{
    action: 'DISCOUNT' | 'VOID';
    amountKes?: number;
  } | null>(null);
  const [supUsername, setSupUsername] = useState('');
  const [supPin, setSupPin] = useState('');
  const [supLoading, setSupLoading] = useState(false);
  const [supError, setSupError] = useState<string | null>(null);
  const [heldBaskets, setHeldBaskets] = useState<HeldBasket[]>(() => {
    try {
      const raw = localStorage.getItem(HELD_STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });

  // Persist held baskets
  useEffect(() => {
    try {
      localStorage.setItem(HELD_STORAGE_KEY, JSON.stringify(heldBaskets));
    } catch {
      // ignore storage errors
    }
  }, [heldBaskets]);

  // Keep a default selected cart line when items are added/removed
  useEffect(() => {
    if (cart.length === 0) {
      setSelectedItemId(null);
      return;
    }
    if (!selectedItemId || !cart.some((i) => i.id === selectedItemId)) {
      setSelectedItemId(cart[cart.length - 1].id);
    }
  }, [cart, selectedItemId]);

  // Load live quick-tap catalog & cache full catalog to IndexedDB for offline scanning
  useEffect(() => {
    const loadCatalog = async () => {
      try {
        const res = await apiFetch('/api/products');
        if (res.ok) {
          const all: ProductDto[] = await res.json();
          setQuickProducts(all.slice(0, 6));
          cacheCatalogProducts(all);
        }
      } catch {
        // leave empty if offline
      }
    };
    loadCatalog();
  }, [authToken]);

  // Broadcast live cart updates to Customer Pole Display (?display=1)
  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const bc = new BroadcastChannel('supermart_customer_display');
    bc.postMessage({ cart, totalKes, subtotalKes, totalTaxKes });
    return () => bc.close();
  }, [cart, totalKes, subtotalKes, totalTaxKes]);

  // Global till keyboard shortcuts: F1 Search, F2 Hold, F4 Checkout
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'F1') {
        e.preventDefault();
        setTillInputMode((m) => (m === 'SCAN' ? 'SEARCH_NAME' : 'SCAN'));
      } else if (e.key === 'F2') {
        e.preventDefault();
        handleHoldCart();
      } else if (e.key === 'F4') {
        e.preventDefault();
        if (shift && cart.length > 0) onOpenPayment();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const selectedItem = cart.find((i) => i.id === selectedItemId) || cart[cart.length - 1] || null;

  const handleHoldCart = () => {
    if (cart.length === 0) {
      showToast('Cart is empty — nothing to place on hold.', 'error');
      return;
    }
    const basket: HeldBasket = {
      id: `HOLD-${Date.now().toString().slice(-4)}`,
      heldAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      items: cart,
      totalKes,
    };
    setHeldBaskets((prev) => [basket, ...prev]);
    onClearCart();
    setKeypadBuffer('');
    showToast(`Basket ${basket.id} (${basket.items.length} lines) placed on hold.`, 'info', 'Till Held');
  };

  const handleResumeHeld = (basket: HeldBasket) => {
    if (cart.length > 0) {
      showToast('Clear or hold the current cart before resuming a held basket.', 'error');
      return;
    }
    setCart(basket.items);
    setHeldBaskets((prev) => prev.filter((b) => b.id !== basket.id));
    setShowHeldDrawer(false);
    playPosBeep();
    showToast(`Resumed basket ${basket.id} (KES ${basket.totalKes.toFixed(2)}).`, 'success');
  };

  const handleAppendKey = (val: string) => {
    if (val === '.' && keypadBuffer.includes('.')) return;
    setKeypadBuffer((prev) => (prev.length >= 8 ? prev : prev + val));
  };

  const handleKeypadBackspace = () => {
    setKeypadBuffer((prev) => prev.slice(0, -1));
  };

  const handleApplyQtyMacro = () => {
    if (!selectedItem) {
      showToast('Select a cart item first to adjust quantity.', 'error');
      return;
    }
    const parsed = parseFloat(keypadBuffer);
    if (!parsed || parsed <= 0) {
      showToast('Type a positive quantity on the keypad, then press QTY.', 'info');
      return;
    }
    setCart((prev) =>
      prev.map((item) => (item.id === selectedItem.id ? { ...item, quantity: parsed } : item))
    );
    playPosBeep();
    showToast(`Updated "${selectedItem.name}" quantity to ${parsed}.`, 'success');
    setKeypadBuffer('');
  };

  const handleVoidMacro = () => {
    if (!selectedItem) {
      showToast('No item selected to void.', 'error');
      return;
    }
    setSupError(null);
    setSupUsername('');
    setSupPin('');
    setOverrideModal({ action: 'VOID' });
  };

  const handleDiscountMacro = () => {
    if (!selectedItem) {
      showToast('Select a cart line first, enter KES discount on keypad, then press DISC.', 'error');
      return;
    }
    const amt = parseFloat(keypadBuffer);
    if (!amt || amt <= 0) {
      showToast('Enter discount amount (KES) on keypad first, then press DISC.', 'info');
      return;
    }
    setSupError(null);
    setSupUsername('');
    setSupPin('');
    setOverrideModal({ action: 'DISCOUNT', amountKes: amt });
  };

  const handleVerifyOverride = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!overrideModal || !selectedItem) return;
    setSupLoading(true);
    setSupError(null);
    try {
      const reason =
        overrideModal.action === 'VOID'
          ? `Void line ${selectedItem.name}`
          : `Discount KES ${overrideModal.amountKes} on ${selectedItem.name}`;
      const res = await apiFetch('/api/auth/verify-supervisor-override', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: supUsername.trim(),
          pin: supPin.trim(),
          reason,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.message || 'Override authorization failed');
      }

      onOverrideAuthorized(data.supervisorId, data.overrideToken ?? null);
      if (overrideModal.action === 'VOID') {
        onRemoveItem(selectedItem.id);
        showToast(`Line void authorized by ${data.supervisorName}.`, 'info');
      } else if (overrideModal.action === 'DISCOUNT' && overrideModal.amountKes) {
        const disc = Math.min(overrideModal.amountKes, selectedItem.priceKes * selectedItem.quantity);
        setCart((prev) =>
          prev.map((i) => (i.id === selectedItem.id ? { ...i, discountKes: disc } : i))
        );
        showToast(`KES ${disc.toFixed(2)} discount authorized by ${data.supervisorName}.`, 'success');
      }
      setKeypadBuffer('');
      setOverrideModal(null);
    } catch (err: any) {
      setSupError(err.message || 'Supervisor PIN rejected');
    } finally {
      setSupLoading(false);
    }
  };

  const handleScaleMacro = () => {
    if (!selectedItem) {
      showToast('Select a weighed item in the cart first.', 'error');
      return;
    }
    onApplyScaleWeight(selectedItem.id);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-4 h-full min-h-[calc(100vh-8.5rem)]">
      {/* LEFT PANEL: Cart & Itemized Bill */}
      <div className="flex-1 flex flex-col bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
        {/* POS Header Bar */}
        <div className="bg-slate-800/80 px-5 py-3 border-b border-slate-700 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 flex-wrap">
            <span className="bg-indigo-600/20 text-indigo-300 font-semibold px-2.5 py-1 rounded-lg text-xs border border-indigo-500/30">
              Till #01
            </span>
            <div className="flex items-center bg-slate-950 p-0.5 rounded-lg border border-slate-700">
              <button
                type="button"
                onClick={() => setTillInputMode('SCAN')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition ${
                  tillInputMode === 'SCAN'
                    ? 'bg-emerald-600 text-white shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Scan Barcode
              </button>
              <button
                type="button"
                onClick={() => setTillInputMode('SEARCH_NAME')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition ${
                  tillInputMode === 'SEARCH_NAME'
                    ? 'bg-indigo-600 text-white shadow'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Search by Name
              </button>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <button
              type="button"
              onClick={() => window.open('?display=1', 'CustomerPoleDisplay', 'width=920,height=640')}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold rounded-lg border border-slate-700 transition"
              title="Open secondary customer-facing screen"
            >
              📺 Customer Screen
            </button>
            {heldBaskets.length > 0 && (
              <button
                type="button"
                onClick={() => setShowHeldDrawer((v) => !v)}
                className="px-3 py-1.5 bg-indigo-500/15 hover:bg-indigo-500/25 text-indigo-300 text-xs font-semibold rounded-lg border border-indigo-500/30 transition"
              >
                Held ({heldBaskets.length})
              </button>
            )}
            <button
              type="button"
              onClick={handleHoldCart}
              className="px-3 py-1.5 bg-amber-500/10 hover:bg-amber-500/20 text-amber-300 text-xs font-semibold rounded-lg border border-amber-500/25 transition"
            >
              Hold Till
            </button>
            <button
              type="button"
              onClick={() => {
                onClearCart();
                setKeypadBuffer('');
                if (cart.length > 0) showToast('Cart cleared', 'info');
              }}
              className="px-3 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 text-xs font-semibold rounded-lg border border-rose-500/25 transition"
            >
              Clear Cart
            </button>
          </div>
        </div>

        {/* Scanner / Search Bar Area */}
        <div className="px-5 py-3.5 bg-slate-900/90 border-b border-slate-800 space-y-2.5">
          {tillInputMode === 'SCAN' ? (
            <>
              <BarcodeInput onScan={onScanBarcode} isLoading={loading} />
              {lastScanned && !error && (
                <p className="text-[11px] text-slate-400 font-mono">
                  Last scanned/selected: <span className="text-emerald-400 font-semibold">{lastScanned}</span>
                </p>
              )}
            </>
          ) : (
            <ProductSearchSelector
              onSelectProduct={(p) => {
                onSelectProduct(p);
                showToast(`Added ${p.name}`, 'success');
              }}
            />
          )}

          {error && (
            <div className="px-3.5 py-2 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs flex items-center justify-between">
              <span>{error}</span>
              <button
                type="button"
                onClick={() => setError(null)}
                className="font-bold text-rose-200 hover:text-white ml-3"
              >
                &times;
              </button>
            </div>
          )}

          {/* Held Baskets Popover Bar */}
          {showHeldDrawer && heldBaskets.length > 0 && (
            <div className="p-3 rounded-xl bg-slate-950 border border-indigo-500/30 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-indigo-300">Held Customer Baskets</span>
                <button
                  type="button"
                  onClick={() => setShowHeldDrawer(false)}
                  className="text-slate-400 hover:text-white"
                >
                  Close
                </button>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {heldBaskets.map((b) => (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => handleResumeHeld(b)}
                    className="p-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-left flex items-center justify-between transition"
                  >
                    <div>
                      <p className="text-xs font-bold text-white">
                        {b.id} • {b.items.length} items
                      </p>
                      <p className="text-[11px] text-slate-400">Held at {b.heldAt}</p>
                    </div>
                    <span className="text-xs font-mono font-bold text-emerald-400">
                      KES {b.totalKes.toFixed(2)}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Cart Items Table */}
        <div className="flex-1 overflow-y-auto min-h-[240px]">
          {cart.length === 0 ? (
            <div className="h-full min-h-[240px] flex flex-col items-center justify-center text-slate-500 text-sm px-6 text-center">
              <span className="text-4xl mb-3 opacity-80">🛒</span>
              <p className="font-semibold text-slate-300">Ready for next customer</p>
              <p className="text-xs text-slate-500 mt-1">
                Scan a product barcode, search by item name, or tap a quick-tap product on the right.
              </p>
            </div>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead className="bg-slate-800/50 sticky top-0 text-[11px] uppercase text-slate-400 tracking-wider border-b border-slate-800 z-10">
                <tr>
                  <th className="py-3 px-4">Item Description</th>
                  <th className="py-3 px-4 text-center">Qty</th>
                  <th className="py-3 px-4 text-right">Unit Price</th>
                  <th className="py-3 px-4 text-right">Total</th>
                  <th className="py-3 px-4 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-sm">
                {cart.map((item) => {
                  const isSelected = selectedItem?.id === item.id;
                  return (
                    <tr
                      key={item.id}
                      onClick={() => setSelectedItemId(item.id)}
                      className={`cursor-pointer transition ${
                        isSelected
                          ? 'bg-emerald-500/10 ring-1 ring-inset ring-emerald-500/30'
                          : 'hover:bg-slate-800/40'
                      }`}
                    >
                      <td className="py-3 px-4">
                        <p className="font-semibold text-white">{item.name}</p>
                        <div className="flex items-center gap-2 mt-0.5">
                          {item.barcode && (
                            <span className="font-mono text-[11px] text-slate-400">{item.barcode}</span>
                          )}
                          <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-slate-800 text-slate-300 border border-slate-700">
                            Cat {item.taxCategory}
                          </span>
                        </div>
                      </td>
                      <td className="py-3 px-4 text-center" onClick={(e) => e.stopPropagation()}>
                        <div className="inline-flex items-center gap-1.5">
                          <div className="inline-flex items-center gap-1 bg-slate-950 px-1.5 py-1 rounded-lg border border-slate-800">
                            <button
                              type="button"
                              onClick={() => onUpdateQuantity(item.id, -1)}
                              className="text-slate-400 hover:text-white px-1.5 font-bold"
                            >
                              -
                            </button>
                            <span className="text-white font-mono font-bold w-8 text-center text-xs">
                              {item.quantity}
                            </span>
                            <button
                              type="button"
                              onClick={() => onUpdateQuantity(item.id, 1)}
                              className="text-slate-400 hover:text-white px-1.5 font-bold"
                            >
                              +
                            </button>
                          </div>
                          <button
                            type="button"
                            onClick={() => onApplyScaleWeight(item.id)}
                            className="px-2 py-1 text-xs rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 transition"
                            title="Read weight (kg) from scale (:9001)"
                          >
                            ⚖️
                          </button>
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-slate-300">
                        KES {item.priceKes.toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right font-mono font-bold text-emerald-400">
                        KES {Math.max(0, item.priceKes * item.quantity - (item.discountKes || 0)).toFixed(2)}
                        {(item.discountKes || 0) > 0 && (
                          <span className="block text-[10px] text-amber-400 font-normal">
                            -KES {Number(item.discountKes).toFixed(2)} disc
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-center" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={() => onRemoveItem(item.id)}
                          className="text-rose-400 hover:text-rose-300 px-2 py-1 font-bold"
                          title="Remove item"
                        >
                          &times;
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Cart Summary Footer */}
        <div className="bg-slate-800/90 border-t border-slate-700 p-4 space-y-2">
          <div className="flex justify-between text-xs text-slate-400">
            <span>Items Count</span>
            <span className="font-mono font-semibold text-white">{totalItems}</span>
          </div>
          <div className="flex justify-between text-xs text-slate-400">
            <span>Subtotal (Excl. VAT)</span>
            <span className="font-mono font-semibold text-white">KES {subtotalKes.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-xs text-slate-400">
            <span>Total VAT / Tax</span>
            <span className="font-mono font-semibold text-white">KES {totalTaxKes.toFixed(2)}</span>
          </div>
          {taxBreakdown.length > 0 && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {taxBreakdown.map((tb) => (
                <span
                  key={tb.category}
                  className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-900 border border-slate-700 text-slate-300"
                >
                  Cat {tb.category} ({(tb.rate * 100).toFixed(0)}%): VAT {tb.tax.toFixed(2)}
                </span>
              ))}
            </div>
          )}
          <div className="flex justify-between items-baseline text-lg font-bold text-white border-t border-slate-700 pt-2">
            <span>Grand Total Due</span>
            <span className="text-emerald-400 text-2xl font-black font-mono">
              KES {totalKes.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
          </div>
        </div>
      </div>

      {/* RIGHT PANEL: Quick Tap Products + Keypad + Checkout CTA */}
      <div className="w-full lg:w-96 flex flex-col gap-4 shrink-0">
        {/* Live Quick Tap Products Grid */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <div className="flex items-center justify-between mb-3">
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Quick Tap Products (Live Catalog)
            </p>
            <span className="text-[10px] text-slate-500 font-mono">{quickProducts.length} items</span>
          </div>
          {quickProducts.length === 0 ? (
            <p className="text-xs text-slate-500 py-4 text-center">
              Loading store catalog...
            </p>
          ) : (
            <div className="grid grid-cols-3 gap-2">
              {quickProducts.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => {
                    onSelectProduct(p);
                    showToast(`Added ${p.name}`, 'success');
                  }}
                  className="bg-slate-800 hover:bg-slate-700 border border-slate-700 p-2.5 rounded-xl text-left transition flex flex-col justify-between h-20 active:scale-[0.97]"
                >
                  <span className="text-[11px] font-semibold text-white line-clamp-2 leading-tight">
                    {p.name}
                  </span>
                  <span className="text-xs font-bold font-mono text-emerald-400">
                    {Number(p.priceKes).toFixed(2)}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Keypad & Macro Action Panel */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl flex-1 flex flex-col justify-between gap-4">
          <div className="space-y-3">
            {/* Live Keypad Buffer Display */}
            <div className="bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 flex items-center justify-between">
              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">
                  Selected Line
                </p>
                <p className="text-xs font-medium text-slate-200 truncate">
                  {selectedItem ? selectedItem.name : 'None'}
                </p>
              </div>
              <div className="text-right shrink-0 ml-3">
                <p className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">
                  Keypad Input
                </p>
                <p className="text-base font-mono font-bold text-emerald-400">
                  {keypadBuffer || '0'}
                </p>
              </div>
            </div>

            <div className="grid grid-cols-4 gap-2">
              {['7', '8', '9'].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => handleAppendKey(d)}
                  className="bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-xl border border-slate-700 text-lg transition active:scale-95"
                >
                  {d}
                </button>
              ))}
              <button
                type="button"
                onClick={handleApplyQtyMacro}
                className="bg-indigo-600/20 hover:bg-indigo-600/35 text-indigo-300 font-bold py-2 rounded-xl border border-indigo-500/30 text-xs transition"
                title="Set selected item quantity to keypad value"
              >
                QTY
              </button>

              {['4', '5', '6'].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => handleAppendKey(d)}
                  className="bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-xl border border-slate-700 text-lg transition active:scale-95"
                >
                  {d}
                </button>
              ))}
              <button
                type="button"
                onClick={handleScaleMacro}
                className="bg-amber-600/20 hover:bg-amber-600/35 text-amber-300 font-bold py-2 rounded-xl border border-amber-500/30 text-xs transition"
                title="Read scale weight (kg) into selected line"
              >
                SCALE
              </button>

              {['1', '2', '3'].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => handleAppendKey(d)}
                  className="bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-xl border border-slate-700 text-lg transition active:scale-95"
                >
                  {d}
                </button>
              ))}
              <button
                type="button"
                onClick={handleVoidMacro}
                className="bg-rose-600/20 hover:bg-rose-600/35 text-rose-300 font-bold py-2 rounded-xl border border-rose-500/30 text-xs transition"
                title="Remove selected item from cart"
              >
                VOID
              </button>

              <button
                type="button"
                onClick={() => handleAppendKey('0')}
                className="bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-xl border border-slate-700 text-lg transition col-span-2 active:scale-95"
              >
                0
              </button>
              <button
                type="button"
                onClick={() => handleAppendKey('.')}
                className="bg-slate-800 hover:bg-slate-700 text-white font-bold py-3 rounded-xl border border-slate-700 text-lg transition active:scale-95"
              >
                .
              </button>
              <button
                type="button"
                onClick={handleKeypadBackspace}
                className="bg-slate-800 hover:bg-slate-700 text-slate-300 font-bold py-2 rounded-xl border border-slate-700 text-xs transition"
                title="Backspace keypad input"
              >
                ⌫ CLR
              </button>
              <button
                type="button"
                onClick={handleDiscountMacro}
                className="col-span-4 bg-purple-600/20 hover:bg-purple-600/35 text-purple-300 font-bold py-2 rounded-xl border border-purple-500/30 text-xs transition"
                title="Apply keypad KES discount to selected line (requires Manager PIN)"
              >
                DISC (Manager PIN Override)
              </button>
            </div>
          </div>

          {/* Checkout / Open Shift Primary CTA */}
          {!shift ? (
            <button
              type="button"
              onClick={onOpenShiftModal}
              className="w-full bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-slate-950 font-black py-4 px-6 rounded-2xl shadow-xl flex items-center justify-center gap-2 transition transform active:scale-[0.98]"
            >
              <span>🔐</span>
              <span className="text-base">OPEN TILL SHIFT TO SELL</span>
            </button>
          ) : (
            <button
              type="button"
              disabled={cart.length === 0}
              onClick={onOpenPayment}
              className="w-full bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 disabled:opacity-40 disabled:cursor-not-allowed text-slate-950 font-black py-4 px-6 rounded-2xl shadow-xl flex items-center justify-center gap-2.5 transition transform active:scale-[0.98]"
            >
              <span>💳</span>
              <span className="text-lg">PROCESS PAYMENT (F4)</span>
            </button>
          )}
        </div>
      </div>

      {/* Supervisor PIN Override Modal */}
      {overrideModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-3xl max-w-sm w-full overflow-hidden shadow-2xl">
            <div className="bg-amber-600 text-slate-950 px-5 py-3.5 flex items-center justify-between font-bold">
              <span>🔐 Manager Override Required</span>
              <button type="button" onClick={() => setOverrideModal(null)} className="text-xl leading-none">
                &times;
              </button>
            </div>
            <form onSubmit={handleVerifyOverride} className="p-5 space-y-3 text-xs">
              <p className="text-slate-300">
                Action:{' '}
                <strong className="text-white">
                  {overrideModal.action === 'VOID'
                    ? `Void "${selectedItem?.name}"`
                    : `Apply KES ${overrideModal.amountKes?.toFixed(2)} discount to "${selectedItem?.name}"`}
                </strong>
              </p>
              {supError && (
                <div className="p-2.5 rounded-lg bg-rose-500/15 border border-rose-500/30 text-rose-300">
                  {supError}
                </div>
              )}
              <div>
                <label className="block font-semibold text-slate-400 mb-1">Manager / Admin Username</label>
                <input
                  type="text"
                  required
                  autoFocus
                  value={supUsername}
                  onChange={(e) => setSupUsername(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white"
                />
              </div>
              <div>
                <label className="block font-semibold text-slate-400 mb-1">Supervisor PIN</label>
                <input
                  type="password"
                  required
                  value={supPin}
                  onChange={(e) => setSupPin(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono"
                />
              </div>
              <button
                type="submit"
                disabled={supLoading}
                className="w-full py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl transition"
              >
                {supLoading ? 'Verifying...' : 'Authorize Override'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
