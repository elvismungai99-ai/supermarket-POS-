# Kenya Retail POS: System Architecture

```mermaid
flowchart TD
  subgraph Frontend ["Till UI (React + TypeScript + Tailwind)"]
    Scanner["Barcode Scanner & Weighing Input"]
    Cart["Cart & Tax Analyzer (Cat A, B, E)"]
    Checkout["Checkout (Cash, M-Pesa STK, Bank)"]
    Receipt["ESC/POS Thermal Receipt"]
    Manager["Inventory & Low-Stock Alerts"]
    Admin["Super Admin & eTIMS Reports"]
  end

  subgraph Backend ["Spring Boot 4 / Java 21"]
    API["REST API (Sales, Products, Shifts, Returns)"]
    Pricing["Pricing & Half-Up VAT Engine"]
    Txn["@Transactional Core"]
    OutboxSched["Outbox Background Processor"]
  end

  subgraph DB ["PostgreSQL 17"]
    Products[("product & barcodes")]
    Sales[("sale & sale_line")]
    Payments[("payment")]
    Shifts[("cashier & shift")]
    Loyalty[("customer")]
    Returns[("sale_return & lines")]
    OutboxTbl[("outbox")]
  end

  subgraph External ["External Services / Emulators"]
    KRA["KRA eTIMS VSCU"]
    Daraja["Safaricom M-Pesa Daraja"]
    Printer["80mm ESC/POS Thermal Printer"]
  end

  Scanner --> Cart
  Cart --> Checkout
  Checkout --> API
  API --> Pricing
  Pricing --> Txn
  Txn --> DB
  Txn --> OutboxTbl
  OutboxSched --> OutboxTbl
  OutboxSched --> KRA
  Checkout -.-> Daraja
  Receipt -.-> Printer
```

## Key Architectural Highlights

1. **Inclusive Pricing & Rounding Precision**:
   - Shelf prices in Kenyan supermarkets are statutory VAT-inclusive.
   - We utilize `BigDecimal` with `RoundingMode.HALF_UP` and 3 decimal places for weighed items (kg) to prevent fractional cent imbalances.
   
2. **Offline-First Resilience (Transactional Outbox)**:
   - Supermarkets experience intermittent internet connectivity.
   - When a sale is made, the sale record, payment, and KRA eTIMS outbox task are committed atomically in PostgreSQL.
   - If offline, sales continue uninterrupted. When the connection resumes, the `OutboxProcessor` drains pending items to KRA VSCU.

3. **Role-Based Access Control**:
   - `CASHIER`: Scans items, manages shift, settles cash/M-Pesa.
   - `SUPERVISOR` / `MANAGER`: Modifies product prices, updates KRA tax categories, investigates low-stock alerts, authorizes returns.
   - `SUPER_ADMIN`: Inspects daily sales grouped by till, monitors outbox sync, exports monthly eTIMS tax invoices for KRA filing.
