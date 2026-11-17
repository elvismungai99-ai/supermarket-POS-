import { useState, useEffect, useRef } from 'react';
import { apiFetch } from '../utils/apiClient';
import { playPosBeep } from '../utils/sound';

export interface ProductItem {
  id: string;
  name: string;
  priceKes: number;
  taxCategory: string;
  stockQuantity: number;
  reorderLevel: number;
  barcode: string;
  costPriceKes?: number;
}

export interface StockReceiptItem {
  id: string;
  supplierName: string;
  supplierInvoiceNo?: string;
  productId: string;
  productName: string;
  quantity: number;
  unitCostKes: number;
  totalCostKes: number;
  erpnextPurchaseInvoiceId?: string;
  erpnextStatus: string;
  errorMessage?: string;
  receivedAt: string;
  batchNumber?: string;
  expiryDate?: string;
}

export interface ExpiringBatchItem {
  id: string;
  productId: string;
  productName: string;
  batchNumber: string;
  expiryDate: string;
  quantityRemaining: number;
  unitCostKes: number;
}

export function ManagerInventoryView() {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [lowStock, setLowStock] = useState<ProductItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPrice, setEditPrice] = useState<string>('');
  const [editCostPrice, setEditCostPrice] = useState<string>('');
  const [editTaxCat, setEditTaxCat] = useState<string>('A');
  const [editStock, setEditStock] = useState<string>('');
  const [editReorder, setEditReorder] = useState<string>('');
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Stock Receiving (GRN) & Batches
  const [receipts, setReceipts] = useState<StockReceiptItem[]>([]);
  const [expiringBatches, setExpiringBatches] = useState<ExpiringBatchItem[]>([]);
  const [showReceiveModal, setShowReceiveModal] = useState(false);
  const [recSupplier, setRecSupplier] = useState('');
  const [recInvoiceNo, setRecInvoiceNo] = useState('');
  const [recProductId, setRecProductId] = useState('');
  const [recProductQuery, setRecProductQuery] = useState('');
  const [recDropdownOpen, setRecDropdownOpen] = useState(false);
  const [recCreatingNew, setRecCreatingNew] = useState(false);
  const [recNewName, setRecNewName] = useState('');
  const [recNewBarcode, setRecNewBarcode] = useState('');
  const [recNewPrice, setRecNewPrice] = useState('');
  const [recNewTaxCat, setRecNewTaxCat] = useState('A');
  const [recNewReorder, setRecNewReorder] = useState('10');
  const [recQty, setRecQty] = useState('10');
  const [recUnitCost, setRecUnitCost] = useState('');
  const [recVatRate, setRecVatRate] = useState('0.16');
  const [recBatchNo, setRecBatchNo] = useState('');
  const [recExpiryDate, setRecExpiryDate] = useState('');
  const [recPostErpNext, setRecPostErpNext] = useState(false);

  // New product form
  const [showAddModal, setShowAddModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPrice, setNewPrice] = useState('');
  const [newCostPrice, setNewCostPrice] = useState('0');
  const [newBarcode, setNewBarcode] = useState('');
  const [newTaxCat, setNewTaxCat] = useState('A');
  const [newStock, setNewStock] = useState('50');
  const [newReorder, setNewReorder] = useState('10');
  const [scannedViaReader, setScannedViaReader] = useState(false);

  const barcodeInputRef = useRef<HTMLInputElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);

  // Auto-focus barcode input when Add Product modal opens
  useEffect(() => {
    if (showAddModal) {
      setScannedViaReader(false);
      const timer = setTimeout(() => {
        barcodeInputRef.current?.focus();
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [showAddModal]);

  // Global barcode reader hardware wedge listener for the Add Product modal
  useEffect(() => {
    if (!showAddModal) return;

    let buffer = '';
    let lastKeyTime = Date.now();

    const handleKeyDown = (e: KeyboardEvent) => {
      const now = Date.now();
      const elapsed = now - lastKeyTime;
      lastKeyTime = now;

      // Scanners rapidly type characters (< 50ms per key) followed by an Enter key
      if (e.key === 'Enter') {
        if (buffer.length >= 4) {
          e.preventDefault();
          const code = buffer.trim();
          setNewBarcode(code);
          setScannedViaReader(true);
          playPosBeep();
          buffer = '';
          nameInputRef.current?.focus();
        }
      } else if (e.key.length === 1) {
        if (elapsed > 120) {
          buffer = e.key;
        } else {
          buffer += e.key;
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [showAddModal]);

  const fetchInventory = async () => {
    setLoading(true);
    try {
      const [prodRes, lowRes, recRes, expRes] = await Promise.all([
        apiFetch('/api/products'),
        apiFetch('/api/products/low-stock'),
        apiFetch('/api/inventory/receipts'),
        apiFetch('/api/inventory/expiring?days=14'),
      ]);
      if (prodRes.ok) {
        const list: ProductItem[] = await prodRes.json();
        setProducts(list);
      }
      if (lowRes.ok) {
        setLowStock(await lowRes.json());
      }
      if (recRes.ok) {
        setReceipts(await recRes.json());
      }
      if (expRes.ok) {
        setExpiringBatches(await expRes.json());
      }
    } catch {
      setMsg({ type: 'error', text: 'Failed to load product inventory' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInventory();
  }, []);

  const handleStartEdit = (p: ProductItem) => {
    setEditingId(p.id);
    setEditPrice(p.priceKes.toString());
    setEditCostPrice((p.costPriceKes ?? 0).toString());
    setEditTaxCat(p.taxCategory);
    setEditStock(p.stockQuantity.toString());
    setEditReorder(p.reorderLevel.toString());
  };

  const handleSaveEdit = async (p: ProductItem) => {
    try {
      const res = await apiFetch(`/api/products/${p.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: p.name,
          priceKes: parseFloat(editPrice),
          costPriceKes: parseFloat(editCostPrice) || 0,
          taxCategory: editTaxCat,
          stockQuantity: parseFloat(editStock),
          reorderLevel: parseFloat(editReorder),
        }),
      });

      if (!res.ok) throw new Error('Failed to update product');
      setMsg({ type: 'success', text: `Product "${p.name}" updated successfully.` });
      setEditingId(null);
      fetchInventory();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error updating product' });
    }
  };

  const handleQuickRestock = async (p: ProductItem, delta = 50) => {
    try {
      const updatedQty = Number((Number(p.stockQuantity) + delta).toFixed(3));
      const res = await apiFetch(`/api/products/${p.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: p.name,
          priceKes: p.priceKes,
          costPriceKes: p.costPriceKes ?? 0,
          taxCategory: p.taxCategory,
          stockQuantity: updatedQty,
          reorderLevel: p.reorderLevel,
        }),
      });
      if (!res.ok) throw new Error('Quick restock failed');
      setMsg({ type: 'success', text: `Restocked "${p.name}" by +${delta} units (new stock: ${updatedQty}).` });
      fetchInventory();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error restocking product' });
    }
  };

  const handleReceiveStock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recCreatingNew && !recProductId) {
      setMsg({
        type: 'error',
        text: 'Select an existing product from the dropdown or click "+ Add as New Product" to create it.',
      });
      return;
    }
    if (recCreatingNew && (!recNewName.trim() || !recNewBarcode.trim() || !recNewPrice)) {
      setMsg({ type: 'error', text: 'Enter name, barcode/SKU, and retail selling price for the new product.' });
      return;
    }

    try {
      const payload = recCreatingNew
        ? {
            supplierName: recSupplier,
            supplierInvoiceNo: recInvoiceNo || null,
            productId: null,
            newProduct: {
              name: recNewName.trim(),
              priceKes: parseFloat(recNewPrice),
              taxCategory: recNewTaxCat,
              barcode: recNewBarcode.trim(),
              reorderLevel: parseFloat(recNewReorder) || 10,
            },
            quantity: parseFloat(recQty),
            unitCostKes: parseFloat(recUnitCost),
            vatRate: parseFloat(recVatRate),
            postToErpNext: recPostErpNext,
            batchNumber: recBatchNo || null,
            expiryDate: recExpiryDate || null,
          }
        : {
            supplierName: recSupplier,
            supplierInvoiceNo: recInvoiceNo || null,
            productId: recProductId,
            quantity: parseFloat(recQty),
            unitCostKes: parseFloat(recUnitCost),
            vatRate: parseFloat(recVatRate),
            postToErpNext: recPostErpNext,
            batchNumber: recBatchNo || null,
            expiryDate: recExpiryDate || null,
          };

      const res = await apiFetch('/api/inventory/receive', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        throw new Error(errText || 'Failed to record stock receipt');
      }
      const created: StockReceiptItem = await res.json();
      setMsg({
        type: 'success',
        text: `${recCreatingNew ? 'Created product & received' : 'Received'} ${created.quantity} units of "${created.productName}" (Batch ${created.batchNumber || 'auto'}).${
          created.erpnextPurchaseInvoiceId ? ` ERPNext Purchase Invoice: ${created.erpnextPurchaseInvoiceId}` : ''
        }`,
      });
      setShowReceiveModal(false);
      setRecSupplier('');
      setRecInvoiceNo('');
      setRecProductId('');
      setRecProductQuery('');
      setRecCreatingNew(false);
      setRecNewName('');
      setRecNewBarcode('');
      setRecNewPrice('');
      setRecNewTaxCat('A');
      setRecNewReorder('10');
      setRecUnitCost('');
      setRecBatchNo('');
      setRecExpiryDate('');
      fetchInventory();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error receiving stock' });
    }
  };

  const handlePostReceiptErpNext = async (id: string) => {
    try {
      const res = await apiFetch(`/api/inventory/receipts/${id}/post-erpnext`, { method: 'POST' });
      if (!res.ok) throw new Error('ERPNext sync request failed');
      const updated: StockReceiptItem = await res.json();
      setMsg({
        type: updated.erpnextStatus === 'POSTED' ? 'success' : 'error',
        text:
          updated.erpnextStatus === 'POSTED'
            ? `Posted Purchase Invoice ${updated.erpnextPurchaseInvoiceId} to ERPNext.`
            : `ERPNext Purchase Invoice sync failed: ${updated.errorMessage || 'Unknown error'}`,
      });
      fetchInventory();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Failed to sync receipt to ERPNext' });
    }
  };

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch('/api/products', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: newName,
          priceKes: parseFloat(newPrice),
          costPriceKes: parseFloat(newCostPrice) || 0,
          taxCategory: newTaxCat,
          barcode: newBarcode,
          stockQuantity: parseFloat(newStock),
          reorderLevel: parseFloat(newReorder),
        }),
      });

      if (!res.ok) throw new Error('Failed to add product');
      setMsg({ type: 'success', text: `Product "${newName}" added to catalogue.` });
      setShowAddModal(false);
      setNewName('');
      setNewPrice('');
      setNewCostPrice('0');
      setNewBarcode('');
      fetchInventory();
    } catch (err: any) {
      setMsg({ type: 'error', text: err.message || 'Error creating product' });
    }
  };

  return (
    <div className="space-y-6">
      {/* Alert Banner */}
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

      {/* Low Stock Alerts */}
      {lowStock.length > 0 && (
        <div className="p-4 rounded-2xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800 text-amber-900 dark:text-amber-200">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-xl">⚠️</span>
            <h3 className="font-bold text-sm uppercase">Low Stock Alert ({lowStock.length} items)</h3>
          </div>
          <p className="text-xs text-amber-700 dark:text-amber-300 mb-3">
            The following items have dropped below their reorder threshold and require restocking:
          </p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {lowStock.map((item) => (
              <div
                key={item.id}
                className="p-3 bg-white dark:bg-zinc-800 rounded-xl border border-amber-300 dark:border-amber-700 text-xs flex justify-between items-center"
              >
                <div>
                  <p className="font-bold text-zinc-900 dark:text-zinc-100">{item.name}</p>
                  <p className="font-mono text-zinc-500 text-[11px]">Barcode: {item.barcode}</p>
                </div>
                <div className="text-right">
                  <span className="px-2 py-0.5 rounded font-mono font-bold bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-300">
                    Stock: {Number(item.stockQuantity).toFixed(1)}
                  </span>
                  <p className="text-[10px] text-zinc-400 mt-0.5">Threshold: {Number(item.reorderLevel).toFixed(1)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Expiring Soon Perishable Batches (FEFO) */}
      {expiringBatches.length > 0 && (
        <div className="p-4 rounded-2xl bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-800 text-rose-900 dark:text-rose-200">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-xl">⏳</span>
            <h3 className="font-bold text-sm uppercase">
              Expiring / Expired Perishable Batches (≤ 14 Days · {expiringBatches.length})
            </h3>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {expiringBatches.map((b) => (
              <div
                key={b.id}
                className="p-3 bg-white dark:bg-zinc-900 rounded-xl border border-rose-300 dark:border-rose-800 text-xs flex justify-between items-center"
              >
                <div>
                  <p className="font-bold text-zinc-900 dark:text-zinc-100">{b.productName}</p>
                  <p className="font-mono text-zinc-500 text-[11px]">Batch: {b.batchNumber}</p>
                </div>
                <div className="text-right">
                  <span className="px-2 py-0.5 rounded font-mono font-bold bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-300">
                    Exp: {b.expiryDate}
                  </span>
                  <p className="text-[10px] text-zinc-400 mt-0.5">Remaining: {Number(b.quantityRemaining).toFixed(1)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Product Management Table */}
      <div className="bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
          <div>
            <h2 className="font-bold text-base">Product Catalogue & KRA Tax Classification</h2>
            <p className="text-xs text-zinc-500">
              Kenyan tax rates: Cat A (16% Standard), Cat B (0% Zero-Rated), Cat E (VAT Exempt).
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowReceiveModal(true)}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl shadow-xs transition flex items-center gap-1.5"
            >
              <span>📥</span> Receive Stock (GRN)
            </button>
            <button
              onClick={() => setShowAddModal(true)}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs rounded-xl shadow-xs transition flex items-center gap-1.5"
            >
              <span>➕</span> Add New Product
            </button>
          </div>
        </div>

        {loading ? (
          <div className="p-8 text-center text-xs text-zinc-500">Loading catalogue...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/50 text-zinc-600 dark:text-zinc-400 border-b border-zinc-200 dark:border-zinc-800">
                <tr>
                  <th className="py-3 px-4">Barcode</th>
                  <th className="py-3 px-4">Item Name</th>
                  <th className="py-3 px-4 text-right">Shelf Price</th>
                  <th className="py-3 px-4 text-right">Cost (COGS)</th>
                  <th className="py-3 px-4 text-center">Tax Cat</th>
                  <th className="py-3 px-4 text-right">Current Stock</th>
                  <th className="py-3 px-4 text-right">Reorder Level</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {products.map((p) => {
                  const isEditing = editingId === p.id;
                  const isLow = p.stockQuantity <= p.reorderLevel;

                  return (
                    <tr key={p.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                      <td className="py-3 px-4 font-mono text-zinc-500">{p.barcode || '—'}</td>
                      <td className="py-3 px-4 font-semibold text-zinc-900 dark:text-zinc-100">{p.name}</td>
                      <td className="py-3 px-4 text-right font-mono">
                        {isEditing ? (
                          <input
                            type="number"
                            step="0.50"
                            value={editPrice}
                            onChange={(e) => setEditPrice(e.target.value)}
                            className="w-20 px-2 py-1 text-right font-mono border rounded bg-white dark:bg-zinc-800"
                          />
                        ) : (
                          `KES ${Number(p.priceKes).toFixed(2)}`
                        )}
                      </td>
                      <td className="py-3 px-4 text-right font-mono text-zinc-500">
                        {isEditing ? (
                          <input
                            type="number"
                            step="0.50"
                            value={editCostPrice}
                            onChange={(e) => setEditCostPrice(e.target.value)}
                            className="w-20 px-2 py-1 text-right font-mono border rounded bg-white dark:bg-zinc-800"
                          />
                        ) : (
                          `KES ${Number(p.costPriceKes ?? 0).toFixed(2)}`
                        )}
                      </td>
                      <td className="py-3 px-4 text-center">
                        {isEditing ? (
                          <select
                            value={editTaxCat}
                            onChange={(e) => setEditTaxCat(e.target.value)}
                            className="px-2 py-1 font-mono border rounded bg-white dark:bg-zinc-800"
                          >
                            <option value="A">Cat A (16% Std)</option>
                            <option value="C">Cat C (8% Special)</option>
                            <option value="B">Cat B (0% Zero)</option>
                            <option value="E">Cat E (Exempt)</option>
                          </select>
                        ) : (
                          <span
                            className={`px-2 py-0.5 rounded text-[11px] font-bold ${
                              p.taxCategory === 'A'
                                ? 'bg-blue-100 text-blue-800 dark:bg-blue-950/50 dark:text-blue-300'
                                : p.taxCategory === 'C'
                                ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-300'
                                : p.taxCategory === 'B'
                                ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300'
                                : 'bg-purple-100 text-purple-800 dark:bg-purple-950/50 dark:text-purple-300'
                            }`}
                          >
                            Cat {p.taxCategory}{' '}
                            {p.taxCategory === 'A'
                              ? '(16%)'
                              : p.taxCategory === 'C'
                              ? '(8%)'
                              : p.taxCategory === 'B'
                              ? '(0%)'
                              : '(Exempt)'}
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right font-mono">
                        {isEditing ? (
                          <input
                            type="number"
                            step="1"
                            value={editStock}
                            onChange={(e) => setEditStock(e.target.value)}
                            className="w-20 px-2 py-1 text-right font-mono border rounded bg-white dark:bg-zinc-800"
                          />
                        ) : (
                          <span className={isLow ? 'text-red-600 font-bold' : ''}>
                            {Number(p.stockQuantity).toFixed(1)}
                          </span>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right font-mono">
                        {isEditing ? (
                          <input
                            type="number"
                            step="1"
                            value={editReorder}
                            onChange={(e) => setEditReorder(e.target.value)}
                            className="w-16 px-2 py-1 text-right font-mono border rounded bg-white dark:bg-zinc-800"
                          />
                        ) : (
                          Number(p.reorderLevel).toFixed(1)
                        )}
                      </td>
                      <td className="py-3 px-4 text-center">
                        {isEditing ? (
                          <div className="flex justify-center gap-1.5">
                            <button
                              onClick={() => handleSaveEdit(p)}
                              className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded font-bold"
                            >
                              Save
                            </button>
                            <button
                              onClick={() => setEditingId(null)}
                              className="px-2 py-1 bg-zinc-200 dark:bg-zinc-700 rounded"
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <div className="flex justify-center gap-1.5">
                            {isLow && (
                              <button
                                onClick={() => handleQuickRestock(p, 50)}
                                className="px-2.5 py-1 text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white rounded transition"
                              >
                                Restock (+50)
                              </button>
                            )}
                            <button
                              onClick={() => handleStartEdit(p)}
                              className="px-2.5 py-1 text-xs font-semibold text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded border border-zinc-200 dark:border-zinc-700"
                            >
                              Edit
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Recent Stock Receipts (GRN) */}
        <div className="mt-6 bg-white dark:bg-zinc-900 rounded-2xl border border-zinc-200 dark:border-zinc-800 shadow-xs overflow-hidden">
          <div className="px-4 py-3 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
            <h3 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
              Recent Goods Received Notes (Stock Receipts)
            </h3>
            <span className="text-xs text-zinc-400">{receipts.length} recorded</span>
          </div>
          {receipts.length === 0 ? (
            <div className="p-6 text-center text-xs text-zinc-400">
              No supplier stock receipts recorded yet. Use "Receive Stock (GRN)" above to record deliveries.
            </div>
          ) : (
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-zinc-50 dark:bg-zinc-950 text-zinc-500 border-b border-zinc-200 dark:border-zinc-800">
                  <th className="py-2.5 px-4 font-semibold">Date</th>
                  <th className="py-2.5 px-4 font-semibold">Supplier</th>
                  <th className="py-2.5 px-4 font-semibold">Invoice #</th>
                  <th className="py-2.5 px-4 font-semibold">Product</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Qty</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Unit Cost</th>
                  <th className="py-2.5 px-4 font-semibold text-right">Total</th>
                  <th className="py-2.5 px-4 font-semibold">ERPNext</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800/60">
                {receipts.slice(0, 15).map((r) => (
                  <tr key={r.id} className="hover:bg-zinc-50 dark:hover:bg-zinc-800/30">
                    <td className="py-2.5 px-4 font-mono text-zinc-500">
                      {new Date(r.receivedAt).toLocaleString()}
                    </td>
                    <td className="py-2.5 px-4 font-medium">{r.supplierName}</td>
                    <td className="py-2.5 px-4 font-mono text-zinc-500">
                      {r.supplierInvoiceNo || '—'}
                    </td>
                    <td className="py-2.5 px-4 font-medium">{r.productName}</td>
                    <td className="py-2.5 px-4 text-right font-mono">{r.quantity}</td>
                    <td className="py-2.5 px-4 text-right font-mono">
                      KES {r.unitCostKes.toFixed(2)}
                    </td>
                    <td className="py-2.5 px-4 text-right font-mono font-bold">
                      KES {r.totalCostKes.toFixed(2)}
                    </td>
                    <td className="py-2.5 px-4">
                      {r.erpnextStatus === 'POSTED' ? (
                        <span className="px-2 py-0.5 rounded bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 font-mono">
                          {r.erpnextPurchaseInvoiceId || 'POSTED'}
                        </span>
                      ) : (
                        <button
                          onClick={() => handlePostReceiptErpNext(r.id)}
                          className="px-2.5 py-1 rounded bg-indigo-600 hover:bg-indigo-700 text-white font-semibold transition"
                          title={r.errorMessage || 'Post Purchase Invoice to ERPNext'}
                        >
                          Post to ERPNext
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Add New Product Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-3xl shadow-2xl max-w-md w-full overflow-hidden border border-zinc-200 dark:border-zinc-800">
            <div className="bg-emerald-600 text-white px-6 py-4 flex items-center justify-between">
              <h3 className="font-bold text-lg">Add New Product</h3>
              <button onClick={() => setShowAddModal(false)} className="text-2xl leading-none">
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateProduct} className="p-6 space-y-4 text-xs">
              {/* Barcode Scanner Input Card */}
              <div className="p-3.5 rounded-2xl bg-zinc-50 dark:bg-zinc-800/70 border border-zinc-200 dark:border-zinc-700 space-y-2">
                <div className="flex items-center justify-between">
                  <label className="font-bold text-zinc-800 dark:text-zinc-200 flex items-center gap-1.5">
                    <span>📟</span>
                    <span>Scan Barcode with Reader (or Type)</span>
                  </label>
                  {scannedViaReader ? (
                    <span className="text-[11px] font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-100 dark:bg-emerald-950/60 px-2 py-0.5 rounded-full flex items-center gap-1">
                      <span>✓</span> Scanned via Reader
                    </span>
                  ) : (
                    <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-medium flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                      Scanner Ready
                    </span>
                  )}
                </div>

                <div className="relative">
                  <input
                    ref={barcodeInputRef}
                    type="text"
                    required
                    placeholder="Point reader gun & scan barcode..."
                    value={newBarcode}
                    onChange={(e) => {
                      setNewBarcode(e.target.value);
                      if (scannedViaReader) setScannedViaReader(false);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault(); // Stop premature form submit from scanner Enter key
                        if (newBarcode.trim()) {
                          playPosBeep();
                          setScannedViaReader(true);
                          nameInputRef.current?.focus();
                        }
                      }
                    }}
                    className={`w-full px-3.5 py-2.5 rounded-xl border-2 font-mono text-sm font-semibold transition-all focus:outline-none ${
                      scannedViaReader
                        ? 'border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/30 text-emerald-900 dark:text-emerald-100'
                        : 'border-zinc-300 dark:border-zinc-600 bg-white dark:bg-zinc-900 focus:border-emerald-500'
                    }`}
                  />
                  {newBarcode && (
                    <button
                      type="button"
                      onClick={() => {
                        setNewBarcode('');
                        setScannedViaReader(false);
                        barcodeInputRef.current?.focus();
                      }}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs px-2 py-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200"
                      title="Clear & Re-scan"
                    >
                      ✕ Clear
                    </button>
                  )}
                </div>

                {newBarcode.trim() && products.some((p) => p.barcode === newBarcode.trim()) ? (
                  <p className="text-[11px] text-amber-600 dark:text-amber-400 font-semibold">
                    ⚠️ Barcode already assigned to &ldquo;{products.find((p) => p.barcode === newBarcode.trim())?.name}&rdquo;.
                  </p>
                ) : (
                  <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                    Scan with handheld barcode reader gun or type code. Scanner advances to Product Name automatically.
                  </p>
                )}
              </div>

              <div>
                <label className="block font-semibold mb-1">Product Name</label>
                <input
                  ref={nameInputRef}
                  type="text"
                  required
                  placeholder="e.g. Kenya Highlands Coffee 250g"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold mb-1">Shelf Price (KES)</label>
                  <input
                    type="number"
                    step="0.50"
                    required
                    placeholder="250.00"
                    value={newPrice}
                    onChange={(e) => setNewPrice(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
                <div>
                  <label className="block font-semibold mb-1">Buying Cost (COGS)</label>
                  <input
                    type="number"
                    step="0.50"
                    placeholder="180.00"
                    value={newCostPrice}
                    onChange={(e) => setNewCostPrice(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
                <div>
                  <label className="block font-semibold mb-1">Tax Category</label>
                  <select
                    value={newTaxCat}
                    onChange={(e) => setNewTaxCat(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                  >
                    <option value="A">Category A - 16% Std</option>
                    <option value="C">Category C - 8% Special</option>
                    <option value="B">Category B - 0% Zero-Rated</option>
                    <option value="E">Category E - VAT Exempt</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold mb-1">Initial Stock Quantity</label>
                  <input
                    type="number"
                    value={newStock}
                    onChange={(e) => setNewStock(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
                <div>
                  <label className="block font-semibold mb-1">Low-Stock Reorder Level</label>
                  <input
                    type="number"
                    value={newReorder}
                    onChange={(e) => setNewReorder(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
              </div>
              <button
                type="submit"
                className="w-full py-3 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-xl shadow-xs transition"
              >
                Save Product to Database
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Receive Stock (GRN) Modal */}
      {showReceiveModal && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white dark:bg-zinc-900 rounded-3xl shadow-2xl max-w-md w-full max-h-[92vh] overflow-y-auto border border-zinc-200 dark:border-zinc-800">
            <div className="bg-indigo-600 text-white px-6 py-4 flex items-center justify-between sticky top-0 z-30">
              <h3 className="font-bold text-lg">Receive Supplier Stock (GRN)</h3>
              <button onClick={() => setShowReceiveModal(false)} className="text-2xl leading-none">
                &times;
              </button>
            </div>
            <form onSubmit={handleReceiveStock} className="p-6 space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                  Supplier Name
                </label>
                <input
                  type="text"
                  required
                  value={recSupplier}
                  onChange={(e) => setRecSupplier(e.target.value)}
                  placeholder="e.g. Brookside Dairy Ltd"
                  className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                />
              </div>
              <div>
                <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                  Supplier Invoice Number (optional)
                </label>
                <input
                  type="text"
                  value={recInvoiceNo}
                  onChange={(e) => setRecInvoiceNo(e.target.value)}
                  placeholder="e.g. INV-2026-0412"
                  className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                />
              </div>
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block font-semibold text-zinc-600 dark:text-zinc-400">
                    Product {recCreatingNew ? '(New Catalog Item)' : '(Search or Type New)'}
                  </label>
                  {recCreatingNew ? (
                    <button
                      type="button"
                      onClick={() => {
                        setRecCreatingNew(false);
                        setRecProductId('');
                        setRecDropdownOpen(true);
                      }}
                      className="text-[11px] font-semibold text-indigo-600 dark:text-indigo-400 hover:underline"
                    >
                      ← Pick existing product
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => {
                        setRecCreatingNew(true);
                        setRecProductId('');
                        setRecNewName(recProductQuery.trim());
                        setRecDropdownOpen(false);
                      }}
                      className="text-[11px] font-semibold text-emerald-600 dark:text-emerald-400 hover:underline"
                    >
                      + Add New Product
                    </button>
                  )}
                </div>

                {!recCreatingNew ? (
                  <div className="relative">
                    <div className="relative">
                      <input
                        type="text"
                        value={recProductQuery}
                        onFocus={() => setRecDropdownOpen(true)}
                        onChange={(e) => {
                          setRecProductQuery(e.target.value);
                          setRecProductId('');
                          setRecDropdownOpen(true);
                        }}
                        placeholder="Type product name or scan barcode..."
                        className="w-full px-3 py-2 pr-8 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                      />
                      {recProductQuery && (
                        <button
                          type="button"
                          onClick={() => {
                            setRecProductQuery('');
                            setRecProductId('');
                            setRecDropdownOpen(true);
                          }}
                          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600 text-sm"
                          title="Clear selection"
                        >
                          &times;
                        </button>
                      )}
                    </div>

                    {recDropdownOpen && (
                      <div className="absolute z-20 mt-1 w-full max-h-52 overflow-y-auto rounded-xl border border-zinc-200 dark:border-zinc-700 bg-white dark:bg-zinc-800 shadow-xl divide-y divide-zinc-100 dark:divide-zinc-700/60">
                        {products
                          .filter((p) => {
                            const q = recProductQuery.trim().toLowerCase();
                            if (!q) return true;
                            return (
                              p.name.toLowerCase().includes(q) ||
                              (p.barcode && p.barcode.toLowerCase().includes(q))
                            );
                          })
                          .map((p) => (
                            <button
                              key={p.id}
                              type="button"
                              onClick={() => {
                                setRecProductId(p.id);
                                setRecProductQuery(p.name);
                                setRecDropdownOpen(false);
                                setRecVatRate(
                                  p.taxCategory === 'A' ? '0.16' : p.taxCategory === 'C' ? '0.08' : '0'
                                );
                              }}
                              className={`w-full text-left px-3 py-2 hover:bg-indigo-50 dark:hover:bg-indigo-950/50 flex items-center justify-between gap-2 ${
                                recProductId === p.id ? 'bg-indigo-50 dark:bg-indigo-950/60 font-semibold' : ''
                              }`}
                            >
                              <span className="truncate">
                                {p.name}{' '}
                                <span className="text-zinc-400 font-mono text-[11px]">({p.barcode})</span>
                              </span>
                              <span className="text-[11px] font-mono text-zinc-500 shrink-0">
                                Stock: {p.stockQuantity}
                              </span>
                            </button>
                          ))}

                        <button
                          type="button"
                          onClick={() => {
                            setRecCreatingNew(true);
                            setRecProductId('');
                            setRecNewName(recProductQuery.trim());
                            setRecDropdownOpen(false);
                          }}
                          className="w-full text-left px-3 py-2.5 bg-emerald-50/70 dark:bg-emerald-950/40 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 text-emerald-700 dark:text-emerald-300 font-semibold flex items-center gap-1.5"
                        >
                          <span>＋</span>
                          <span>
                            {recProductQuery.trim()
                              ? `Add & Receive "${recProductQuery.trim()}" as a New Product`
                              : 'Add & Receive a New Product not in list'}
                          </span>
                        </button>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="p-3 rounded-2xl border border-emerald-500/40 bg-emerald-50/40 dark:bg-emerald-950/20 space-y-2.5">
                    <div>
                      <label className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
                        New Product Name
                      </label>
                      <input
                        type="text"
                        required
                        value={recNewName}
                        onChange={(e) => setRecNewName(e.target.value)}
                        placeholder="e.g. Pembe Maize Flour 2kg"
                        className="w-full px-3 py-1.5 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                      />
                    </div>
                    <div className="grid grid-cols-2 gap-2.5">
                      <div>
                        <label className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
                          Barcode / SKU
                        </label>
                        <div className="flex gap-1">
                          <input
                            type="text"
                            required
                            value={recNewBarcode}
                            onChange={(e) => setRecNewBarcode(e.target.value)}
                            placeholder="Scan or type barcode"
                            className="flex-1 min-w-0 px-2.5 py-1.5 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                          />
                          <button
                            type="button"
                            onClick={() =>
                              setRecNewBarcode(`616${Math.floor(100000000 + Math.random() * 900000000)}`)
                            }
                            className="px-2 py-1.5 rounded-xl bg-zinc-200 dark:bg-zinc-700 text-zinc-700 dark:text-zinc-200 font-semibold shrink-0"
                            title="Generate internal barcode"
                          >
                            Gen
                          </button>
                        </div>
                      </div>
                      <div>
                        <label className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
                          Selling Price (KES)
                        </label>
                        <input
                          type="number"
                          step="0.01"
                          min="0.01"
                          required
                          value={recNewPrice}
                          onChange={(e) => setRecNewPrice(e.target.value)}
                          placeholder="0.00"
                          className="w-full px-2.5 py-1.5 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-2.5">
                      <div>
                        <label className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
                          KRA Tax Category
                        </label>
                        <select
                          value={recNewTaxCat}
                          onChange={(e) => {
                            const cat = e.target.value;
                            setRecNewTaxCat(cat);
                            setRecVatRate(cat === 'A' ? '0.16' : cat === 'C' ? '0.08' : '0');
                          }}
                          className="w-full px-2.5 py-1.5 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                        >
                          <option value="A">Category A - 16% Std</option>
                          <option value="C">Category C - 8% Special</option>
                          <option value="B">Category B - 0% Zero-Rated</option>
                          <option value="E">Category E - VAT Exempt</option>
                        </select>
                      </div>
                      <div>
                        <label className="block font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
                          Reorder Level
                        </label>
                        <input
                          type="number"
                          step="1"
                          min="0"
                          value={recNewReorder}
                          onChange={(e) => setRecNewReorder(e.target.value)}
                          className="w-full px-2.5 py-1.5 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                        />
                      </div>
                    </div>
                  </div>
                )}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                    Quantity Received
                  </label>
                  <input
                    type="number"
                    step="0.001"
                    min="0.001"
                    required
                    value={recQty}
                    onChange={(e) => setRecQty(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                    Unit Cost (KES)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    required
                    value={recUnitCost}
                    onChange={(e) => setRecUnitCost(e.target.value)}
                    placeholder="0.00"
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
              </div>
              <div>
                <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                  Supplier Input VAT Rate (for Monthly KRA VAT Offset)
                </label>
                <select
                  value={recVatRate}
                  onChange={(e) => setRecVatRate(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800"
                >
                  <option value="0.16">16% Standard Input VAT</option>
                  <option value="0.08">8% Special Rate Input VAT</option>
                  <option value="0">0% Zero-Rated / Non-VAT Supplier</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                    Batch / Lot # (FEFO)
                  </label>
                  <input
                    type="text"
                    value={recBatchNo}
                    onChange={(e) => setRecBatchNo(e.target.value)}
                    placeholder="Auto if blank"
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-zinc-600 dark:text-zinc-400 mb-1">
                    Expiry Date (optional)
                  </label>
                  <input
                    type="date"
                    value={recExpiryDate}
                    onChange={(e) => setRecExpiryDate(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 font-mono"
                  />
                </div>
              </div>
              <label className="flex items-center gap-2 text-zinc-700 dark:text-zinc-300 cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={recPostErpNext}
                  onChange={(e) => setRecPostErpNext(e.target.checked)}
                  className="rounded border-zinc-300 dark:border-zinc-700"
                />
                <span>Immediately post Purchase Invoice to ERPNext</span>
              </label>
              <button
                type="submit"
                className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl shadow-xs transition"
              >
                Record Stock Receipt
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
