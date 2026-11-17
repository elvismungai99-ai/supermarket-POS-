---
name: postgresql-migration-validator
description: Enforces PostgreSQL schema design rules, snake_case naming conventions, foreign key indexing, Flyway versioning, and JPA entity validation compatibility.
---

# PostgreSQL Migration Validator (`backend/src/main/resources/db/migration/`)

Apply these rules whenever designing, reviewing, or adding database schema migrations for PostgreSQL 17 and Flyway.

## Core Rules

1. **Naming Conventions:** Use lowercase `snake_case` for all table names, columns, indexes, triggers, and constraint names (e.g., `tax_payment`, `tax_period`, `idx_tax_payment_period`).
2. **Primary Keys:** Every table must have a primary key, following the project convention of UUID primary keys (`id uuid primary key`).
3. **Foreign Key Indexing:** Every foreign key column (`references <table>(<col>)`) must have an explicit supporting index created with `create index if not exists idx_<table>_<col> on <table> (<col>);` to prevent sequential scans and table locks during joins.
4. **Immutable Applied History & Sequential Naming:** **Never edit already-applied migrations (`V1` through `V10`)**, as Flyway validates checksums on startup. Always create a new sequential file starting from **`V11__<short_snake_case_description>.sql`** onward.
5. **Idempotent & Backward-Compatible Migrations:** Use `create table if not exists`, `add column if not exists`, and `create index if not exists`. Separate new column additions/backfills from legacy column drops across deployment cycles.
6. **Hibernate `ddl-auto: validate` Alignment:** `backend/src/main/resources/application.yml` runs Hibernate with `ddl-auto: validate`. Every new table, column, nullability constraint, and numeric precision in SQL must match its corresponding JPA `@Entity` / `@Table` / `@Column` mapping before booting.
7. **Audit Integrity:** Never weaken or drop append-only protections such as `trg_audit_log_immutable` (`V9`).

## Validation Helper & Verification

Run the bundled migration validator script before starting the backend:

```bash
bash .agents/skills/postgresql-migration-validator/scripts/validate-migrations.sh
```

Then confirm Flyway + Hibernate `ddl-auto: validate` passes against PostgreSQL (`infra-db-1` on port `5433`).
