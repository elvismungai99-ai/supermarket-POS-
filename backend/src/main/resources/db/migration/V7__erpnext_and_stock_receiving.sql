-- Migration V7: ERPNext completeness, Category C 8% VAT, Loyalty GL balancing, Offline Idempotency, and Supplier Stock Receiving (GRN)
-- Note: eTIMS / KRA OSCU changes intentionally omitted per project scope.

-- 1. Extend daily_accounting_summary for Category C (8%), Loyalty Redeemed, and Returns COGS reversal
alter table daily_accounting_summary
  add column if not exists sales_special_rated_net_kes numeric(12,2) not null default 0.00,
  add column if not exists loyalty_redeemed_kes numeric(12,2) not null default 0.00,
  add column if not exists returns_cogs_kes numeric(12,2) not null default 0.00;

-- 2. Extend accounting_config for docstatus submission & additional COA accounts
alter table accounting_config
  add column if not exists submit_on_post boolean not null default true,
  add column if not exists account_sales_special text not null default 'Sales - Special Rated 8% - SP',
  add column if not exists account_loyalty_discount text not null default 'Loyalty Points Redeemed - SP',
  add column if not exists account_input_vat text not null default 'Input VAT 16% - SP',
  add column if not exists account_accounts_payable text not null default 'Creditors - SP';

-- 3. Extend sale for offline idempotency & loyalty redemption tracking
alter table sale
  add column if not exists client_sale_id uuid,
  add column if not exists loyalty_redeemed_kes numeric(12,2) not null default 0.00;

create unique index if not exists uq_sale_client_sale_id
  on sale (client_sale_id)
  where client_sale_id is not null;

-- 4. Track refund tender method on sale_return
alter table sale_return
  add column if not exists refund_method text not null default 'CASH';

-- 5. Supplier Stock Receiving / Goods Received Notes (GRN) with ERPNext Purchase Invoice linkage
create table if not exists stock_receipt (
  id uuid primary key,
  supplier_name text not null,
  supplier_invoice_no text,
  product_id uuid not null references product(id) on delete cascade,
  quantity numeric(12,3) not null,
  unit_cost_kes numeric(12,2) not null,
  vat_rate numeric(5,4) not null default 0.0000,
  total_cost_kes numeric(12,2) not null,
  received_by uuid references cashier(id),
  notes text,
  erpnext_purchase_invoice_id text,
  erpnext_status text not null default 'DRAFT',
  error_message text,
  received_at timestamptz not null default now()
);
