create table cashier (
  id uuid primary key,
  username text not null unique,
  name text not null,
  pin_hash text not null,
  role text not null,
  active boolean not null default true
);

create table shift (
  id uuid primary key,
  till_id uuid not null,
  cashier_id uuid not null references cashier(id),
  opened_at timestamptz not null default now(),
  closed_at timestamptz,
  opening_float_kes numeric(12,2) not null default 0.00,
  closing_cash_kes numeric(12,2),
  status text not null default 'OPEN'
);

alter table sale_line add column if not exists tax_category text;
alter table sale_line add column if not exists tax_amount_kes numeric(12,2);
alter table sale_line add column if not exists line_total_kes numeric(12,2);

-- Seed default cashiers
insert into cashier (id, username, name, pin_hash, role, active) values
  ('11111111-1111-1111-1111-111111111111', 'cashier1', 'Jane Wanjiku', '$2a$10$2/G4ntG3vJPCxWLbCdj4L.oD30Zykz1pa9vby1V2tFrwPPWiISY5a', 'CASHIER', true),
  ('22222222-2222-2222-2222-222222222222', 'supervisor1', 'John Kamau', '$2a$10$WkxE/1ruRcBCdqOY1CoR6eNNCTal85rFPhRle5DFvTa62Qz9rKZxu', 'SUPERVISOR', true)
on conflict (id) do nothing;

-- Seed sample supermarket products with varied tax categories
insert into product (id, name, price_kes, tax_category, active) values
  ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Brookside Whole Milk 500ml', 65.00, 'A', true),
  ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'Jogoo Maize Meal 2kg', 170.00, 'B', true),
  ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'White Bread 400g', 65.00, 'B', true),
  ('d4e5f6a7-b8c9-0123-def1-234567890123', 'Panadol Extra 10s', 120.00, 'E', true),
  ('e5f6a7b8-c9d0-1234-ef12-345678901234', 'Mumias Sugar 1kg', 145.00, 'A', true)
on conflict (id) do update set
  name = excluded.name,
  price_kes = excluded.price_kes,
  tax_category = excluded.tax_category;

insert into product_barcode (barcode, product_id) values
  ('6161100001234', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'),
  ('6161100002345', 'b2c3d4e5-f6a7-8901-bcde-f12345678901'),
  ('6161100003456', 'c3d4e5f6-a7b8-9012-cdef-123456789012'),
  ('6161100004567', 'd4e5f6a7-b8c9-0123-def1-234567890123'),
  ('6161100005678', 'e5f6a7b8-c9d0-1234-ef12-345678901234')
on conflict (barcode) do nothing;
