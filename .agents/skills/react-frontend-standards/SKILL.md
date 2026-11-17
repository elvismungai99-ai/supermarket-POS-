---
name: react-frontend-standards
description: Enforces React component organization, TypeScript typing standards, custom hooks, and UI styling conventions for till-ui.
---

# React Frontend Standards (`till-ui/`)

Apply these rules whenever creating or modifying components, hooks, or utilities under `till-ui/src/`.

## Core Rules

1. **Component Modularity & Custom Hooks:** Keep React components small and single-purpose. For complex features, extract data fetching, polling, or multi-step form workflows into custom hooks under `till-ui/src/hooks/use<FeatureName>.ts` rather than growing monolithic views.
2. **TypeScript Strictness:** Never introduce `any` in new or edited code. Define explicit TypeScript `interface` / `type` declarations for all component props and backend API payloads (`Response` / `Request` types), and narrow caught errors with `err instanceof Error ? err.message : ...`.
3. **State Management & API Calls:** Keep `till-ui` dependency-free (no external state/query libraries unless explicitly approved). Fetch server data via `apiFetch` / `downloadAuthenticatedFile` from `src/utils/apiClient.ts` (so JWT `Authorization` headers and `401` session expiration handling stay automatic), and reserve React `useState` / `useReducer` strictly for local component UI state.
4. **Clean Imports:** Use clean package-relative imports matching the current setup (`../utils/apiClient`, `./MonthlyTaxView`). Do **not** use `@/` path aliases unless `paths` in `till-ui/tsconfig.app.json` and `resolve.alias` in `till-ui/vite.config.ts` are configured together first; avoid deep `../../../` relative chains by organizing components cleanly.
5. **Styling & Accessibility:** Follow the existing Tailwind CSS v4 dark-slate / emerald POS palette (for Manager/Super Admin panels) and high-contrast till controls, keeping modals scrollable (`max-h-[92vh] overflow-y-auto`) on smaller laptop viewports.

## Verification

Run after frontend changes (must finish with 0 `oxlint` errors and clean `tsc -b && vite build`):

```bash
cd till-ui && npm run lint && npm run build
```
