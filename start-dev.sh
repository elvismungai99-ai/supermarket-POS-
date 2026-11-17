#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_PID=""
HW_PID=""

free_port() {
  local port="$1"
  if command -v fuser >/dev/null 2>&1 && fuser "${port}/tcp" >/dev/null 2>&1; then
    echo "    Stopping stale process on port ${port}..."
    fuser -k -TERM "${port}/tcp" >/dev/null 2>&1 || true
    sleep 2
    if fuser "${port}/tcp" >/dev/null 2>&1; then
      fuser -k -KILL "${port}/tcp" >/dev/null 2>&1 || true
    fi
  fi
}

cleanup() {
  echo ""
  echo "Stopping local POS services..."
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    kill "${BACKEND_PID}" 2>/dev/null || true
  fi
  if [[ -n "${HW_PID}" ]] && kill -0 "${HW_PID}" 2>/dev/null; then
    kill "${HW_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "1/4 Starting PostgreSQL (Docker Compose on port 5433)..."
docker compose -f "${ROOT_DIR}/infra/docker-compose.yml" up -d

echo "2/4 Starting local Hardware Agent (127.0.0.1:9001)..."
if [[ "${1:-}" == "--restart" ]]; then
  free_port 9001
fi
if ! curl -fsS http://127.0.0.1:9001/health >/dev/null 2>&1; then
  node "${ROOT_DIR}/hardware-agent/index.js" >/tmp/pos-hardware-agent.log 2>&1 &
  HW_PID=$!
fi

echo "3/4 Starting Spring Boot Backend (http://127.0.0.1:8080)..."
if [[ "${1:-}" == "--restart" ]]; then
  free_port 8080
fi
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  free_port 8080
  (cd "${ROOT_DIR}/backend" && ./mvnw spring-boot:run) >/tmp/pos-backend.log 2>&1 &
  BACKEND_PID=$!
  echo "    Waiting for backend health check..."
  for _ in $(seq 1 45); do
    if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      echo "    Backend is UP!"
      break
    fi
    sleep 2
  done
else
  echo "    Backend already healthy on :8080 (pass --restart to force restart)."
fi

echo "4/4 Starting Vite Frontend (http://localhost:5173)..."
if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
  echo "    Frontend already running at http://localhost:5173"
  wait
else
  cd "${ROOT_DIR}/till-ui"
  npm run dev
fi
