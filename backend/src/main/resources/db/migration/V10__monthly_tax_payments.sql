-- Migration V10: Monthly KRA Tax Payment & Remittance Ledger

create table if not exists tax_payment (
  id uuid primary key,
  tax_period text not null,
  tax_type text not null default 'VAT',
  amount_paid_kes numeric(12,2) not null,
  payment_date date not null default current_date,
  payment_method text not null default 'KRA_ITAX_MPESA',
  kra_prn text not null,
  notes text,
  recorded_by uuid references cashier(id),
  recorded_by_username text,
  created_at timestamptz not null default now()
);

create index if not exists idx_tax_payment_period on tax_payment (tax_period desc, payment_date desc);
