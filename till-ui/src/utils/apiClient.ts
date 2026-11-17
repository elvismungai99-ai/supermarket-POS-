let currentAuthToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAuthToken(token: string | null | undefined) {
  currentAuthToken = token || null;
}

export function getAuthToken(): string | null {
  return currentAuthToken;
}

export function setOnUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers || {});
  if (currentAuthToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${currentAuthToken}`);
  }
  const response = await fetch(input, { ...init, headers });
  if (response.status === 401 && unauthorizedHandler && typeof input === 'string' && !input.includes('/api/auth/')) {
    unauthorizedHandler();
  }
  return response;
}

export async function downloadAuthenticatedFile(url: string, fallbackFilename: string): Promise<void> {
  const res = await apiFetch(url);
  if (!res.ok) {
    throw new Error(`Download failed (HTTP ${res.status})`);
  }
  const blob = await res.blob();
  const objUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objUrl;
  a.download = fallbackFilename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objUrl);
}
