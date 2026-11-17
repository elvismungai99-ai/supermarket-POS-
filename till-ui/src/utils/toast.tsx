import { createContext, useCallback, useContext, useRef, useState } from 'react';
import type { ReactNode } from 'react';

export type ToastType = 'success' | 'error' | 'info';

interface ToastState {
  visible: boolean;
  title: string;
  message: string;
  type: ToastType;
}

interface ToastContextValue {
  showToast: (message: string, type?: ToastType, title?: string) => void;
}

const ToastContext = createContext<ToastContextValue>({
  showToast: () => {},
});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState>({
    visible: false,
    title: 'Success',
    message: '',
    type: 'success',
  });
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((message: string, type: ToastType = 'success', title?: string) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    const resolvedTitle =
      title || (type === 'success' ? 'Success' : type === 'error' ? 'Notice' : 'Information');

    setToast({ visible: true, title: resolvedTitle, message, type });
    timerRef.current = setTimeout(() => {
      setToast((prev) => ({ ...prev, visible: false }));
    }, 3200);
  }, []);

  const iconBg =
    toast.type === 'success'
      ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
      : toast.type === 'error'
      ? 'bg-rose-500/20 text-rose-400 border-rose-500/30'
      : 'bg-indigo-500/20 text-indigo-400 border-indigo-500/30';

  const iconSymbol = toast.type === 'success' ? '✓' : toast.type === 'error' ? '!' : 'i';

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div
        className={`fixed bottom-6 right-6 z-50 bg-slate-900 border border-slate-700 text-white px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-3 max-w-sm transition-all duration-300 pointer-events-none ${
          toast.visible ? 'translate-y-0 opacity-100' : 'translate-y-20 opacity-0'
        }`}
      >
        <div
          className={`w-7 h-7 rounded-full border flex items-center justify-center font-bold text-xs shrink-0 ${iconBg}`}
        >
          {iconSymbol}
        </div>
        <div className="min-w-0">
          <p className="text-xs font-bold text-white truncate">{toast.title}</p>
          <p className="text-xs text-slate-300 leading-snug">{toast.message}</p>
        </div>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
