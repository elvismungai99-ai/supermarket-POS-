-- Migration V4: Cashier approval workflow and status tracking

alter table cashier add column if not exists status text not null default 'ACTIVE';
alter table cashier add column if not exists created_at timestamptz not null default now();

-- Ensure pre-seeded cashiers are ACTIVE
update cashier set status = 'ACTIVE' where status is null or status = '';
