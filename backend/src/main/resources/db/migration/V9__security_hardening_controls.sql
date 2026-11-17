-- Migration V9: Cybersecurity Hardening (Append-only Audit Log, JWT Revocation, Single-Use Override Tokens, Admin MFA)

-- 1. Enforce append-only audit_log at PostgreSQL layer (block UPDATE and DELETE)
create or replace function prevent_audit_log_mutation()
returns trigger as $$
begin
  raise exception 'audit_log is strictly append-only; UPDATE and DELETE are prohibited';
end;
$$ language plpgsql;

drop trigger if exists trg_audit_log_immutable on audit_log;
create trigger trg_audit_log_immutable
  before update or delete on audit_log
  for each row execute function prevent_audit_log_mutation();

-- 2. Server-side JWT revocation blocklist (for explicit logout)
create table if not exists revoked_token (
  jti text primary key,
  expires_at timestamptz not null,
  revoked_at timestamptz not null default now()
);

create index if not exists idx_revoked_token_expires_at on revoked_token (expires_at);

-- 3. Single-use cryptographic supervisor override tokens (line voids & discounts)
create table if not exists supervisor_override_token (
  id uuid primary key,
  supervisor_id uuid not null references cashier(id),
  cashier_id uuid,
  reason text,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

-- 4. Opt-in TOTP MFA columns on cashier/user accounts
alter table cashier
  add column if not exists mfa_secret text,
  add column if not exists mfa_enabled boolean not null default false;
