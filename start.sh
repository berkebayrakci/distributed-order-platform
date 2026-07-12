#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"
STOP_INFRA=false
WITH_UI=false
PIDS=()
NAMES=()
TAIL_PID=""

usage() {
  cat <<'EOF'
Usage: ./start.sh [options]

Options:
  --with-ui             Also start the React UI on port 5173
  --stop-infra-on-exit  Stop PostgreSQL and RabbitMQ when this script exits
  --down                Stop Compose infrastructure and exit
  -h, --help            Show this help

By default Ctrl+C stops the application processes but leaves PostgreSQL and
RabbitMQ running for the next development session.
EOF
}

log() {
  printf '[platform] %s\n' "$*"
}

die() {
  printf '[platform] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  if [[ -n "$TAIL_PID" ]] && kill -0 "$TAIL_PID" 2>/dev/null; then
    kill "$TAIL_PID" 2>/dev/null || true
  fi

  if ((${#PIDS[@]} > 0)); then
    log "Stopping application processes..."
    for pid in "${PIDS[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
      fi
    done

    for _ in {1..20}; do
      local running=false
      for pid in "${PIDS[@]}"; do
        kill -0 "$pid" 2>/dev/null && running=true
      done
      $running || break
      sleep 0.25
    done

    for pid in "${PIDS[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
      fi
    done
  fi

  if $STOP_INFRA; then
    log "Stopping Compose infrastructure..."
    (cd "$ROOT_DIR" && docker compose down) || true
  fi

  exit "$exit_code"
}

start_process() {
  local name=$1
  local directory=$2
  shift 2
  local logfile="$LOG_DIR/$name.log"

  : >"$logfile"
  (
    cd "$directory"
    exec setsid "$@"
  ) >"$logfile" 2>&1 &

  local pid=$!
  PIDS+=("$pid")
  NAMES+=("$name")
  printf '%s\n' "$pid" >"$RUN_DIR/$name.pid"
  log "Started $name (PID $pid, log: .run/logs/$name.log)"
}

wait_for_health() {
  local name=$1
  local url=$2
  local pid=$3
  local timeout=${4:-120}
  local started=$SECONDS

  while ((SECONDS - started < timeout)); do
    if ! kill -0 "$pid" 2>/dev/null; then
      printf '\n--- %s startup log ---\n' "$name" >&2
      tail -n 80 "$LOG_DIR/$name.log" >&2 || true
      die "$name exited before becoming healthy"
    fi

    if curl --silent --fail --max-time 2 "$url" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      log "$name is healthy"
      return 0
    fi
    sleep 1
  done

  printf '\n--- %s startup log ---\n' "$name" >&2
  tail -n 80 "$LOG_DIR/$name.log" >&2 || true
  die "$name did not become healthy within ${timeout}s"
}

while (($# > 0)); do
  case "$1" in
    --with-ui) WITH_UI=true ;;
    --stop-infra-on-exit) STOP_INFRA=true ;;
    --down)
      require_command docker
      cd "$ROOT_DIR"
      docker compose down
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "Unknown option: $1"
      ;;
  esac
  shift
done

require_command docker
require_command java
require_command curl
require_command setsid

for wrapper in \
  catalog-service/gradlew \
  subscriber-service/gradlew \
  orchestration-service/gradlew \
  crm-service/gradlew; do
  [[ -f "$ROOT_DIR/$wrapper" ]] || die "Missing Gradle wrapper: $wrapper"
  chmod +x "$ROOT_DIR/$wrapper"
done

if $WITH_UI; then
  require_command npm
  [[ -d "$ROOT_DIR/crm-ui/node_modules" ]] || die "Run 'cd crm-ui && npm ci' before using --with-ui"
fi

mkdir -p "$LOG_DIR"
rm -f "$RUN_DIR"/*.pid
trap cleanup EXIT INT TERM

# WSL can resolve localhost differently depending on Windows hosts and IPv6
# configuration. Explicit IPv4 defaults make host-published Docker ports
# deterministic while still allowing every value to be overridden.
export RABBITMQ_HOST="${RABBITMQ_HOST:-127.0.0.1}"
export CRM_DB_URL="${CRM_DB_URL:-jdbc:postgresql://127.0.0.1:5432/order_system?currentSchema=crm}"
export CATALOG_DB_URL="${CATALOG_DB_URL:-jdbc:postgresql://127.0.0.1:5432/order_system?currentSchema=catalog}"
export SUBSCRIBER_DB_URL="${SUBSCRIBER_DB_URL:-jdbc:postgresql://127.0.0.1:5432/order_system?currentSchema=subscriber}"
export ORCHESTRATOR_DB_URL="${ORCHESTRATOR_DB_URL:-jdbc:postgresql://127.0.0.1:5432/order_system?currentSchema=orchestrator}"
export CRM_BASE_URL="${CRM_BASE_URL:-http://127.0.0.1:8081}"
export CATALOG_BASE_URL="${CATALOG_BASE_URL:-http://127.0.0.1:8082}"
export SUBSCRIBER_BASE_URL="${SUBSCRIBER_BASE_URL:-http://127.0.0.1:8083}"
export ORCHESTRATOR_BASE_URL="${ORCHESTRATOR_BASE_URL:-http://127.0.0.1:8084}"

log "Starting PostgreSQL and RabbitMQ..."
(cd "$ROOT_DIR" && docker compose up -d --wait --wait-timeout 120)

start_process catalog "$ROOT_DIR/catalog-service" ./gradlew bootRun --console=plain
start_process subscriber "$ROOT_DIR/subscriber-service" ./gradlew bootRun --console=plain
start_process orchestrator "$ROOT_DIR/orchestration-service" ./gradlew bootRun --console=plain
start_process crm "$ROOT_DIR/crm-service" ./gradlew bootRun --console=plain

wait_for_health catalog http://127.0.0.1:8082/actuator/health "${PIDS[0]}"
wait_for_health subscriber http://127.0.0.1:8083/actuator/health "${PIDS[1]}"
wait_for_health orchestrator http://127.0.0.1:8084/actuator/health "${PIDS[2]}"
wait_for_health crm http://127.0.0.1:8081/actuator/health "${PIDS[3]}"

if $WITH_UI; then
  start_process ui "$ROOT_DIR/crm-ui" npm run dev
  log "CRM UI will be available at http://localhost:5173"
fi

log "Platform is ready. Press Ctrl+C to stop application processes."
log "Following service logs..."
tail -n 20 -F "$LOG_DIR"/*.log &
TAIL_PID=$!

while true; do
  for index in "${!PIDS[@]}"; do
    if ! kill -0 "${PIDS[$index]}" 2>/dev/null; then
      wait "${PIDS[$index]}" || true
      printf '\n--- %s final log ---\n' "${NAMES[$index]}" >&2
      tail -n 80 "$LOG_DIR/${NAMES[$index]}.log" >&2 || true
      die "${NAMES[$index]} stopped unexpectedly"
    fi
  done
  sleep 2
done
