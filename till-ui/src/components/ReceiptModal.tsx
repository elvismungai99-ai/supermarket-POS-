import { useState } from 'react';
import { printEscPosReceipt, openCashDrawer } from '../utils/offlineQueue';

export interface ReceiptLineDto {
  productId: string;
  name: string;
  quantity: number;
  unitPriceKes: number;
  lineTotalKes: number;
  taxCategory: string;
  taxRate: number;
  taxAmountKes: number;
}

export interface TaxBreakdownDto {
  category: string;
  rate: number;
  taxableKes: number;
  taxKes: number;
  grossKes: number;
}

export interface ReceiptDto {
  saleId: string;
  receiptNumber: string;
  shopId: string;
  tillId: string;
  cashierId: string;
  cashierName: string;
  createdAt: string;
  lines: ReceiptLineDto[];
  subtotalKes: number;
  totalTaxKes: number;
  totalKes: number;
  taxBreakdowns: TaxBreakdownDto[];
  amountTenderedKes: number;
  changeKes: number;
  paymentMethod: string;
  etimsInvoiceStatus: string;
  paymentReference?: string;
  loyaltyPointsEarned?: number;
  loyaltyPointsBalance?: number;
}

export function ReceiptModal({
  receipt,
  onClose,
}: {
  receipt: ReceiptDto;
  onClose: () => void;
}) {
  const [hwStatus, setHwStatus] = useState<string | null>(null);
  const formattedDate = new Date(receipt.createdAt).toLocaleString('en-KE', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  });

  const handleThermalPrint = async () => {
    setHwStatus(null);
    try {
      const res = await printEscPosReceipt(receipt, receipt.paymentMethod === 'CASH');
      setHwStatus(`Sent ${res.bytesLength} bytes to ESC/POS printer (${res.transport?.mode || 'ok'}).`);
    } catch {
      setHwStatus('Hardware agent (:9001) offline — opening browser print dialog.');
      window.print();
    }
  };

  const handleOpenDrawer = async () => {
    setHwStatus(null);
    try {
      await openCashDrawer();
      setHwStatus('Cash drawer kick pulse sent.');
    } catch {
      setHwStatus('Hardware agent (:9001) not reachable to kick cash drawer.');
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-white text-zinc-900 rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-zinc-200">
        {/* Modal Action Bar */}
        <div className="bg-emerald-600 px-6 py-4 flex items-center justify-between text-white">
          <div className="flex items-center gap-2">
            <span className="text-xl">🧾</span>
            <h3 className="font-bold text-lg">Sale Completed</h3>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white font-bold text-2xl leading-none"
          >
            &times;
          </button>
        </div>

        {/* Thermal Receipt Print Preview */}
        <div className="p-6 font-mono text-xs max-h-[70vh] overflow-y-auto bg-amber-50/40">
          <div className="text-center mb-4">
            <h2 className="font-bold text-base tracking-wider uppercase">NAIROBI FRESH SUPERMARKET</h2>
            <p className="text-[11px] text-zinc-600">Moi Avenue Branch, Nairobi, Kenya</p>
            <p className="text-[11px] text-zinc-600">KRA PIN: P051234567Z | Tel: +254 700 000 000</p>
            <p className="font-bold text-emerald-800 mt-1 uppercase text-[10px]">
              *** OFFICIAL FISCAL RECEIPT ***
            </p>
          </div>

          <div className="border-t border-b border-dashed border-zinc-400 py-2 my-2 space-y-1">
            <div className="flex justify-between">
              <span>Receipt No:</span>
              <span className="font-bold">{receipt.receiptNumber}</span>
            </div>
            <div className="flex justify-between">
              <span>Date & Time:</span>
              <span>{formattedDate}</span>
            </div>
            <div className="flex justify-between">
              <span>Cashier:</span>
              <span>{receipt.cashierName}</span>
            </div>
            <div className="flex justify-between">
              <span>Till ID:</span>
              <span>{receipt.tillId.slice(0, 8)}</span>
            </div>
          </div>

          {/* Line items */}
          <table className="w-full text-left my-2">
            <thead>
              <tr className="border-b border-zinc-300">
                <th className="py-1">Item</th>
                <th className="py-1 text-center">Qty</th>
                <th className="py-1 text-right">Price</th>
                <th className="py-1 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200">
              {receipt.lines.map((item, idx) => (
                <tr key={idx}>
                  <td className="py-1.5 pr-1">
                    <div className="font-semibold">{item.name}</div>
                    <div className="text-[10px] text-zinc-500">Tax Cat: {item.taxCategory} ({item.taxRate * 100}%)</div>
                  </td>
                  <td className="py-1.5 text-center">{Number(item.quantity).toFixed(3)}</td>
                  <td className="py-1.5 text-right">{Number(item.unitPriceKes).toFixed(2)}</td>
                  <td className="py-1.5 text-right font-bold">{Number(item.lineTotalKes).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Financial Totals */}
          <div className="border-t border-dashed border-zinc-400 pt-2 space-y-1.5">
            <div className="flex justify-between text-sm">
              <span>Subtotal (Excl. Tax):</span>
              <span>KES {Number(receipt.subtotalKes).toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span>Total VAT / Tax:</span>
              <span>KES {Number(receipt.totalTaxKes).toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-base font-bold border-t border-zinc-800 pt-1">
              <span>TOTAL PAYABLE:</span>
              <span>KES {Number(receipt.totalKes).toFixed(2)}</span>
            </div>
            <div className="flex justify-between text-sm pt-1">
              <span>Payment ({receipt.paymentMethod}):</span>
              <span>KES {Number(receipt.amountTenderedKes).toFixed(2)}</span>
            </div>
            {receipt.paymentMethod === 'CASH' && (
              <div className="flex justify-between text-sm font-semibold text-emerald-800">
                <span>CHANGE GIVEN:</span>
                <span>KES {Number(receipt.changeKes).toFixed(2)}</span>
              </div>
            )}
            {receipt.paymentReference && (
              <div className="flex justify-between text-xs text-zinc-600">
                <span>Reference:</span>
                <span className="font-bold">{receipt.paymentReference}</span>
              </div>
            )}
          </div>

          {/* Customer Loyalty Section */}
          {receipt.loyaltyPointsEarned !== undefined && receipt.loyaltyPointsEarned !== null && receipt.loyaltyPointsEarned > 0 && (
            <div className="border-t border-dashed border-zinc-400 mt-2 pt-1 text-xs">
              <div className="flex justify-between text-emerald-700 font-semibold">
                <span>Tokens Earned Today:</span>
                <span>+{receipt.loyaltyPointsEarned.toFixed(2)} pts</span>
              </div>
              {receipt.loyaltyPointsBalance !== undefined && (
                <div className="flex justify-between text-zinc-600">
                  <span>Current Points Balance:</span>
                  <span>{receipt.loyaltyPointsBalance.toFixed(2)} pts</span>
                </div>
              )}
            </div>
          )}

          {/* Tax Breakdown Table (KRA eTIMS Format) */}
          <div className="border-t border-dashed border-zinc-400 mt-3 pt-2">
            <p className="font-bold text-[10px] text-zinc-600 uppercase mb-1">TAX ANALYSIS (KRA eTIMS):</p>
            <div className="grid grid-cols-4 gap-1 text-[10px] font-semibold border-b border-zinc-300 pb-0.5">
              <span>CAT</span>
              <span className="text-right">TAXABLE</span>
              <span className="text-right">VAT</span>
              <span className="text-right">GROSS</span>
            </div>
            {receipt.taxBreakdowns.map((tb, i) => (
              <div key={i} className="grid grid-cols-4 gap-1 text-[10px] py-0.5">
                <span>{tb.category} ({tb.rate * 100}%)</span>
                <span className="text-right">{Number(tb.taxableKes).toFixed(2)}</span>
                <span className="text-right">{Number(tb.taxKes).toFixed(2)}</span>
                <span className="text-right">{Number(tb.grossKes).toFixed(2)}</span>
              </div>
            ))}
          </div>

          {/* eTIMS Compliance & Outbox Marker */}
          <div className="border-t border-dashed border-zinc-400 mt-3 pt-2 text-center text-[10px] space-y-1">
            <p className="font-bold text-emerald-700">✓ KRA eTIMS INVOICE QUEUED IN OUTBOX</p>
            <p className="text-zinc-500">KRA CU: KRA-VSCU-{receipt.saleId.slice(0, 8).toUpperCase()}</p>
            <div className="my-2 p-2 bg-white inline-block border border-zinc-300 rounded">
              <span className="text-3xl">⬛⬜⬛</span>
              <p className="text-[8px] text-zinc-400 mt-0.5">Verify on eTIMS</p>
            </div>
            <p className="text-zinc-600 mt-2">Asante kwa kununua nasi!</p>
            <p className="text-zinc-600">Goods once sold are not returnable without valid receipt.</p>
          </div>
        </div>

        {/* Modal Buttons */}
        {hwStatus && (
          <div className="px-4 py-2 bg-emerald-50 text-emerald-800 border-t border-emerald-200 text-[11px] font-medium">
            {hwStatus}
          </div>
        )}
        <div className="p-4 bg-zinc-50 border-t border-zinc-200 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleThermalPrint}
            className="flex-1 py-2.5 px-3 rounded-xl bg-zinc-900 hover:bg-zinc-800 text-white text-xs font-bold transition"
          >
            🖨️ Print Thermal (ESC/POS)
          </button>
          <button
            type="button"
            onClick={handleOpenDrawer}
            className="py-2.5 px-3 rounded-xl border border-zinc-300 hover:bg-zinc-100 text-xs font-semibold text-zinc-700 transition"
            title="Kick Cash Drawer"
          >
            💵 Drawer
          </button>
          <button
            type="button"
            onClick={() => window.print()}
            className="py-2.5 px-3 rounded-xl border border-zinc-300 hover:bg-zinc-100 text-xs font-semibold text-zinc-700 transition"
          >
            Browser Print
          </button>
          <button
            type="button"
            onClick={onClose}
            className="flex-1 py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition shadow-sm"
          >
            Start Next Sale
          </button>
        </div>
      </div>
    </div>
  );
}
