-- Migration V12: Outbox exponential backoff / dead-letter support and GL-linked stock adjustments

alter table outbox
  add column if not exists attempt_count integer not null default 0,
  add column if not exists next_retry_at timestamptz,
  add column if not exists last_error text,
  add column if not exists failed_permanently boolean not null default false;

create index if not exists idx_outbox_pending_retry
  on outbox (next_retry_at)
  where sent_at is null and failed_permanently = false;

create table if not exists stock_adjustment (
  id uuid primary key,
  product_id uuid not null references product(id) on delete cascade,
  adjustment_type text not null,
  quantity_delta numeric(12,3) not null,
  unit_cost_kes numeric(12,2) not null default 0.00,
  total_cost_kes numeric(12,2) not null default 0.00,
  reason text not null,
  recorded_by uuid references cashier(id),
  recorded_by_username text,
  erpnext_journal_entry_id text,
  erpnext_status text not null default 'LOCAL_ONLY',
  error_message text,
  created_at timestamptz not null default now()
);

create index if not exists idx_stock_adjustment_product_id on stock_adjustment (product_id);
create index if not exists idx_stock_adjustment_recorded_by on stock_adjustment (recorded_by);
create index if not exists idx_stock_adjustment_created_at on stock_adjustment (created_at desc);
