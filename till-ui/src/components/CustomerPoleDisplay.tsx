import { useEffect, useState } from 'react';
import type { CartItem } from './CheckoutModal';

export function CustomerPoleDisplay() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [totalKes, setTotalKes] = useState(0);
  const [subtotalKes, setSubtotalKes] = useState(0);
  const [totalTaxKes, setTotalTaxKes] = useState(0);

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const bc = new BroadcastChannel('supermart_customer_display');
    bc.onmessage = (ev) => {
      if (ev.data) {
        setCart(ev.data.cart || []);
        setTotalKes(Number(ev.data.totalKes || 0));
        setSubtotalKes(Number(ev.data.subtotalKes || 0));
        setTotalTaxKes(Number(ev.data.totalTaxKes || 0));
      }
    };
    return () => bc.close();
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col p-6 font-sans">
      <header className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
        <div>
          <h1 className="text-2xl font-black text-white">SuperMart Enterprise</h1>
          <p className="text-xs text-emerald-400 font-semibold">Customer Display — Till #01</p>
        </div>
        <div className="text-right">
          <p className="text-xs uppercase text-slate-400">Amount Due</p>
          <p className="text-4xl font-black font-mono text-emerald-400">
            KES {totalKes.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </p>
        </div>
      </header>

      <div className="flex-1 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col">
        {cart.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-slate-400">
            <p className="text-3xl font-bold text-white mb-2">Welcome to SuperMart</p>
            <p className="text-sm">Your scanned items will appear here.</p>
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto divide-y divide-slate-800">
            {cart.map((item) => (
              <div key={item.id} className="px-6 py-4 flex items-center justify-between text-lg">
                <div>
                  <p className="font-bold text-white">{item.name}</p>
                  <p className="text-xs text-slate-400 font-mono">
                    {item.quantity} × KES {item.priceKes.toFixed(2)}
                  </p>
                </div>
                <p className="font-mono font-bold text-emerald-400">
                  KES {Math.max(0, item.priceKes * item.quantity - (item.discountKes || 0)).toFixed(2)}
                </p>
              </div>
            ))}
          </div>
        )}

        <footer className="bg-slate-800/90 border-t border-slate-700 px-6 py-4 flex justify-between items-center text-sm">
          <span className="text-slate-300">
            Subtotal: <strong className="font-mono">KES {subtotalKes.toFixed(2)}</strong> · VAT:{' '}
            <strong className="font-mono">KES {totalTaxKes.toFixed(2)}</strong>
          </span>
          <span className="font-bold text-emerald-400 text-lg font-mono">
            Total: KES {totalKes.toFixed(2)}
          </span>
        </footer>
      </div>
    </div>
  );
}
