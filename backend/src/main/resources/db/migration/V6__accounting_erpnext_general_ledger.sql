-- Migration V6: Accounting (General Ledger) and ERPNext Integration

-- 1. Cost price on product and sale lines for COGS (Cost of Goods Sold)
alter table product add column if not exists cost_price_kes numeric(12,2) not null default 0.00;

update product set cost_price_kes = 50.00 where id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'; -- Milk
update product set cost_price_kes = 135.00 where id = 'b2c3d4e5-f6a7-8901-bcde-f12345678901'; -- Maize Meal
update product set cost_price_kes = 48.00 where id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'; -- Bread
update product set cost_price_kes = 80.00 where id = 'd4e5f6a7-b8c9-0123-def1-234567890123'; -- Panadol
update product set cost_price_kes = 115.00 where id = 'e5f6a7b8-c9d0-1234-ef12-345678901234'; -- Sugar

alter table sale_line add column if not exists cost_price_kes numeric(12,2);

-- 2. Daily Accounting Summary (one row per shop per business day)
create table if not exists daily_accounting_summary (
  id uuid primary key,
  shop_id uuid not null,
  business_date date not null,
  
  -- Sales breakdown by tax category (net amounts)
  sales_standard_rated_net_kes numeric(12,2) not null default 0.00,
  sales_zero_rated_net_kes numeric(12,2) not null default 0.00,
  sales_exempt_net_kes numeric(12,2) not null default 0.00,
  output_vat_kes numeric(12,2) not null default 0.00,
  total_gross_sales_kes numeric(12,2) not null default 0.00,
  
  -- Takings by payment method
  cash_takings_kes numeric(12,2) not null default 0.00,
  mpesa_takings_kes numeric(12,2) not null default 0.00,
  bank_takings_kes numeric(12,2) not null default 0.00,
  
  -- Returns
  returns_total_kes numeric(12,2) not null default 0.00,
  returns_tax_kes numeric(12,2) not null default 0.00,
  returns_net_kes numeric(12,2) not null default 0.00,
  
  -- Cash differences from shift reconciliations (closing cash - expected cash)
  cash_difference_kes numeric(12,2) not null default 0.00,
  
  -- Cost of Goods Sold
  cogs_total_kes numeric(12,2) not null default 0.00,
  
  -- Double entry balancing verification
  total_debits_kes numeric(12,2) not null default 0.00,
  total_credits_kes numeric(12,2) not null default 0.00,
  is_balanced boolean not null default true,
  
  -- Status and ERPNext Journal Entry tracking
  status text not null default 'DRAFT', -- 'DRAFT', 'POSTED', 'QUEUED', 'FAILED'
  erpnext_journal_entry_id text,
  erpnext_company text,
  posted_at timestamptz,
  error_message text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  
  constraint uq_shop_business_date unique (shop_id, business_date)
);

-- 3. Accounting & ERPNext Configuration (Chart of Accounts mapping and credentials)
create table if not exists accounting_config (
  id uuid primary key,
  company_name text not null default 'Supermarket POS Ltd',
  erpnext_base_url text not null default 'http://localhost:8000',
  erpnext_api_key text default '',
  erpnext_api_secret text default '',
  auto_post_on_eod boolean not null default false,
  
  -- Chart of Accounts mapping (defaults to Kenya Retail COA)
  account_cash_on_hand text not null default 'Cash On Hand - SP',
  account_mpesa_clearing text not null default 'M-Pesa Till Clearing - SP',
  account_bank_clearing text not null default 'Bank Clearing - SP',
  account_sales_standard text not null default 'Sales - Standard Rated 16% - SP',
  account_sales_zero_rated text not null default 'Sales - Zero Rated - SP',
  account_sales_exempt text not null default 'Sales - Exempt - SP',
  account_output_vat text not null default 'Output VAT 16% - SP',
  account_sales_returns text not null default 'Sales Returns and Refunds - SP',
  account_cash_over_short text not null default 'Cash Over and Short - SP',
  account_cogs text not null default 'Cost of Goods Sold - SP',
  account_inventory text not null default 'Stock in Hand - SP',
  account_cost_center text not null default 'Main - SP',
  
  updated_at timestamptz not null default now()
);

-- Seed default configuration row
insert into accounting_config (
  id, company_name, erpnext_base_url, erpnext_api_key, erpnext_api_secret, auto_post_on_eod,
  account_cash_on_hand, account_mpesa_clearing, account_bank_clearing,
  account_sales_standard, account_sales_zero_rated, account_sales_exempt, account_output_vat,
  account_sales_returns, account_cash_over_short, account_cogs, account_inventory, account_cost_center
) values (
  '11111111-2222-3333-4444-555555555555',
  'Supermarket POS Ltd',
  'http://localhost:8000',
  'demo_api_key_erpnext',
  'demo_api_secret_erpnext',
  false,
  'Cash On Hand - SP',
  'M-Pesa Till Clearing - SP',
  'Bank Clearing - SP',
  'Sales - Standard Rated 16% - SP',
  'Sales - Zero Rated - SP',
  'Sales - Exempt - SP',
  'Output VAT 16% - SP',
  'Sales Returns and Refunds - SP',
  'Cash Over and Short - SP',
  'Cost of Goods Sold - SP',
  'Stock in Hand - SP',
  'Main - SP'
) on conflict (id) do nothing;
