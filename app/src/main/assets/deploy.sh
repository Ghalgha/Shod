#!/bin/bash

set -Eeuo pipefail

export DEBIAN_FRONTEND=noninteractive
export TERM="${TERM:-xterm}"

readonly SCRIPT_VERSION="2.5.3"

_name=shod; _iface=shod1; _net=10.66.67; _net6=fd66:67; _tag=Shod_MANAGED
_bin=/usr/local/bin/shod; _lib=/usr/local/lib/shod; _conf=/etc/shod
_img_bin=/usr/local/bin/shod; _img_lib=/usr/local/lib/shod; _img_conf=/etc/shod
_peer=46000; _web=46002
_le_helper=/usr/local/lib/shod-letsencrypt-renew.sh; _le_venv=/opt/shod-certbot; _le_before=259200; _le_every=24h

readonly Shod_NAME="$_name"                    # systemd unit and Docker container
readonly Shod_UNIT="$_name"
readonly Shod_BIN="$_bin"
readonly Shod_LIB_DIR="$_lib"
readonly Shod_IMAGE_BIN="$_img_bin"            # inside the Docker image
readonly Shod_IMAGE_LIB_DIR="$_img_lib"
readonly Shod_IMAGE_CONFIG_DIR="$_img_conf"
readonly Shod_SUBNET="${_net}.0/24"
readonly Shod_GATEWAY="${_net}.1"
readonly Shod_SUBNET6="${_net6}::/64"
readonly LOG_FILE="/var/log/${_name}-install.log"
readonly PEER_PORT="${Shod_PEER_PORT:-$_peer}"
readonly SSH_PORT="${Shod_SSH_PORT:-22}"
readonly WEB_PORT="${Shod_WEB_PORT:-$_web}"
readonly LE_HTTP_PORT="80"
readonly LE_RENEW_BEFORE_SECONDS="$_le_before"
readonly LE_RENEW_EVERY="$_le_every"
readonly DEPLOY_MODE="${Shod_DEPLOY_MODE:-systemd}"
readonly Shod_IFACE="$_iface"
readonly Shod_CONFIG_DIR="$_conf"
readonly Shod_LE_CERTBOT="${_le_venv}/bin/certbot"
readonly Shod_LE_VENV="$_le_venv"
readonly Shod_LE_STATE_FILE="${Shod_CONFIG_DIR}/letsencrypt-ip.env"
readonly Shod_LE_RENEW_HELPER="$_le_helper"
readonly Shod_LE_SERVICE="${_name}-letsencrypt.service"
readonly Shod_LE_TIMER="${_name}-letsencrypt.timer"
readonly Shod_DATABASE_FILE="shod.db"
readonly Shod_DATABASE_WAL_FILE="shod.db-wal"
readonly Shod_DATABASE_SHM_FILE="shod.db-shm"
readonly Shod_LEGACY_MIGRATION_JSON="passwords.json"
readonly Shod_LEGACY_MIGRATION_IMPORTED_JSON="passwords.json.imported"
readonly Shod_ENV_FILE="${Shod_CONFIG_DIR}/shod.env"
readonly Shod_DEPLOY_OVERRIDES_FILE="${Shod_CONFIG_DIR}/deploy-overrides.json"
readonly UPLOAD_BINARY="/tmp/.shod-upload-server"
readonly UPLOAD_ENV_FILE="/tmp/.shod-upload-web.env"
readonly UPLOAD_OVERRIDES_FILE="/tmp/.shod-upload-overrides.json"
readonly Shod_SYSCTL_FILE="/etc/sysctl.d/99-${_name}.conf"
readonly Shod_UDP_SYSCTL_FILE="/etc/sysctl.d/99-${_name}-udp-buffers.conf"
readonly IPT_COMMENT="$_tag"
readonly IPT_TPROXY_COMMENT="${_tag%_MANAGED}_TPROXY"
readonly Shod_DOCKER_IMAGE="${_name}:${SCRIPT_VERSION}"
readonly Shod_DOCKER_CONTAINER="$_name"
readonly Shod_DOCKER_PREREQ_SERVICE="${_name}-docker-prereq.service"
readonly Shod_DOCKER_PREREQ_HELPER="${_lib}/docker-prereq.sh"
readonly Shod_RUNTIME_DIR="/run/${_name}"
readonly Shod_DOCKER_NETWORK_READY="${Shod_RUNTIME_DIR}/docker-network.ready"
readonly XT_WAIT="${Shod_XT_WAIT:-2}"
readonly START_STABILITY_SECONDS="${Shod_START_STABILITY_SECONDS:-4}"
readonly DOCKER_BUILD_TIMEOUT_SECONDS="${Shod_DOCKER_BUILD_TIMEOUT_SECONDS:-180}"
readonly EXIT_INVALID_ARGUMENT=2
readonly EXIT_PREFLIGHT_FAILED=20
readonly EXIT_CUTOVER_FAILED=30


SYSTEMD_NEEDS_RELOAD=0
DEPLOY_PHASE="initial"
PRESERVE_CONFIG=0
DOCKER_CONTEXT_DIR=""
Shod_DOCKER_CANDIDATE_IMAGE=""

validate_port() {
    local name="$1" value="$2"
    case "$value" in
        ''|*[!0-9]*) die "$name должен быть числом от 1 до 65535, получено: $value" ;;
    esac
    if [ "$value" -lt 1 ] || [ "$value" -gt 65535 ]; then
        die "$name должен быть в диапазоне 1..65535, получено: $value"
    fi
}

validate_positive_seconds() {
    local name="$1" value="$2"
    case "$value" in
        ''|*[!0-9]*) die "$name должен быть положительным числом секунд, получено: $value" ;;
    esac
    if [ "$value" -lt 1 ] || [ "$value" -gt 900 ]; then
        die "$name должен быть в диапазоне 1..900, получено: $value"
    fi
}

validate_distinct_network_ports() {
    [ "$PEER_PORT" != "$SSH_PORT" ] || die "Shod_PEER_PORT не может совпадать с SSH-портом"
    [ "$PEER_PORT" != "$WEB_PORT" ] || die "Shod_PEER_PORT не может совпадать с WEB-портом"
    [ "$PEER_PORT" != "$LE_HTTP_PORT" ] || die "Shod_PEER_PORT не может совпадать с HTTP-портом"
    [ "$WEB_PORT" != "$SSH_PORT" ] || die "Shod_WEB_PORT не может совпадать с SSH-портом"
}

C_GREEN=''; C_YELLOW=''; C_RED=''
C_CYAN='';  C_BOLD='';      C_NC=''

log_info()  { echo -e "${C_GREEN}[✓]${C_NC} $*" | tee -a "$LOG_FILE"; }
log_warn()  { echo -e "${C_YELLOW}[!]${C_NC} $*" | tee -a "$LOG_FILE"; }
log_error() { echo -e "${C_RED}[✗]${C_NC} $*" | tee -a "$LOG_FILE"; }
log_step()  { echo -e "${C_CYAN}[►]${C_NC} ${C_BOLD}$*${C_NC}" | tee -a "$LOG_FILE"; }

default_exit_code() {
    case "$DEPLOY_PHASE" in
        validation) printf '%s' "$EXIT_INVALID_ARGUMENT" ;;
        preflight) printf '%s' "$EXIT_PREFLIGHT_FAILED" ;;
        cutover|activation) printf '%s' "$EXIT_CUTOVER_FAILED" ;;
        *) printf '%s' 1 ;;
    esac
}

die() {
    local message="$1" code="${2:-$(default_exit_code)}"
    log_error "$message"
    printf 'Shod_DEPLOY_ERROR|%s|%s\n' "$DEPLOY_PHASE" "$message"
    cleanup_deploy_uploads
    exit "$code"
}

shod_systemd_unit_exists() {
    command -v systemctl >/dev/null 2>&1 || return 1
    local state
    state="$(systemctl show --value -p LoadState "$Shod_UNIT" 2>/dev/null || true)"
    [ -n "$state" ] && [ "$state" != "not-found" ]
}

# Does this unit text run the managed Shod server?
unit_text_runs_this_shod() {
    grep -Eq '(/usr/local/bin/shod|/usr/local/lib/shod/)'
}

shod_systemd_unit_is_managed() {
    shod_systemd_unit_exists || return 1
    local unit
    unit="$(systemctl cat "$Shod_UNIT" 2>/dev/null || true)"
    printf '%s\n' "$unit" | unit_text_runs_this_shod
}

shod_docker_container_exists() {
    command -v docker >/dev/null 2>&1 && docker inspect "$Shod_DOCKER_CONTAINER" >/dev/null 2>&1
}

docker_container_runs_shod() {
    local container="$1" container_id proc pid cgroup executable command_line details
    docker inspect "$container" >/dev/null 2>&1 || return 1
    details="$(docker inspect --format '{{.Name}}|{{.Config.Image}}|{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{range .Mounts}}{{.Source}}:{{.Destination}}|{{end}}' "$container" 2>/dev/null || true)"
    case "$details" in
        *'|shod|'*|*'|shod:'*|*'|shod-docker-'*|*'/usr/local/bin/shod'*|*":${Shod_CONFIG_DIR}"*) return 0 ;;
    esac
    if docker top "$container" -eo pid,args 2>/dev/null | awk '
NR > 1 && $0 ~ /(^|[[:space:]])\/usr\/local\/bin\/shod([[:space:]]|$)/ { found = 1 }
END { exit !found }
'; then
        return 0
    fi
    container_id="$(docker inspect --format '{{.Id}}' "$container" 2>/dev/null || true)"
    [ -n "$container_id" ] || return 1
    for proc in /proc/[0-9]*; do
        pid="${proc##*/}"
        cgroup="$(cat "$proc/cgroup" 2>/dev/null || true)"
        case "$cgroup" in
            *"docker-${container_id}.scope"*|*"/docker/${container_id}"*) ;;
            *) continue ;;
        esac
        executable="$(readlink "$proc/exe" 2>/dev/null || true)"
        case "$executable" in
            /usr/local/bin/shod|'/usr/local/bin/shod (deleted)') return 0 ;;
        esac
        command_line="$({ tr '\0' ' ' < "$proc/cmdline"; } 2>/dev/null || true)"
        case " $command_line " in
            *" /usr/local/bin/shod "*|*" --config-dir ${Shod_CONFIG_DIR} "*) return 0 ;;
        esac
    done
    return 1
}

shod_persistent_state_exists() {
    [ -f "$Shod_ENV_FILE" ] || [ -f "${Shod_CONFIG_DIR}/${Shod_DATABASE_FILE}" ] || \
        [ -f "${Shod_CONFIG_DIR}/${Shod_LEGACY_MIGRATION_JSON}" ] || \
        [ -f "${Shod_CONFIG_DIR}/${Shod_LEGACY_MIGRATION_IMPORTED_JSON}" ] || \
        [ -f "${Shod_CONFIG_DIR}/web_cert.pem" ] || [ -f "${Shod_CONFIG_DIR}/web_key.pem" ]
}

peer_port_listeners() {
    ss -H -lunp "sport = :${PEER_PORT}" 2>/dev/null || true
}

peer_port_listener_pids() {
    peer_port_listeners | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -un || true
}

docker_container_for_pid() {
    local pid="$1" cgroup container_id
    command -v docker >/dev/null 2>&1 || return 1
    cgroup="$(cat "/proc/$pid/cgroup" 2>/dev/null || true)"
    container_id="$(printf '%s\n' "$cgroup" | sed -nE 's#.*docker-([0-9a-f]{12,64})\.scope.*#\1#p; s#.*/docker/([0-9a-f]{12,64}).*#\1#p' | head -n 1)"
    [ -n "$container_id" ] || return 1
    docker inspect "$container_id" >/dev/null 2>&1 || return 1
    printf '%s\n' "$container_id"
}

shod_process_is_owned() {
    local pid="$1" executable command_line
    case "$pid" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ -r "/proc/$pid/cmdline" ] || return 1
    executable="$(readlink "/proc/$pid/exe" 2>/dev/null || true)"
    case "$executable" in
        "$Shod_BIN"|"$Shod_BIN (deleted)"|"$Shod_IMAGE_BIN"|"$Shod_IMAGE_BIN (deleted)") return 0 ;;
    esac
    command_line="$({ tr '\0' ' ' < "/proc/$pid/cmdline"; } 2>/dev/null || true)"
    case " $command_line " in
        *" --config-dir ${Shod_CONFIG_DIR} "*|*" --config-dir ${Shod_IMAGE_CONFIG_DIR} "*|*" ${Shod_BIN} "*|*" ${Shod_IMAGE_BIN} "*|*" /usr/local/bin/shod "*|*" /usr/local/lib/shod/"*) return 0 ;;
        *) return 1 ;;
    esac
}

shod_process_owns_peer_port() { shod_process_is_owned "$1"; }

all_shod_process_pids() {
    local proc pid
    for proc in /proc/[0-9]*; do
        pid="${proc##*/}"
        shod_process_is_owned "$pid" && printf '%s\n' "$pid"
    done
    return 0
}

shod_peer_port_is_owned() {
    local pid
    while IFS= read -r pid; do
        shod_process_owns_peer_port "$pid" && return 0
    done < <(peer_port_listener_pids)
    return 1
}

force_stop_shod_processes() {
    local pid attempt found
    for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
        found=0
        while IFS= read -r pid; do
            found=1
            log_warn "Принудительное завершение Shod: PID $pid"
            if [ "$attempt" -le 2 ]; then
                kill -TERM "$pid" 2>/dev/null || true
            else
                kill -KILL "$pid" 2>/dev/null || true
            fi
        done < <(all_shod_process_pids)
        if [ "$found" -eq 0 ]; then
            return 0
        fi
        sleep 0.2
    done
    return 1
}

force_release_peer_port() {
    local attempt pid container found
    for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
        [ -z "$(peer_port_listeners)" ] && return 0
        found=0
        while IFS= read -r pid; do
            [ -n "$pid" ] || continue
            found=1
            pid_is_this_shod "$pid" || die "UDP/${PEER_PORT} занят чужим процессом: $(describe_pid "$pid")" "$EXIT_PREFLIGHT_FAILED"
            container="$(docker_container_for_pid "$pid" 2>/dev/null || true)"
            if [ -n "$container" ]; then
                log_warn "Принудительное удаление Docker runtime на UDP/${PEER_PORT}: ${container:0:12}"
                docker update --restart=no "$container" >/dev/null 2>&1 || true
                docker rm -f "$container" >/dev/null 2>&1 || true
            elif [ "$attempt" -le 2 ]; then
                log_warn "Принудительное завершение runtime на UDP/${PEER_PORT}: PID $pid"
                kill -TERM "$pid" 2>/dev/null || true
            else
                log_warn "Принудительное завершение runtime на UDP/${PEER_PORT}: PID $pid"
                kill -KILL "$pid" 2>/dev/null || true
            fi
        done < <(peer_port_listener_pids)
        if [ "$found" -eq 0 ]; then
            log_error "UDP/${PEER_PORT} занят runtime без доступного PID"
            peer_port_listeners | tee -a "$LOG_FILE" >&2
            return 1
        fi
        sleep 0.2
    done
    peer_port_listeners | tee -a "$LOG_FILE" >&2
    return 1
}

# A port held by anything but this server is refused, never killed.
pid_is_this_shod() {
    local pid="$1" container
    shod_process_is_owned "$pid" && return 0
    container="$(docker_container_for_pid "$pid" 2>/dev/null || true)"
    [ -n "$container" ] && docker_container_runs_shod "$container"
}

# A process's name and binary, for a refusal message.
describe_pid() {
    printf '%s (PID %s, %s)' "$(cat "/proc/$1/comm" 2>/dev/null || echo '?')" "$1" "$(readlink "/proc/$1/exe" 2>/dev/null || echo '?')"
}

refuse_foreign_port_holder() {
    local proto="$1" port="$2" label="$3" pid holders=""
    while IFS= read -r pid; do
        [ -n "$pid" ] || continue
        pid_is_this_shod "$pid" && continue
        holders="${holders:+$holders, }$(describe_pid "$pid")"
    done < <(ss -H -l"${proto}"np "sport = :${port}" 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -un || true)
    if [ -z "$holders" ] && ss -H -l"${proto}"n "sport = :${port}" 2>/dev/null | grep -q . && \
       [ -z "$(ss -H -l"${proto}"np "sport = :${port}" 2>/dev/null | grep -oE 'pid=[0-9]+' || true)" ]; then
        holders="процесс без видимого PID"
    fi
    [ -z "$holders" ] || die "${label} ${port} занят: ${holders}. Выберите другой порт («Ручные порты»)" "$EXIT_PREFLIGHT_FAILED"
}

# PIDs holding the TUN interface $1 open: the kernel names it in the descriptor's fdinfo ("iff:	<name>").
tun_holder_pids() {
    local name="$1" fd
    { find /proc/[0-9]*/fd -lname /dev/net/tun 2>/dev/null || true; } | while IFS= read -r fd; do
        grep -qxE "iff:[[:space:]]*${name}" "${fd%/fd/*}/fdinfo/${fd##*/}" 2>/dev/null || continue
        fd="${fd#/proc/}"
        echo "${fd%%/*}"
    done | sort -un || true
}

# Holders of this server's interface that are not this server:
# first shod server shod1 too). Empty when the interface is free, or a leftover nobody holds (deleted as before).
foreign_tun_holders() {
    local pid holders=""
    while IFS= read -r pid; do
        [ -n "$pid" ] || continue
        pid_is_this_shod "$pid" && continue
        holders="${holders:+$holders, }$(describe_pid "$pid")"
    done < <(tun_holder_pids "$Shod_IFACE")
    printf '%s' "$holders"
}

# Refuse to touch a TUN interface held by any process other than this server.
refuse_anothers_server() {
    local holders
    holders="$(foreign_tun_holders)"
    [ -z "$holders" ] || die "Интерфейс ${Shod_IFACE} занят другим процессом: ${holders}" "$EXIT_PREFLIGHT_FAILED"
}

assert_peer_port_is_available() {
    local listeners
    listeners="$(peer_port_listeners)"
    [ -z "$listeners" ] && return 0
    refuse_foreign_port_holder u "$PEER_PORT" "UDP-порт"
    log_warn "UDP/${PEER_PORT} занят; выполняется освобождение собственного runtime"
    force_release_peer_port || die "Не удалось принудительно освободить UDP/${PEER_PORT}"
    [ -z "$(peer_port_listeners)" ] || die "UDP/${PEER_PORT} остался занят после принудительной очистки"
}

stop_shod_docker_container() {
    local container="$1" name running
    docker inspect "$container" >/dev/null 2>&1 || return 0
    name="$(docker inspect --format '{{.Name}}' "$container" 2>/dev/null || printf '%s' "$container")"
    name="${name#/}"
    docker update --restart=no "$container" >/dev/null 2>&1 || true
    running="$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || printf false)"
    if [ "$running" = "true" ]; then
        timeout 10 docker stop --time 5 "$container" >/dev/null 2>&1 || true
        if [ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || printf false)" = "true" ]; then
            docker kill "$container" >/dev/null 2>&1 || true
        fi
    fi
    timeout 10 docker rm -f "$container" >/dev/null 2>&1 || \
        die "Не удалось остановить старый Docker-контейнер Shod"
}

remove_all_shod_docker_containers() {
    command -v docker >/dev/null 2>&1 || return 0
    local container name
    while IFS= read -r container; do
        [ -n "$container" ] || continue
        docker_container_runs_shod "$container" || continue
        name="$(docker inspect --format '{{.Name}}' "$container" 2>/dev/null || printf '%s' "$container")"
        name="${name#/}"
        log_info "Остановка Docker runtime Shod: $name"
        stop_shod_docker_container "$container"
    done < <(docker ps -aq --no-trunc 2>/dev/null || true)
}

systemd_unit_runs_shod() {
    local unit="$1" content
    content="$(systemctl cat "$unit" 2>/dev/null || true)"
    if printf '%s\n' "$content" | unit_text_runs_this_shod; then
        return 0
    fi
    return 1
}

list_shod_systemd_units() {
    command -v systemctl >/dev/null 2>&1 || return 0
    local unit
    while IFS= read -r unit; do
        case "$unit" in
            *.service) ;;
            *) continue ;;
        esac
        case "$unit" in
            "$Shod_DOCKER_PREREQ_SERVICE"|"$Shod_LE_SERVICE") continue ;;
        esac
        if systemd_unit_runs_shod "$unit"; then
            printf '%s\n' "$unit"
        fi
    done < <({
        systemctl list-units --type=service --all --no-legend --plain 2>/dev/null || true
        systemctl list-unit-files --type=service --no-legend --plain 2>/dev/null || true
    } | awk '{print $1}' | sort -u)
    return 0
}

stop_shod_docker_prereq() {
    command -v systemctl >/dev/null 2>&1 || return 0
    local requires_link="/etc/systemd/system/docker.service.requires/${Shod_DOCKER_PREREQ_SERVICE}"
    local wants_link="/etc/systemd/system/docker.service.wants/${Shod_DOCKER_PREREQ_SERVICE}"
    if [ -e "$requires_link" ] || [ -L "$requires_link" ]; then
        rm -f -- "$requires_link"
        SYSTEMD_NEEDS_RELOAD=1
    fi
    if [ -e "$wants_link" ] || [ -L "$wants_link" ]; then
        rm -f -- "$wants_link"
        SYSTEMD_NEEDS_RELOAD=1
    fi
    systemctl disable "$Shod_DOCKER_PREREQ_SERVICE" >/dev/null 2>&1 || true
    if [ "$SYSTEMD_NEEDS_RELOAD" -eq 1 ]; then
        systemctl daemon-reload || die "systemctl daemon-reload завершился ошибкой при отключении Docker prerequisites"
        SYSTEMD_NEEDS_RELOAD=0
    fi
    if systemctl is-active --quiet "$Shod_DOCKER_PREREQ_SERVICE"; then
        log_info "Остановка Docker prerequisite Shod: $Shod_DOCKER_PREREQ_SERVICE"
        timeout 10 systemctl stop "$Shod_DOCKER_PREREQ_SERVICE" >/dev/null 2>&1 || true
        if systemctl is-active --quiet "$Shod_DOCKER_PREREQ_SERVICE"; then
            timeout 2 systemctl kill --kill-who=all --signal=SIGKILL "$Shod_DOCKER_PREREQ_SERVICE" >/dev/null 2>&1 || true
        fi
        if systemctl is-active --quiet "$Shod_DOCKER_PREREQ_SERVICE"; then
            die "Не удалось остановить Docker prerequisite Shod: $Shod_DOCKER_PREREQ_SERVICE"
        fi
    fi
}

stop_all_running_shod_systemd_units() {
    command -v systemctl >/dev/null 2>&1 || return 0
    local unit
    while IFS= read -r unit; do
        [ -n "$unit" ] || continue
        log_info "Остановка systemd runtime Shod: $unit"
        if ! timeout 10 systemctl stop "$unit" >/dev/null 2>&1; then
            log_warn "systemctl stop завершился с ошибкой для $unit; проверяется фактическое состояние"
        fi
        if systemctl is-active --quiet "$unit"; then
            timeout 2 systemctl kill --kill-who=all --signal=SIGKILL "$unit" >/dev/null 2>&1 || true
        fi
        if systemctl is-active --quiet "$unit"; then
            die "Не удалось остановить systemd runtime Shod: $unit"
        fi
        systemctl disable "$unit" >/dev/null 2>&1 || true
    done < <(list_shod_systemd_units)
    return 0
}

remove_all_shod_systemd_units() {
    command -v systemctl >/dev/null 2>&1 || return 0
    local unit fragment
    while IFS= read -r unit; do
        [ -n "$unit" ] || continue
        fragment="$(systemctl show --value -p FragmentPath "$unit" 2>/dev/null || true)"
        case "$fragment" in
            /etc/systemd/system/*)
                rm -f -- "$fragment"
                rm -rf -- "${fragment}.d"
                rm -f -- "/etc/systemd/system/multi-user.target.wants/${unit}"
                SYSTEMD_NEEDS_RELOAD=1
                ;;
        esac
    done < <(list_shod_systemd_units)
    return 0
}

prog() { echo "Shod_PROGRESS|$1|$2"; }

run_timed() {
    local label="$1" started=$SECONDS
    shift
    "$@"
    log_info "Время этапа «$label»: $((SECONDS - started))с"
}

check_root() {
    if [ "$(id -u)" -ne 0 ]; then
        die "Скрипт должен быть запущен от root. Если sudo отсутствует, зайдите под root и запустите: bash $0 $*"
    fi
}

OS_ID="" ; PKG_MGR=""

detect_os() {
    log_step "Определение операционной системы..."
    if [ ! -f /etc/os-release ]; then
        die "Файл /etc/os-release не найден."
    fi
    . /etc/os-release
    OS_ID="${ID:-unknown}"
    case "$OS_ID" in
        ubuntu|debian|linuxmint|pop)     PKG_MGR="apt" ;;
        centos|rhel|rocky|almalinux|oracle|ol) PKG_MGR="yum"
            command -v dnf &>/dev/null && PKG_MGR="dnf" ;;
        fedora)                          PKG_MGR="dnf" ;;
        arch|manjaro|endeavouros)        PKG_MGR="pacman" ;;
        *)
            case " ${ID_LIKE:-} " in
                *" debian "*) PKG_MGR="apt" ;;
                *" rhel "*|*" fedora "*)
                    PKG_MGR="yum"
                    command -v dnf &>/dev/null && PKG_MGR="dnf"
                    ;;
                *" arch "*) PKG_MGR="pacman" ;;
                *) die "Неподдерживаемый дистрибутив: $OS_ID" ;;
            esac
            ;;
    esac
    log_info "ОС: ${PRETTY_NAME:-$OS_ID} | PM: $PKG_MGR"
}

pkg_update_done=0

pkg_update() {
    [ "$pkg_update_done" = "1" ] && return 0
    log_step "Обновление индексов пакетов..."
    case "$PKG_MGR" in
        apt)
            export DEBIAN_FRONTEND=noninteractive
            apt-get update -y >>"$LOG_FILE" 2>&1 || log_warn "apt update завершился с ошибкой, пробую продолжить"
            ;;
        dnf)    dnf makecache -y >>"$LOG_FILE" 2>&1 || true ;;
        yum)    yum makecache -y >>"$LOG_FILE" 2>&1 || true ;;
        pacman) : ;;
    esac
    pkg_update_done=1
}

pkg_install() {
    [ "$#" -eq 0 ] && return 0
    case "$PKG_MGR" in
        apt)
            export DEBIAN_FRONTEND=noninteractive
            apt-get install -y -qq --no-install-recommends "$@" >>"$LOG_FILE" 2>&1
            ;;
        dnf)    dnf install -y "$@" >>"$LOG_FILE" 2>&1 ;;
        yum)    yum install -y "$@" >>"$LOG_FILE" 2>&1 ;;
        pacman) pacman -Syu --noconfirm --needed "$@" >>"$LOG_FILE" 2>&1 ;;
    esac
}

pkg_install_with_refresh() {
    pkg_install "$@" && return 0
    pkg_update
    pkg_install "$@"
}

install_prerequisites() {
    prog 0.10 "Пакеты..."
    log_step "Проверка базовых зависимостей..."

    local ip_package procps_package
    case "$PKG_MGR" in
        apt)     ip_package="iproute2"; procps_package="procps" ;;
        dnf|yum) ip_package="iproute";  procps_package="procps-ng" ;;
        pacman)  ip_package="iproute2"; procps_package="procps-ng" ;;
    esac

    local -a packages=()
    local need_iptables=0
    command -v ip >/dev/null 2>&1 || packages+=("$ip_package")
    command -v curl >/dev/null 2>&1 || packages+=("curl")
    command -v modprobe >/dev/null 2>&1 || packages+=("kmod")
    if ! command -v sysctl >/dev/null 2>&1; then
        packages+=("$procps_package")
    fi
    command -v iptables >/dev/null 2>&1 || need_iptables=1

    if [ "$PKG_MGR" = "pacman" ] && [ "$need_iptables" -eq 1 ]; then
        packages+=("iptables")
        need_iptables=0
    fi

    if [ "${#packages[@]}" -eq 0 ] && [ "$need_iptables" -eq 0 ]; then
        log_info "Все системные зависимости уже установлены — обновление пакетов не требуется"
        return 0
    fi

    pkg_update
    if [ "${#packages[@]}" -gt 0 ]; then
        pkg_install "${packages[@]}" || die "Не удалось установить обязательные пакеты: ${packages[*]}"
    fi

    if [ "$need_iptables" -eq 1 ]; then
        case "$PKG_MGR:$OS_ID" in
            dnf:fedora)
                pkg_install iptables-nft || pkg_install iptables || \
                    die "Не удалось установить iptables/iptables-nft"
                ;;
            dnf:*|yum:*)
                pkg_install iptables || pkg_install iptables-nft || \
                    die "Не удалось установить iptables/iptables-nft"
                ;;
            *)
                pkg_install iptables || die "Не удалось установить обязательный пакет iptables"
                ;;
        esac
    fi
}

require_runtime_tools() {
    command -v ip >/dev/null 2>&1 || die "Команда ip не найдена. Установите iproute2/iproute."
    command -v iptables >/dev/null 2>&1 || die "Команда iptables не найдена. Она обязательна для TUN и NAT."
    command -v sysctl >/dev/null 2>&1 || die "Команда sysctl не найдена. Установите procps/procps-ng."
    if [ "$DEPLOY_MODE" = "systemd" ]; then
        command -v systemctl >/dev/null 2>&1 || die "systemctl не найден. Для native-установки нужен VPS с systemd."
    fi
    case "$(uname -m)" in
        x86_64|amd64) log_info "Архитектура VPS: amd64" ;;
        aarch64|arm64) log_info "Архитектура VPS: ARM64" ;;
        armv7l|armv7|armhf) log_info "Архитектура VPS: ARM32" ;;
        *) die "Архитектура VPS $(uname -m) не поддерживается. Поддерживаются: x86_64, aarch64, armv7l." ;;
    esac
}

detect_wan_interface() {
    local iface fallback=""
    while IFS= read -r iface; do
        iface="${iface%%@*}"
        [ -n "$iface" ] || continue
        [ -n "$fallback" ] || fallback="$iface"
        if ! is_ignored_wan_interface "$iface" && ip link show "$iface" >/dev/null 2>&1; then
            echo "$iface"
            return 0
        fi
    done <<EOF
$(ip -o -4 route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="dev") {print $(i+1); break}}')
EOF
    while IFS= read -r iface; do
        iface="${iface%%@*}"
        [ -n "$iface" ] || continue
        [ -n "$fallback" ] || fallback="$iface"
        if ! is_ignored_wan_interface "$iface" && ip link show "$iface" >/dev/null 2>&1; then
            echo "$iface"
            return 0
        fi
    done <<EOF
$(ip -o -4 addr show scope global 2>/dev/null | awk '{sub(/@.*/, "", $2); print $2}')
EOF
    while IFS= read -r iface; do
        iface="${iface%%@*}"
        [ -n "$iface" ] || continue
        [ "$iface" = "lo" ] && continue
        [ -n "$fallback" ] || fallback="$iface"
        if ! is_ignored_wan_interface "$iface" && ip link show "$iface" >/dev/null 2>&1; then
            echo "$iface"
            return 0
        fi
    done <<EOF
$(ls /sys/class/net/ 2>/dev/null || true)
EOF
    [ -n "$fallback" ] && echo "$fallback"
}

is_ignored_wan_interface() {
    local iface="${1%%@*}"
    case "$iface" in
        ""|lo|"$Shod_IFACE"|shod*|tun*|tap*|wg*|warp*|Warp*|WARP*|CloudflareWARP*|tailscale*|zt*|docker*|br-*|veth*|cni*|flannel*|virbr*|podman*|kube*|dummy*|ifb*)
            return 0
            ;;
    esac
    return 1
}

detect_firewall() {
    command -v iptables >/dev/null 2>&1 || \
        die "iptables не найден: без него сервер не сможет создать рабочий TUN/NAT."
    local ver
    ver="$(iptables --version 2>/dev/null || echo unknown)"
    log_info "Firewall backend: iptables (${ver})"
}

ipt_add_or_ensure() {
    local table="$1" chain="$2"
    shift 2
    local -a targs=()
    [ "$table" = "filter" ] || targs=(-t "$table")
    iptables -w "$XT_WAIT" "${targs[@]}" -C "$chain" "$@" >/dev/null 2>&1 || \
        iptables -w "$XT_WAIT" "${targs[@]}" -I "$chain" 1 "$@" >>"$LOG_FILE" 2>&1
}

fw_add_input_udp() {
    local port="$1"
    ipt_add_or_ensure filter INPUT -p udp --dport "$port" -m comment --comment "$IPT_COMMENT" -j ACCEPT || \
        die "Не удалось открыть ${port}/udp в iptables"
}

fw_add_input_tcp() {
    local port="$1"
    ipt_add_or_ensure filter INPUT -p tcp --dport "$port" -m comment --comment "$IPT_COMMENT" -j ACCEPT || \
        die "Не удалось открыть ${port}/tcp в iptables"
}

fw_add_forward() {
    ipt_add_or_ensure filter INPUT -i "$Shod_IFACE" -s "$Shod_SUBNET" -m comment --comment "$IPT_COMMENT" -j ACCEPT || \
        die "Не удалось установить INPUT -i $Shod_IFACE"
    ipt_add_or_ensure filter FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT || true
    ipt_add_or_ensure filter FORWARD -i "$Shod_IFACE" -m comment --comment "$IPT_COMMENT" -j ACCEPT || \
        die "Не удалось установить FORWARD -i $Shod_IFACE"
    ipt_add_or_ensure filter FORWARD -o "$Shod_IFACE" -m comment --comment "$IPT_COMMENT" -j ACCEPT || \
        die "Не удалось установить FORWARD -o $Shod_IFACE"
}

fw_add_masquerade() {
    local iface="$1" subnet="$2"
    if [ -n "$iface" ]; then
        iptables -w "$XT_WAIT" -t nat -C POSTROUTING -s "$subnet" -o "$iface" -m comment --comment "$IPT_COMMENT" -j MASQUERADE >/dev/null 2>&1 || \
            iptables -w "$XT_WAIT" -t nat -A POSTROUTING -s "$subnet" -o "$iface" -m comment --comment "$IPT_COMMENT" -j MASQUERADE >>"$LOG_FILE" 2>&1 || true
        local wan_ip
        wan_ip="$(ip -4 -o addr show dev "$iface" scope global 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -n 1)"
        if [ -n "$wan_ip" ]; then
            iptables -w "$XT_WAIT" -t nat -C POSTROUTING -s "$subnet" -o "$iface" -m comment --comment "$IPT_COMMENT" -j SNAT --to-source "$wan_ip" >/dev/null 2>&1 || \
                iptables -w "$XT_WAIT" -t nat -I POSTROUTING 1 -s "$subnet" -o "$iface" -m comment --comment "$IPT_COMMENT" -j SNAT --to-source "$wan_ip" >>"$LOG_FILE" 2>&1 || true
        fi
    fi
    iptables -w "$XT_WAIT" -t nat -C POSTROUTING -s "$subnet" ! -o "$Shod_IFACE" -m comment --comment "$IPT_COMMENT" -j MASQUERADE >/dev/null 2>&1 || \
        iptables -w "$XT_WAIT" -t nat -A POSTROUTING -s "$subnet" ! -o "$Shod_IFACE" -m comment --comment "$IPT_COMMENT" -j MASQUERADE >>"$LOG_FILE" 2>&1 || \
        die "Не удалось установить NAT MASQUERADE"
}

fw_add_mss_clamping() {
    local subnet="$1"
    ipt_add_or_ensure mangle FORWARD -s "$subnet" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu || \
        die "Не удалось установить исходящий TCP MSS clamping"
    ipt_add_or_ensure mangle FORWARD -d "$subnet" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu || \
        die "Не удалось установить входящий TCP MSS clamping"
}

delete_marked_rules() {
    local table="$1" chain="$2" marker="$3" i numbers number
    local -a targs=()
    [ "$table" = "filter" ] || targs=(-t "$table")
    for i in 1 2 3 4 5 6 7 8; do
        numbers="$(iptables -w "$XT_WAIT" "${targs[@]}" -S "$chain" 2>/dev/null | awk -v chain="$chain" -v marker="$marker" '
$1 == "-A" && $2 == chain {
    n++
    if (index($0, marker)) hit[++h] = n
}
END {
    for (i = h; i >= 1; i--) print hit[i]
}
')" || return 0
        [ -n "$numbers" ] || break
        while IFS= read -r number; do
            [ -n "$number" ] && iptables -w "$XT_WAIT" "${targs[@]}" -D "$chain" "$number" >/dev/null 2>&1 || true
        done <<EOF
$numbers
EOF
    done
}

cleanup_shod_netfilter_rules() {
    local table chain marker
    local markers="$IPT_COMMENT Shod_MIRRORED Shod_TPROXY Shod_LOCAL_SOCKS Shod_LOCAL_SOCKS_MARK Shod_SOCKS Shod_CASCADE_NO_QUIC"
    for marker in $markers; do
        for table in filter nat mangle; do
            for chain in INPUT FORWARD PREROUTING POSTROUTING OUTPUT; do
                delete_marked_rules "$table" "$chain" "$marker"
            done
        done
    done
}

secure_persistent_state() {
    [ -d "$Shod_CONFIG_DIR" ] || return 0
    chmod 700 "$Shod_CONFIG_DIR" 2>/dev/null || true

    local file
    for file in \
        "$Shod_DATABASE_FILE" \
        "$Shod_DATABASE_WAL_FILE" \
        "$Shod_DATABASE_SHM_FILE" \
        "$Shod_LEGACY_MIGRATION_JSON" \
        "$Shod_LEGACY_MIGRATION_IMPORTED_JSON"; do
        [ -f "$Shod_CONFIG_DIR/$file" ] && chmod 600 "$Shod_CONFIG_DIR/$file" 2>/dev/null || true
    done
    [ -f "$Shod_ENV_FILE" ] && chmod 600 "$Shod_ENV_FILE" 2>/dev/null || true
    [ -f "$Shod_DEPLOY_OVERRIDES_FILE" ] && chmod 600 "$Shod_DEPLOY_OVERRIDES_FILE" 2>/dev/null || true
    [ -f "$Shod_CONFIG_DIR/web_cert.pem" ] && chmod 644 "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true
    [ -f "$Shod_CONFIG_DIR/web_key.pem" ] && chmod 600 "$Shod_CONFIG_DIR/web_key.pem" 2>/dev/null || true
    [ -f "$Shod_LE_STATE_FILE" ] && chmod 600 "$Shod_LE_STATE_FILE" 2>/dev/null || true
}

ensure_shod_directory() {
    local path="$1" mode="$2"
    if [ -L "$path" ] || [ ! -d "$path" ]; then
        rm -rf -- "$path" || die "Не удалось очистить путь Shod: $path"
    fi
    mkdir -p -- "$path" || die "Не удалось подготовить каталог Shod: $path"
    chmod "$mode" "$path" || die "Не удалось установить права на каталог Shod: $path"
}

clear_runtime_config_preserving_database() {
    local entry base
    ensure_shod_directory "$Shod_CONFIG_DIR" 700
    shopt -s nullglob dotglob
    for entry in "$Shod_CONFIG_DIR"/*; do
        base="${entry##*/}"
        case "$base" in
            "$Shod_DATABASE_FILE"|"$Shod_DATABASE_WAL_FILE"|"$Shod_DATABASE_SHM_FILE"|"$Shod_LEGACY_MIGRATION_JSON"|"$Shod_LEGACY_MIGRATION_IMPORTED_JSON"|web_cert.pem|web_key.pem|letsencrypt-ip.env) continue ;;
        esac
        rm -rf -- "$entry"
    done
    shopt -u nullglob dotglob
    secure_persistent_state
}

remove_managed_work_dir() {
    local path="$1"
    case "$path" in
        /tmp/shod-docker.*)
            rm -rf -- "$path"
            ;;
        "")
            ;;
        *)
            log_warn "Отказ от удаления неожиданного временного пути: $path"
            return 1
            ;;
    esac
}

cleanup_deploy_uploads() {
    local docker_context="$DOCKER_CONTEXT_DIR"
    DOCKER_CONTEXT_DIR=""
    remove_managed_work_dir "$docker_context" || true
    if command -v docker >/dev/null 2>&1 && [ -n "$Shod_DOCKER_CANDIDATE_IMAGE" ]; then
        docker image rm "$Shod_DOCKER_CANDIDATE_IMAGE" >/dev/null 2>&1 || true
        Shod_DOCKER_CANDIDATE_IMAGE=""
    fi
    rm -f -- "$UPLOAD_BINARY" "$UPLOAD_ENV_FILE" "$UPLOAD_OVERRIDES_FILE"
}

validate_candidate_environment() {
    local candidate_env="$1" candidate_overrides="$2"
    grep -Eq '^[[:space:]]*(export[[:space:]]+)?Shod_WEB_USER=.+$' "$candidate_env" || \
        die "В переданной WEB-конфигурации отсутствует Shod_WEB_USER" "$EXIT_PREFLIGHT_FAILED"
    grep -Eq '^[[:space:]]*(export[[:space:]]+)?Shod_WEB_PASS=.+$' "$candidate_env" || \
        die "В переданной WEB-конфигурации отсутствует Shod_WEB_PASS" "$EXIT_PREFLIGHT_FAILED"
    grep -q '"main_password"' "$candidate_overrides" || \
        die "В deploy-конфигурации отсутствует main_password" "$EXIT_PREFLIGHT_FAILED"
}

prepare_uploaded_release() {
    DEPLOY_PHASE="preflight"
    if [ "$PRESERVE_CONFIG" = "1" ]; then
        # An update uploads neither file (the app keeps the server's own), so files already in /tmp are leftovers
        # from an interrupted deployment; the installed server's config always wins.
        rm -f -- "$UPLOAD_ENV_FILE" "$UPLOAD_OVERRIDES_FILE"
        [ -s "$Shod_ENV_FILE" ] || \
            die "Не найдена существующая WEB-конфигурация ${Shod_ENV_FILE}; выполните первый деплой" "$EXIT_PREFLIGHT_FAILED"
        install -m 0600 "$Shod_ENV_FILE" "$UPLOAD_ENV_FILE" || die "Не удалось прочитать существующую WEB-конфигурацию" "$EXIT_PREFLIGHT_FAILED"
        if [ -s "$Shod_DEPLOY_OVERRIDES_FILE" ]; then
            install -m 0600 "$Shod_DEPLOY_OVERRIDES_FILE" "$UPLOAD_OVERRIDES_FILE" || die "Не удалось прочитать существующую deploy-конфигурацию" "$EXIT_PREFLIGHT_FAILED"
        else
            # The server deletes its deploy overrides once it has applied them (main.rs), so a server that has started
            # has none; its tunnel password and DNS are in its database by then, and an empty main_password keeps them.
            # Without that database there is nothing to keep: a first deploy is needed.
            [ -s "${Shod_CONFIG_DIR}/${Shod_DATABASE_FILE}" ] || \
                die "Не найдена база сервера ${Shod_CONFIG_DIR}/${Shod_DATABASE_FILE}; выполните первый деплой" "$EXIT_PREFLIGHT_FAILED"
            ( umask 077; printf '{"main_password":""}\n' > "$UPLOAD_OVERRIDES_FILE" ) || \
                die "Не удалось подготовить deploy-конфигурацию обновления" "$EXIT_PREFLIGHT_FAILED"
        fi
        log_info "Режим обновления: логин, пароли и путь панели сохраняются"
    fi
    [ -f "$UPLOAD_BINARY" ] && [ -s "$UPLOAD_BINARY" ] || \
        die "Новый бинарник не загружен или пуст" "$EXIT_PREFLIGHT_FAILED"
    [ -f "$UPLOAD_ENV_FILE" ] && [ -s "$UPLOAD_ENV_FILE" ] || \
        die "Одноразовая WEB-конфигурация не загружена" "$EXIT_PREFLIGHT_FAILED"
    [ -f "$UPLOAD_OVERRIDES_FILE" ] && [ -s "$UPLOAD_OVERRIDES_FILE" ] || \
        die "Одноразовая deploy-конфигурация не загружена" "$EXIT_PREFLIGHT_FAILED"

    chmod 0755 "$UPLOAD_BINARY" || die "Не удалось подготовить загруженный бинарник shod" "$EXIT_PREFLIGHT_FAILED"
    chmod 0600 "$UPLOAD_ENV_FILE" "$UPLOAD_OVERRIDES_FILE" || \
        die "Не удалось защитить загруженную конфигурацию" "$EXIT_PREFLIGHT_FAILED"
    validate_candidate_environment "$UPLOAD_ENV_FILE" "$UPLOAD_OVERRIDES_FILE"
    [ -x "$UPLOAD_BINARY" ] || die "Загруженный бинарник Shod не исполняемый" "$EXIT_PREFLIGHT_FAILED"
    log_info "Загруженный бинарник shod проверен"
    log_info "Загруженный бинарник и конфигурация проверены до остановки текущего сервиса"
}

finish_deployment() {
    if command -v docker >/dev/null 2>&1 && [ -n "$Shod_DOCKER_CANDIDATE_IMAGE" ]; then
        docker image rm "$Shod_DOCKER_CANDIDATE_IMAGE" >/dev/null 2>&1 || \
            log_warn "Не удалось снять временный Docker tag $Shod_DOCKER_CANDIDATE_IMAGE"
    fi
    cleanup_deploy_uploads
}

remove_shod_tun_interface() {
    local attempt waited=0
    if ! ip link show "$Shod_IFACE" >/dev/null 2>&1; then
        return 0
    fi
    local foreign
    foreign="$(foreign_tun_holders)"
    if [ -n "$foreign" ]; then
        log_error "TUN-интерфейс $Shod_IFACE держит другой сервер (${foreign}) — не удаляется"
        return 1
    fi

    log_warn "Обнаружен остаточный TUN-интерфейс $Shod_IFACE; выполняется восстановление runtime"
    for attempt in 1 2 3 4; do
        if timeout 2 ip link del "$Shod_IFACE" >>"$LOG_FILE" 2>&1; then
            waited=0
            while [ "$waited" -lt 20 ]; do
                if ! ip link show "$Shod_IFACE" >/dev/null 2>&1; then
                    log_info "Остаточный TUN-интерфейс $Shod_IFACE удалён"
                    return 0
                fi
                sleep 0.1
                waited=$((waited + 1))
            done
        fi
        sleep 0.2
    done

    ip -d link show "$Shod_IFACE" 2>&1 | tee -a "$LOG_FILE" >&2 || true
    return 1
}

shod_cleanup() {
    prog 0.15 "Очистка..."
    echo "🧹 Переключение со старой установки Shod..."

    local had_old_install=0 managed_systemd_unit=0
    if shod_systemd_unit_is_managed; then
        managed_systemd_unit=1
    fi
    if [ -e "$Shod_BIN" ] || [ -d "$Shod_LIB_DIR" ] || \
       [ "$managed_systemd_unit" -eq 1 ] || shod_docker_container_exists || \
       shod_persistent_state_exists || ip link show "$Shod_IFACE" >/dev/null 2>&1 || \
       shod_peer_port_is_owned; then
        had_old_install=1
    fi

    remove_all_shod_docker_containers

    if command -v systemctl >/dev/null 2>&1; then
        stop_shod_docker_prereq
        stop_all_running_shod_systemd_units
    fi

    force_stop_shod_processes || die "Не удалось завершить процессы Shod"
    remove_all_shod_systemd_units
    [ -e "/etc/systemd/system/${Shod_DOCKER_PREREQ_SERVICE}" ] && SYSTEMD_NEEDS_RELOAD=1
    rm -f "/etc/systemd/system/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "/etc/systemd/system/docker.service.requires/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "/etc/systemd/system/docker.service.wants/${Shod_DOCKER_PREREQ_SERVICE}"
    if [ "$SYSTEMD_NEEDS_RELOAD" -eq 1 ] && command -v systemctl >/dev/null 2>&1; then
        systemctl daemon-reload || die "systemctl daemon-reload завершился ошибкой после удаления старого runtime"
        SYSTEMD_NEEDS_RELOAD=0
    fi
    rm -f "$Shod_BIN"
    rm -rf "$Shod_LIB_DIR"
    clear_runtime_config_preserving_database

    assert_peer_port_is_available

    remove_shod_tun_interface || die "Не удалось освободить TUN-интерфейс $Shod_IFACE; переключение отменено"
    if [ "$had_old_install" -eq 1 ]; then
        cleanup_shod_netfilter_rules || true
    fi

    echo "✓ Старый runtime удалён; SQLite/legacy JSON state сохранён"
}

setup_sysctl() {
    prog 0.25 "Sysctl..."
    echo "⚙️  Настройка сетевых параметров..."

    modprobe tcp_bbr 2>/dev/null || true
    if [ -d /etc/modules-load.d ]; then
        echo "tcp_bbr" > /etc/modules-load.d/bbr.conf 2>/dev/null || true
    fi

    echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true
    echo 2 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true
    echo 2 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null || true
    echo 0 > /proc/sys/net/ipv6/conf/default/disable_ipv6 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv6/conf/all/forwarding 2>/dev/null || true
    echo 1 > /proc/sys/net/ipv6/conf/default/forwarding 2>/dev/null || true
    echo 2 > /proc/sys/net/ipv6/conf/all/accept_ra 2>/dev/null || true
    echo 2 > /proc/sys/net/ipv6/conf/default/accept_ra 2>/dev/null || true
    for dev in /proc/sys/net/ipv6/conf/*; do
        if [ -d "$dev" ]; then
            echo 0 > "$dev/disable_ipv6" 2>/dev/null || true
            echo 2 > "$dev/accept_ra" 2>/dev/null || true
        fi
    done
    mkdir -p /etc/sysctl.d
    cat > "$Shod_SYSCTL_FILE" << 'SYSEOF'
net.ipv4.ip_forward = 1
net.ipv4.conf.all.rp_filter = 2
net.ipv4.conf.default.rp_filter = 2
net.ipv6.conf.all.disable_ipv6 = 0
net.ipv6.conf.default.disable_ipv6 = 0
net.ipv6.conf.all.forwarding = 1
net.ipv6.conf.default.forwarding = 1
net.ipv6.conf.all.accept_ra = 2
net.ipv6.conf.default.accept_ra = 2
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.core.netdev_max_backlog = 10000
net.core.somaxconn = 65535
net.ipv4.tcp_rmem = 4096 131072 67108864
net.ipv4.tcp_wmem = 4096 131072 67108864
net.ipv4.tcp_mtu_probing = 1
net.ipv4.tcp_slow_start_after_idle = 0
net.ipv4.tcp_window_scaling = 1
net.ipv4.tcp_sack = 1
net.ipv4.tcp_timestamps = 1
net.ipv4.tcp_fastopen = 3
SYSEOF

    cat > "$Shod_UDP_SYSCTL_FILE" << 'SYSEOF'
net.core.rmem_max = 67108864
net.core.wmem_max = 67108864
SYSEOF

    sysctl -p "$Shod_SYSCTL_FILE" >>"$LOG_FILE" 2>&1 || log_warn "Не удалось применить обязательные параметры маршрутизации"
    sysctl -p "$Shod_UDP_SYSCTL_FILE" >>"$LOG_FILE" 2>&1 || log_warn "Ядро ограничило лимиты сетевых буферов; сервер продолжит работу с доступными значениями"

    [ "$(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null || echo 0)" = "1" ] || \
        die "IPv4 forwarding невозможно включить на этом VPS"

    echo "✓ Sysctl настроен (IPv4/IPv6 forwarding + BBR + буферы)"
}

# 2.5.0 added "mitigations=off nowatchdog" to the GRUB kernel command line here: it switched the kernel's CPU-vulnerability
# protections off from the next reboot, uninstall never put them back, and the server does not depend on it (2.1.9 never
# did it; the gain was never measured). Dropped in 2.5.2 — a VPN install does not decide the host kernel's security.
# A box that already has the flag keeps it: nothing here edits GRUB.

setup_nat_and_firewall() {
    prog 0.40 "NAT + Firewall..."
    echo "🛡  Настройка NAT и фаервола..."

    local iface
    iface=$(detect_wan_interface)

    if [ -z "$iface" ]; then
        die "Не удалось определить WAN-интерфейс для NAT подсети $Shod_SUBNET"
    fi
    ip link show "$iface" >/dev/null 2>&1 || die "Определённый WAN-интерфейс $iface не существует"

    log_info "WAN-интерфейс: $iface"
    sysctl -w "net.ipv4.conf.${iface}.rp_filter=2" >>"$LOG_FILE" 2>&1 || true
    sysctl -w "net.ipv6.conf.${iface}.disable_ipv6=0" >>"$LOG_FILE" 2>&1 || true
    sysctl -w "net.ipv6.conf.${iface}.accept_ra=2" >>"$LOG_FILE" 2>&1 || true
    sysctl -w "net.ipv6.conf.${iface}.forwarding=1" >>"$LOG_FILE" 2>&1 || true
    # 2.5.0 ran `netplan apply` here. It re-applies the whole network configuration mid-install (the uplink can drop, and on a
    # box running other software networkd may remove routes it did not configure), and on a netplan box it undoes the line
    # above: networkd resets accept_ra on the links it manages. The server is IPv4-only; 2.1.9 never did it. Dropped in 2.5.2.

    local gw_ip
    gw_ip="$(ip -4 route show default 2>/dev/null | awk '/default/ {for(i=1;i<=NF;i++) if($i=="via") {print $(i+1); break}}')"
    if [ -n "$gw_ip" ]; then
        ip route replace "$gw_ip" dev "$iface" scope link >>"$LOG_FILE" 2>&1 || true
    fi

    fw_add_input_udp "$PEER_PORT"
    fw_add_input_tcp "$WEB_PORT"
    fw_add_input_tcp "$LE_HTTP_PORT"

    fw_add_forward

    fw_add_masquerade "$iface" "$Shod_SUBNET"
    
    fw_add_mss_clamping "$Shod_SUBNET"

    echo "✓ NAT: MASQUERADE на $iface для $Shod_SUBNET"
    echo "✓ Порты Shod: ${PEER_PORT}/udp(PEER), ${WEB_PORT}/tcp(WEB), ${LE_HTTP_PORT}/tcp(LE)"
    echo "✓ TCP MSS Clamping включен"
}

write_network_helper() {
    local target="$1"
    cat > "$target" << NETEOF
#!/bin/sh
set -eu
    PEER_PORT="$PEER_PORT"
WEB_PORT="$WEB_PORT"
LE_HTTP_PORT="$LE_HTTP_PORT"
Shod_IFACE="$Shod_IFACE"
IPT_COMMENT="$IPT_COMMENT"
SUBNET="$Shod_SUBNET"
SUBNET6="$Shod_SUBNET6"
XT_WAIT="$XT_WAIT"
Shod_RUNTIME_DIR="$Shod_RUNTIME_DIR"
NETWORK_READY_FILE="$Shod_DOCKER_NETWORK_READY"

command -v ip >/dev/null 2>&1 || exit 20
command -v iptables >/dev/null 2>&1 || exit 21
[ -w /proc/sys/net/ipv4/ip_forward ] && echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true
[ -w /proc/sys/net/ipv6/conf/all/forwarding ] && echo 1 > /proc/sys/net/ipv6/conf/all/forwarding 2>/dev/null || true
[ -w /proc/sys/net/ipv4/conf/all/rp_filter ] && echo 2 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true
[ -w /proc/sys/net/ipv4/conf/default/rp_filter ] && echo 2 > /proc/sys/net/ipv4/conf/default/rp_filter 2>/dev/null || true
[ -w /proc/sys/net/ipv4/tcp_notsent_lowat ] && echo 131072 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null || true

is_ignored_wan_interface() {
    ignored_iface="\${1%%@*}"
    case "\$ignored_iface" in
        ""|lo|"\$Shod_IFACE"|shod*|tun*|tap*|wg*|warp*|Warp*|WARP*|CloudflareWARP*|tailscale*|zt*|docker*|br-*|veth*|cni*|flannel*|virbr*|podman*|kube*|dummy*|ifb*)
            return 0
            ;;
    esac
    return 1
}

detect_wan_interface() {
    fallback=""
    for iface in \$(ip -o -4 route show default 2>/dev/null | awk '{for(i=1;i<=NF;i++) if(\$i=="dev") {print \$(i+1); break}}'); do
        iface="\${iface%%@*}"
        [ -n "\$iface" ] || continue
        [ -n "\$fallback" ] || fallback="\$iface"
        if ! is_ignored_wan_interface "\$iface" && ip link show "\$iface" >/dev/null 2>&1; then
            echo "\$iface"
            return 0
        fi
    done
    for iface in \$(ip -o -4 addr show scope global 2>/dev/null | awk '{sub(/@.*/, "", \$2); print \$2}'); do
        iface="\${iface%%@*}"
        [ -n "\$iface" ] || continue
        [ -n "\$fallback" ] || fallback="\$iface"
        if ! is_ignored_wan_interface "\$iface" && ip link show "\$iface" >/dev/null 2>&1; then
            echo "\$iface"
            return 0
        fi
    done
    for iface in \$(ls /sys/class/net/ 2>/dev/null || true); do
        iface="\${iface%%@*}"
        [ -n "\$iface" ] || continue
        [ "\$iface" = "lo" ] && continue
        [ -n "\$fallback" ] || fallback="\$iface"
        if ! is_ignored_wan_interface "\$iface" && ip link show "\$iface" >/dev/null 2>&1; then
            echo "\$iface"
            return 0
        fi
    done
    [ -n "\$fallback" ] && echo "\$fallback"
}

WAN_IFACE=""
network_waited=0
while [ "\$network_waited" -lt 90 ]; do
    candidate_iface=\$(detect_wan_interface || true)
    if [ -n "\$candidate_iface" ] && ip link show "\$candidate_iface" >/dev/null 2>&1 && \
       ip -o -4 route show default 2>/dev/null | grep -q .; then
        WAN_IFACE="\$candidate_iface"
        break
    fi
    network_waited=\$((network_waited + 1))
    sleep 1
done
[ -n "\$WAN_IFACE" ] || exit 22
[ -w "/proc/sys/net/ipv4/conf/\$WAN_IFACE/rp_filter" ] && echo 2 > "/proc/sys/net/ipv4/conf/\$WAN_IFACE/rp_filter" 2>/dev/null || true
[ -w "/proc/sys/net/ipv4/conf/\$Shod_IFACE/rp_filter" ] && echo 2 > "/proc/sys/net/ipv4/conf/\$Shod_IFACE/rp_filter" 2>/dev/null || true

# Для /32 сетей с onlink-шлюзом обеспечиваем явный host route к шлюзу
gw=\$(ip -4 route show default 2>/dev/null | awk '/default/ {for(i=1;i<=NF;i++) if(\$i=="via") {print \$(i+1); break}}')
if [ -n "\$gw" ]; then
    ip route replace "\$gw" dev "\$WAN_IFACE" scope link 2>/dev/null || true
fi

if ip link show "\$Shod_IFACE" >/dev/null 2>&1; then
    ip link set dev "\$Shod_IFACE" txqueuelen 1000 2>/dev/null || true
fi

ipt() { iptables -w "\$XT_WAIT" "\$@"; }
ipt -C INPUT -p udp --dport "\$PEER_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I INPUT -p udp --dport "\$PEER_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -C INPUT -p tcp --dport "\$WEB_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I INPUT -p tcp --dport "\$WEB_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -C INPUT -p tcp --dport "\$LE_HTTP_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I INPUT -p tcp --dport "\$LE_HTTP_PORT" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -C INPUT -i "\$Shod_IFACE" -s "\$SUBNET" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I INPUT -i "\$Shod_IFACE" -s "\$SUBNET" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -C FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || ipt -I FORWARD 1 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
ipt -C FORWARD -i "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I FORWARD -i "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -C FORWARD -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ipt -I FORWARD -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
ipt -t nat -C POSTROUTING -s "\$SUBNET" -o "\$WAN_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE 2>/dev/null || ipt -t nat -A POSTROUTING -s "\$SUBNET" -o "\$WAN_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE || true
ipt -t nat -C POSTROUTING -s "\$SUBNET" ! -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE 2>/dev/null || ipt -t nat -A POSTROUTING -s "\$SUBNET" ! -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE
WAN_IP="\$(ip -4 -o addr show dev "\$WAN_IFACE" scope global 2>/dev/null | awk '{print \$4}' | cut -d/ -f1 | head -n 1)"
if [ -n "\$WAN_IP" ]; then
    ipt -t nat -C POSTROUTING -s "\$SUBNET" -o "\$WAN_IFACE" -m comment --comment "\$IPT_COMMENT" -j SNAT --to-source "\$WAN_IP" 2>/dev/null || \
    ipt -t nat -I POSTROUTING 1 -s "\$SUBNET" -o "\$WAN_IFACE" -m comment --comment "\$IPT_COMMENT" -j SNAT --to-source "\$WAN_IP" 2>/dev/null || true
fi
ipt -t mangle -C FORWARD -s "\$SUBNET" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || ipt -t mangle -I FORWARD -s "\$SUBNET" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu
ipt -t mangle -C FORWARD -d "\$SUBNET" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || ipt -t mangle -I FORWARD -d "\$SUBNET" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu

if command -v ip6tables >/dev/null 2>&1 && ip -6 route show default 2>/dev/null | grep -q .; then
    ip6t() { ip6tables -w "\$XT_WAIT" "\$@"; }
    ip6t -C FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || ip6t -I FORWARD 1 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
    ip6t -C FORWARD -i "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ip6t -I FORWARD -i "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
    ip6t -C FORWARD -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT 2>/dev/null || ip6t -I FORWARD -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j ACCEPT
    ip6t -t nat -C POSTROUTING -s "\$SUBNET6" ! -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE 2>/dev/null || ip6t -t nat -A POSTROUTING -s "\$SUBNET6" ! -o "\$Shod_IFACE" -m comment --comment "\$IPT_COMMENT" -j MASQUERADE || true
    ip6t -t mangle -C FORWARD -s "\$SUBNET6" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || ip6t -t mangle -I FORWARD -s "\$SUBNET6" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || true
    ip6t -t mangle -C FORWARD -d "\$SUBNET6" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || ip6t -t mangle -I FORWARD -d "\$SUBNET6" -p tcp -m tcp --tcp-flags SYN,RST SYN -m comment --comment "\$IPT_COMMENT" -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null || true
fi
umask 077
mkdir -p "\$Shod_RUNTIME_DIR"
: > "\$NETWORK_READY_FILE"
NETEOF
    chmod 0755 "$target"
}


write_tun_recovery_helper() {
    local target="$1"
    cat > "$target" << RECOVEREOF
shod_process_is_owned() {
    pid="\$1"
    [ -r "/proc/\$pid/cmdline" ] || return 1
    executable="\$(readlink "/proc/\$pid/exe" 2>/dev/null || true)"
    case "\$executable" in
        /usr/local/bin/shod|'/usr/local/bin/shod (deleted)'|/usr/local/lib/shod/*|'/usr/local/lib/shod/'*' (deleted)') return 0 ;;
    esac
    command_line="\$({ tr '\\0' ' ' < "/proc/\$pid/cmdline"; } 2>/dev/null || true)"
    case " \$command_line " in
        *" --config-dir $Shod_CONFIG_DIR "*|*" /usr/local/bin/shod "*|*" /usr/local/lib/shod/"*) return 0 ;;
        *) return 1 ;;
    esac
}

release_owned_peer_port() {
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        peer_port_is_busy || return 0
        pids="\$(peer_port_pids)"
        if [ -z "\$pids" ]; then
            echo "[Shod] UDP/\$PEER_PORT is occupied by a runtime without visible PID" >&2
            ss -H -lunp "sport = :\$PEER_PORT" >&2 || true
            return 24
        fi
        for pid in \$pids; do
            # next to a stock server: only our own stale runtime is ours to stop; anything else holding the port is
            # refused (the deploy picked a free one), so this start fails loudly instead of killing a neighbour
            if ! shod_process_is_owned "\$pid"; then
                echo "[Shod] UDP/\$PEER_PORT is held by PID \$pid, which is not this server; not touching it" >&2
                return 24
            fi
            if [ "\$attempt" -le 2 ]; then
                echo "[Shod] UDP/\$PEER_PORT runtime PID \$pid; terminating" >&2
                kill -TERM "\$pid" 2>/dev/null || true
            else
                echo "[Shod] UDP/\$PEER_PORT runtime PID \$pid; killing" >&2
                kill -KILL "\$pid" 2>/dev/null || true
            fi
        done
        sleep 0.2
    done
    echo "[Shod] UDP/\$PEER_PORT did not release" >&2
    ss -H -lunp "sport = :\$PEER_PORT" >&2 || true
    return 24
}

release_owned_peer_port || exit \$?

if ! ip link show "\$Shod_IFACE" >/dev/null 2>&1; then
    exit 0
fi

echo "[Shod] stale TUN interface \$Shod_IFACE detected; recovering runtime" >&2
for attempt in 1 2 3 4; do
    timeout 2 ip link del "\$Shod_IFACE" >/dev/null 2>&1 || true
    if ! ip link show "\$Shod_IFACE" >/dev/null 2>&1; then
        echo "[Shod] stale TUN interface \$Shod_IFACE removed" >&2
        exit 0
    fi
    sleep 0.2
done

ip -d link show "\$Shod_IFACE" >&2 || true
echo "[Shod] unable to release TUN interface \$Shod_IFACE" >&2
exit 23
RECOVEREOF
    chmod 0755 "$target"
}

install_network_helper() {
    ensure_shod_directory "$Shod_LIB_DIR" 755
    write_network_helper "$Shod_LIB_DIR/network-up.sh"
    write_tun_recovery_helper "$Shod_LIB_DIR/tun-recover.sh"
}

verify_configured_network() {
    local iface="${1:-$(detect_wan_interface)}"
    [ -n "$iface" ] || die "WAN-интерфейс не определён при проверке сети"
    [ "$(cat /proc/sys/net/ipv4/ip_forward 2>/dev/null || echo 0)" = "1" ] || die "IPv4 forwarding не включён"
    ip link show "$iface" >/dev/null 2>&1 || die "WAN-интерфейс $iface исчез"
}

setup_shod_binary() {
    prog 0.60 "Бинарник..."
    echo "Установка shod..."

    mkdir -p "$(dirname "$Shod_BIN")"     # never re-chmod an existing /usr/local/bin (Debian: 2775 root:staff)
    install -m 0755 "$UPLOAD_BINARY" "$Shod_BIN" || \
        die "Не удалось установить новый бинарник shod"
    echo "✓ shod установлен: $Shod_BIN"

    rm -f /usr/local/bin/hev-socks5-tunnel
    echo "✓ SOCKS5-форвардер встроен в shod (TPROXY, поднимается при первом использовании)"

    if command -v systemctl >/dev/null 2>&1; then
        systemctl stop shod-cascade.service --no-block >/dev/null 2>&1 || true
        timeout 1 systemctl kill --kill-who=all --signal=SIGKILL shod-cascade.service >/dev/null 2>&1 || true
    fi
    if [ -e /etc/systemd/system/shod-cascade.service ] || [ -L /etc/systemd/system/shod-cascade.service ]; then
        SYSTEMD_NEEDS_RELOAD=1
    fi
    rm -f /etc/systemd/system/shod-cascade.service
    rm -f /etc/systemd/system/multi-user.target.wants/shod-cascade.service
    rm -f /usr/local/bin/shod-cascade /usr/local/bin/hev-socks5-server
    rm -f /usr/local/share/shod/cascade.sh

    mkdir -p "$Shod_CONFIG_DIR"
}

setup_shod_environment() {
    ensure_shod_directory "$Shod_CONFIG_DIR" 700
    install -m 0600 "$UPLOAD_ENV_FILE" "$Shod_ENV_FILE" || \
        die "Не удалось активировать загруженную WEB-конфигурацию"
    if ! grep -Eq '^[[:space:]]*(export[[:space:]]+)?Shod_PUBLIC_IP=.+$' "$Shod_ENV_FILE"; then
        local detected_ip="${Shod_PUBLIC_IP:-}"
        [ -n "$detected_ip" ] || detected_ip="$(detect_public_ipv4 2>/dev/null || true)"
        if [ -n "$detected_ip" ]; then
            printf 'Shod_PUBLIC_IP=%s\n' "$detected_ip" >> "$Shod_ENV_FILE"
            log_info "Определен публичный IP сервера: $detected_ip"
        fi
    fi
    install -m 0600 "$UPLOAD_OVERRIDES_FILE" "$Shod_DEPLOY_OVERRIDES_FILE" || \
        die "Не удалось активировать загруженную deploy-конфигурацию"
    echo "✓ Конфигурация запуска установлена безопасным EnvironmentFile"
}

LE_CERTBOT_COMMAND=""

is_public_ipv4() {
    local ip="$1" first second third fourth value
    case "$ip" in
        *[!0-9.]*|*..*|.*|*.) return 1 ;;
    esac
    IFS=. read -r first second third fourth <<< "$ip"
    for value in "$first" "$second" "$third" "$fourth"; do
        case "$value" in
            ''|*[!0-9]*) return 1 ;;
        esac
        [ "$value" -le 255 ] || return 1
    done
    [ "$first" -gt 0 ] || return 1
    [ "$first" -lt 224 ] || return 1
    [ "$first" -ne 10 ] || return 1
    [ "$first" -ne 127 ] || return 1
    [ "$first" -ne 0 ] || return 1
    [ "$first" -ne 169 ] || [ "$second" -ne 254 ] || return 1
    [ "$first" -ne 192 ] || [ "$second" -ne 168 ] || return 1
    [ "$first" -ne 172 ] || [ "$second" -lt 16 ] || [ "$second" -gt 31 ] || return 1
    [ "$first" -ne 100 ] || [ "$second" -lt 64 ] || [ "$second" -gt 127 ] || return 1
    [ "$first" -ne 198 ] || [ "$second" -ne 18 ] || return 1
    [ "$first" -ne 198 ] || [ "$second" -ne 19 ] || return 1
    return 0
}

detect_public_ipv4() {
    local route_ip
    route_ip="$(ip -4 route get 77.88.8.8 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -n 1)"
    if [ -n "$route_ip" ] && is_public_ipv4 "$route_ip"; then
        printf '%s\n' "$route_ip"
        return 0
    fi
    local endpoint value
    for endpoint in https://api.ipify.org https://ipv4.icanhazip.com; do
        value="$(curl -4 -fsS --connect-timeout 3 --max-time 7 "$endpoint" 2>/dev/null || true)"
        value="$(printf '%s' "$value" | tr -d '[:space:]')"
        if is_public_ipv4 "$value"; then
            printf '%s\n' "$value"
            return 0
        fi
    done
    return 1
}

certbot_supports_ip_certificates() {
    "$1" certonly --help all 2>&1 | grep -Fq -- "--ip-address"
}

ensure_letsencrypt_client() {
    local system_certbot=""
    local -a python_packages=()
    if command -v certbot >/dev/null 2>&1; then
        system_certbot="$(command -v certbot)"
        if certbot_supports_ip_certificates "$system_certbot"; then
            LE_CERTBOT_COMMAND="$system_certbot"
            return 0
        fi
    fi
    if [ -x "$Shod_LE_CERTBOT" ] && certbot_supports_ip_certificates "$Shod_LE_CERTBOT"; then
        LE_CERTBOT_COMMAND="$Shod_LE_CERTBOT"
        return 0
    fi

    log_step "Подготовка Let’s Encrypt клиента..."
    if ! command -v python3 >/dev/null 2>&1 || ! python3 -c 'import ensurepip' >/dev/null 2>&1; then
        case "$PKG_MGR" in
            apt) python_packages=(python3 python3-venv) ;;
            dnf|yum) python_packages=(python3 python3-pip) ;;
            pacman) python_packages=(python) ;;
        esac
        if ! pkg_install_with_refresh "${python_packages[@]}"; then
            log_warn "Python/venv недоступны; WEB-панель временно останется на self-signed TLS"
            return 1
        fi
    fi
    if ! command -v openssl >/dev/null 2>&1; then
        if ! pkg_install_with_refresh openssl; then
            log_warn "OpenSSL недоступен; проверка и обновление Let’s Encrypt сертификата отложены"
            return 1
        fi
    fi
    if [ ! -x "$Shod_LE_CERTBOT" ] || ! "$Shod_LE_CERTBOT" --version >/dev/null 2>&1; then
        rm -rf "$Shod_LE_VENV"
        if ! python3 -m venv "$Shod_LE_VENV" >>"$LOG_FILE" 2>&1; then
            log_warn "Не удалось создать изолированный Let’s Encrypt client; WEB-панель временно останется на self-signed TLS"
            return 1
        fi
    fi
    if ! timeout 180 "$Shod_LE_VENV/bin/pip" install --disable-pip-version-check --no-cache-dir --upgrade 'certbot>=5.4,<6' >>"$LOG_FILE" 2>&1; then
        log_warn "Не удалось установить актуальный Certbot с поддержкой IP-сертификатов; WEB-панель временно останется на self-signed TLS"
        return 1
    fi
    if ! certbot_supports_ip_certificates "$Shod_LE_CERTBOT"; then
        log_warn "Установленный Certbot не поддерживает Let’s Encrypt IP-сертификаты"
        return 1
    fi
    LE_CERTBOT_COMMAND="$Shod_LE_CERTBOT"
    return 0
}

letsencrypt_cert_name() {
    printf 'shod-ip-%s\n' "${1//./-}"
}

state_value() {
    local key="$1"
    [ -f "$Shod_LE_STATE_FILE" ] || return 1
    sed -n "s/^${key}=//p" "$Shod_LE_STATE_FILE" | head -n 1
}

certificate_is_shod_rcgen() {
    local issuer subject
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    issuer="$(openssl x509 -noout -issuer -nameopt RFC2253 -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    subject="$(openssl x509 -noout -subject -nameopt RFC2253 -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    issuer="${issuer#issuer=}"
    subject="${subject#subject=}"
    [ "$subject" = "CN=rcgen self signed cert" ] && [ "$issuer" = "$subject" ]
}

certificate_is_shod_letsencrypt() {
    local saved_ip cert_name issuer subject_alt_names
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    [ -s "$Shod_CONFIG_DIR/web_key.pem" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    saved_ip="$(state_value IP 2>/dev/null || true)"
    cert_name="$(state_value CERT_NAME 2>/dev/null || true)"
    is_public_ipv4 "$saved_ip" || return 1
    [ "$cert_name" = "$(letsencrypt_cert_name "$saved_ip")" ] || return 1
    issuer="$(openssl x509 -noout -issuer -nameopt RFC2253 -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    issuer="${issuer#issuer=}"
    case "$issuer" in
        *"O=Let's Encrypt"*|*ISRG*) ;;
        *) return 1 ;;
    esac
    subject_alt_names="$(openssl x509 -noout -ext subjectAltName -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    printf '%s\n' "$subject_alt_names" | grep -Fq "IP Address:${saved_ip}" || printf '%s\n' "$subject_alt_names" | grep -Fq "IP:${saved_ip}"
}

web_certificate_is_shod_managed() {
    certificate_is_shod_rcgen || certificate_is_shod_letsencrypt
}

web_certificate_is_user_managed() {
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    ! web_certificate_is_shod_managed
}

letsencrypt_certificate_is_current() {
    local public_ip="$1" saved_ip
    certificate_is_shod_letsencrypt || return 1
    saved_ip="$(state_value IP 2>/dev/null || true)"
    [ "$saved_ip" = "$public_ip" ] || return 1
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    openssl x509 -noout -checkend "$LE_RENEW_BEFORE_SECONDS" -in "$Shod_CONFIG_DIR/web_cert.pem" >/dev/null 2>&1
}

disable_letsencrypt_renewal_timer() {
    rm -f -- /etc/cron.d/shod-letsencrypt
    command -v systemctl >/dev/null 2>&1 || return 0
    systemctl disable --now "$Shod_LE_TIMER" >/dev/null 2>&1 || true
}

install_letsencrypt_certificate() {
    local public_ip="$1" cert_name="$2" source_dir cert_tmp key_tmp state_tmp
    source_dir="/etc/letsencrypt/live/${cert_name}"
    [ -s "$source_dir/fullchain.pem" ] && [ -s "$source_dir/privkey.pem" ] || return 1
    cert_tmp="$(mktemp "${Shod_CONFIG_DIR}/.web_cert.XXXXXX")" || return 1
    key_tmp="$(mktemp "${Shod_CONFIG_DIR}/.web_key.XXXXXX")" || {
        rm -f -- "$cert_tmp"
        return 1
    }
    state_tmp="$(mktemp "${Shod_CONFIG_DIR}/.letsencrypt-ip.XXXXXX")" || {
        rm -f -- "$cert_tmp" "$key_tmp"
        return 1
    }
    if ! install -m 0644 "$source_dir/fullchain.pem" "$cert_tmp" || \
       ! install -m 0600 "$source_dir/privkey.pem" "$key_tmp"; then
        rm -f -- "$cert_tmp" "$key_tmp" "$state_tmp"
        return 1
    fi
    printf 'IP=%s\nCERT_NAME=%s\n' "$public_ip" "$cert_name" > "$state_tmp"
    chmod 0600 "$state_tmp"
    mv -f -- "$cert_tmp" "$Shod_CONFIG_DIR/web_cert.pem"
    mv -f -- "$key_tmp" "$Shod_CONFIG_DIR/web_key.pem"
    mv -f -- "$state_tmp" "$Shod_LE_STATE_FILE"
    secure_persistent_state
}

letsencrypt_certificate_ip() {
    local subject_alt_names
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    subject_alt_names="$(openssl x509 -noout -ext subjectAltName -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    printf '%s\n' "$subject_alt_names" | sed -n -E 's/.*(IP Address:|IP:)([0-9.]+).*/\2/p' | head -n 1
}

adopt_letsencrypt_certificate() {
    local public_ip cert_name source_dir issuer
    [ -s "$Shod_CONFIG_DIR/web_cert.pem" ] || return 1
    [ -s "$Shod_CONFIG_DIR/web_key.pem" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    issuer="$(openssl x509 -noout -issuer -nameopt RFC2253 -in "$Shod_CONFIG_DIR/web_cert.pem" 2>/dev/null || true)"
    issuer="${issuer#issuer=}"
    case "$issuer" in
        *"O=Let's Encrypt"*|*ISRG*) ;;
        *) return 1 ;;
    esac
    public_ip="$(letsencrypt_certificate_ip 2>/dev/null || true)"
    is_public_ipv4 "$public_ip" || return 1
    cert_name="$(letsencrypt_cert_name "$public_ip")"
    source_dir="/etc/letsencrypt/live/${cert_name}"
    [ -s "$source_dir/fullchain.pem" ] || return 1
    [ -s "$source_dir/privkey.pem" ] || return 1
    install_letsencrypt_certificate "$public_ip" "$cert_name"
}

issue_letsencrypt_ip_certificate() {
    local certbot="$1" public_ip="$2" cert_name="$3"
    if ss -ltnH "sport = :${LE_HTTP_PORT}" 2>/dev/null | grep -q .; then
        log_warn "TCP/${LE_HTTP_PORT} уже занят другим сервисом; Let’s Encrypt IP-сертификат отложен, сохранён self-signed fallback"
        return 1
    fi
    if ! timeout 90 "$certbot" certonly --standalone --http-01-port "$LE_HTTP_PORT" \
        --non-interactive --agree-tos --register-unsafely-without-email \
        --preferred-profile shortlived --ip-address "$public_ip" \
        --cert-name "$cert_name" --keep-until-expiring >>"$LOG_FILE" 2>&1; then
        if ! timeout 90 "$certbot" certonly --standalone --http-01-port "$LE_HTTP_PORT" \
            --non-interactive --agree-tos --register-unsafely-without-email \
            --ip-address "$public_ip" \
            --cert-name "$cert_name" --keep-until-expiring >>"$LOG_FILE" 2>&1; then
            log_warn "Let’s Encrypt не подтвердил IP ${public_ip}; сохранён self-signed fallback, следующая проверка выполнится автоматически"
            return 1
        fi
    fi
    if ! install_letsencrypt_certificate "$public_ip" "$cert_name"; then
        log_warn "Let’s Encrypt выдал сертификат, но Shod не смог безопасно активировать его"
        return 1
    fi
    log_info "Let’s Encrypt IP-сертификат активирован для ${public_ip}"
}

generate_rcgen_fallback_certificate() {
    local cert_tmp key_tmp current_ip="${1:-}" san_ext
    command -v openssl >/dev/null 2>&1 || return 1
    cert_tmp="$(mktemp "${Shod_CONFIG_DIR}/.web_cert.XXXXXX")" || return 1
    key_tmp="$(mktemp "${Shod_CONFIG_DIR}/.web_key.XXXXXX")" || { rm -f -- "$cert_tmp"; return 1; }
    san_ext="subjectAltName = DNS:localhost,IP:127.0.0.1"
    if [ -n "$current_ip" ] && is_public_ipv4 "$current_ip"; then
        san_ext="${san_ext},IP:${current_ip}"
    fi
    if ! openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -subj "/CN=rcgen self signed cert" \
        -addext "$san_ext" \
        -keyout "$key_tmp" -out "$cert_tmp" >/dev/null 2>&1; then
        if ! openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
            -subj "/CN=rcgen self signed cert" \
            -keyout "$key_tmp" -out "$cert_tmp" >/dev/null 2>&1; then
            rm -f -- "$cert_tmp" "$key_tmp"
            return 1
        fi
    fi
    mv -f -- "$cert_tmp" "$Shod_CONFIG_DIR/web_cert.pem"
    mv -f -- "$key_tmp" "$Shod_CONFIG_DIR/web_key.pem"
    chmod 644 "$Shod_CONFIG_DIR/web_cert.pem"
    chmod 600 "$Shod_CONFIG_DIR/web_key.pem"
    rm -f -- "$Shod_LE_STATE_FILE"
}

write_letsencrypt_renewal_helper() {
    install -d -m 0755 "$(dirname "$Shod_LE_RENEW_HELPER")"
    cat > "$Shod_LE_RENEW_HELPER" << 'LEHELPER'
#!/bin/bash
set -Eeuo pipefail

LE_VENV="${Shod_LE_VENV:-/opt/shod-certbot}"
CERTBOT="${Shod_LE_CERTBOT:-}"
if [ -z "$CERTBOT" ] || ! "$CERTBOT" --version >/dev/null 2>&1; then
    if [ -x "$LE_VENV/bin/certbot" ] && "$LE_VENV/bin/certbot" --version >/dev/null 2>&1; then
        CERTBOT="$LE_VENV/bin/certbot"
    elif command -v certbot >/dev/null 2>&1 && certbot --version >/dev/null 2>&1; then
        CERTBOT="$(command -v certbot)"
    fi
fi
CONFIG_DIR="${Shod_CONFIG_DIR:-/etc/shod}"
STATE_FILE="${CONFIG_DIR}/letsencrypt-ip.env"
CERT_PATH="${CONFIG_DIR}/web_cert.pem"
KEY_PATH="${CONFIG_DIR}/web_key.pem"
RENEW_BEFORE_SECONDS="${Shod_LE_RENEW_BEFORE_SECONDS:-259200}"
HTTP_PORT="${Shod_LE_HTTP_PORT:-80}"
SERVICE_MANAGER="${Shod_DEPLOY_MODE:-systemd}"
UNIT_NAME="${Shod_UNIT_NAME:-shod}"          # the unit / container this certificate belongs to

log() {
    logger -t shod-letsencrypt -- "$*" 2>/dev/null || true
    printf '%s\n' "$*"
}

is_public_ipv4() {
    local ip="$1" first second third fourth value
    case "$ip" in
        *[!0-9.]*|*..*|.*|*.) return 1 ;;
    esac
    IFS=. read -r first second third fourth <<< "$ip"
    for value in "$first" "$second" "$third" "$fourth"; do
        case "$value" in
            ''|*[!0-9]*) return 1 ;;
        esac
        [ "$value" -le 255 ] || return 1
    done
    [ "$first" -gt 0 ] && [ "$first" -lt 224 ] && [ "$first" -ne 10 ] && [ "$first" -ne 127 ] || return 1
    [ "$first" -ne 169 ] || [ "$second" -ne 254 ] || return 1
    [ "$first" -ne 192 ] || [ "$second" -ne 168 ] || return 1
    [ "$first" -ne 172 ] || [ "$second" -lt 16 ] || [ "$second" -gt 31 ] || return 1
    [ "$first" -ne 100 ] || [ "$second" -lt 64 ] || [ "$second" -gt 127 ] || return 1
    [ "$first" -ne 198 ] || [ "$second" -ne 18 ] || return 1
    [ "$first" -ne 198 ] || [ "$second" -ne 19 ] || return 1
}

public_ipv4() {
    local route_ip
    route_ip="$(ip -4 route get 77.88.8.8 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -n 1)"
    if [ -n "$route_ip" ] && is_public_ipv4 "$route_ip"; then
        printf '%s\n' "$route_ip"
        return 0
    fi
    local endpoint value
    for endpoint in https://api.ipify.org https://ipv4.icanhazip.com; do
        value="$(curl -4 -fsS --connect-timeout 3 --max-time 7 "$endpoint" 2>/dev/null || true)"
        value="$(printf '%s' "$value" | tr -d '[:space:]')"
        if is_public_ipv4 "$value"; then
            printf '%s\n' "$value"
            return 0
        fi
    done
    value="$(state_value IP 2>/dev/null || true)"
    if is_public_ipv4 "$value"; then
        printf '%s\n' "$value"
        return 0
    fi
    return 1
}

state_value() {
    local key="$1"
    [ -f "$STATE_FILE" ] || return 1
    sed -n "s/^${key}=//p" "$STATE_FILE" | head -n 1
}

certificate_is_shod_rcgen() {
    local issuer subject
    [ -s "$CERT_PATH" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    issuer="$(openssl x509 -noout -issuer -nameopt RFC2253 -in "$CERT_PATH" 2>/dev/null || true)"
    subject="$(openssl x509 -noout -subject -nameopt RFC2253 -in "$CERT_PATH" 2>/dev/null || true)"
    issuer="${issuer#issuer=}"
    subject="${subject#subject=}"
    [ "$subject" = "CN=rcgen self signed cert" ] && [ "$issuer" = "$subject" ]
}

certificate_is_shod_letsencrypt() {
    local saved_ip cert_name issuer subject_alt_names
    [ -s "$CERT_PATH" ] || return 1
    [ -s "$KEY_PATH" ] || return 1
    command -v openssl >/dev/null 2>&1 || return 1
    saved_ip="$(state_value IP 2>/dev/null || true)"
    cert_name="$(state_value CERT_NAME 2>/dev/null || true)"
    if [ -z "$saved_ip" ] || [ -z "$cert_name" ]; then
        saved_ip="$(openssl x509 -noout -ext subjectAltName -in "$CERT_PATH" 2>/dev/null | sed -n -E 's/.*(IP Address:|IP:)([0-9.]+).*/\2/p' | head -n 1)"
        cert_name="shod-ip-${saved_ip//./-}"
    fi
    is_public_ipv4 "$saved_ip" || return 1
    [ "$cert_name" = "shod-ip-${saved_ip//./-}" ] || return 1
    issuer="$(openssl x509 -noout -issuer -nameopt RFC2253 -in "$CERT_PATH" 2>/dev/null || true)"
    issuer="${issuer#issuer=}"
    case "$issuer" in
        *"O=Let's Encrypt"*|*ISRG*) ;;
        *) return 1 ;;
    esac
    subject_alt_names="$(openssl x509 -noout -ext subjectAltName -in "$CERT_PATH" 2>/dev/null || true)"
    printf '%s\n' "$subject_alt_names" | grep -Fq "IP Address:${saved_ip}" || printf '%s\n' "$subject_alt_names" | grep -Fq "IP:${saved_ip}"
}

certificate_is_shod_managed() {
    certificate_is_shod_rcgen || certificate_is_shod_letsencrypt
}

needs_renewal() {
    local current_ip="$1" saved_ip
    if certificate_is_shod_rcgen; then
        return 0
    fi
    saved_ip="$(state_value IP 2>/dev/null || true)"
    [ "$saved_ip" = "$current_ip" ] || return 0
    [ -s "$CERT_PATH" ] || return 0
    openssl x509 -noout -checkend "$RENEW_BEFORE_SECONDS" -in "$CERT_PATH" >/dev/null 2>&1 || return 0
    return 1
}

certificate_is_expired() {
    [ -s "$CERT_PATH" ] || return 0
    command -v openssl >/dev/null 2>&1 || return 1
    ! openssl x509 -noout -checkend 0 -in "$CERT_PATH" >/dev/null 2>&1
}

generate_rcgen_fallback() {
    local current_ip="${1:-}" cert_tmp key_tmp san_ext
    command -v openssl >/dev/null 2>&1 || return 1
    cert_tmp="$(mktemp "${CONFIG_DIR}/.web_cert.XXXXXX")" || return 1
    key_tmp="$(mktemp "${CONFIG_DIR}/.web_key.XXXXXX")" || { rm -f -- "$cert_tmp"; return 1; }
    san_ext="subjectAltName = DNS:localhost,IP:127.0.0.1"
    if [ -n "$current_ip" ] && is_public_ipv4 "$current_ip"; then
        san_ext="${san_ext},IP:${current_ip}"
    fi
    if ! openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -subj "/CN=rcgen self signed cert" \
        -addext "$san_ext" \
        -keyout "$key_tmp" -out "$cert_tmp" >/dev/null 2>&1; then
        if ! openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
            -subj "/CN=rcgen self signed cert" \
            -keyout "$key_tmp" -out "$cert_tmp" >/dev/null 2>&1; then
            rm -f -- "$cert_tmp" "$key_tmp"
            return 1
        fi
    fi
    mv -f -- "$cert_tmp" "$CERT_PATH"
    mv -f -- "$key_tmp" "$KEY_PATH"
    chmod 644 "$CERT_PATH"
    chmod 600 "$KEY_PATH"
    rm -f -- "$STATE_FILE"
}

fallback_if_expired() {
    local current_ip="${1:-}"
    if certificate_is_expired; then
        log "Let’s Encrypt: текущий сертификат истёк; активируется fallback rcgen сертификат"
        if generate_rcgen_fallback "$current_ip"; then
            reload_shod_tls
            log "Let’s Encrypt: fallback rcgen сертификат успешно активирован"
        fi
    fi
}

install_certificate() {
    local current_ip="$1" cert_name="$2" source_dir cert_tmp key_tmp state_tmp
    source_dir="/etc/letsencrypt/live/${cert_name}"
    [ -s "$source_dir/fullchain.pem" ] && [ -s "$source_dir/privkey.pem" ] || return 1
    cert_tmp="$(mktemp "${CONFIG_DIR}/.web_cert.XXXXXX")" || return 1
    key_tmp="$(mktemp "${CONFIG_DIR}/.web_key.XXXXXX")" || { rm -f -- "$cert_tmp"; return 1; }
    state_tmp="$(mktemp "${CONFIG_DIR}/.letsencrypt-ip.XXXXXX")" || { rm -f -- "$cert_tmp" "$key_tmp"; return 1; }
    if ! install -m 0644 "$source_dir/fullchain.pem" "$cert_tmp" || ! install -m 0600 "$source_dir/privkey.pem" "$key_tmp"; then
        rm -f -- "$cert_tmp" "$key_tmp" "$state_tmp"
        return 1
    fi
    printf 'IP=%s\nCERT_NAME=%s\n' "$current_ip" "$cert_name" > "$state_tmp"
    chmod 0600 "$state_tmp"
    mv -f -- "$cert_tmp" "$CERT_PATH"
    mv -f -- "$key_tmp" "$KEY_PATH"
    mv -f -- "$state_tmp" "$STATE_FILE"
    chmod 644 "$CERT_PATH"
    chmod 600 "$KEY_PATH" "$STATE_FILE"
}

reload_shod_tls() {
    if [ "$SERVICE_MANAGER" = "docker" ]; then
        docker kill --signal USR1 "$UNIT_NAME" >/dev/null 2>&1 || return 0
    else
        systemctl kill --kill-who=main --signal USR1 "$UNIT_NAME" >/dev/null 2>&1 || return 0
    fi
}

main() {
    local current_ip cert_name
    if [ -s "$CERT_PATH" ] && ! certificate_is_shod_managed; then
        log "Let’s Encrypt: обнаружен пользовательский TLS-сертификат; Shod не изменяет его и отключает собственное продление"
        return 0
    fi
    current_ip="$(public_ipv4 2>/dev/null || true)"
    if [ -z "$CERTBOT" ] || [ ! -x "$CERTBOT" ]; then
        log "Let’s Encrypt: Certbot не найден, обновление отложено"
        fallback_if_expired "$current_ip"
        return 0
    fi
    if [ -z "$current_ip" ]; then
        log "Let’s Encrypt: публичный IPv4 не определён, обновление отложено"
        fallback_if_expired ""
        return 0
    fi
    if ! needs_renewal "$current_ip"; then
        return 0
    fi
    if ss -ltnH "sport = :${HTTP_PORT}" 2>/dev/null | grep -q .; then
        log "Let’s Encrypt: TCP/${HTTP_PORT} занят, обновление отложено"
        fallback_if_expired "$current_ip"
        return 0
    fi
    cert_name="shod-ip-${current_ip//./-}"
    if ! timeout 90 "$CERTBOT" certonly --standalone --http-01-port "$HTTP_PORT" --non-interactive --agree-tos --register-unsafely-without-email --preferred-profile shortlived --ip-address "$current_ip" --cert-name "$cert_name" --force-renewal; then
        if ! timeout 90 "$CERTBOT" certonly --standalone --http-01-port "$HTTP_PORT" --non-interactive --agree-tos --register-unsafely-without-email --ip-address "$current_ip" --cert-name "$cert_name" --force-renewal; then
            log "Let’s Encrypt: перевыпуск IP-сертификата не удался, текущий сертификат сохранён"
            fallback_if_expired "$current_ip"
            return 0
        fi
    fi
    if ! install_certificate "$current_ip" "$cert_name"; then
        log "Let’s Encrypt: не удалось безопасно активировать новый сертификат"
        fallback_if_expired "$current_ip"
        return 0
    fi
    reload_shod_tls
    log "Let’s Encrypt: IP-сертификат обновлён и TLS-конфигурация Shod перезагружена"
}

main "$@"
LEHELPER
    chmod 0755 "$Shod_LE_RENEW_HELPER"
}

install_letsencrypt_renewal_timer() {
    rm -f -- /etc/cron.d/shod-letsencrypt
    command -v systemctl >/dev/null 2>&1 || {
        log_warn "systemd не найден: автоматическое продление Let’s Encrypt требует systemd"
        return 0
    }
    cat > "/etc/systemd/system/${Shod_LE_SERVICE}" << LEUNIT
[Unit]
Description=Renew Shod Let’s Encrypt IP certificate
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
Environment=Shod_DEPLOY_MODE=${DEPLOY_MODE}
Environment=Shod_LE_CERTBOT=${LE_CERTBOT_COMMAND}
Environment=Shod_LE_RENEW_BEFORE_SECONDS=${LE_RENEW_BEFORE_SECONDS}
Environment=Shod_CONFIG_DIR=${Shod_CONFIG_DIR}
Environment=Shod_LE_VENV=${Shod_LE_VENV}
Environment=Shod_UNIT_NAME=${Shod_UNIT}
ExecStart=${Shod_LE_RENEW_HELPER}
LEUNIT
    cat > "/etc/systemd/system/${Shod_LE_TIMER}" << LETIMER
[Unit]
Description=Check Shod Let’s Encrypt IP certificate lifetime

[Timer]
OnBootSec=5min
OnUnitInactiveSec=${LE_RENEW_EVERY}
RandomizedDelaySec=10min
Persistent=true

[Install]
WantedBy=timers.target
LETIMER
    systemctl daemon-reload || {
        log_warn "Не удалось обновить systemd unit для Let’s Encrypt"
        return 0
    }
    systemctl enable --now "$Shod_LE_TIMER" >/dev/null 2>&1 || \
        log_warn "Не удалось включить автоматическое продление Let’s Encrypt"
}

setup_letsencrypt_ip_tls() {
    local public_ip cert_name
    if ! certificate_is_shod_letsencrypt && adopt_letsencrypt_certificate; then
        log_info "Let’s Encrypt IP-сертификат Shod принят под автоматическое продление"
    fi
    if web_certificate_is_user_managed; then
        rm -f -- "$Shod_LE_STATE_FILE"
        disable_letsencrypt_renewal_timer
        log_info "Используется заранее установленный TLS-сертификат пользователя; Shod сохраняет его и не заменяет Let’s Encrypt"
        return 0
    fi
    public_ip="$(detect_public_ipv4 2>/dev/null || true)"
    if [ -z "$public_ip" ]; then
        public_ip="$(state_value IP 2>/dev/null || true)"
    fi
    if ! ensure_letsencrypt_client; then
        if [ ! -s "$Shod_CONFIG_DIR/web_cert.pem" ] || ! openssl x509 -noout -checkend 0 -in "$Shod_CONFIG_DIR/web_cert.pem" >/dev/null 2>&1; then
            generate_rcgen_fallback_certificate "$public_ip" || true
        fi
        return 0
    fi
    write_letsencrypt_renewal_helper
    if [ -z "$public_ip" ]; then
        log_warn "Публичный IPv4 не определён; WEB-панель временно останется на self-signed TLS"
        if [ ! -s "$Shod_CONFIG_DIR/web_cert.pem" ] || ! openssl x509 -noout -checkend 0 -in "$Shod_CONFIG_DIR/web_cert.pem" >/dev/null 2>&1; then
            generate_rcgen_fallback_certificate "" || true
        fi
        install_letsencrypt_renewal_timer
        return 0
    fi
    if letsencrypt_certificate_is_current "$public_ip"; then
        log_info "Let’s Encrypt IP-сертификат действителен; перевыпуск не требуется"
    else
        cert_name="$(letsencrypt_cert_name "$public_ip")"
        if ! issue_letsencrypt_ip_certificate "$LE_CERTBOT_COMMAND" "$public_ip" "$cert_name"; then
            if [ ! -s "$Shod_CONFIG_DIR/web_cert.pem" ] || ! openssl x509 -noout -checkend 0 -in "$Shod_CONFIG_DIR/web_cert.pem" >/dev/null 2>&1; then
                generate_rcgen_fallback_certificate "$public_ip" || true
            fi
        fi
    fi
    install_letsencrypt_renewal_timer
}

verify_web_panel() {
    local attempt scheme code
    local probe_file="/tmp/shod-web-probe.$$"
    local secret_path="${Shod_WEB_SECRET_PATH:-}"
    if [ -z "$secret_path" ] && [ -f "$Shod_ENV_FILE" ]; then
        secret_path="$(grep -E '^[[:space:]]*(export[[:space:]]+)?Shod_WEB_SECRET_PATH=' "$Shod_ENV_FILE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\"' | tr -d "'\"'" | tr -d '[:space:]' || true)"
    fi
    local probe_path=""
    if [ -n "$secret_path" ]; then
        probe_path="${secret_path#/}"
    fi

    for attempt in $(seq 1 24); do
        for scheme in https http; do
            code="$(curl -k -sS -o "$probe_file" -w "%{http_code}" \
                --connect-timeout 1 --max-time 2 \
                "${scheme}://127.0.0.1:${WEB_PORT}/${probe_path}" 2>>"$LOG_FILE" || true)"
            case "$code" in
                200|301|302|401)
                    rm -f -- "$probe_file"
                    log_info "WEB-панель ответила локальной health-проверке (${scheme}, HTTP ${code})"
                    return 0
                    ;;
            esac
        done
        sleep 0.25
    done
    rm -f -- "$probe_file"
    die "WEB-панель не ответила на локальную health-проверку порта ${WEB_PORT}"
}

ensure_docker() {
    log_step "Проверка Docker Engine..."

    if ! command -v docker >/dev/null 2>&1; then
        pkg_update
        if [ "$PKG_MGR" = "pacman" ]; then
            pkg_install docker || die "Не удалось установить Docker Engine"
        else
            pkg_install ca-certificates curl || die "Не удалось установить curl и ca-certificates для Docker"
            local installer
            installer="$(mktemp)"
            curl -fsSL https://get.docker.com -o "$installer" || die "Не удалось загрузить официальный установщик Docker"
            sh "$installer" >>"$LOG_FILE" 2>&1 || {
                rm -f "$installer"
                die "Не удалось установить Docker Engine"
            }
            rm -f "$installer"
        fi
    fi

    if command -v systemctl >/dev/null 2>&1; then
        systemctl enable --now docker >>"$LOG_FILE" 2>&1 || true
    elif command -v service >/dev/null 2>&1; then
        service docker start >>"$LOG_FILE" 2>&1 || true
    fi

    docker --version | tee -a "$LOG_FILE"
    docker info >>"$LOG_FILE" 2>&1 || die "Docker установлен, но демон недоступен; проверьте docker info"
    log_info "Docker Engine готов"
}

ensure_tun_device() {
    if [ -c /dev/net/tun ]; then
        chmod 666 /dev/net/tun 2>/dev/null || true
        return 0
    fi
    if command -v modprobe >/dev/null 2>&1; then
        modprobe tun >>"$LOG_FILE" 2>&1 || true
    fi
    mkdir -p /dev/net
    [ -c /dev/net/tun ] || mknod /dev/net/tun c 10 200 >>"$LOG_FILE" 2>&1 || true
    [ -c /dev/net/tun ] || die "Устройство /dev/net/tun недоступно на этом VPS"
    chmod 666 /dev/net/tun 2>/dev/null || true
}

check_kernel_compatibility() {
    [ "$(uname -s)" = "Linux" ] || die "Shod Server поддерживает только Linux"
    local release base major minor
    release="$(uname -r)"
    base="${release%%-*}"
    major="${base%%.*}"
    base="${base#*.}"
    minor="${base%%.*}"
    case "$major:$minor" in
        *[!0-9:]*|:*) log_warn "Не удалось разобрать версию ядра $release; решающей будет рабочая проверка TUN" ;;
        *)
            if [ "$major" -lt 5 ] || { [ "$major" -eq 5 ] && [ "$minor" -lt 5 ]; }; then
                log_warn "Ядро $release старше проверенного диапазона 5.5–7.x; установка продолжится только при успешных рабочих пробах"
            else
                log_info "Ядро Linux $release входит в поддерживаемый диапазон"
            fi
            ;;
    esac
}

load_kernel_network_modules() {
    command -v modprobe >/dev/null 2>&1 || return 0
    local module
    for module in tun nf_tables ip_tables iptable_filter iptable_nat iptable_mangle nf_nat nf_conntrack xt_comment xt_MASQUERADE xt_TCPMSS; do
        modprobe "$module" >>"$LOG_FILE" 2>&1 || true
    done
}

probe_tun_support() {
    ensure_tun_device
    local probe_iface="cqtprobe$(( $$ % 100000 ))"
    ip link del "$probe_iface" >>"$LOG_FILE" 2>&1 || true
    if ! ip tuntap add dev "$probe_iface" mode tun >>"$LOG_FILE" 2>&1; then
        load_kernel_network_modules
        ip link del "$probe_iface" >>"$LOG_FILE" 2>&1 || true
        if ! ip tuntap add dev "$probe_iface" mode tun >>"$LOG_FILE" 2>&1; then
            die "TUN существует, но ядро или политика VPS запрещает TUNSETIFF/CAP_NET_ADMIN"
        fi
    fi
    if ! ip link del "$probe_iface" >>"$LOG_FILE" 2>&1; then
        die "Рабочий TUN создан, но тестовый интерфейс $probe_iface не удалось удалить"
    fi
    log_info "TUN проверен реальным созданием и удалением интерфейса"
}

run_platform_preflight() {
    prog 0.35 "Совместимость ядра..."
    log_step "Проверка совместимости ядра и VPS..."
    check_kernel_compatibility
    probe_tun_support
}

install_docker_boot_prerequisite() {
    command -v systemctl >/dev/null 2>&1 || \
        die "Docker-режим требует systemd для подготовки TUN до автозапуска контейнера"

    ensure_shod_directory "$Shod_LIB_DIR" 755
    cat > "$Shod_DOCKER_PREREQ_HELPER" << PREREQ
#!/bin/sh
set -eu

if [ ! -c /dev/net/tun ]; then
    command -v modprobe >/dev/null 2>&1 && modprobe tun || true
    mkdir -p /dev/net
    [ -c /dev/net/tun ] || mknod /dev/net/tun c 10 200
fi

[ -c /dev/net/tun ] || exit 20
chmod 666 /dev/net/tun 2>/dev/null || true
mkdir -p ${Shod_RUNTIME_DIR}
chmod 0755 ${Shod_RUNTIME_DIR} 2>/dev/null || true
rm -f ${Shod_DOCKER_NETWORK_READY}
touch /run/xtables.lock
chmod 600 /run/xtables.lock 2>/dev/null || true
PREREQ
    chmod 0755 "$Shod_DOCKER_PREREQ_HELPER"

    local unit_tmp
    unit_tmp="$(mktemp)"
    cat > "$unit_tmp" << PREREQUNIT
[Unit]
Description=Prepare Shod Docker TUN and xtables lock
After=local-fs.target
Before=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=${Shod_DOCKER_PREREQ_HELPER}

[Install]
RequiredBy=docker.service
PREREQUNIT
    install -m 0644 "$unit_tmp" "/etc/systemd/system/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "$unit_tmp"
    systemctl daemon-reload || die "Не удалось обновить systemd для Docker prerequisites"
    systemctl enable "$Shod_DOCKER_PREREQ_SERVICE" >/dev/null 2>&1 || \
        die "Не удалось включить Docker prerequisites"
    "$Shod_DOCKER_PREREQ_HELPER" || \
        die "Не удалось обновить Docker runtime prerequisites"
    systemctl start "$Shod_DOCKER_PREREQ_SERVICE" || \
        die "Не удалось подготовить TUN/xtables lock для Docker"
}

write_shod_dockerfile() {
    local target="$1"
    cat > "$target" << 'DOCKERFILE'
FROM alpine:3.21
ARG Shod_BUILD_DNS_PRIMARY
ARG Shod_BUILD_DNS_SECONDARY
RUN set -eu; if [ -n "${Shod_BUILD_DNS_PRIMARY:-}" ]; then printf 'nameserver %s\n' "$Shod_BUILD_DNS_PRIMARY" > /etc/resolv.conf; fi; if [ -n "${Shod_BUILD_DNS_SECONDARY:-}" ]; then printf 'nameserver %s\n' "$Shod_BUILD_DNS_SECONDARY" >> /etc/resolv.conf; fi; timeout -k 5s 120s apk add --no-cache --no-progress ca-certificates iproute2 iptables; rm -rf /var/cache/apk/*
COPY shod @IMG_BIN@
COPY network-up.sh @IMG_LIB@/network-up.sh
COPY tun-recover.sh @IMG_LIB@/tun-recover.sh
RUN chmod 0755 @IMG_BIN@ @IMG_LIB@/network-up.sh @IMG_LIB@/tun-recover.sh
ENTRYPOINT ["/bin/sh", "-ec", "@IMG_LIB@/network-up.sh; @IMG_LIB@/tun-recover.sh; exec @IMG_BIN@ \"$@\"", "--"]
DOCKERFILE
    sed -i "s#@IMG_BIN@#${Shod_IMAGE_BIN}#g; s#@IMG_LIB@#${Shod_IMAGE_LIB_DIR}#g" "$target"
}

docker_build_candidate_image() {
    local image="$1" context_dir="$2" primary_log fallback_log
    primary_log="$(mktemp)" || return 1
    if timeout -k 15s "${DOCKER_BUILD_TIMEOUT_SECONDS}s" docker build --network host -t "$image" "$context_dir" >"$primary_log" 2>&1; then
        cat "$primary_log" >>"$LOG_FILE"
        rm -f -- "$primary_log"
        return 0
    fi
    cat "$primary_log" >>"$LOG_FILE"
    if ! grep -Eqi 'Temporary failure resolving|Could not resolve|Name or service not known|Connection timed out|Could not connect|Network is unreachable|Failed to fetch|apk|returned a non-zero code: 124' "$primary_log"; then
        rm -f -- "$primary_log"
        return 1
    fi
    rm -f -- "$primary_log"
    log_warn "Docker build не получил DNS через сеть VPS; повтор с резервными DNS Яндекса"
    fallback_log="$(mktemp)" || return 1
    if timeout -k 15s "${DOCKER_BUILD_TIMEOUT_SECONDS}s" docker build --network default \
        --build-arg Shod_BUILD_DNS_PRIMARY=77.88.8.8 \
        --build-arg Shod_BUILD_DNS_SECONDARY=77.88.8.1 \
        -t "$image" "$context_dir" >"$fallback_log" 2>&1; then
        cat "$fallback_log" >>"$LOG_FILE"
        rm -f -- "$fallback_log"
        log_info "Docker-образ собран через резервные DNS Яндекса"
        return 0
    fi
    cat "$fallback_log" >>"$LOG_FILE"
    rm -f -- "$fallback_log"
    return 1
}

prepare_docker_candidate() {
    local source_binary="$1" context_dir
    [ -x "$source_binary" ] || \
        die "Docker-образ нельзя собрать без установленного бинарника shod" "$EXIT_PREFLIGHT_FAILED"
    ensure_tun_device
    DOCKER_CONTEXT_DIR="$(mktemp -d /tmp/shod-docker.XXXXXX)" || \
        die "Не удалось создать Docker staging context" "$EXIT_PREFLIGHT_FAILED"
    context_dir="$DOCKER_CONTEXT_DIR"
    install -m 0755 "$source_binary" "$context_dir/shod" || \
        die "Не удалось подготовить Docker-бинарник" "$EXIT_PREFLIGHT_FAILED"
    write_network_helper "$context_dir/network-up.sh" || \
        die "Не удалось подготовить Docker network helper" "$EXIT_PREFLIGHT_FAILED"
    write_tun_recovery_helper "$context_dir/tun-recover.sh" || \
        die "Не удалось подготовить Docker TUN recovery helper" "$EXIT_PREFLIGHT_FAILED"
    write_shod_dockerfile "$context_dir/Dockerfile"

    Shod_DOCKER_CANDIDATE_IMAGE="${Shod_DOCKER_IMAGE}-candidate-$$"
    if ! docker_build_candidate_image "$Shod_DOCKER_CANDIDATE_IMAGE" "$context_dir"; then
        tail -n 80 "$LOG_FILE" | sed 's/^/   >> /' >&2 || true
        die "Сборка Docker-образа Shod завершилась ошибкой" "$EXIT_PREFLIGHT_FAILED"
    fi
    probe_docker_runtime "$Shod_DOCKER_CANDIDATE_IMAGE"
    remove_managed_work_dir "$DOCKER_CONTEXT_DIR" || true
    DOCKER_CONTEXT_DIR=""
    log_info "Docker-образ прошёл проверку: $Shod_DOCKER_CANDIDATE_IMAGE"
}

setup_shod_docker() {
    prog 0.75 "Docker-образ..."
    [ -n "$Shod_DOCKER_CANDIDATE_IMAGE" ] || \
        die "Не найден проверенный Docker-кандидат" "$EXIT_PREFLIGHT_FAILED"
    docker tag "$Shod_DOCKER_CANDIDATE_IMAGE" "$Shod_DOCKER_IMAGE" || \
        die "Не удалось активировать проверенный Docker-образ"
    install_docker_boot_prerequisite
    log_info "Проверенный Docker-образ активирован: $Shod_DOCKER_IMAGE"
}

probe_docker_runtime() {
    local image="${1:-$Shod_DOCKER_IMAGE}"
    local probe_iface="cqtd$(( $$ % 100000 ))" probe_chain="Shod_D$(( $$ % 1000000 ))" output
    if ! output="$(docker run --rm \
        --network host \
        --cap-drop ALL \
        --cap-add NET_ADMIN \
        --cap-add NET_RAW \
        --device /dev/net/tun:/dev/net/tun \
        --security-opt seccomp=unconfined \
        --env "PROBE_IFACE=$probe_iface" \
        --env "PROBE_CHAIN=$probe_chain" \
        --entrypoint /bin/sh \
        "$image" -ec '
ip tuntap add dev "$PROBE_IFACE" mode tun
ip link del "$PROBE_IFACE"
for table in filter nat mangle; do
    iptables -w 2 -t "$table" -N "$PROBE_CHAIN"
    iptables -w 2 -t "$table" -X "$PROBE_CHAIN"
done
' 2>&1)"; then
        printf '%s\n' "$output" >>"$LOG_FILE"
        die "Docker не прошёл рабочую проверку TUN или netfilter"
    fi
    printf '%s\n' "$output" >>"$LOG_FILE"
    log_info "Docker runtime проверен: TUN/netfilter"
}

start_shod_docker() {
    prog 0.90 "Запуск Docker..."
    ensure_tun_device
    remove_all_shod_docker_containers
    assert_peer_port_is_available
    remove_shod_tun_interface || die "Не удалось освободить TUN-интерфейс $Shod_IFACE перед запуском Docker"

    docker run -d \
        --name "$Shod_DOCKER_CONTAINER" \
        --label com.shod.managed=true \
        --label com.shod.component=server \
        --restart unless-stopped \
        --network host \
        --cap-drop ALL \
        --cap-add NET_ADMIN \
        --cap-add NET_RAW \
        --device /dev/net/tun:/dev/net/tun \
        --security-opt seccomp=unconfined \
        --stop-timeout 5 \
        --ulimit nofile=65535:65535 \
        --env-file "$Shod_ENV_FILE" \
        --env Shod_SERVICE_MANAGER=docker \
        --volume "$Shod_CONFIG_DIR:$Shod_IMAGE_CONFIG_DIR" \
        --mount "type=bind,src=${Shod_RUNTIME_DIR},dst=${Shod_RUNTIME_DIR}" \
        --mount "type=bind,src=/run/xtables.lock,dst=/run/xtables.lock" \
        "$Shod_DOCKER_IMAGE" \
        --listen "0.0.0.0:${PEER_PORT}" \
        --web-port "$WEB_PORT" \
        --config-dir "$Shod_IMAGE_CONFIG_DIR" >>"$LOG_FILE" 2>&1 || \
        die "Не удалось создать Docker-контейнер Shod"

    local state running main_pid restarts candidate_pid=0 candidate_restarts=0 found=0
    local attempts=0
    while [ "$attempts" -lt 48 ]; do
        attempts=$((attempts + 1))
        state="$(docker inspect --format '{{.State.Running}}|{{.State.Pid}}|{{.RestartCount}}' "$Shod_DOCKER_CONTAINER" 2>/dev/null || echo 'false|0|-1')"
        IFS='|' read -r running main_pid restarts <<< "$state"
        case "$main_pid" in ''|*[!0-9]*) main_pid=0 ;; esac
        case "$restarts" in ''|*[!0-9]*) restarts=-1 ;; esac
        if [ "$running" = "true" ] && [ "$main_pid" -gt 1 ] && ip link show "$Shod_IFACE" >/dev/null 2>&1; then
            candidate_pid="$main_pid"
            candidate_restarts="$restarts"
            found=1
            break
        fi
        sleep 0.25
    done

    if [ "$found" -ne 1 ]; then
        docker logs --tail 50 "$Shod_DOCKER_CONTAINER" 2>&1 | sed 's/^/   >> /' || true
        die "Docker Shod не запустился или TUN $Shod_IFACE не создан"
    fi

    sleep "$START_STABILITY_SECONDS"
    state="$(docker inspect --format '{{.State.Running}}|{{.State.Pid}}|{{.RestartCount}}' "$Shod_DOCKER_CONTAINER" 2>/dev/null || echo 'false|0|-1')"
    IFS='|' read -r running main_pid restarts <<< "$state"
    if [ "$running" != "true" ] || [ "$main_pid" != "$candidate_pid" ] || [ "$restarts" != "$candidate_restarts" ] || \
       ! ip link show "$Shod_IFACE" >/dev/null 2>&1; then
        docker logs --tail 50 "$Shod_DOCKER_CONTAINER" 2>&1 | sed 's/^/   >> /' || true
        die "Docker Shod вошёл в crash-loop во время стабильной проверки"
    fi

    verify_configured_network
    verify_web_panel
    finish_deployment
    prog 1.0 "Готово!"
    echo ""
    echo "✓ Деплой успешно завершён"
    echo "Shod_DEPLOY_OK"
    echo "   Режим:      Docker"
    echo "   Бинарник:   ${Shod_IMAGE_BIN} (в образе)"
    echo "   Конфиг:     ${Shod_CONFIG_DIR}"
    echo "   Контейнер:  ${Shod_DOCKER_CONTAINER}"
    echo "   Образ:      ${Shod_DOCKER_IMAGE}"
    echo "   PEER:       порт ${PEER_PORT}"
    echo "   SSH:        порт ${SSH_PORT}"
    echo "   WEB:        порт ${WEB_PORT}"
    echo "   PID:        ${candidate_pid}"
    echo "   Логи:       docker logs -f ${Shod_DOCKER_CONTAINER}"
    echo "   Статус:     docker ps --filter name=${Shod_DOCKER_CONTAINER}"
    echo ""
}

setup_shod_service() {
    prog 0.75 "Сервис..."
    echo "🔧 Создание systemd-сервиса Shod..."

    local unit_tmp
    unit_tmp="$(mktemp)"
    cat > "$unit_tmp" << ShodSVC
[Unit]
Description=Shod VPN Server
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
EnvironmentFile=-${Shod_ENV_FILE}
Environment=Shod_SERVICE_MANAGER=systemd
ExecStartPre=${Shod_LIB_DIR}/network-up.sh
ExecStartPre=${Shod_LIB_DIR}/tun-recover.sh
ExecStart=${Shod_BIN} --listen 0.0.0.0:${PEER_PORT} --web-port ${WEB_PORT} --config-dir ${Shod_CONFIG_DIR}
Restart=on-failure
RestartSec=3
KillMode=control-group
TimeoutStartSec=30
TimeoutStopSec=5
Nice=-20

[Install]
WantedBy=multi-user.target
ShodSVC

    if [ ! -f "/etc/systemd/system/${Shod_UNIT}.service" ] || ! cmp -s "$unit_tmp" "/etc/systemd/system/${Shod_UNIT}.service"; then
        install -m 0644 "$unit_tmp" "/etc/systemd/system/${Shod_UNIT}.service"
        SYSTEMD_NEEDS_RELOAD=1
    fi
    rm -f "$unit_tmp"

    if [ "$SYSTEMD_NEEDS_RELOAD" -eq 1 ]; then
        systemctl daemon-reload || die "systemctl daemon-reload завершился ошибкой"
        SYSTEMD_NEEDS_RELOAD=0
    fi

    mkdir -p /etc/systemd/system/multi-user.target.wants
    ln -sfn "/etc/systemd/system/${Shod_UNIT}.service" "/etc/systemd/system/multi-user.target.wants/${Shod_UNIT}.service"
    echo "✓ Сервис ${Shod_UNIT}.service создан и включён"
}

diagnose_systemd_failure() {
    local props result="unknown" exec_code="unknown" exec_status="unknown" restarts="unknown" active="unknown" sub="unknown" pid="0"
    props="$(systemctl show "$Shod_UNIT" -p Result -p ExecMainCode -p ExecMainStatus -p NRestarts -p ActiveState -p SubState -p MainPID 2>/dev/null || true)"
    while IFS='=' read -r key value; do
        case "$key" in
            Result) result="$value" ;; ExecMainCode) exec_code="$value" ;; ExecMainStatus) exec_status="$value" ;;
            NRestarts) restarts="$value" ;; ActiveState) active="$value" ;; SubState) sub="$value" ;; MainPID) pid="$value" ;;
        esac
    done <<< "$props"
    log_error "systemd: active=$active/$sub result=$result code=$exec_code status=$exec_status restarts=$restarts pid=$pid"
    journalctl -u "$Shod_UNIT" -b -n 50 --no-pager 2>/dev/null | sed 's/^/   >> /' | tee -a "$LOG_FILE" || true
}

start_shod() {
    if [ "$DEPLOY_MODE" = "docker" ]; then
        start_shod_docker
        return
    fi
    prog 0.90 "Запуск..."
    echo "🚀 Запуск Shod VPN Server..."

    [ -x "$Shod_BIN" ] || die "Исполняемый файл $Shod_BIN не установлен"

    if ! systemctl start "$Shod_UNIT"; then
        systemctl reset-failed "$Shod_UNIT" >/dev/null 2>&1 || true
        if ! systemctl start "$Shod_UNIT"; then
            diagnose_systemd_failure
            die "systemctl start $Shod_UNIT завершился ошибкой"
        fi
    fi

    local props key value active sub main_pid restarts
    local candidate_pid=0 candidate_restarts=0 attempts=0 found=0
    while [ "$attempts" -lt 48 ]; do
        attempts=$((attempts + 1))
        active=""; sub=""; main_pid=0; restarts=0
        props="$(systemctl show "$Shod_UNIT" -p ActiveState -p SubState -p MainPID -p NRestarts 2>/dev/null || true)"
        while IFS='=' read -r key value; do
            case "$key" in
                ActiveState) active="$value" ;; SubState) sub="$value" ;; MainPID) main_pid="$value" ;; NRestarts) restarts="$value" ;;
            esac
        done <<< "$props"
        case "$main_pid" in ''|*[!0-9]*) main_pid=0 ;; esac
        case "$restarts" in ''|*[!0-9]*) restarts=0 ;; esac

        if [ "$active" = "active" ] && [ "$sub" = "running" ] && [ "$main_pid" -gt 1 ] && \
           kill -0 "$main_pid" 2>/dev/null && ip link show "$Shod_IFACE" >/dev/null 2>&1; then
            candidate_pid="$main_pid"
            candidate_restarts="$restarts"
            found=1
            break
        fi
        sleep 0.25
    done

    if [ "$found" -ne 1 ]; then
        diagnose_systemd_failure
        die "Shod не запустился: сервис не running или TUN $Shod_IFACE не создан"
    fi

    sleep "$START_STABILITY_SECONDS"
    active=""; sub=""; main_pid=0; restarts=0
    props="$(systemctl show "$Shod_UNIT" -p ActiveState -p SubState -p MainPID -p NRestarts 2>/dev/null || true)"
    while IFS='=' read -r key value; do
        case "$key" in
            ActiveState) active="$value" ;; SubState) sub="$value" ;; MainPID) main_pid="$value" ;; NRestarts) restarts="$value" ;;
        esac
    done <<< "$props"
    case "$main_pid" in ''|*[!0-9]*) main_pid=0 ;; esac
    case "$restarts" in ''|*[!0-9]*) restarts=0 ;; esac

    if [ "$active" != "active" ] || [ "$sub" != "running" ] || [ "$main_pid" != "$candidate_pid" ] || \
       [ "$restarts" != "$candidate_restarts" ] || ! kill -0 "$candidate_pid" 2>/dev/null || \
       ! ip link show "$Shod_IFACE" >/dev/null 2>&1; then
        diagnose_systemd_failure
        die "Shod вошёл в crash-loop или потерял TUN во время стабильной проверки"
    fi

    verify_configured_network
    verify_web_panel
    [ "$restarts" -eq 0 ] || log_warn "Во время старта shod перезапустился $restarts раз(а), затем стабилизировался"
    log_info "Shod сервер подтверждён: PID $candidate_pid, интерфейс $Shod_IFACE активен"
    finish_deployment

    prog 1.0 "Готово!"
    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo "✓ Деплой успешно завершён"
    echo "Shod_DEPLOY_OK"
    echo "   Режим: systemd"
    echo "   Бинарник: ${Shod_BIN}"
    echo "   Unit: /etc/systemd/system/${Shod_UNIT}.service"
    echo "   Конфиг: ${Shod_CONFIG_DIR}"
    echo "   NAT:  MASQUERADE (восстанавливается после reboot)"
    echo "   PEER: порт ${PEER_PORT}"
    echo "   SSH:  порт ${SSH_PORT}"
    echo "   WEB:  порт ${WEB_PORT}"
    echo "   PID:  ${candidate_pid}"
    echo "   Логи:   journalctl -u ${Shod_UNIT} -f"
    echo "   Статус: systemctl status ${Shod_UNIT}"
    echo "══════════════════════════════════════════════════════════════"
    echo ""
}

# Is the managed Shod installation present? Do not report a successful uninstall when nothing is installed.
shod_server_installed() {
    [ -e "$Shod_BIN" ] || [ -d "$Shod_LIB_DIR" ] || shod_systemd_unit_exists || shod_docker_container_exists || \
        ip link show "$Shod_IFACE" >/dev/null 2>&1 || [ -e "/etc/systemd/system/${Shod_LE_TIMER}" ]
}

do_uninstall() {
    log_step "Удаление Shod..."
    shod_server_installed || die "Сервер ${Shod_NAME} на этом VPS не установлен — удалять нечего"
 # this removal's cleanup stops, kills and deletes every shod unit, container and process it matches
 local strangers
 strangers="$(main_place_strangers | cut -d'|' -f2 | paste -sd ',' - | sed 's/,/, /g' || true)"
 [ -z "$strangers" ] || die "Здесь работает ещё один сервер shod, установленный не этим приложением: ${strangers}. Удаление стандартного сервера остановило бы и удалило его — отменено" "$EXIT_PREFLIGHT_FAILED"

    remove_all_shod_docker_containers
    if command -v docker >/dev/null 2>&1; then
        # every tag of this instance's image, not only this version's (a 2.5.0 image stayed behind a 2.5.2 upgrade)
        docker images --format '{{.Repository}}:{{.Tag}}' "$Shod_NAME" 2>/dev/null | while IFS= read -r image; do
            timeout 5 docker image rm "$image" >/dev/null 2>&1 || true
        done || true
    fi

    if command -v systemctl >/dev/null 2>&1; then
        systemctl disable --now "$Shod_LE_TIMER" >/dev/null 2>&1 || true
        systemctl stop "$Shod_LE_SERVICE" --no-block >/dev/null 2>&1 || true
        stop_shod_docker_prereq
        stop_all_running_shod_systemd_units
    fi
    force_stop_shod_processes || log_warn "Не все процессы Shod завершились до удаления runtime"
    remove_all_shod_systemd_units
    rm -f "/etc/systemd/system/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "/etc/systemd/system/docker.service.requires/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "/etc/systemd/system/docker.service.wants/${Shod_DOCKER_PREREQ_SERVICE}"
    rm -f "/etc/systemd/system/${Shod_LE_SERVICE}" "/etc/systemd/system/${Shod_LE_TIMER}" /etc/cron.d/shod-letsencrypt
    command -v systemctl >/dev/null 2>&1 && systemctl daemon-reload >/dev/null 2>&1 || true

    remove_shod_tun_interface || log_warn "Не удалось удалить TUN-интерфейс $Shod_IFACE во время uninstall"
    cleanup_shod_netfilter_rules || true

    rm -f "$Shod_BIN" "$Shod_LE_RENEW_HELPER"
    rm -rf "$Shod_RUNTIME_DIR" "$Shod_LIB_DIR" "$Shod_LE_VENV"
    rm -f /usr/local/bin/shod-cascade /usr/local/bin/hev-socks5-tunnel /usr/local/bin/hev-socks5-server
    rm -rf /var/lib/shod
    secure_persistent_state
    rm -f "$Shod_SYSCTL_FILE" "$Shod_UDP_SYSCTL_FILE"
    sysctl --system >/dev/null 2>&1 || true

    log_info "Shod удалён. SQLite state и конфигурация сохранены в ${Shod_CONFIG_DIR}"
    echo "Shod_UNINSTALL_OK"
}

do_status() {
    echo "Статус Shod:"
    echo ""
    if command -v docker >/dev/null 2>&1 && docker inspect "$Shod_DOCKER_CONTAINER" >/dev/null 2>&1; then
        if [ "$(docker inspect --format '{{.State.Running}}' "$Shod_DOCKER_CONTAINER" 2>/dev/null)" = "true" ]; then
            log_info "Docker-контейнер: АКТИВЕН"
        else
            log_warn "Docker-контейнер: НЕ АКТИВЕН"
        fi
    fi
    if command -v systemctl >/dev/null 2>&1 && systemctl is-active "$Shod_UNIT" &>/dev/null; then
        log_info "Сервис: АКТИВЕН"
    else
        log_warn "Сервис: НЕ АКТИВЕН"
    fi
    if [ -f "$Shod_BIN" ]; then
        log_info "Бинарник: установлен"
    else
        log_warn "Бинарник: НЕ найден"
    fi
    if ip link show "$Shod_IFACE" &>/dev/null; then
        log_info "Shod интерфейс ($Shod_IFACE): активен"
    else
        log_warn "Shod интерфейс ($Shod_IFACE): не активен"
    fi
}

# The version of the STOCK-layout server on this box (shod.service / container shod / /usr/local/bin/shod), read from the
# label its web panel compiles in — never by running it. Empty when there is none or it carries no label.
main_install_shod_version() {
    local pid="" exe=""
    if command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet shod 2>/dev/null; then
        pid="$(systemctl show --value -p MainPID shod 2>/dev/null || true)"
    fi
    if { [ -z "$pid" ] || [ "$pid" = 0 ]; } && command -v docker >/dev/null 2>&1 && docker inspect shod >/dev/null 2>&1; then
        pid="$(docker inspect --format '{{.State.Pid}}' shod 2>/dev/null || true)"
    fi
    if [ -n "$pid" ] && [ "$pid" != 0 ] && [ -e "/proc/$pid/exe" ]; then
        exe="/proc/$pid/exe"
    elif [ -f /usr/local/bin/shod ] && { [ "$(systemctl show --value -p LoadState shod 2>/dev/null || true)" = loaded ] || \
            { command -v docker >/dev/null 2>&1 && docker inspect shod >/dev/null 2>&1; }; }; then
        exe=/usr/local/bin/shod
    else
        return 0
    fi
    binary_version_label "$exe"
}

# The version label a shod binary compiles into its web panel (2.1.9 and 2.5.x carry one); empty without it.
binary_version_label() {
    { grep -a -o -m1 -E 'aria-label="v[0-9][0-9A-Za-z.+_-]*[^"]*"' "$1" 2>/dev/null || true; } | head -n 1 | \
        sed -E 's/^aria-label="v([0-9][0-9A-Za-z.+_-]*)[^"]*"$/\1/'
}

# ---- The server check the app runs before it offers anything (deploy-check-first-design.md §1–§2) ----
# `deploy.sh inventory`: read-only — installs, stops and writes nothing. One Shod_INV line per finding; the app trusts the
# result only after Shod_INV|end.

# The standard place's own units (2.1.9's cascade helper /usr/local/bin/shod-cascade matches the stock pattern too).
readonly STANDARD_PLACE_UNITS="shod.service shod-cascade.service shod-letsencrypt.service shod-docker-prereq.service"

# "<udp> <web>" from a server command line (--listen HOST:PORT, --web-port PORT); an absent flag is the server's own
# default, 46000 / 46002.
server_ports_in() {
    local listen web
    listen="$(grep -oE -- '--listen[= ][^ "]+' <<<"$1" | tail -n1 | sed -E 's/.*://' || true)"
    web="$(grep -oE -- '--web-port[= ][0-9]+' <<<"$1" | tail -n1 | grep -oE '[0-9]+$' || true)"
    case "$listen" in ''|*[!0-9]*) listen=46000 ;; esac
    case "$web" in ''|*[!0-9]*) web=46002 ;; esac
    printf '%s %s' "$listen" "$web"
}

pid_args() { { tr '\0' ' ' < "/proc/$1/cmdline"; } 2>/dev/null || true; }

# Running for the purpose of "is it someone's live server": active, or activating — a unit between crash restarts
# ("activating (auto-restart)") is a running server, not a stopped leftover.
unit_live() {
    case "$(systemctl show --value -p ActiveState "$1" 2>/dev/null || true)" in
        active|activating|reloading) return 0 ;;
    esac
    return 1
}

docker_id() { command -v docker >/dev/null 2>&1 && docker inspect --format '{{.Id}}' "$1" 2>/dev/null || true; }

# Is PID $1 in one of these units or containers? By cgroup: a hand-made server runs the same /usr/local/bin/shod, only
# from its own unit or container. $2: unit names, $3: container ids (space-separated).
pid_in_cgroups() {
    local cg u
    cg="$(cat "/proc/$1/cgroup" 2>/dev/null || true)"
    for u in $2; do case "$cg" in *"/$u"*) return 0 ;; esac; done
    for u in $3; do case "$cg" in *"$u"*) return 0 ;; esac; done
    return 1
}

# Find other running Shod servers that cleanup of the standard installation could affect:
# non-standard systemd units, Docker containers, and stock-binary processes outside the managed units.
# Emits "<runtime>|<name>|<pid>" per line.
main_place_strangers() {
    local unit c pid known_ids strange_units="" strange_ids=""
    known_ids="$(docker_id shod)"
    while IFS= read -r unit; do
        [ -n "$unit" ] || continue
        case " $STANDARD_PLACE_UNITS " in *" $unit "*) continue ;; esac
        unit_live "$unit" || continue
        strange_units="$strange_units $unit"
        printf 'systemd|%s|%s\n' "$unit" "$(systemctl show --value -p MainPID "$unit" 2>/dev/null || true)"
    done < <(list_shod_systemd_units)
    if command -v docker >/dev/null 2>&1; then
        while IFS= read -r c; do
            case "$c" in ''|shod) continue ;; esac
            docker_container_runs_shod "$c" || continue
            strange_ids="$strange_ids $(docker_id "$c")"
            printf 'docker|%s|%s\n' "$c" "$(docker inspect --format '{{.State.Pid}}' "$c" 2>/dev/null || true)"
        done < <(docker ps --format '{{.Names}}' 2>/dev/null || true)
    fi
    for pid in $(all_shod_process_pids); do
        pid_in_cgroups "$pid" "$STANDARD_PLACE_UNITS$strange_units" "$known_ids$strange_ids" && continue
        printf 'process|%s|%s\n' "$(describe_pid "$pid")" "$pid"
    done
    return 0
}

inv_line() {
    local out="Shod_INV" field
    for field in "$@"; do out="$out|${field//|/}"; done
    printf '%s\n' "${out//$'\n'/ }"
}

# One place's server: a running unit, else a running container, else a stopped unit or container, else what is left.
inv_place() {
    local place="$1" unit="$2" ctr="$3" bin="$4" store="$5" text_pattern="$6"
    local runtime="" state="" name="" pid="" exe="$bin" args="" ver="" text="" image="" unit_ok=0 ctr_ok=0 peer web
    if command -v systemctl >/dev/null 2>&1 && [ "$(systemctl show --value -p LoadState "$unit" 2>/dev/null || true)" = loaded ]; then
        text="$(systemctl cat "$unit" 2>/dev/null || true)"
        grep -Eq "$text_pattern" <<<"$text" && unit_ok=1
    fi
    command -v docker >/dev/null 2>&1 && docker inspect "$ctr" >/dev/null 2>&1 && ctr_ok=1
    if [ "$unit_ok" = 1 ] && systemctl is-active --quiet "$unit" 2>/dev/null; then
        runtime=systemd; state=running; name="$unit"
        pid="$(systemctl show --value -p MainPID "$unit" 2>/dev/null || true)"
    elif [ "$ctr_ok" = 1 ] && [ "$(docker inspect --format '{{.State.Running}}' "$ctr" 2>/dev/null || true)" = true ]; then
        runtime=docker; state=running; name="$ctr"
        pid="$(docker inspect --format '{{.State.Pid}}' "$ctr" 2>/dev/null || true)"
    elif [ "$unit_ok" = 1 ]; then
        runtime=systemd; state=stopped; name="$unit"
        args="$(sed -n 's/^ExecStart=//p' <<<"$text" | tail -n1 || true)"
    elif [ "$ctr_ok" = 1 ]; then
        runtime=docker; state=stopped; name="$ctr"
        args="$(docker inspect --format '{{.Path}} {{join .Args " "}}' "$ctr" 2>/dev/null || true)"
    elif [ -e "$bin" ] || [ -f "$store/$Shod_DATABASE_FILE" ] || [ -f "$store/shod.env" ]; then
        runtime=none; state=leftover; name="$bin"; [ -e "$bin" ] || name="$store"
    else
        return 0
    fi
    if [ -n "$pid" ] && [ "$pid" != 0 ] && [ -e "/proc/$pid/exe" ]; then
        exe="/proc/$pid/exe"; args="$(pid_args "$pid")"
    fi
    ver="$(binary_version_label "$exe")"
    if [ -z "$ver" ] && [ "$runtime" = docker ]; then
        # a stopped container's binary lives in its image, tagged with the version (shod:2.5.0)
        image="$(docker inspect --format '{{.Config.Image}}' "$ctr" 2>/dev/null || true)"
        case "${image##*:}" in [0-9]*) ver="${image##*:}" ;; esac
    fi
    read -r peer web <<<"$(server_ports_in "$args")"
    inv_line server "place=$place" "runtime=$runtime" "state=$state" "version=${ver:-?}" "peer=$peer" "web=$web" "name=$name"
}

do_inventory() {
    local runtime name pid unit iface listen exe ver peer web spec proto port holders args
    inv_place standard shod.service shod /usr/local/bin/shod /etc/shod '(/usr/local/bin/shod|/usr/local/lib/shod/)'
    while IFS='|' read -r runtime name pid; do
        [ -n "$runtime" ] || continue
        exe=""; [ -n "$pid" ] && [ "$pid" != 0 ] && [ -e "/proc/$pid/exe" ] && exe="/proc/$pid/exe"
        ver="$( [ -n "$exe" ] && binary_version_label "$exe" || true)"
        if [ -n "$exe" ]; then
            args="$(pid_args "$pid")"
        elif [ "$runtime" = systemd ]; then
            # between crash restarts there is no process: its unit's command line says the ports
            args="$(systemctl show --value -p ExecStart "$name" 2>/dev/null | sed -n 's/.*argv\[\]=\([^;]*\);.*/\1/p' | head -n1 || true)"
        else
            args=""
        fi
        read -r peer web <<<"$(server_ports_in "$args")"
        inv_line server place=other "runtime=$runtime" state=running "version=${ver:-?}" "peer=$peer" "web=$web" "name=$name"
    done < <(main_place_strangers)
    # Stopped units with the stock paths outside the standard place are also removed; report them here.
    while IFS= read -r unit; do
        [ -n "$unit" ] || continue
        case " $STANDARD_PLACE_UNITS " in *" $unit "*) continue ;; esac
        unit_live "$unit" && continue
        read -r peer web <<<"$(server_ports_in "$(systemctl show --value -p ExecStart "$unit" 2>/dev/null | sed -n 's/.*argv\[\]=\([^;]*\);.*/\1/p' | head -n1 || true)")"
        inv_line server place=leftover runtime=systemd state=stopped version=? "peer=$peer" "web=$web" "name=$unit"
    done < <(list_shod_systemd_units)
    # stopped containers running the stock binary: the standard place's cleanup removes them too (docker ps -a)
    if command -v docker >/dev/null 2>&1; then
        while IFS= read -r name; do
            case "$name" in ''|shod) continue ;; esac
            docker_container_runs_shod "$name" || continue
            read -r peer web <<<"$(server_ports_in "$(docker inspect --format '{{.Path}} {{join .Args " "}}' "$name" 2>/dev/null || true)")"
            inv_line server place=leftover runtime=docker state=stopped version=? "peer=$peer" "web=$web" "name=$name"
        done < <(docker ps -a --filter status=exited --filter status=created --format '{{.Names}}' 2>/dev/null || true)
    fi
    # the default ports, when something that is not a shod server holds them
    for spec in u:46000 t:46002; do
        proto="${spec%%:*}"; port="${spec#*:}"; holders=""
        while IFS= read -r pid; do
            [ -n "$pid" ] || continue
            case "$(readlink "/proc/$pid/exe" 2>/dev/null || true)" in *shod*) continue ;; esac
            holders="${holders:+$holders, }$(describe_pid "$pid")"
        done < <(ss -H -l"${proto}"np "sport = :${port}" 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -un || true)
        if [ -z "$holders" ] && ss -H -l"${proto}"n "sport = :${port}" 2>/dev/null | grep -q . && \
           [ -z "$(ss -H -l"${proto}"np "sport = :${port}" 2>/dev/null | grep -oE 'pid=[0-9]+' || true)" ]; then
            holders="процесс без видимого PID"
        fi
        [ -z "$holders" ] || inv_line busy "proto=$( [ "$proto" = u ] && echo udp || echo tcp)" "port=$port" "by=$holders"
    done
    inv_line end
}

# Refuse foreign listeners and processes before replacing the existing server.
main_preflight() {
    local port
    refuse_anothers_server
    refuse_foreign_port_holder u "$PEER_PORT" "UDP-порт"
    refuse_foreign_port_holder t "$WEB_PORT" "TCP-порт WEB-панели"
    # A server running outside the managed installation would be stopped or deleted by cleanup.
    local strangers
    strangers="$(main_place_strangers | cut -d'|' -f2 | paste -sd ',' - | sed 's/,/, /g' || true)"
    [ -z "$strangers" ] || die "Здесь работает ещё один сервер shod, установленный не этим приложением: ${strangers}. Установка на стандартное место остановила бы и удалила его — отменено" "$EXIT_PREFLIGHT_FAILED"
}

handle_unexpected_error() {
    local line="$1" status="$2"
    trap - ERR
    log_error "Неперехваченная ошибка deploy.sh (строка $line, exit=$status)"
    printf 'Shod_DEPLOY_ERROR|%s|Неперехваченная ошибка deploy.sh (строка %s, exit=%s)\n' "$DEPLOY_PHASE" "$line" "$status"
    cleanup_deploy_uploads
    case "$DEPLOY_PHASE" in
        validation) exit "$EXIT_INVALID_ARGUMENT" ;;
        preflight) exit "$EXIT_PREFLIGHT_FAILED" ;;
        *) exit "$status" ;;
    esac
}

trap 'handle_unexpected_error "$LINENO" "$?"' ERR

main() {
    if [ "${1:-}" = inventory ]; then
        check_root
        do_inventory
        return 0
    fi
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║       Shod VPN Server — Installer v${SCRIPT_VERSION}                    ║"
    echo "║       PEER: ${PEER_PORT}  |  SSH: ${SSH_PORT}  | WEB: ${WEB_PORT}       ║"
    echo "╚══════════════════════════════════════════════════════════════╝"

    local action="${1:-install}"
    check_root
    mkdir -p "$(dirname "$LOG_FILE")"
    echo "=== Shod Installer v${SCRIPT_VERSION} — $(date) ===" >> "$LOG_FILE"

    case "$action" in
        update|--update|-up) PRESERVE_CONFIG=1 ;;
    esac

    case "$action" in
        status|--status|-s)       do_status ;;
        uninstall|--uninstall|-u)
            DEPLOY_PHASE="validation"
            validate_port "Shod_PEER_PORT" "$PEER_PORT"
            do_uninstall
            ;;
        purge|--purge|-p)
            DEPLOY_PHASE="validation"
            validate_port "Shod_PEER_PORT" "$PEER_PORT"
            do_uninstall
            rm -rf -- "$Shod_CONFIG_DIR"
            log_info "Конфигурация, SQLite-база и сертификаты Shod удалены"
            echo "Shod_PURGE_OK"
            ;;
        install|--install|-i|update|--update|-up)
            DEPLOY_PHASE="validation"
            case "$DEPLOY_MODE" in
                systemd|docker) ;;
                *) die "Shod_DEPLOY_MODE должен быть systemd или docker" ;;
            esac
            validate_port "Shod_PEER_PORT" "$PEER_PORT"
            validate_port "Shod_SSH_PORT" "$SSH_PORT"
            validate_port "Shod_WEB_PORT" "$WEB_PORT"
            validate_positive_seconds "Shod_DOCKER_BUILD_TIMEOUT_SECONDS" "$DOCKER_BUILD_TIMEOUT_SECONDS"
            validate_distinct_network_ports

            local total_started=$SECONDS
            detect_os
            run_timed "зависимости" install_prerequisites
            require_runtime_tools
            main_preflight
            if [ "$DEPLOY_MODE" = "docker" ]; then
                prog 0.12 "Docker..."
                run_timed "Docker" ensure_docker
            fi
            run_timed "sysctl" setup_sysctl

            run_timed "проверка upload" prepare_uploaded_release
            if [ "$DEPLOY_MODE" = "docker" ]; then
                run_timed "Docker preflight" prepare_docker_candidate "$UPLOAD_BINARY"
            fi
            DEPLOY_PHASE="cutover"
            run_timed "переключение runtime" shod_cleanup
            run_timed "preflight" run_platform_preflight
            detect_firewall
            run_timed "NAT/firewall" setup_nat_and_firewall
            install_network_helper
            if [ "$PRESERVE_CONFIG" = "1" ]; then
                log_info "Режим обновления: логин, пароли и путь панели сохраняются без изменений"
            fi
            setup_shod_environment
            run_timed "Let’s Encrypt" setup_letsencrypt_ip_tls
            DEPLOY_PHASE="activation"
            run_timed "бинарник" setup_shod_binary
            run_timed "принудительная остановка" force_stop_shod_processes
            if [ "$DEPLOY_MODE" = "docker" ]; then
                run_timed "Docker activation" setup_shod_docker
            else
                run_timed "systemd unit" setup_shod_service
            fi
            run_timed "запуск" start_shod
            log_info "Общее время deploy.sh: $((SECONDS - total_started))с"
            ;;
        *) die "Неизвестная команда установщика: $action" ;;
    esac
}

main "$@"
