#!/usr/bin/env bash
set -euo pipefail

PORT="${SERVER_PORT:-8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Free port 8080 safely if a previous pos-backend instance is still running
if command -v fuser >/dev/null 2>&1; then
  if fuser "${PORT}/tcp" >/dev/null 2>&1; then
    echo "Port ${PORT} is already in use. Stopping previous process on port ${PORT}..."
    fuser -k -TERM "${PORT}/tcp" >/dev/null 2>&1 || true
    sleep 2
    if fuser "${PORT}/tcp" >/dev/null 2>&1; then
      fuser -k -KILL "${PORT}/tcp" >/dev/null 2>&1 || true
      sleep 1
    fi
  fi
fi

cd "${SCRIPT_DIR}"
exec ./mvnw spring-boot:run
