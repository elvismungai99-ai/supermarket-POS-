-- Migration V8: Phase A Security Audit Log + Phase B Batches/FEFO, Line Discounts, Split Payments & Branches

-- 1. Immutable Security & Compliance Audit Log
create table if not exists audit_log (
  id uuid primary key,
  actor_id uuid,
  actor_username text,
  actor_role text,
  action text not null,
  target_entity text,
  target_id text,
  ip_address text,
  details text,
  created_at timestamptz not null default now()
);

create index if not exists idx_audit_log_created_at on audit_log (created_at desc);

-- 2. Multi-branch Shop & Till master tables
create table if not exists shop (
  id uuid primary key,
  code text not null unique,
  name text not null,
  location text,
  cost_center text not null default 'Main - SP',
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists till (
  id uuid primary key,
  shop_id uuid not null references shop(id) on delete cascade,
  till_number text not null,
  label text not null,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into shop (id, code, name, location, cost_center)
values ('00000000-0000-0000-0000-000000000001', 'MAIN-01', 'Main Supermarket', 'CBD Branch', 'Main - SP')
on conflict (id) do nothing;

insert into till (id, shop_id, till_number, label)
values ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '01', 'Till #01 (Main Lane)')
on conflict (id) do nothing;

-- 3. Perishable Product Batch & Expiry Tracking (FEFO)
create table if not exists product_batch (
  id uuid primary key,
  product_id uuid not null references product(id) on delete cascade,
  batch_number text not null,
  expiry_date date,
  quantity_remaining numeric(12,3) not null default 0.000,
  unit_cost_kes numeric(12,2) not null default 0.00,
  stock_receipt_id uuid references stock_receipt(id) on delete set null,
  received_at timestamptz not null default now()
);

create index if not exists idx_product_batch_fefo
  on product_batch (product_id, expiry_date);

alter table stock_receipt
  add column if not exists batch_number text,
  add column if not exists expiry_date date;

-- 4. Discounts & Supervisor Override Linkage on Sales
alter table sale
  add column if not exists discount_kes numeric(12,2) not null default 0.00,
  add column if not exists override_by uuid references cashier(id);

alter table sale_line
  add column if not exists discount_kes numeric(12,2) not null default 0.00;
