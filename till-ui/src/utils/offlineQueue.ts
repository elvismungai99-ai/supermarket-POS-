import type { ReceiptDto } from '../components/ReceiptModal';
import { apiFetch } from './apiClient';

export const HARDWARE_AGENT_URL = 'http://127.0.0.1:9001';
const OFFLINE_QUEUE_KEY = 'kenya_pos_offline_sales_v1';
const CATALOG_CACHE_KEY = 'kenya_pos_catalog_cache_v1';
const HW_TOKEN_KEY = 'supermart_hw_token';
const IDB_NAME = 'supermart_pos_offline_db';
const IDB_VERSION = 1;

export interface OfflineQueuedSale {
  clientSaleId: string;
  queuedAt: string;
  payload: any;
  totalKes: number;
  integrityTag?: string;
}

function computeIntegrityTag(item: Pick<OfflineQueuedSale, 'clientSaleId' | 'queuedAt' | 'totalKes' | 'payload'>): string {
  const canonical = JSON.stringify({
    clientSaleId: item.clientSaleId,
    queuedAt: item.queuedAt,
    totalKes: Number(item.totalKes).toFixed(2),
    payload: item.payload,
  });
  let h1 = 0xdeadbeef ^ canonical.length;
  let h2 = 0x41c6ce57 ^ canonical.length;
  for (let i = 0; i < canonical.length; i++) {
    const ch = canonical.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return `${(h2 >>> 0).toString(16).padStart(8, '0')}${(h1 >>> 0).toString(16).padStart(8, '0')}`;
}

export interface CachedCatalogItem {
  id: string;
  name: string;
  priceKes: number;
  taxCategory: string;
  stockQuantity?: number;
  reorderLevel?: number;
  barcode?: string;
}

export function generateUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function openOfflineDb(): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    if (typeof indexedDB === 'undefined') {
      resolve(null);
      return;
    }
    try {
      const req = indexedDB.open(IDB_NAME, IDB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('sales')) {
          db.createObjectStore('sales', { keyPath: 'clientSaleId' });
        }
        if (!db.objectStoreNames.contains('catalog')) {
          db.createObjectStore('catalog', { keyPath: 'id' });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

export function getOfflineQueue(): OfflineQueuedSale[] {
  try {
    const raw = localStorage.getItem(OFFLINE_QUEUE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function enqueueOfflineSale(item: OfflineQueuedSale): OfflineQueuedSale[] {
  const sealed: OfflineQueuedSale = {
    ...item,
    integrityTag: computeIntegrityTag(item),
  };
  const current = getOfflineQueue();
  const next = [...current, sealed];
  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(next));

  // Mirror asynchronously into IndexedDB for resilience
  openOfflineDb().then((db) => {
    if (!db) return;
    try {
      const tx = db.transaction('sales', 'readwrite');
      tx.objectStore('sales').put(sealed);
    } catch {
      // ignore
    }
  });

  return next;
}

export async function cacheCatalogProducts(items: CachedCatalogItem[]): Promise<void> {
  try {
    localStorage.setItem(CATALOG_CACHE_KEY, JSON.stringify(items));
  } catch {
    // fallback if quota exceeded
  }
  const db = await openOfflineDb();
  if (!db) return;
  try {
    const tx = db.transaction('catalog', 'readwrite');
    const store = tx.objectStore('catalog');
    store.clear();
    for (const item of items) {
      store.put(item);
    }
  } catch {
    // ignore
  }
}

export function findCachedProductByBarcode(barcode: string): CachedCatalogItem | null {
  try {
    const raw = localStorage.getItem(CATALOG_CACHE_KEY);
    if (!raw) return null;
    const list: CachedCatalogItem[] = JSON.parse(raw);
    return list.find((p) => p.barcode === barcode) || null;
  } catch {
    return null;
  }
}

export async function syncOfflineQueue(authToken?: string): Promise<{ synced: number; remaining: number }> {
  const queue = getOfflineQueue();
  if (queue.length === 0) return { synced: 0, remaining: 0 };

  const remaining: OfflineQueuedSale[] = [];
  let synced = 0;

  for (const item of queue) {
    if (item.integrityTag && computeIntegrityTag(item) !== item.integrityTag) {
      console.warn('Rejected tampered offline queued sale (integrity tag mismatch):', item.clientSaleId);
      continue;
    }
    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

      const res = await apiFetch('/api/sales', {
        method: 'POST',
        headers,
        body: JSON.stringify(item.payload),
      });

      if (res.ok || res.status === 201) {
        synced++;
        const db = await openOfflineDb();
        if (db) {
          try {
            db.transaction('sales', 'readwrite').objectStore('sales').delete(item.clientSaleId);
          } catch {
            // ignore
          }
        }
      } else if (res.status >= 400 && res.status < 500) {
        console.warn('Dropped invalid offline queued sale', item.clientSaleId, res.status);
      } else {
        remaining.push(item);
      }
    } catch {
      remaining.push(item);
    }
  }

  localStorage.setItem(OFFLINE_QUEUE_KEY, JSON.stringify(remaining));
  return { synced, remaining: remaining.length };
}

function hardwareHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  try {
    const hwToken =
      localStorage.getItem(HW_TOKEN_KEY) ||
      (import.meta.env.VITE_HARDWARE_TOKEN as string | undefined);
    if (hwToken) h['X-Hardware-Token'] = hwToken;
  } catch {
    // ignore
  }
  return h;
}

export async function checkHardwareHealth(): Promise<{ online: boolean; printerMode?: string; scaleSource?: string }> {
  try {
    const res = await fetch(`${HARDWARE_AGENT_URL}/health`, { signal: AbortSignal.timeout(1500) });
    if (!res.ok) return { online: false };
    const data = await res.json();
    return { online: data.status === 'UP', printerMode: data.printerMode, scaleSource: data.scaleSource };
  } catch {
    return { online: false };
  }
}

export async function readScaleWeight(): Promise<{ weightKg: number; unit: string; source: string }> {
  const res = await fetch(`${HARDWARE_AGENT_URL}/scale/weight`, {
    headers: hardwareHeaders(),
    signal: AbortSignal.timeout(2000),
  });
  if (!res.ok) throw new Error('Scale agent unavailable');
  return res.json();
}

export async function printEscPosReceipt(receipt: ReceiptDto, openDrawer = true) {
  const res = await fetch(`${HARDWARE_AGENT_URL}/print`, {
    method: 'POST',
    headers: hardwareHeaders(),
    body: JSON.stringify({ ...receipt, openDrawer }),
  });
  if (!res.ok) throw new Error('Hardware printer returned HTTP ' + res.status);
  return res.json();
}

export async function openCashDrawer() {
  const res = await fetch(`${HARDWARE_AGENT_URL}/drawer/open`, {
    method: 'POST',
    headers: hardwareHeaders(),
  });
  if (!res.ok) throw new Error('Cash drawer endpoint failed');
  return res.json();
}
