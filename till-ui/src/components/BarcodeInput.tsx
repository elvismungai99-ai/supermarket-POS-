import { useState, useRef, useEffect } from 'react';

export function BarcodeInput({
  onScan,
  isLoading,
}: {
  onScan: (code: string) => void;
  isLoading?: boolean;
}) {
  const [value, setValue] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, [isLoading]);

  return (
    <div className="relative w-full">
      <input
        ref={inputRef}
        autoFocus
        value={value}
        disabled={isLoading}
        placeholder="Scan barcode or type & press Enter (e.g. 6161100001234)..."
        className="w-full px-5 py-4 text-xl font-mono border-2 border-emerald-500 rounded-xl shadow-sm focus:outline-none focus:ring-4 focus:ring-emerald-200 transition-all bg-white dark:bg-zinc-800 dark:border-emerald-600 dark:text-zinc-100 disabled:opacity-60"
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && value.trim()) {
            onScan(value.trim());
            setValue('');
          }
        }}
      />
      {isLoading && (
        <div className="absolute right-4 top-1/2 -translate-y-1/2 flex items-center gap-2 text-sm text-emerald-600 font-semibold">
          <span className="inline-block w-4 h-4 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></span>
          Looking up...
        </div>
      )}
    </div>
  );
}
