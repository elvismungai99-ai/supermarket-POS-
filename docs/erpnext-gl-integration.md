# ERPNext GL-Only Integration Guide (`supermarketPOS`)

## 1. Architecture Overview (GL-Only EOD Model)

`supermarketPOS` integrates with ERPNext using a **General Ledger (GL) & Accounts Payable (AP) only** architecture. Real-time till checkout, product catalogue, barcode pricing, and FEFO stock quantities live locally in PostgreSQL (`pos`), while ERPNext acts as the financial accounting system of record.

| Responsibility | System of Record | ERPNext DocType |
| --- | --- | --- |
| Barcode lookup, checkout, discounts, M-Pesa/Card/Cash payments, KRA eTIMS receipts | **Java POS (PostgreSQL)** | None (local real-time) |
| Daily consolidated sales, VAT, returns, loyalty discounts, cash over/short, COGS | **ERPNext GL** | `Journal Entry` (End-of-Day Z-Report) |
| Supplier deliveries (GRN), Input VAT credit, supplier payables | **ERPNext AP & GL** | `Purchase Invoice` (`update_stock = 0`) |
| Shrinkage, expired write-offs, damaged goods, cycle-count variances | **ERPNext GL** | `Journal Entry` (Dr COGS / Cr Inventory, or reverse for surplus) |

> **Why GL-Only EOD?**
> Supermarket tills cannot block on remote ERP calls per scan, and posting thousands of item-level `POS Invoice` documents inflates ERPNext database size without accounting benefit. Consolidated EOD `Journal Entry` vouchers keep ERPNext lean while maintaining 100% double-entry General Ledger accuracy and KRA VAT reconciliation.

---

## 2. Least-Privilege ERPNext API User Setup

Create a dedicated integration user in ERPNext (for example, `pos-integration@yourstore.co.ke`) rather than reusing `Administrator`:

1. Create a custom Role **`POS GL Integration`** with only these DocType permissions:
   - **`Journal Entry`**: `Read`, `Create`, `Submit` (optional `Write` for drafts)
   - **`Purchase Invoice`**: `Read`, `Create`, `Submit`
   - **`Account`**: `Read` (required for Chart of Accounts validation / connection check)
   - **`Supplier`**: `Read` (if matching existing supplier names on GRN)
2. Assign **`POS GL Integration`** to the dedicated API user.
3. Open **User → API Access → Generate Keys** to generate `API Key` and `API Secret`.
4. Configure the credentials in the POS **Super Admin → Accounting & ERPNext** tab (the backend encrypts `erpnext_api_secret` at rest with AES-256-GCM before storing it in `accounting_config`).
5. Requests use Frappe token authentication:
   ```http
   Authorization: token <api_key>:<api_secret>
   Content-Type: application/json
   ```

---

## 3. Chart of Accounts (COA) Mapping

Ensure the following 15 accounts plus Cost Center exist in your ERPNext Company (replace `- SP` with your company abbreviation) and match the **Super Admin → Accounting** configuration:

| Config Field | Default ERPNext Account | Usage |
| --- | --- | --- |
| `accountCashOnHand` | `Cash On Hand - SP` | Cash tender collected |
| `accountMpesaClearing` | `M-Pesa Till Clearing - SP` | M-Pesa STK / Till tender |
| `accountBankClearing` | `Bank Clearing - SP` | Card / PDQ tender |
| `accountSalesStandard` | `Sales - Standard Rated 16% - SP` | KRA Cat A net revenue |
| `accountSalesSpecial` | `Sales - Special Rated 8% - SP` | KRA Cat C net revenue |
| `accountSalesZeroRated` | `Sales - Zero Rated - SP` | KRA Cat B revenue |
| `accountSalesExempt` | `Sales - Exempt - SP` | KRA Cat E revenue |
| `accountOutputVat` | `Output VAT 16% - SP` | Output VAT payable to KRA |
| `accountSalesReturns` | `Sales Returns and Refunds - SP` | Customer refunds (contra-revenue) |
| `accountLoyaltyDiscount` | `Loyalty Points Redeemed - SP` | Redeemed loyalty discounts |
| `accountCashOverShort` | `Cash Over and Short - SP` | Till blind-drop variance |
| `accountCogs` | `Cost of Goods Sold - SP` | Daily COGS & shrinkage expense |
| `accountInventory` | `Stock in Hand - SP` | Inventory asset reduction/addition |
| `accountInputVat` | `Input VAT 16% - SP` | Supplier Purchase Invoice `taxes` (`On Net Total`) |
| `accountAccountsPayable` | `Creditors - SP` | Supplier payable (`credit_to`) |
| `accountCostCenter` | `Main - SP` | Branch/store cost center |

### Supplier GRN `Purchase Invoice` Input VAT
When a manager receives stock with a VAT rate > 0% (e.g., `16%` or `8%`), `ErpNextClient.postPurchaseInvoice` attaches an ERPNext `taxes` line:
- `charge_type`: `"On Net Total"`
- `account_head`: configured `accountInputVat`
- `rate`: `vatRate * 100` (e.g., `16.0`)
- `update_stock`: `0` (POS manages unit-level stock & FEFO batches locally)

---

## 4. Idempotency & Duplicate Prevention

Network timeouts can occur after ERPNext creates a document but before the HTTP response reaches Java. To prevent double-posting on retries, every ERPNext document carries a deterministic reference key checked via `GET /api/resource/{doctype}?filters=...` before any `POST`:

| Workflow | ERPNext DocType | Idempotency Field | Deterministic Key Format |
| --- | --- | --- | --- |
| Daily EOD Z-Report | `Journal Entry` | `cheque_no` | `POS-EOD-<shopId8>-<YYYY-MM-DD>` |
| Stock Adjustment / Shrinkage | `Journal Entry` | `cheque_no` | `POS-ADJ-<adjustmentId8>` |
| Supplier Stock Receipt (GRN) | `Purchase Invoice` | `bill_no` | Supplier invoice # (or `POS-GRN-<receiptId8>`) |

If ERPNext already contains a document with that reference key (`docstatus < 2`), `ErpNextClient` returns the existing ERPNext document ID without creating a duplicate.

---

## 5. Outbox Resilience, Exponential Backoff & Dead-Letter Replay

If ERPNext is offline or unreachable, POS operations never fail at the counter or receiving bay. Events are persisted in the transactional `outbox` table and processed by `OutboxProcessor` every **15 seconds**:

### Supported Outbox Topics
- `etims.invoice.queued` — KRA eTIMS / VSCU fiscal invoice sync
- `accounting.erpnext.journal_entry` — EOD Z-Report `Journal Entry`
- `accounting.erpnext.purchase_invoice` — Supplier GRN `Purchase Invoice`
- `accounting.erpnext.stock_adjustment` — Shrinkage / Expiry / Damage / Cycle-Count `Journal Entry`

### Exponential Backoff Schedule (`MAX_ATTEMPTS = 8`)
| Failed Attempt | Delay Before Next Retry |
| --- | --- |
| 1 | `15 seconds` |
| 2 | `60 seconds` (1 min) |
| 3 | `300 seconds` (5 min) |
| 4 | `1800 seconds` (30 min) |
| 5 | `7200 seconds` (2 hours) |
| 6–7 | `21600 seconds` (6 hours) |
| 8 | Marked `failed_permanently = true` (Dead-Letter) |

### Dead-Letter Monitoring & Manual Replay
- **Status endpoint**: `GET /api/reports/outbox-status` returns `pendingCount`, `sentCount`, `totalCount`, `failedCount`, and `status`.
- **Replay endpoint** (`SUPER_ADMIN`): `POST /api/reports/outbox/retry-failed` resets `failed_permanently = false`, `attempt_count = 0`, and `next_retry_at = NOW()` for all dead-lettered entries.
- **UI**: The **Offline Outbox Queue** card in the Super Admin dashboard displays the live dead-letter count and shows a **↻ Retry Failed** button whenever `failedCount > 0`.

---

## 6. Hosting & Backup Operations Checklist

1. **POS PostgreSQL Backups**:
   - Run `infra/scripts/backup-postgres.sh` on a daily cron schedule and ship encrypted dumps offsite.
   - Store `POS_ENCRYPTION_KEY` in a secure vault/secret manager so backups containing encrypted `erpnext_api_secret` values remain decryptable on restore.
2. **ERPNext Hosting**:
   - **Frappe Cloud (Recommended for single/multi-branch SMEs)**: Managed TLS, automated MariaDB backups, zero bench maintenance. Set `erpnext_base_url` to `https://<your-site>.frappe.cloud`.
   - **Self-Hosted (Docker / Frappe Bench)**: Ensure MariaDB, Redis, and `bench backup --with-files` run on a separate daily schedule from PostgreSQL, terminate HTTPS behind Nginx/Caddy, and verify `http://localhost:8000` (or internal HTTPS URL) is reachable from the POS backend host.
3. **Health & Reconciliation**:
   - Periodically run **Test Connection** in Super Admin → Accounting after ERPNext upgrades.
   - Verify `failedCount == 0` in the Offline Outbox Queue card at EOD close.
