#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
SERVER_BIN="${SHOD_SERVER_BIN:-$PROJECT_ROOT/app/src/main/assets/shod-linux-amd64}"
TEST_BIN="${SHOD_SERVER_TEST_BIN:-}"

WORK_DIR=""
SERVER_PID=""
DOCKER_NAME=""
SYSTEMD_UNIT_NAME=""
SYSTEMD_UNIT_FILE=""

cleanup() {
    local status=$?
    if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill -TERM "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    if [[ -n "$SYSTEMD_UNIT_NAME" ]] && command -v systemctl >/dev/null 2>&1; then
        systemctl stop "$SYSTEMD_UNIT_NAME" >/dev/null 2>&1 || true
    fi
    if [[ -n "$SYSTEMD_UNIT_FILE" && -f "$SYSTEMD_UNIT_FILE" ]]; then
        rm -f -- "$SYSTEMD_UNIT_FILE"
        systemctl daemon-reload >/dev/null 2>&1 || true
    fi
    if [[ -n "$DOCKER_NAME" ]] && command -v docker >/dev/null 2>&1; then
        docker rm -f "$DOCKER_NAME" >/dev/null 2>&1 || true
    fi
    if [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]]; then
        rm -rf -- "$WORK_DIR"
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

fail() {
    printf 'FAILED: %s\n' "$*" >&2
    exit 1
}

if [[ ! -x "$SERVER_BIN" ]]; then
    fail "server binary is missing or not executable: $SERVER_BIN (set SHOD_SERVER_BIN to override)"
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/shod-wsl-check.XXXXXXXX")"
printf '%s\n' '============================================================'
if [[ -r /etc/os-release ]]; then
    . /etc/os-release
    printf 'Shod Linux Environment Diagnostics: %s\n' "${PRETTY_NAME:-unknown}"
fi
printf 'Project root: %s\n' "$PROJECT_ROOT"
printf '%s\n' '============================================================'

# Resolve the hashed Cargo test executable dynamically, or accept an explicit path.
if [[ -z "$TEST_BIN" ]]; then
    TEST_BIN="$(find "$PROJECT_ROOT/rust-server/target" -type f -path '*/debug/deps/shod-*' -perm -u+x 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$TEST_BIN" || ! -x "$TEST_BIN" ]]; then
    fail "native server test executable not found; build it first or set SHOD_SERVER_TEST_BIN"
fi

printf '[1/3] Native server tests: '
TEST_OUT="$("$TEST_BIN" --test-threads=4 2>&1)" || {
    printf 'FAILED\n%s\n' "$TEST_OUT" | tail -n 30
    exit 1
}
printf '%s\n' "$TEST_OUT" | grep -E 'test result: ok\.' | tail -n 1 || {
    printf '%s\n' "$TEST_OUT" | tail -n 30
    fail "native test result marker was not found"
}
printf 'OK\n'

# Use ephemeral loopback ports and private config directories. Never touch a
# pre-existing TUN interface or any unrelated process.
allocate_port() {
    python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()'
}

if ! command -v ip >/dev/null 2>&1; then
    fail "iproute2 (ip) is required for the TUN checks"
fi

WEB_PORT="$(allocate_port)" || fail "python3 is required to allocate an ephemeral port"
LISTEN_PORT="$(allocate_port)" || fail "could not allocate listen port"

printf '[2/4] Standalone TUN (MTU 1500, qlen 1000) and HTTPS smoke test: '
if ip link show dev shod1 >/dev/null 2>&1; then
    fail "TUN interface shod1 already exists; refusing to modify or remove it"
fi
"$SERVER_BIN" --config-dir "$WORK_DIR/standalone" --web-port "$WEB_PORT" --listen "127.0.0.1:$LISTEN_PORT" >"$WORK_DIR/standalone.log" 2>&1 &
SERVER_PID=$!
HTTP_CODE=""
TUN_LINE=""
for _ in $(seq 1 30); do
    HTTP_CODE="$(curl --insecure --silent --output /dev/null --write-out '%{http_code}' "https://127.0.0.1:$WEB_PORT/" || true)"
    TUN_LINE="$(ip -o link show dev shod1 2>/dev/null || true)"
    if [[ "$HTTP_CODE" == "200" && "$TUN_LINE" == *"mtu 1500"* && "$TUN_LINE" == *"qlen 1000"* ]]; then
        break
    fi
    kill -0 "$SERVER_PID" 2>/dev/null || break
    sleep 1
done
if [[ "$HTTP_CODE" != "200" || "$TUN_LINE" != *"mtu 1500"* || "$TUN_LINE" != *"qlen 1000"* ]]; then
    printf 'FAILED (HTTP %s; TUN: %s)\n' "${HTTP_CODE:-000}" "${TUN_LINE:-missing}"
    tail -n 30 "$WORK_DIR/standalone.log" || true
    exit 1
fi
kill -TERM "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""
if ip link show dev shod1 >/dev/null 2>&1; then
    fail "TUN interface shod1 remained after the standalone server stopped; refusing to delete it"
fi
printf 'OK (TUN MTU 1500, qlen 1000; HTTPS 200)\n'

# Exercise the systemd deployment lifecycle with a uniquely named transient
# test unit. Skip only when systemd/root access is unavailable.
printf '[3/4] Systemd service lifecycle: '
if [[ ! -d /run/systemd/system ]] || ! command -v systemctl >/dev/null 2>&1; then
    printf 'SKIPPED (systemd is not running)\n'
elif [[ "$EUID" -ne 0 ]]; then
    printf 'SKIPPED (root privileges are required)\n'
else
    if ip link show dev shod1 >/dev/null 2>&1; then
        fail "TUN interface shod1 already exists before the systemd check; refusing to modify or remove it"
    fi
    SYSTEMD_WEB_PORT="$(allocate_port)" || fail "could not allocate systemd web port"
    SYSTEMD_LISTEN_PORT="$(allocate_port)" || fail "could not allocate systemd listen port"
    SYSTEMD_UNIT_NAME="shod-check-$$-$(date +%s).service"
    SYSTEMD_UNIT_FILE="/etc/systemd/system/$SYSTEMD_UNIT_NAME"
    mkdir -p "$WORK_DIR/systemd"
    cat >"$SYSTEMD_UNIT_FILE" <<EOF
[Unit]
Description=Shod isolated diagnostic check
After=network.target

[Service]
Type=simple
ExecStart=$SERVER_BIN --config-dir $WORK_DIR/systemd --web-port $SYSTEMD_WEB_PORT --listen 127.0.0.1:$SYSTEMD_LISTEN_PORT
Restart=no
LimitNOFILE=65535
EOF
    systemctl daemon-reload
    systemctl start "$SYSTEMD_UNIT_NAME"
    SYSTEMD_ACTIVE=""
    SYSTEMD_HTTP=""
    for _ in $(seq 1 30); do
        SYSTEMD_ACTIVE="$(systemctl is-active "$SYSTEMD_UNIT_NAME" 2>/dev/null || true)"
        SYSTEMD_HTTP="$(curl --insecure --silent --output /dev/null --write-out '%{http_code}' "https://127.0.0.1:$SYSTEMD_WEB_PORT/" || true)"
        [[ "$SYSTEMD_ACTIVE" == "active" && "$SYSTEMD_HTTP" == "200" ]] && break
        sleep 1
    done
    if [[ "$SYSTEMD_ACTIVE" != "active" || "$SYSTEMD_HTTP" != "200" ]]; then
        printf 'FAILED (state: %s; HTTP: %s)\n' "${SYSTEMD_ACTIVE:-unknown}" "${SYSTEMD_HTTP:-000}"
        journalctl -u "$SYSTEMD_UNIT_NAME" -n 30 --no-pager 2>/dev/null || true
        exit 1
    fi
    systemctl stop "$SYSTEMD_UNIT_NAME"
    if systemctl is-active --quiet "$SYSTEMD_UNIT_NAME"; then
        fail "systemd test unit did not stop cleanly: $SYSTEMD_UNIT_NAME"
    fi
    SYSTEMD_UNIT_NAME=""
    rm -f -- "$SYSTEMD_UNIT_FILE"
    SYSTEMD_UNIT_FILE=""
    systemctl daemon-reload
    if ip link show dev shod1 >/dev/null 2>&1; then
        fail "TUN interface shod1 remained after the systemd service stopped; refusing to delete it"
    fi
    printf 'OK (active, HTTPS 200, stopped and unit removed)\n'
fi

# Docker is an opt-in runtime check. This script never installs/starts the
# host daemon, uses host networking, or removes a container it did not create.
if [[ "${SHOD_CHECK_DOCKER:-0}" == "1" ]]; then
    printf '[4/4] Docker server-container smoke test: '
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
        printf 'SKIPPED (Docker daemon unavailable; it was not started)\n'
    else
        DOCKER_NAME="shod-check-$(date +%s)-$$"
        DOCKER_WEB_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')" || fail "could not allocate Docker web port"
        DOCKER_LISTEN_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')" || fail "could not allocate Docker listen port"
        mkdir -p "$WORK_DIR/docker-config"
        docker run --detach --name "$DOCKER_NAME" \
            --cap-add=NET_ADMIN \
            --publish "127.0.0.1:$DOCKER_WEB_PORT:$DOCKER_WEB_PORT" \
            --publish "127.0.0.1:$DOCKER_LISTEN_PORT:$DOCKER_LISTEN_PORT" \
            --volume "$WORK_DIR/docker-config:/etc/shod" \
            --volume "$SERVER_BIN:/usr/local/bin/shod:ro" \
            debian:bookworm-slim \
            /usr/local/bin/shod --config-dir /etc/shod --web-port "$DOCKER_WEB_PORT" --listen "0.0.0.0:$DOCKER_LISTEN_PORT" >/dev/null
        HTTP_CODE=""
        for _ in $(seq 1 30); do
            HTTP_CODE="$(curl --insecure --silent --output /dev/null --write-out '%{http_code}' "https://127.0.0.1:$DOCKER_WEB_PORT/" || true)"
            [[ "$HTTP_CODE" == "200" ]] && break
            docker inspect --format '{{.State.Running}}' "$DOCKER_NAME" 2>/dev/null | grep -q true || break
            sleep 1
        done
        if [[ "$HTTP_CODE" != "200" ]]; then
            printf 'FAILED (HTTP %s)\n' "${HTTP_CODE:-000}"
            docker logs "$DOCKER_NAME" 2>&1 | tail -n 30 || true
            exit 1
        fi
        docker rm -f "$DOCKER_NAME" >/dev/null
        DOCKER_NAME=""
        printf 'OK (container HTTPS 200)\n'
    fi
else
    printf '[3/3] Docker check: SKIPPED (set SHOD_CHECK_DOCKER=1 to opt in)\n'
fi

printf '%s\n' 'STATUS: requested checks completed successfully.'
