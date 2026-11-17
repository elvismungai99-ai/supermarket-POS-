#!/usr/bin/env bash
set -euo pipefail

# Generate self-signed TLS 1.3 certificates for LAN Nginx reverse proxy deployments.
# Run on the store host before starting infra/docker-compose.prod.yml.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}/../nginx/certs"
DOMAIN="${POS_TLS_DOMAIN:-pos.local}"

mkdir -p "${CERT_DIR}"

openssl req -x509 -nodes -newkey rsa:4096 \
  -keyout "${CERT_DIR}/privkey.pem" \
  -out "${CERT_DIR}/fullchain.pem" \
  -days 825 \
  -subj "/CN=${DOMAIN}/O=KenyaRetailPOS/C=KE" \
  -addext "subjectAltName=DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"

chmod 600 "${CERT_DIR}/privkey.pem"
chmod 644 "${CERT_DIR}/fullchain.pem"

echo "Generated TLS certificate and private key in ${CERT_DIR} for ${DOMAIN}."
