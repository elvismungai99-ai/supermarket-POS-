import { useState, useMemo } from 'react';
import type { ReceiptDto } from './ReceiptModal';
import { apiFetch } from '../utils/apiClient';
import { enqueueOfflineSale, generateUuid } from '../utils/offlineQueue';

export interface CartItem {
  id: string;
  barcode: string;
  name: string;
  priceKes: number;
  quantity: number;
  taxCategory: string;
  discountKes?: number;
}

export function CheckoutModal({
  cart,
  totalKes,
  tillId,
  cashierId,
  cashierName,
  authToken,
  overrideBy,
  overrideToken,
  onSuccess,
  onOfflineQueued,
  onCancel,
}: {
  cart: CartItem[];
  totalKes: number;
  tillId: string;
  cashierId: string;
  cashierName?: string;
  authToken?: string;
  overrideBy?: string | null;
  overrideToken?: string | null;
  onSuccess: (receipt: ReceiptDto) => void;
  onOfflineQueued?: () => void;
  onCancel: () => void;
}) {
  const [paymentMethod, setPaymentMethod] = useState<'CASH' | 'MPESA' | 'BANK' | 'SPLIT'>('CASH');
  const [amountTendered, setAmountTendered] = useState<string>(totalKes.toFixed(2));
  const [splitCashAmount, setSplitCashAmount] = useState<string>((totalKes / 2).toFixed(2));
  const [phone, setPhone] = useState('');
  const [bankRef, setBankRef] = useState('');
  const [customerName, setCustomerName] = useState<string | null>(null);
  const [pointsBalance, setPointsBalance] = useState<number | null>(null);
  const [pointsToRedeem, setPointsToRedeem] = useState<string>('0');
  const [loadingCustomer, setLoadingCustomer] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [stkStatus, setStkStatus] = useState<string | null>(null);

  // Check customer loyalty by phone
  const handleLookupPhone = async (num: string) => {
    setPhone(num);
    if (num.trim().length >= 10) {
      setLoadingCustomer(true);
      try {
        const res = await apiFetch(`/api/customers/by-phone/${encodeURIComponent(num.trim())}`);
        if (res.ok) {
          const data = await res.json();
          setCustomerName(data.name);
          setPointsBalance(Number(data.pointsBalance));
        } else {
          setCustomerName(null);
          setPointsBalance(null);
        }
      } catch {
        setCustomerName(null);
        setPointsBalance(null);
      } finally {
        setLoadingCustomer(false);
      }
    }
  };

  const ptsRedeemNum = parseFloat(pointsToRedeem) || 0;
  const payableAfterPoints = Math.max(0, totalKes - ptsRedeemNum);

  const tenderedNum = parseFloat(amountTendered) || 0;
  const changeDue = useMemo(() => {
    return Math.max(0, tenderedNum - payableAfterPoints);
  }, [tenderedNum, payableAfterPoints]);

  const splitCashNum = Math.min(payableAfterPoints, Math.max(0, parseFloat(splitCashAmount) || 0));
  const splitMpesaNum = Number(Math.max(0, payableAfterPoints - splitCashNum).toFixed(2));

  const isUnderpaid = paymentMethod === 'CASH' && tenderedNum < payableAfterPoints;

  const handleQuickAdd = (add: number) => {
    setAmountTendered((prev) => {
      const current = parseFloat(prev) || 0;
      return (current + add).toFixed(2);
    });
  };

  const handleSetExact = () => {
    setAmountTendered(payableAfterPoints.toFixed(2));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (paymentMethod === 'CASH' && isUnderpaid) {
      setError(`Amount tendered must be at least KES ${payableAfterPoints.toFixed(2)}`);
      return;
    }

    if ((paymentMethod === 'MPESA' || (paymentMethod === 'SPLIT' && splitMpesaNum > 0)) && (!phone || phone.trim().length < 9)) {
      setError('A valid customer phone number is required for M-Pesa STK push');
      return;
    }

    setLoading(true);
    setError(null);

    if (paymentMethod === 'MPESA' || (paymentMethod === 'SPLIT' && splitMpesaNum > 0)) {
      setStkStatus('STK Push sent to customer phone. Awaiting M-Pesa PIN prompt entry...');
    }

    const clientSaleId = generateUuid();
    const baseItems = cart.map((item) => ({
      productId: item.id,
      quantity: item.quantity,
      unitPriceKes: item.priceKes,
      discountKes: item.discountKes || 0,
    }));

    const payload =
      paymentMethod === 'SPLIT'
        ? {
            shopId: '00000000-0000-0000-0000-000000000001',
            tillId,
            cashierId,
            clientSaleId,
            overrideBy: overrideBy || null,
            overrideToken: overrideToken || null,
            items: baseItems,
            payments: [
              ...(splitCashNum > 0
                ? [
                    {
                      method: 'CASH',
                      amountTenderedKes: splitCashNum,
                      customerPhone: phone ? phone.trim() : null,
                      pointsToRedeem: ptsRedeemNum > 0 ? ptsRedeemNum : null,
                    },
                  ]
                : []),
              ...(splitMpesaNum > 0
                ? [
                    {
                      method: 'MPESA',
                      amountTenderedKes: splitMpesaNum,
                      customerPhone: phone.trim(),
                      pointsToRedeem: splitCashNum === 0 && ptsRedeemNum > 0 ? ptsRedeemNum : null,
                    },
                  ]
                : []),
            ],
          }
        : {
            shopId: '00000000-0000-0000-0000-000000000001',
            tillId,
            cashierId,
            clientSaleId,
            overrideBy: overrideBy || null,
            overrideToken: overrideToken || null,
            items: baseItems,
            payment: {
              method: paymentMethod,
              amountTenderedKes: paymentMethod === 'CASH' ? tenderedNum : payableAfterPoints,
              customerPhone: phone ? phone.trim() : null,
              pointsToRedeem: ptsRedeemNum > 0 ? ptsRedeemNum : null,
              bankReference: paymentMethod === 'BANK' && bankRef.trim() ? bankRef.trim() : null,
            },
          };

    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

      const res = await apiFetch('/api/sales', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      });

      if (res.status === 502 || res.status === 503 || res.status === 504) {
        if (paymentMethod === 'CASH') {
          throw new Error('__OFFLINE_FALLBACK__');
        }
      }

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || `Sale failed with status ${res.status}`);
      }

      const receipt: ReceiptDto = await res.json();
      onSuccess(receipt);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err || '');
      const name = err instanceof Error ? err.name : '';
      const isNetworkFailure =
        msg === '__OFFLINE_FALLBACK__' ||
        name === 'TypeError' ||
        msg.toLowerCase().includes('fetch');

      if (isNetworkFailure && paymentMethod === 'CASH') {
        enqueueOfflineSale({
          clientSaleId,
          queuedAt: new Date().toISOString(),
          payload,
          totalKes: payableAfterPoints,
        });
        onOfflineQueued?.();
        const nowIso = new Date().toISOString();
        const offlineReceipt: ReceiptDto = {
          saleId: clientSaleId,
          receiptNumber: `OFFLINE-${clientSaleId.slice(0, 8).toUpperCase()}`,
          shopId: payload.shopId,
          tillId,
          cashierId,
          cashierName: cashierName || 'Cashier',
          createdAt: nowIso,
          lines: cart.map((item) => ({
            productId: item.id,
            name: item.name,
            quantity: item.quantity,
            unitPriceKes: item.priceKes,
            lineTotalKes: Number((item.priceKes * item.quantity).toFixed(2)),
            taxCategory: item.taxCategory,
            taxRate: item.taxCategory === 'A' ? 0.16 : item.taxCategory === 'C' ? 0.08 : 0,
            taxAmountKes: 0,
          })),
          subtotalKes: payableAfterPoints,
          totalTaxKes: 0,
          totalKes: payableAfterPoints,
          taxBreakdowns: [],
          amountTenderedKes: tenderedNum,
          changeKes: changeDue,
          paymentMethod: 'CASH',
          etimsInvoiceStatus: 'QUEUED_OFFLINE_LOCAL',
          paymentReference: 'OFFLINE_QUEUED',
        };
        onSuccess(offlineReceipt);
        return;
      }

      setError(msg || 'Payment processing failed');
      setStkStatus(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 rounded-3xl shadow-2xl max-w-lg w-full overflow-hidden border border-zinc-200 dark:border-zinc-800">
        <div className="bg-emerald-600 text-white px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-xl">💳</span>
            <h3 className="font-bold text-lg">Checkout Settlement</h3>
          </div>
          <button onClick={onCancel} className="text-white/80 hover:text-white font-bold text-2xl leading-none">
            &times;
          </button>
        </div>

        {error && (
          <div className="mx-6 mt-4 p-3 rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 text-red-700 dark:text-red-300 text-xs">
            {error}
          </div>
        )}

        {stkStatus && (
          <div className="mx-6 mt-4 p-3 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-300 text-xs flex items-center gap-2 animate-pulse">
            <span>📲</span>
            <span>{stkStatus}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Payment Method Selector */}
          <div className="grid grid-cols-4 gap-1.5 p-1 bg-zinc-100 dark:bg-zinc-800 rounded-xl">
            <button
              type="button"
              onClick={() => {
                setPaymentMethod('CASH');
                setAmountTendered(payableAfterPoints.toFixed(2));
              }}
              className={`py-2 text-xs font-bold rounded-lg transition ${
                paymentMethod === 'CASH'
                  ? 'bg-white dark:bg-zinc-900 text-emerald-700 dark:text-emerald-400 shadow-xs'
                  : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'
              }`}
            >
              💵 Cash
            </button>
            <button
              type="button"
              onClick={() => setPaymentMethod('MPESA')}
              className={`py-2 text-xs font-bold rounded-lg transition ${
                paymentMethod === 'MPESA'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'
              }`}
            >
              📲 M-Pesa
            </button>
            <button
              type="button"
              onClick={() => setPaymentMethod('BANK')}
              className={`py-2 text-xs font-bold rounded-lg transition ${
                paymentMethod === 'BANK'
                  ? 'bg-white dark:bg-zinc-900 text-blue-600 dark:text-blue-400 shadow-xs'
                  : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'
              }`}
            >
              🏦 Bank
            </button>
            <button
              type="button"
              onClick={() => {
                setPaymentMethod('SPLIT');
                setSplitCashAmount((payableAfterPoints / 2).toFixed(2));
              }}
              className={`py-2 text-xs font-bold rounded-lg transition ${
                paymentMethod === 'SPLIT'
                  ? 'bg-indigo-600 text-white shadow-xs'
                  : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'
              }`}
            >
              ⚡ Split
            </button>
          </div>

          {/* Loyalty & Phone Section */}
          <div className="p-3 rounded-2xl bg-zinc-50 dark:bg-zinc-800/60 border border-zinc-200 dark:border-zinc-700 space-y-2">
            <div className="flex justify-between items-center">
              <label className="text-xs font-semibold text-zinc-600 dark:text-zinc-400">
                Customer Phone (Loyalty & M-Pesa)
              </label>
              {loadingCustomer && <span className="text-[10px] text-zinc-400">Looking up...</span>}
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                value={phone}
                onChange={(e) => handleLookupPhone(e.target.value)}
                placeholder="e.g. 07XXXXXXXX"
                className="flex-1 px-3 py-2 text-sm font-mono rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <button
                type="button"
                onClick={() => handleLookupPhone(phone)}
                className="px-3 py-2 text-xs font-semibold bg-zinc-200 dark:bg-zinc-700 rounded-xl hover:bg-zinc-300 transition"
              >
                Lookup
              </button>
            </div>

            {customerName && (
              <div className="flex justify-between items-center text-xs pt-1 border-t border-zinc-200 dark:border-zinc-700">
                <span className="font-semibold text-emerald-700 dark:text-emerald-400">
                  👤 {customerName}
                </span>
                <span className="font-mono text-zinc-600 dark:text-zinc-400">
                  Points: <strong className="text-emerald-600">{pointsBalance?.toFixed(2)}</strong>
                </span>
              </div>
            )}

            {pointsBalance && pointsBalance > 0 && (
              <div className="flex items-center justify-between text-xs pt-1">
                <span>Redeem Loyalty Points (1 pt = 1 KES):</span>
                <input
                  type="number"
                  min="0"
                  max={Math.min(pointsBalance, totalKes)}
                  value={pointsToRedeem}
                  onChange={(e) => setPointsToRedeem(e.target.value)}
                  className="w-20 px-2 py-1 text-right font-mono rounded border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                />
              </div>
            )}
          </div>

          {/* Total Payable Box */}
          <div className="p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-800/60 border border-zinc-200 dark:border-zinc-700 flex justify-between items-center">
            <div>
              <p className="text-xs text-zinc-500 uppercase font-semibold">Total Due</p>
              <p className="text-2xl font-extrabold font-mono text-emerald-600 dark:text-emerald-400">
                KES {payableAfterPoints.toFixed(2)}
              </p>
              {ptsRedeemNum > 0 && (
                <p className="text-[10px] text-zinc-400">
                  (KES {totalKes.toFixed(2)} - {ptsRedeemNum} pts)
                </p>
              )}
            </div>
            {paymentMethod === 'CASH' && (
              <div className="text-right">
                <p className="text-xs text-zinc-500 uppercase font-semibold">Change Due</p>
                <p className="text-2xl font-extrabold font-mono text-zinc-800 dark:text-zinc-100">
                  KES {changeDue.toFixed(2)}
                </p>
              </div>
            )}
          </div>

          {/* Cash Specific Controls */}
          {paymentMethod === 'CASH' && (
            <>
              <div>
                <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                  Cash Tendered (KES)
                </label>
                <input
                  type="number"
                  step="0.50"
                  autoFocus
                  value={amountTendered}
                  onChange={(e) => setAmountTendered(e.target.value)}
                  className="w-full px-5 py-3 text-2xl font-mono font-bold text-center rounded-2xl border-2 border-emerald-500 bg-white dark:bg-zinc-800 focus:outline-none focus:ring-4 focus:ring-emerald-200"
                />
              </div>

              <div className="grid grid-cols-4 gap-2">
                <button
                  type="button"
                  onClick={handleSetExact}
                  className="py-2 text-xs font-bold rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-100 dark:bg-zinc-800 hover:bg-emerald-50 hover:text-emerald-700 transition"
                >
                  Exact
                </button>
                <button
                  type="button"
                  onClick={() => handleQuickAdd(100)}
                  className="py-2 text-xs font-bold rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-100 dark:bg-zinc-800 hover:bg-emerald-50 hover:text-emerald-700 transition"
                >
                  +100
                </button>
                <button
                  type="button"
                  onClick={() => handleQuickAdd(500)}
                  className="py-2 text-xs font-bold rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-100 dark:bg-zinc-800 hover:bg-emerald-50 hover:text-emerald-700 transition"
                >
                  +500
                </button>
                <button
                  type="button"
                  onClick={() => handleQuickAdd(1000)}
                  className="py-2 text-xs font-bold rounded-lg border border-zinc-300 dark:border-zinc-700 bg-zinc-100 dark:bg-zinc-800 hover:bg-emerald-50 hover:text-emerald-700 transition"
                >
                  +1,000
                </button>
              </div>
            </>
          )}

          {/* M-Pesa Specific Message */}
          {paymentMethod === 'MPESA' && (
            <div className="p-3 rounded-xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 text-xs text-emerald-800 dark:text-emerald-300 space-y-1">
              <p className="font-bold">Daraja STK Push Simulation:</p>
              <p>
                An instant prompt will be triggered to phone <strong>{phone || '(enter phone)'}</strong> for KES{' '}
                <strong>{payableAfterPoints.toFixed(2)}</strong>.
              </p>
            </div>
          )}

          {/* Bank Specific Controls */}
          {paymentMethod === 'BANK' && (
            <div>
              <label className="block text-xs font-semibold uppercase text-zinc-500 mb-1">
                Bank / Card Reference
              </label>
              <input
                type="text"
                placeholder="e.g. STANBIC-POS-9812"
                value={bankRef}
                onChange={(e) => setBankRef(e.target.value)}
                className="w-full px-4 py-2.5 text-sm font-mono rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
              />
            </div>
          )}

          {/* Split Tender Controls (Cash + M-Pesa) */}
          {paymentMethod === 'SPLIT' && (
            <div className="p-3.5 rounded-2xl bg-indigo-50 dark:bg-indigo-950/30 border border-indigo-200 dark:border-indigo-800 space-y-2.5 text-xs">
              <p className="font-bold text-indigo-800 dark:text-indigo-300">
                Split Settlement (Cash + M-Pesa STK)
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold mb-1">Cash Portion (KES)</label>
                  <input
                    type="number"
                    step="0.50"
                    min="0"
                    max={payableAfterPoints}
                    value={splitCashAmount}
                    onChange={(e) => setSplitCashAmount(e.target.value)}
                    className="w-full px-3 py-2 font-mono font-bold rounded-xl border border-indigo-300 dark:border-indigo-700 bg-white dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="block font-semibold mb-1">M-Pesa Portion (KES)</label>
                  <div className="px-3 py-2 font-mono font-bold rounded-xl border border-indigo-200 dark:border-indigo-800 bg-white/70 dark:bg-zinc-900 text-emerald-600 dark:text-emerald-400">
                    {splitMpesaNum.toFixed(2)}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Submit */}
          <button
            type="submit"
            disabled={loading || (paymentMethod === 'CASH' && isUnderpaid)}
            className="w-full py-4 px-6 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 disabled:cursor-not-allowed text-white font-bold text-lg rounded-2xl transition shadow-md flex items-center justify-center gap-2"
          >
            {loading ? (
              'Processing Payment...'
            ) : paymentMethod === 'MPESA' ? (
              '📲 Send STK Push & Complete'
            ) : paymentMethod === 'BANK' ? (
              '🏦 Confirm Bank Payment'
            ) : paymentMethod === 'SPLIT' ? (
              `⚡ Settle Cash (${splitCashNum.toFixed(0)}) + M-Pesa (${splitMpesaNum.toFixed(0)})`
            ) : (
              'Complete Cash Sale'
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
