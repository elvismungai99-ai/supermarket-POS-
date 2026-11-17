# Supermarket POS — Industry Comparison, Enhancement Roadmap & Security Architecture Blueprint

**Prepared by:** Senior Software Engineering, Frontend, DevOps & Cybersecurity Review  
**Target System:** `supermarketPOS` (`pos-backend` Java 21 / Spring Boot 4 + `till-ui` React 19 / TypeScript + `hardware-agent` Node.js + PostgreSQL 17 + ERPNext)  
**Document Status:** Design & Implementation Specification (Pending Review — No Code Implemented Yet)

---

## 1. Competitive Comparison: Our System vs. Leading Supermarket POS Platforms

Supermarket checkouts differ significantly from general retail or cafes: they require sub-second barcode scanning, weighted produce scales (3-decimal precision), multi-rate statutory VAT handling, perishable batch/expiry control, high-lane concurrency, and uninterrupted offline operation.

| Capability | Our Current POS (`supermarketPOS`) | **ERPNext POS** | **Odoo POS** | **Square for Retail** | **Loyverse POS** | **Erply POS** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Target Store Size** | Single/multi-lane Kenyan supermarkets | Mid-to-large tech-enabled stores | Growing SMBs to enterprise | Small convenience & mini-marts | Corner shops & micro-retail | Medium-to-large supermarket chains |
| **Core Stack** | Spring Boot 4 + React 19 + Postgres 17 + ERPNext GL | Frappe / Python + MariaDB | Python + PostgreSQL | Proprietary Cloud SaaS | Cloud SaaS (iOS/Android) | Cloud ERP + Desktop/Web |
| **Kenyan Tax & VAT (A/B/C/E)** | **Native** (16%, 8%, 0%, Exempt, HALF_UP inclusive) | Needs Kenya localization app | Needs partner tax localization | Not Kenyan-tax native | Manual tax setup | Partner/custom setup |
| **M-Pesa Integration** | **Built-in** Daraja STK Push + PDQ Ref | Via Kenya compliance add-on | Custom/third-party module | Not native | Not native | Custom gateway |
| **Accounting / GL** | **Integrated** with ERPNext (Daily GL + Purchase Invoices + CSV) | Native GL built-in | Native via Odoo Accounting | Basic (needs Xero/QB) | Needs QuickBooks/Xero | External ERP integrations |
| **Weighing Scale & Hardware** | Local Node agent (`:9001` ESC/POS + RS-232 scale) | Custom scripts / Print Node | IoT Box / network scales | Proprietary hardware lock-in | Basic Bluetooth/USB | Strong enterprise scale driver |
| **Offline Continuity** | Local offline cash queue + DB `outbox` | Browser IndexedDB offline POS | Local IoT/browser cache | Offline card limits | Basic offline queue | Strong offline store server |
| **Batch, Lot & Expiry Dates** | **Missing** (tracks aggregate stock only) | Native batch & expiry tracking | Native with Traceability | Basic | No | Strong |
| **Promotions / Bundles / Happy Hour** | **Missing** (flat SKU shelf price) | Native Pricing Rules | Native Pricelists & Combos | Basic discounts | Basic discounts | Advanced retail promo engine |
| **Multi-Branch & Transfers** | Schema has `shop_id`, UI/API single-store focused | Native multi-warehouse | Native multi-branch | Paid multi-location | Paid add-on | Strong multi-store sync |
| **Supplier PO Lifecycle** | Direct GRN -> Purchase Invoice (`stock_receipt`) | Full RFQ -> PO -> Receipt -> PI | Full Purchase workflow | Basic POs | Basic POs | Full replenishment & PO |
| **Per-Item Discount / Price Override** | **Missing** (Cashier keypad cannot override without manager PIN workflow) | Manager PIN override | Manager PIN override | Manager PIN | Role permission | Manager PIN + audit log |

### Key Takeaway
Our system **beats generic global SaaS tools** (Square, Loyverse) on Kenyan VAT accuracy (`BigDecimal` inclusive rounding), M-Pesa Daraja readiness, strict Cashier-only till isolation, and automated double-entry ERPNext GL posting. Where commercial supermarket platforms (Odoo, Erply, native ERPNext) lead is **perishable/expiry tracking, promotions/pricing rules, manager-PIN till overrides, and store-server replication across branches**.

---

## 2. Recommended Feature Roadmap (Frontend, Backend & DevOps)

### 2.1 Backend & Domain Enhancements (`pos-backend`)

1. **Perishable Batch, Lot & Expiry Tracking (Critical for Supermarkets)**
   - Add `product_batch` (`id`, `product_id`, `batch_number`, `expiry_date`, `quantity_remaining`, `unit_cost_kes`, `stock_receipt_id`).
   - Update `StockReceivingController` (`/api/inventory/receive`) to capture expiry dates on delivery and deduct stock via **FEFO (First-Expired, First-Out)** on sales.
   - Expose `/api/inventory/expiring-soon?days=14` alerts to the Store Manager dashboard.
2. **Weighted Barcode Parsing (Scale-Printed Labels)**
   - Supermarkets print variable-weight EAN-13 labels (`20XXXXXWWWWWC` / `21...`) at deli, butchery, and produce counters where digits 2–6 identify the PLU/SKU and digits 7–11 encode weight or price.
   - Add a `BarcodeParserService` so scanning a scale-printed barcode automatically extracts the product and exact `BigDecimal` weight/price.
3. **Promotions, Loyalty Tiers & Split-Tender Checkout**
   - **Split Payments**: Allow a single basket to be settled partly in Cash + partly via M-Pesa STK Push (currently `CreateSaleRequest.payment` accepts a single `PaymentRequest`).
   - **Promotional Rules**: Support scheduled price discounts, "Buy X Get Y", and wholesale tier pricing (`min_qty` breaks).
4. **Manager Override Workflow (Real-Time PIN Authorization at the Till)**
   - Allow cashiers to request an in-cart **Line Void**, **Item Discount**, or **Price Override** at the till, gated by an instant modal asking a Store Manager to enter their credentials/PIN (`POST /api/auth/verify-supervisor-override`), logged into an immutable `audit_log` table.
5. **Multi-Branch & Central Replication (`shop` & `till` entities)**
   - Promote `shopId` and `tillId` from static UUIDs to full `shop` and `till` master tables with branch-specific COA cost centers (`Main - CBD - SP`, `Main - Westlands - SP`), inter-branch stock transfers, and store-server-to-HQ replication.
6. **Full Purchase Order (PO) Cycle & Supplier Management**
   - Add a `supplier` master table (KRA PIN, payment terms, ERPNext supplier ID) and `purchase_order` -> `stock_receipt` (partial delivery variance check) -> ERPNext `Purchase Invoice`.

### 2.2 Frontend Developer Specification (`till-ui`)

1. **Centralized Authenticated API Client (`src/utils/apiClient.ts`)**
   - Replace scattered raw `fetch('/api/...')` calls across components (`CashierManagementView`, `ManagerInventoryView`, `ReturnsView`, `AccountingErpNextView`, `CashierShiftModal`, `App.tsx`) with one wrapper that injects `Authorization: Bearer <token>`, handles `401` (auto-logout with session-expired banner) and `403` (clean permission toast), and standardizes error parsing.
2. **Zero External CDN Dependencies for Offline LAN Operation**
   - Remove the Google Fonts `<link>` in `till-ui/index.html` and self-host `@fontsource/inter` inside `node_modules` so tills on air-gapped store LANs render instantly with zero console network errors.
3. **Keyboard-First Cashier Ergonomics & Customer Pole Display**
   - Bind global till shortcuts: `F1` (Search by Name), `F2` (Hold Till), `F4` (Open Payment Modal), `F8` (Void Selected Line with Manager Override), `Esc` (Close Modal / Clear Buffer).
   - Add a secondary browser window / `BroadcastChannel` route (`/customer-display`) for a rear-facing customer screen showing scanned items, running total, and M-Pesa STK prompt status.
4. **Upgrade Local Offline Queue from `localStorage` to `IndexedDB`**
   - `localStorage` has a ~5 MB limit and synchronous main-thread I/O. Move `offlineQueue.ts` and a full local product catalog cache (`id`, `barcode`, `name`, `priceKes`, `taxCategory`) into **IndexedDB** so cashiers can scan and search *any* SKU by barcode or name even when the store server or network link is temporarily down.
5. **Manager & Admin UX Upgrades**
   - Add interactive sales trend charts (hourly till throughput, Cash vs. M-Pesa vs. Bank split, category margin) and live pending **Till Override Approval** notifications.
   - Add Z-Report thermal print formatting when a Cashier closes a shift (`CashierShiftModal`).

### 2.3 DevOps & Infrastructure Specification (`infra/` & `.github/`)

1. **Containerize Full Stack & Isolate Networks (`infra/docker-compose.yml`)**
   - Package `pos-backend` (multi-stage Eclipse Temurin 21 JRE image, non-root user) and `till-ui` (built static assets served by **Nginx**) behind an Nginx/Caddy reverse proxy terminating **TLS 1.3**.
   - Move PostgreSQL onto an internal Docker bridge network (`internal: true`) — never expose port `5432`/`5433` on `0.0.0.0` in production.
2. **Database Backup, WAL Archiving & Restore Drills**
   - Run scheduled automated `pg_dump` / `pgBackRest` backups encrypted with `age`/`gpg`, replicated off-site (e.g., S3 / GCS / offsite NAS), with weekly automated restore verification into a staging DB.
3. **CI/CD Pipeline Hardening (`.github/workflows/ci.yml`)**
   - Note current CI sets `java-version: 25` while `backend/pom.xml` specifies `<java.version>21</java.version>` — align CI to Java 21 LTS.
   - Add stages:
     - Frontend: `npm run lint` (`oxlint`), `tsc -b`, `npm run build`, `npm audit --audit-level=high`.
     - Backend: `./mvnw -B verify` (unit + Testcontainers integration tests), OWASP Dependency-Check / Trivy vulnerability scan.
     - Container scan (`trivy image`) and SBOM generation before pushing signed images.
4. **Observability & Alerting Stack**
   - Export Spring Boot Actuator + Micrometer Prometheus metrics (`/actuator/prometheus`), HikariCP pool stats, PostgreSQL exporter, and structured JSON logs (correlation IDs, `shopId`, `tillId`, `cashierId` — **never** PINs, tokens, or API secrets) to **Prometheus + Grafana + Loki**.
   - Alert on: Outbox backlog > threshold, ERPNext post failure, DB disk > 80%, elevated HTTP `401`/`429`/`5xx` spikes.

---

## 3. Cybersecurity Audit & Required Security Controls

Below is the prioritized security hardening specification based on an audit of the current codebase against **OWASP API Security Top 10 (2023)**, **PCI DSS**, and the **Kenya Data Protection Act (2019)**.

### 3.1 Critical Findings in Current Codebase (Must Fix Before Production)

| Severity | Finding & File Location | Risk | Required Remediation |
| :--- | :--- | :--- | :--- |
| **CRITICAL** | **Auth disabled by default** (`pos.security.require-auth: false` in `application.yml` & `SecurityConfig.java`) | Anyone on the LAN can call `/api/accounting/**`, `/api/cashiers/**`, `/api/products`, `/api/returns` without a JWT. | Default `require-auth` to `true` in production profiles (`application-prod.yml`); allow `false` only in an explicit `test` profile. |
| **CRITICAL** | **Self-registration privilege escalation** (`AuthController.java` `POST /api/auth/register`) | Any unauthenticated caller can pass `"role": "SUPER_ADMIN"` or `"SUPERVISOR"` and receive an immediately `ACTIVE` privileged account. | Restrict `/api/auth/register` strictly to `CASHIER` (`PENDING_APPROVAL`). Creating `SUPERVISOR` / `SUPER_ADMIN` accounts must require an authenticated `SUPER_ADMIN` token via a dedicated `/api/admin/users` endpoint (with bootstrap CLI/env for the very first Super Admin). |
| **CRITICAL** | **Plaintext ERPNext secrets stored & returned via API** (`AccountingConfig.java`, `AccountingController.java`) | `GET /api/accounting/config` returns `erpnextApiKey` & `erpnextApiSecret` in clear JSON; DB stores them unencrypted. | Encrypt `erpnext_api_secret` at rest (AES-256-GCM using a KMS/env master key, or store exclusively in secrets manager env vars). Mask secrets (`"********"`) in `GET /api/accounting/config` responses and only overwrite on `PUT` if a non-masked value is supplied. |
| **HIGH** | **Client-supplied identity (OWASP API1 - BOLA)** (`SaleController.java`, `ShiftController.java`, `ReturnController.java`) | Request DTOs trust client-supplied `cashierId`; a compromised client token could attribute sales/returns/shifts to another cashier. | Extract authenticated user UUID (`sub` claim) and role from Spring `SecurityContext` (`@AuthenticationPrincipal`) on the server and ignore or reject mismatched `cashierId` fields. |
| **HIGH** | **Hardcoded fallback JWT secret & long unrevocable tokens** (`JwtService.java`, `AuthController.java` 3-arg constructor) | Default dev secret is committed in source code; 12h JWTs cannot be revoked if a cashier is deleted or suspended mid-shift. | Remove hardcoded fallback secret in `AuthController` production path; enforce minimum 32-byte random secret via `POS_JWT_SECRET` at startup (fail-fast if default is used in `prod`). Use short-lived access tokens (15–30 min) + HttpOnly Secure refresh tokens or check user `status == ACTIVE` / token `jti` blocklist. |
| **HIGH** | **Unverified M-Pesa Daraja callback** (`MpesaCallbackController.java`) | `/api/payments/mpesa-callback` is `permitAll()` without Safaricom IP allowlisting or amount verification against `Sale.totalKes` / `Payment.amountKes`. | Allowlist official Safaricom Daraja IP ranges at Nginx/firewall; verify callback `Amount` equals expected `Payment.amountKes` and transaction state transitions strictly from `PENDING` -> `PAID` (idempotently); add scheduled Daraja Transaction Status reconciliation job. |
| **HIGH** | **Wildcard CORS on local hardware agent** (`hardware-agent/index.js` line 15: `Access-Control-Allow-Origin: *`) | Any malicious browser tab could send `POST http://localhost:9001/drawer/open` or `/print` (CSRF/drive-by cash drawer pop). | Restrict `Access-Control-Allow-Origin` strictly to the POS frontend origin, bind HTTP server to `127.0.0.1` only (`server.listen(PORT, '127.0.0.1')`), and require a per-machine local shared token header (`X-Hardware-Token`). |
| **MEDIUM** | **In-memory single-node rate limiting** (`LoginRateLimiter.java`) | Rate limiter only tracks username attempts in memory; attackers can brute-force across IPs or reset on restart, or lock out known usernames (DoS). | Combine per-IP + per-username rate limiting (Bucket4j / Redis / Nginx `limit_req`), add exponential backoff, and require strong password/PIN complexity rules (ban trivial `0000`/`1234` PINs; migrate cashiers to >= 6-digit PINs or passwords + optional MFA for Super Admin). |
| **MEDIUM** | **CSV Formula Injection on exports** (`StandardAccountingExporter.java`, `ReportsController`) | Product names or remarks starting with `=`, `+`, `-`, `@` can execute formulas when opened in Excel/LibreOffice. | Prefix risky cell values with a single quote (`'`) when generating accounting and tax invoice CSVs. |
| **MEDIUM** | **Missing Security Headers & Unbounded Inputs** | No Content-Security-Policy (CSP), HSTS, or explicit pagination caps on list endpoints (`getAllProducts`, `listReceipts`, `getAllReturns`). | Configure Nginx + Spring Security headers (`Strict-Transport-Security`, `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`) and enforce server-side pagination (`Pageable`, max page size 100). |

### 3.2 Mandatory Compliance & Operational Security Controls

1. **Immutable Audit Trail (`audit_log` table)**
   - Log every security-sensitive action with `actor_id`, `actor_role`, `action`, `target_entity`, `target_id`, `ip_address`, `before_value`, `after_value`, and `timestamp`:
     - Logins, failed attempts, lockouts, cashier authorizations, deletions, and PIN resets
     - Product price/cost/tax category updates and manual stock adjustments
     - Returns/refunds, voided items, forced ERPNext reposts, and accounting config changes
   - Enforce append-only semantics (DB user for the app should have `INSERT`/`SELECT` only on `audit_log`, no `UPDATE`/`DELETE`).
2. **Network Segmentation & Terminal Lockdown (PCI DSS Alignment)**
   - Separate VLANs for: (a) POS Tills & Store Server, (b) Backoffice Admin PCs, (c) Guest/Customer Wi-Fi, (d) Printers/CCTV/IoT.
   - Lock POS till terminals to kiosk mode (dedicated Chromium kiosk profile pointing only to POS origin, USB storage disabled, BIOS/OS boot locked, only HID barcode scanner + receipt printer + scale allowed).
3. **Kenya Data Protection Act (2019) Controls**
   - Customer records (`customer.phone_number`, `name`, loyalty balance) and employee records (`cashier`) constitute personal data:
     - Enforce TLS 1.3 in transit and encrypted database volumes at rest (LUKS / managed disk encryption).
     - Restrict customer phone number export/bulk access to authorized Admin roles and mask phone numbers (`0712***678`) in till/receipt views where full digits aren't needed.
     - Define data retention policies for logs and loyalty records.
4. **Incident Response & Secret Rotation Runbook**
   - Document procedures for: revoking compromised JWT signing keys, rotating Daraja consumer/passkey credentials, rotating ERPNext Frappe API tokens, isolating a compromised till machine, and restoring PostgreSQL from verified backups.

---

## 4. Phased Implementation Plan (Recommended Execution Order)

| Phase | Owner | Scope | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Phase A: Security Lockdown (Pre-Pilot Blocker)** | Backend + Security + DevOps | Fix `/api/auth/register` privilege escalation; enforce `require-auth=true`; derive `cashierId` from JWT; encrypt/mask ERPNext secrets; add Daraja IP whitelist + amount verification; bind `hardware-agent` to `127.0.0.1` + CORS allowlist; add `audit_log`; centralize `till-ui` auth client. | Penetration checklist & OWASP API Top 10 tests pass with `require-auth=true`. |
| **Phase B: Supermarket Retail Completeness** | Backend + Frontend | Batch/expiry FEFO tracking; weighted EAN-13 scale barcode parsing; split-tender checkout; Manager PIN override modal for voids/discounts; IndexedDB offline catalog & queue; self-hosted Inter font/assets. | Full offline checkout & weighted-produce test completed without internet. |
| **Phase C: DevOps Productionization** | DevOps | Docker Compose prod stack with Nginx TLS 1.3 + internal DB network; automated encrypted backups + restore drill; CI alignment (Java 21, Trivy/OWASP scan); Prometheus + Grafana + Loki dashboards. | Staging failover, backup restore, and load test (1,000+ sales/day) verified. |
