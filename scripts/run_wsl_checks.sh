#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
SERVER_BIN="$PROJECT_ROOT/app/src/main/assets/shod-linux-amd64"
TEST_DIR="$PROJECT_ROOT/rust-server/target/x86_64-unknown-linux-musl/debug/deps"
RUN_ID="shod-check-$$"
STANDALONE_DIR="/tmp/$RUN_ID-standalone"
STANDALONE_LOG="/tmp/$RUN_ID-standalone.log"
STANDALONE_PID=""
SYSTEMD_UNIT=""
DOCKER_NAME="$RUN_ID-docker"

cleanup() {
    if [[ -n "$STANDALONE_PID" ]]; then
        kill "$STANDALONE_PID" 2>/dev/null || true
        wait "$STANDALONE_PID" 2>/dev/null || true
    fi
    if [[ -n "$SYSTEMD_UNIT" ]]; then
        systemctl stop "$SYSTEMD_UNIT" 2>/dev/null || true
        rm -f "/etc/systemd/system/$SYSTEMD_UNIT"
        systemctl daemon-reload >/dev/null 2>&1 || true
    fi
    if command -v docker >/dev/null 2>&1; then
        docker rm -f "$DOCKER_NAME" >/dev/null 2>&1 || true
    fi
    rm -rf -- "$STANDALONE_DIR" "$STANDALONE_LOG"
}
trap cleanup EXIT INT TERM

echo "============================================================"
echo "Shod Linux Environment Diagnostics: $(. /etc/os-release && echo "${PRETTY_NAME:-Linux}")"
echo "Project: $PROJECT_ROOT"
echo "============================================================"

if [[ ! -x "$SERVER_BIN" ]]; then
    echo "FAILED: Linux server asset not found or not executable: $SERVER_BIN"
    exit 1
fi

if ip link show shod1 >/dev/null 2>&1; then
    echo "FAILED: interface shod1 already exists; refusing to alter an existing interface."
    exit 1
fi

TEST_ELF=""
if [[ -d "$TEST_DIR" ]]; then
    while IFS= read -r candidate; do
        if "$candidate" --list >/dev/null 2>&1; then
            TEST_ELF="$candidate"
            break
        fi
    done < <(find "$TEST_DIR" -maxdepth 1 -type f -name 'shod-*' -perm /111 -print | sort)
fi
if [[ -z "$TEST_ELF" ]]; then
    echo "FAILED: compiled Rust server test harness not found in $TEST_DIR"
    exit 1
fi

echo -n "[1/4] Running native server tests: "
TEST_OUT="$("$TEST_ELF" --test-threads=4 2>&1)" || {
    echo "FAILED"
    printf '%s\n' "$TEST_OUT" | tail -n 20
    exit 1
}
if ! grep -Eq 'test result: ok\.' <<<"$TEST_OUT"; then
    echo "FAILED"
    printf '%s\n' "$TEST_OUT" | tail -n 20
    exit 1
fi
printf '%s\n' "$TEST_OUT" | grep 'test result:'
echo "OK"

echo -n "[2/4] Testing standalone TUN and HTTPS web panel: "
mkdir -p "$STANDALONE_DIR"
"$SERVER_BIN" --config-dir "$STANDALONE_DIR" --web-port 46102 --listen 0.0.0.0:46100 >"$STANDALONE_LOG" 2>&1 &
STANDALONE_PID=$!
sleep 2
TUN_LINE="$(ip link show shod1 2>/dev/null || true)"
HTTP_CODE="$(curl -k -s -o /dev/null -w '%{http_code}' https://127.0.0.1:46102/ || true)"
kill "$STANDALONE_PID" 2>/dev/null || true
wait "$STANDALONE_PID" 2>/dev/null || true
STANDALONE_PID=""
if grep -q 'qlen 1000' <<<"$TUN_LINE" && [[ "$HTTP_CODE" == "200" ]]; then
    echo "OK (TUN qlen 1000, HTTPS 200)"
else
    echo "FAILED (TUN: $TUN_LINE, HTTP: $HTTP_CODE)"
    tail -n 20 "$STANDALONE_LOG" 2>/dev/null || true
    exit 2
fi

echo -n "[3/4] Systemd service lifecycle: "
if [[ "${SHOD_WSL_ENABLE_SYSTEMD_TEST:-0}" != "1" ]]; then
    echo "SKIPPED (set SHOD_WSL_ENABLE_SYSTEMD_TEST=1 to opt in)"
elif [[ "$(id -u)" -ne 0 ]] || ! command -v systemctl >/dev/null 2>&1 || ! systemctl is-system-running >/dev/null 2>&1; then
    echo "SKIPPED (systemd unavailable)"
else
    SYS_DIR="/tmp/$RUN_ID-systemd"
    mkdir -p "$SYS_DIR"
    SYSTEMD_UNIT="$RUN_ID.service"
    cat >"/etc/systemd/system/$SYSTEMD_UNIT" <<EOF
[Unit]
Description=Shod isolated verification service
After=network.target

[Service]
Type=simple
ExecStart=$SERVER_BIN --config-dir $SYS_DIR --web-port 46202 --listen 0.0.0.0:46200
Restart=no
LimitNOFILE=65535
EOF
    systemctl daemon-reload
    systemctl start "$SYSTEMD_UNIT"
    sleep 2
    SYS_ACTIVE="$(systemctl is-active "$SYSTEMD_UNIT" 2>/dev/null || true)"
    SYS_HTTP="$(curl -k -s -o /dev/null -w '%{http_code}' https://127.0.0.1:46202/ || true)"
    systemctl stop "$SYSTEMD_UNIT" 2>/dev/null || true
    rm -f "/etc/systemd/system/$SYSTEMD_UNIT"
    SYSTEMD_UNIT=""
    systemctl daemon-reload
    rm -rf -- "$SYS_DIR"
    if [[ "$SYS_ACTIVE" == "active" && "$SYS_HTTP" == "200" ]]; then
        echo "OK (active, HTTPS 200, clean stop)"
    else
        echo "FAILED (status: $SYS_ACTIVE, HTTP: $SYS_HTTP)"
        exit 3
    fi
fi

echo -n "[4/4] Docker deployment: "
if [[ "${SHOD_WSL_ENABLE_DOCKER_TEST:-0}" != "1" ]]; then
    echo "SKIPPED (set SHOD_WSL_ENABLE_DOCKER_TEST=1 to opt in)"
elif ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "SKIPPED (Docker daemon unavailable; runner will not start it)"
else
    DOCKER_DIR="/tmp/$RUN_ID-docker"
    mkdir -p "$DOCKER_DIR"
    if ! docker run -d --name "$DOCKER_NAME" --net=host --cap-add=NET_ADMIN \
        -v "$DOCKER_DIR:/etc/shod" -v "$SERVER_BIN:/usr/local/bin/shod:ro" \
        debian:bookworm-slim /usr/local/bin/shod --config-dir /etc/shod \
        --web-port 46302 --listen 0.0.0.0:46300 >/dev/null; then
        echo "FAILED (container could not start)"
        exit 4
    fi
    sleep 3
    DOCK_HTTP="$(curl -k -s -o /dev/null -w '%{http_code}' https://127.0.0.1:46302/ || true)"
    docker rm -f "$DOCKER_NAME" >/dev/null 2>&1 || true
    rm -rf -- "$DOCKER_DIR"
    if [[ "$DOCK_HTTP" == "200" ]]; then
        echo "OK (container HTTPS 200)"
    else
        echo "FAILED (HTTP: $DOCK_HTTP)"
        exit 4
    fi
fi

echo ">> STATUS: ALL REQUIRED SYSTEM CHECKS PASSED SUCCESSFULLY!"
