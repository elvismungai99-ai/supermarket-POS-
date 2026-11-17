import { useState, useEffect } from 'react';
import { apiFetch } from '../utils/apiClient';

export interface ReturnRecord {
  returnId: string;
  originalSaleId: string;
  receiptNumber: string;
  cashierId: string;
  cashierName: string;
  totalRefundKes: number;
  reason: string;
  createdAt: string;
  lines: {
    productId: string;
    productName: string;
    quantity: number;
    unitPriceKes: number;
    refundTotalKes: number;
  }[];
}

export function ReturnsView({
  cashierId,
  cashierName,
}: {
  cashierId: string;
  cashierName: string;
}) {
  const [returnsList, setReturnsList] = useState<ReturnRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [saleId, setSaleId] = useState('');
  const [receiptNumber, setReceiptNumber] = useState('');
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [reason, setReason] = useState('Customer returned unopened item with valid receipt');
  const [products, setProducts] = useState<{ id: string; name: string; priceKes: number }[]>([]);
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const [retRes, prodRes] = await Promise.all([
        apiFetch('/api/returns'),
        apiFetch('/api/products'),
      ]);
      if (retRes.ok) setReturnsList(await retRes.json());
      if (prodRes.ok) {
        const prodData = await prodRes.json();
        setProducts(prodData);
        if (prodData.length > 0 && !productId) {
          setProductId(prodData[0].id);
        }
      }
    } catch {
      setMsg({ type: 'error', text: 'Failed to load returns data' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleProcessReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!saleId.trim()) {
      setMsg({ type: 'error', text: 'Original Sale ID is required to process return' });
      return;
    }

    setSubmitting(true);
    setMsg(null);

    try {
      const payload = {
        saleId: saleId.trim(),
        receiptNumber: receiptNumber.trim() || undefined,
        cashierId,
        reason,
        items: [
          {
            productId,
            quantity: parseFloat(quantity) || 1,
          },
        ],
      };

      const res = await apiFetch('/api/returns', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Failed to process return');
      }

      const returnData: ReturnRecord = await res.json();
      setMsg({
        type: 'success',
        text: `Return processed! KES ${returnData.totalRefundKes.toFixed(2)} refunded. Inventory restocked & KRA eTIMS credit note queued.`,
      });
      setSaleId('');
      setReceiptNumber('');
      loadData();
    } catch (err: unknown) {
      setMsg({ type: 'error', text: err instanceof Error ? err.message : 'Error processing return' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {msg && (
        <div
          className={`p-4 rounded-xl flex items-center justify-between text-xs font-semibold ${
            msg.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200'
              : 'bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-300 border border-red-200'
          }`}
        >
          <span>{msg.text}</span>
          <button onClick={() => setMsg(null)} className="font-bold text-sm">
            &times;
          </button>
        </div>
      )}

      {/* Return Form & Instructions */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-6 bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 p-5 shadow-xs">
          <h2 className="font-bold text-base mb-1">Process Sale Return / Refund</h2>
          <p className="text-xs text-zinc-500 mb-4">
            Authorized Cashier: <strong>{cashierName}</strong>. Restores stock inventory and queues eTIMS credit note.
          </p>

          <form onSubmit={handleProcessReturn} className="space-y-4 text-xs">
            <div>
              <label className="block font-semibold mb-1">Original Sale ID (UUID)</label>
              <input
                type="text"
                required
                placeholder="e.g. 1a2b3c4d-0000-0000-0000-000000000000"
                value={saleId}
                onChange={(e) => setSaleId(e.target.value)}
                className="w-full px-3 py-2 font-mono rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
              />
            </div>
            <div>
              <label className="block font-semibold mb-1">Receipt Number (Optional)</label>
              <input
                type="text"
                placeholder="e.g. REC-20261112-..."
                value={receiptNumber}
                onChange={(e) => setReceiptNumber(e.target.value)}
                className="w-full px-3 py-2 font-mono rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold mb-1">Returned Product</label>
                <select
                  value={productId}
                  onChange={(e) => setProductId(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                >
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} (KES {Number(p.priceKes).toFixed(2)})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block font-semibold mb-1">Quantity Returned</label>
                <input
                  type="number"
                  step="1"
                  min="1"
                  required
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  className="w-full px-3 py-2 font-mono rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                />
              </div>
            </div>
            <div>
              <label className="block font-semibold mb-1">Reason for Return</label>
              <textarea
                rows={2}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
              />
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="w-full py-3 bg-red-600 hover:bg-red-700 text-white font-bold rounded-xl shadow-xs transition"
            >
              {submitting ? 'Processing Return...' : 'Authorize & Process Refund'}
            </button>
          </form>
        </div>

        {/* Policy / Audit Guidelines */}
        <div className="lg:col-span-6 bg-zinc-50 dark:bg-zinc-800/40 rounded-2xl border border-zinc-200 dark:border-zinc-700 p-5 text-xs space-y-3">
          <h3 className="font-bold text-sm text-zinc-900 dark:text-zinc-100 flex items-center gap-1.5">
            <span>🛡️</span> Kenya Retail Return & Tax Rules
          </h3>
          <ul className="list-disc pl-4 space-y-2 text-zinc-600 dark:text-zinc-300">
            <li>
              <strong>Manager Oversight:</strong> Every return explicitly logs the authorizing cashier ID so managers can audit which cashier handled each refund.
            </li>
            <li>
              <strong>KRA eTIMS Fiscal Credit Note:</strong> An electronic credit note event is queued in the transaction outbox to balance the merchant VAT ledger.
            </li>
            <li>
              <strong>Stock Restoring:</strong> Product stock level is automatically incremented in the database upon return authorization.
            </li>
          </ul>
        </div>
      </div>

      {/* Returns History Table */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800">
          <h2 className="font-bold text-base">Returns & Credit Notes Audit Trail</h2>
          <p className="text-xs text-zinc-500">Historical log of returned items, refund amounts, and handling cashiers.</p>
        </div>

        {loading ? (
          <div className="p-8 text-center text-xs text-zinc-500">Loading returns history...</div>
        ) : returnsList.length === 0 ? (
          <div className="p-8 text-center text-xs text-zinc-400">No returns processed yet.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/50 text-zinc-600 dark:text-zinc-400 border-b border-zinc-200 dark:border-zinc-800">
                <tr>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">Receipt No</th>
                  <th className="py-3 px-4">Authorized Cashier</th>
                  <th className="py-3 px-4">Reason</th>
                  <th className="py-3 px-4 text-right">Refund Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {returnsList.map((ret) => (
                  <tr key={ret.returnId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                    <td className="py-3 px-4 text-zinc-500">
                      {new Date(ret.createdAt).toLocaleString('en-KE', { dateStyle: 'short', timeStyle: 'short' })}
                    </td>
                    <td className="py-3 px-4 font-mono font-semibold">{ret.receiptNumber}</td>
                    <td className="py-3 px-4 font-medium text-zinc-900 dark:text-zinc-100">
                      👤 {ret.cashierName}
                    </td>
                    <td className="py-3 px-4 text-zinc-600 dark:text-zinc-300">{ret.reason}</td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-red-600 dark:text-red-400">
                      KES {Number(ret.totalRefundKes).toFixed(2)}
                    </td>
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
