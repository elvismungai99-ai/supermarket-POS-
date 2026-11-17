#!/usr/bin/env bash
set -euo pipefail

MIG_DIR="backend/src/main/resources/db/migration"

if [[ ! -d "$MIG_DIR" ]]; then
  echo "Migration directory not found: $MIG_DIR" >&2
  exit 1
fi

echo "Checking Flyway SQL migrations in $MIG_DIR..."

versions=()
errors=0

while IFS= read -r file; do
  base="$(basename "$file")"
  if [[ ! "$base" =~ ^V([0-9]+)__([a-z0-9_]+)\.sql$ ]]; then
    echo "ERROR: Invalid Flyway filename (expected V<num>__<snake_case>.sql): $base"
    errors=$((errors + 1))
    continue
  fi

  ver="${BASH_REMATCH[1]}"
  versions+=("$ver")

  # Only enforce strict additive/index checks on new migrations (V11+)
  if (( ver >= 11 )); then
    # Check for REFERENCES without CREATE INDEX in the same file
    if grep -qiE '\breferences\b' "$file" && ! grep -qiE 'create[[:space:]]+(unique[[:space:]]+)?index' "$file"; then
      echo "WARN ($base): Foreign key reference found without an index in this migration."
    fi

    # Check for unguarded DROP TABLE / DROP COLUMN
    if grep -qiE '\bdrop[[:space:]]+(table|column)\b' "$file"; then
      echo "WARN ($base): Destructive DROP statement found; verify backward-compatibility."
    fi
  fi
done < <(find "$MIG_DIR" -maxdepth 1 -type f -name 'V*.sql' | sort -V)

dups="$(printf '%s\n' "${versions[@]}" | sort -n | uniq -d || true)"
if [[ -n "$dups" ]]; then
  echo "ERROR: Duplicate Flyway migration version(s): $dups"
  errors=$((errors + 1))
fi

echo "Found ${#versions[@]} Flyway migration(s); latest version: V$(printf '%s\n' "${versions[@]}" | sort -n | tail -n 1)."
if (( errors > 0 )); then
  exit 1
fi

echo "Migration validation passed."
