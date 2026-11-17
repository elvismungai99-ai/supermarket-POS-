#!/usr/bin/env bash
set -euo pipefail

# Automated compressed (and optionally GPG-encrypted) PostgreSQL backup with 14-day pruning
BACKUP_DIR="${BACKUP_DIR:-/var/backups/supermarket-pos}"
COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
PG_DB="${POSTGRES_DB:-pos}"
PG_USER="${POSTGRES_USER:-pos}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/pos_${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

docker compose -f "${COMPOSE_FILE}" exec -T db pg_dump -U "${PG_USER}" -d "${PG_DB}" --no-owner --no-acl | gzip > "${OUT_FILE}"

if [[ -n "${GPG_RECIPIENT:-}" ]]; then
  gpg --batch --yes --trust-model always -e -r "${GPG_RECIPIENT}" -o "${OUT_FILE}.gpg" "${OUT_FILE}"
  rm -f "${OUT_FILE}"
  echo "Encrypted backup created: ${OUT_FILE}.gpg"
else
  echo "Compressed backup created: ${OUT_FILE}"
fi

find "${BACKUP_DIR}" -type f \( -name "pos_*.sql.gz" -o -name "pos_*.sql.gz.gpg" \) -mtime "+${RETENTION_DAYS}" -delete
