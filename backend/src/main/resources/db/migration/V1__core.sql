create table product (
  id uuid primary key,
  name text not null,
  price_kes numeric(12,2) not null check (price_kes >= 0),
  tax_category text not null,
  active boolean not null default true
);

create table product_barcode (
  barcode text primary key,
  product_id uuid not null references product(id)
);

create table sale (
  id uuid primary key,
  shop_id uuid not null,
  till_id uuid not null,
  cashier_id uuid not null,
  total_kes numeric(12,2) not null,
  status text not null,
  created_at timestamptz not null default now()
);

create table sale_line (
  id uuid primary key,
  sale_id uuid not null references sale(id),
  product_id uuid not null references product(id),
  quantity numeric(12,3) not null,
  unit_price_kes numeric(12,2) not null
);

create table payment (
  id uuid primary key,
  sale_id uuid not null references sale(id),
  method text not null,
  amount_kes numeric(12,2) not null,
  reference text,
  state text not null
);

create table outbox (
  id uuid primary key,
  topic text not null,
  payload jsonb not null,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);
