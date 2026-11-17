import { useState, useEffect, useRef } from 'react';
import type { ProductDto } from '../App';
import { apiFetch } from '../utils/apiClient';
import { playSuccessChime } from '../utils/sound';

interface ProductSearchSelectorProps {
  onSelectProduct: (product: ProductDto) => void;
}

export function ProductSearchSelector({ onSelectProduct }: ProductSearchSelectorProps) {
  const [searchTerm, setSearchTerm] = useState('');
  const [results, setResults] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [justAddedId, setJustAddedId] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Debounced search when user types product name
  useEffect(() => {
    const trimmed = searchTerm.trim();
    if (trimmed.length < 2) {
      setResults([]);
      setHasSearched(false);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await apiFetch(`/api/products/search?q=${encodeURIComponent(trimmed)}`);
        if (res.ok) {
          const data: ProductDto[] = await res.json();
          setResults(data);
          setHasSearched(true);
        } else {
          setResults([]);
        }
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [searchTerm]);

  const handleManualSelect = (product: ProductDto) => {
    onSelectProduct(product);
    playSuccessChime();
    setJustAddedId(product.id);
    setTimeout(() => {
      setJustAddedId((curr) => (curr === product.id ? null : curr));
    }, 1200);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      // If there is exactly one result, add it automatically
      if (results.length === 1) {
        handleManualSelect(results[0]);
      }
    }
  };

  const handleClear = () => {
    setSearchTerm('');
    setResults([]);
    setHasSearched(false);
    inputRef.current?.focus();
  };

  return (
    <div className="space-y-3">
      {/* Search Bar Input */}
      <div className="relative">
        <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-zinc-400">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
        <input
          ref={inputRef}
          type="text"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type product name to search (e.g. Milk, Bread, Sugar, Flour)..."
          className="w-full pl-11 pr-24 py-3.5 text-base rounded-xl border-2 border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-800 focus:outline-none focus:ring-4 focus:ring-blue-100 dark:focus:ring-blue-900 focus:border-blue-500 transition-all font-medium placeholder-zinc-400"
        />
        <div className="absolute inset-y-0 right-0 pr-3 flex items-center gap-2">
          {loading && (
            <span className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          )}
          {searchTerm && (
            <button
              type="button"
              onClick={handleClear}
              className="text-xs px-2 py-1 bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-700 dark:hover:bg-zinc-600 rounded-lg text-zinc-500 transition"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Results Dropdown / List */}
      {hasSearched && (
        <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl shadow-xl overflow-hidden max-h-80 overflow-y-auto">
          {results.length === 0 ? (
            <div className="p-6 text-center text-zinc-500 text-sm">
              <p className="font-semibold">No products found matching &ldquo;{searchTerm}&rdquo;</p>
              <p className="text-xs text-zinc-400 mt-1">Try another brand name or scan the barcode reader instead.</p>
            </div>
          ) : (
            <div className="divide-y divide-zinc-100 dark:divide-zinc-800">
              <div className="px-4 py-2 bg-zinc-50 dark:bg-zinc-800/50 text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex justify-between">
                <span>Matching Products ({results.length})</span>
                <span>Click item to add to sale</span>
              </div>
              {results.map((product) => {
                const isJustAdded = justAddedId === product.id;
                const cat = (product.taxCategory || 'A').toUpperCase();
                const taxLabel = cat === 'A' ? '16% VAT' : cat === 'B' ? '0% Zero' : 'Exempt';
                const taxBadgeColor = cat === 'A'
                  ? 'bg-purple-50 text-purple-700 border-purple-200 dark:bg-purple-950/40 dark:text-purple-300 dark:border-purple-800'
                  : cat === 'B'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
                  : 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800';

                return (
                  <div
                    key={product.id}
                    onClick={() => handleManualSelect(product)}
                    className="p-3.5 flex items-center justify-between hover:bg-blue-50/70 dark:hover:bg-blue-950/30 cursor-pointer transition group"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-sm text-zinc-900 dark:text-zinc-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition">
                          {product.name}
                        </span>
                        <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold border ${taxBadgeColor}`}>
                          {taxLabel}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-zinc-500">
                        {product.barcode && (
                          <span className="font-mono text-[11px] bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 rounded text-zinc-600 dark:text-zinc-400">
                            📟 {product.barcode}
                          </span>
                        )}
                        {product.stockQuantity !== undefined && (
                          <span className={product.stockQuantity <= (product.reorderLevel ?? 10) ? 'text-amber-600 font-semibold' : 'text-zinc-500'}>
                            Stock: {product.stockQuantity} units
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <span className="font-mono font-bold text-base text-zinc-900 dark:text-zinc-100">
                        KES {Number(product.priceKes).toFixed(2)}
                      </span>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleManualSelect(product);
                        }}
                        className={`px-3 py-1.5 rounded-xl font-bold text-xs transition shadow-xs flex items-center gap-1 ${
                          isJustAdded
                            ? 'bg-emerald-600 text-white'
                            : 'bg-blue-600 hover:bg-blue-700 text-white'
                        }`}
                      >
                        {isJustAdded ? (
                          <>
                            <span>✓</span>
                            <span>Added!</span>
                          </>
                        ) : (
                          <>
                            <span>+</span>
                            <span>Add</span>
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
