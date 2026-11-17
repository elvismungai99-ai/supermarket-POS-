-- Migration V3: Full Lifecycle Schema (Loyalty, Inventory, Returns, Super Admin)

-- 1. Inventory fields on product
alter table product add column if not exists stock_quantity numeric(12,3) not null default 100.000;
alter table product add column if not exists reorder_level numeric(12,3) not null default 10.000;

-- Adjust demo stock quantities for realistic low-stock alerts
update product set stock_quantity = 4.000, reorder_level = 10.000 where id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'; -- Milk
update product set stock_quantity = 45.000, reorder_level = 15.000 where id = 'b2c3d4e5-f6a7-8901-bcde-f12345678901'; -- Maize Meal
update product set stock_quantity = 8.000, reorder_level = 15.000 where id = 'c3d4e5f6-a7b8-9012-cdef-123456789012'; -- Bread
update product set stock_quantity = 80.000, reorder_level = 20.000 where id = 'd4e5f6a7-b8c9-0123-def1-234567890123'; -- Panadol
update product set stock_quantity = 2.000, reorder_level = 10.000 where id = 'e5f6a7b8-c9d0-1234-ef12-345678901234'; -- Sugar

-- 2. Customer loyalty table
create table if not exists customer (
  id uuid primary key,
  phone_number text not null unique,
  name text not null,
  points_balance numeric(12,2) not null default 0.00,
  created_at timestamptz not null default now()
);

-- Seed demo customers
insert into customer (id, phone_number, name, points_balance) values
  ('c1111111-1111-1111-1111-111111111111', '0712345678', 'David Ochieng', 150.00),
  ('c2222222-2222-2222-2222-222222222222', '0722000000', 'Amina Mohamed', 320.00)
on conflict (id) do nothing;

-- 3. Sale returns and refunds
create table if not exists sale_return (
  id uuid primary key,
  original_sale_id uuid not null references sale(id),
  receipt_number text not null,
  cashier_id uuid not null references cashier(id),
  refund_amount_kes numeric(12,2) not null,
  reason text not null,
  created_at timestamptz not null default now()
);

create table if not exists sale_return_line (
  id uuid primary key,
  sale_return_id uuid not null references sale_return(id),
  product_id uuid not null references product(id),
  quantity numeric(12,3) not null,
  unit_price_kes numeric(12,2) not null,
  refund_total_kes numeric(12,2) not null
);

-- 4. Super Admin user seed (PIN 0000)
insert into cashier (id, username, name, pin_hash, role, active) values
  ('33333333-3333-3333-3333-333333333333', 'admin1', 'Super Admin Mwangi', '$2a$10$BF1Rfxd97NE7btvGAYLZmuzuAol2YSa39sj8ppDa3ghAJmGosBdPy', 'SUPER_ADMIN', true)
on conflict (id) do nothing;
