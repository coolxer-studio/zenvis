#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEFAULT_REPO_URL="https://gitee.com/coolxer-studio/zenvis.git"
readonly DEFAULT_REPO_BRANCH="feature/zenvis-1.0"
readonly DEFAULT_STARTUP_TIMEOUT="600"
readonly DEFAULT_CHECK_INTERVAL="5"

REPO_URL="${ZENVIS_REPO_URL:-${DEFAULT_REPO_URL}}"
REPO_BRANCH="${ZENVIS_REPO_BRANCH:-${DEFAULT_REPO_BRANCH}}"
STARTUP_TIMEOUT="${ZENVIS_STARTUP_TIMEOUT:-${DEFAULT_STARTUP_TIMEOUT}}"
CHECK_INTERVAL="${ZENVIS_CHECK_INTERVAL:-${DEFAULT_CHECK_INTERVAL}}"
PROJECT_DIR=""
COMPOSE_CMD=()
ENV_TEMP_FILE=""

if [[ -t 1 ]]; then
    readonly COLOR_GREEN=$'\033[0;32m'
    readonly COLOR_YELLOW=$'\033[0;33m'
    readonly COLOR_RED=$'\033[0;31m'
    readonly COLOR_BLUE=$'\033[0;34m'
    readonly COLOR_RESET=$'\033[0m'
else
    readonly COLOR_GREEN=""
    readonly COLOR_YELLOW=""
    readonly COLOR_RED=""
    readonly COLOR_BLUE=""
    readonly COLOR_RESET=""
fi

info() {
    printf '%s[信息]%s %s\n' "${COLOR_BLUE}" "${COLOR_RESET}" "$*"
}

success() {
    printf '%s[成功]%s %s\n' "${COLOR_GREEN}" "${COLOR_RESET}" "$*"
}

warn() {
    printf '%s[提示]%s %s\n' "${COLOR_YELLOW}" "${COLOR_RESET}" "$*"
}

fail() {
    printf '%s[错误]%s %s\n' "${COLOR_RED}" "${COLOR_RESET}" "$*" >&2
    exit 1
}

step() {
    printf '\n%s========== 第 %s 步：%s ==========%s\n' \
        "${COLOR_BLUE}" "$1" "$2" "${COLOR_RESET}"
}

cleanup() {
    if [[ -n "${ENV_TEMP_FILE}" && -f "${ENV_TEMP_FILE}" ]]; then
        rm -f -- "${ENV_TEMP_FILE}"
    fi
}

trap cleanup EXIT

usage() {
    cat <<'EOF'
ZenVis 在线快速部署脚本

用法：
  ./quick-deploy.sh

可选环境变量：
  ZENVIS_REPO_URL          Git 仓库地址
  ZENVIS_REPO_BRANCH       Git 分支
  ZENVIS_INSTALL_DIR       不在项目中时的克隆目录，默认：当前目录/zenvis
  ZENVIS_STARTUP_TIMEOUT   容器启动超时秒数，默认：600
  ZENVIS_CHECK_INTERVAL    状态检查间隔秒数，默认：5
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

if [[ "$#" -gt 0 ]]; then
    usage
    fail "不支持的参数：$1"
fi

case "${STARTUP_TIMEOUT}" in
    ''|*[!0-9]*) fail "ZENVIS_STARTUP_TIMEOUT 必须是正整数" ;;
esac

case "${CHECK_INTERVAL}" in
    ''|*[!0-9]*) fail "ZENVIS_CHECK_INTERVAL 必须是正整数" ;;
esac

if [[ "${STARTUP_TIMEOUT}" -eq 0 || "${CHECK_INTERVAL}" -eq 0 ]]; then
    fail "启动超时和检查间隔必须大于 0"
fi

is_zenvis_project() {
    local candidate="$1"

    [[ -f "${candidate}/README.md" ]] \
        && [[ -f "${candidate}/deploy/.env" ]] \
        && [[ -f "${candidate}/deploy/docker-compose.yml" ]] \
        && grep -q '^# ZenVis' "${candidate}/README.md"
}

find_project_from() {
    local candidate="$1"
    local parent

    [[ -d "${candidate}" ]] || return 1
    candidate="$(cd "${candidate}" && pwd -P)"

    while true; do
        if is_zenvis_project "${candidate}"; then
            printf '%s\n' "${candidate}"
            return 0
        fi

        parent="$(dirname "${candidate}")"
        if [[ "${parent}" == "${candidate}" ]]; then
            break
        fi
        candidate="${parent}"
    done

    return 1
}

select_compose_command() {
    if docker compose version >/dev/null 2>&1; then
        COMPOSE_CMD=(docker compose)
        return
    fi

    if command -v docker-compose >/dev/null 2>&1 \
        && docker-compose version >/dev/null 2>&1; then
        COMPOSE_CMD=(docker-compose)
        return
    fi

    fail "未检测到 Docker Compose。请自行安装 docker compose 插件或 docker-compose 后重新运行。"
}

detect_architecture() {
    local machine
    machine="$(uname -m)"

    case "${machine}" in
        x86_64|amd64)
            printf 'amd64\n'
            ;;
        arm64|aarch64)
            printf 'arm64\n'
            ;;
        *)
            fail "暂不支持系统架构 ${machine}，当前部署镜像仅支持 amd64 和 arm64。"
            ;;
    esac
}

update_architecture_env() {
    local env_file="$1"
    local architecture="$2"
    local current_arch

    current_arch="$(
        awk -F= '
            /^[[:space:]]*ARCH[[:space:]]*=/ {
                value = $2
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
                print value
                exit
            }
        ' "${env_file}"
    )"

    if [[ "${current_arch}" == "${architecture}" ]]; then
        info "deploy/.env 已使用当前系统架构：${architecture}"
        return
    fi

    ENV_TEMP_FILE="${env_file}.tmp.$$"
    awk -v architecture="${architecture}" '
        BEGIN {
            updated = 0
        }
        /^[[:space:]]*ARCH[[:space:]]*=/ {
            if (!updated) {
                print "ARCH=" architecture
                updated = 1
            }
            next
        }
        {
            print
        }
        END {
            if (!updated) {
                print "ARCH=" architecture
            }
        }
    ' "${env_file}" > "${ENV_TEMP_FILE}"

    mv -- "${ENV_TEMP_FILE}" "${env_file}"
    ENV_TEMP_FILE=""
    success "已将 deploy/.env 更新为 ARCH=${architecture}"
}

show_compose_diagnostics() {
    warn "以下是当前容器状态："
    "${COMPOSE_CMD[@]}" ps -a || true
    warn "以下是最近 100 行容器日志："
    "${COMPOSE_CMD[@]}" logs --tail=100 || true
}

wait_for_containers() {
    local services="$1"
    local total=0
    local ready
    local service
    local container_ids
    local container_id
    local state
    local health
    local pending
    local failed
    local deadline
    local summary
    local last_summary=""

    for service in ${services}; do
        total=$((total + 1))
    done

    if [[ "${total}" -eq 0 ]]; then
        fail "docker-compose.yml 中未找到任何服务。"
    fi

    deadline=$((SECONDS + STARTUP_TIMEOUT))
    while (( SECONDS < deadline )); do
        ready=0
        pending=""
        failed=""

        for service in ${services}; do
            container_ids="$("${COMPOSE_CMD[@]}" ps -a -q "${service}" 2>/dev/null || true)"
            container_id="${container_ids%%$'\n'*}"

            if [[ -z "${container_id}" ]]; then
                pending="${pending} ${service}(未创建)"
                continue
            fi

            state="$(docker inspect --format '{{.State.Status}}' "${container_id}" 2>/dev/null || true)"
            health="$(
                docker inspect \
                    --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
                    "${container_id}" 2>/dev/null || true
            )"

            case "${state}" in
                running)
                    if [[ "${health}" == "healthy" || "${health}" == "none" ]]; then
                        ready=$((ready + 1))
                    else
                        pending="${pending} ${service}(${health:-未知})"
                    fi
                    ;;
                exited|dead|removing)
                    failed="${failed} ${service}(${state})"
                    ;;
                *)
                    pending="${pending} ${service}(${state:-未知})"
                    ;;
            esac
        done

        summary="${ready}/${total} 个容器已就绪"
        if [[ "${summary}${pending}${failed}" != "${last_summary}" ]]; then
            info "${summary}；等待：${pending:-无}"
            last_summary="${summary}${pending}${failed}"
        fi

        if [[ -n "${failed}" ]]; then
            show_compose_diagnostics
            fail "检测到异常退出的容器：${failed}"
        fi

        if [[ "${ready}" -eq "${total}" ]]; then
            success "全部 ${total} 个容器均已运行并通过健康检查。"
            return
        fi

        sleep "${CHECK_INTERVAL}"
    done

    show_compose_diagnostics
    fail "等待容器启动超时（${STARTUP_TIMEOUT} 秒）。仍未就绪：${pending:-未知}"
}

check_web_page() {
    local url="$1"

    if command -v curl >/dev/null 2>&1; then
        curl --fail --silent --show-error --max-time 10 \
            --output /dev/null "${url}"
        return
    fi

    if command -v wget >/dev/null 2>&1; then
        wget --quiet --timeout=10 --spider "${url}"
        return
    fi

    fail "系统缺少 curl 或 wget，无法验证 11000 端口的 Web 页面。请安装其中任一工具后重试。"
}

detect_host_ip() {
    local host_ip=""

    if command -v hostname >/dev/null 2>&1; then
        host_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
    fi

    if [[ -z "${host_ip}" ]] && command -v ipconfig >/dev/null 2>&1; then
        host_ip="$(ipconfig getifaddr en0 2>/dev/null || true)"
    fi

    if [[ -z "${host_ip}" ]] && command -v ipconfig >/dev/null 2>&1; then
        host_ip="$(ipconfig getifaddr en1 2>/dev/null || true)"
    fi

    printf '%s\n' "${host_ip}"
}

step "1" "检查 Docker 环境"

if ! command -v docker >/dev/null 2>&1; then
    fail "未检测到 Docker。请自行安装并启动 Docker 后重新运行本脚本。"
fi

select_compose_command
success "已检测到 Docker：$(docker --version)"
success "将使用 Compose 命令：${COMPOSE_CMD[*]}"

if ! docker info >/dev/null 2>&1; then
    fail "Docker 已安装，但服务当前不可用。请启动 Docker 服务后重新运行本脚本。"
fi
success "Docker 服务运行正常。"

step "2" "定位或拉取 ZenVis 项目"

if PROJECT_DIR="$(find_project_from "${PWD}")"; then
    success "当前位于 ZenVis 项目中：${PROJECT_DIR}"
else
    script_dir=""
    script_source="${BASH_SOURCE[0]:-}"
    if [[ -n "${script_source}" && -f "${script_source}" ]]; then
        script_dir="$(cd "$(dirname "${script_source}")" && pwd -P)"
    fi

    if [[ -n "${script_dir}" ]] && PROJECT_DIR="$(find_project_from "${script_dir}")"; then
        success "已从脚本位置找到 ZenVis 项目：${PROJECT_DIR}"
    else
        if ! command -v git >/dev/null 2>&1; then
            fail "当前不在 ZenVis 项目中，且未检测到 Git。请自行安装 Git 后重新运行。"
        fi

        install_dir="${ZENVIS_INSTALL_DIR:-${PWD}/zenvis}"
        if [[ -e "${install_dir}" ]]; then
            if is_zenvis_project "${install_dir}"; then
                PROJECT_DIR="$(cd "${install_dir}" && pwd -P)"
                success "使用已有 ZenVis 项目：${PROJECT_DIR}"
            else
                fail "目标目录已存在但不是 ZenVis 项目：${install_dir}。请移走该目录或通过 ZENVIS_INSTALL_DIR 指定其他目录。"
            fi
        else
            info "当前不在 ZenVis 项目中，开始拉取：${REPO_URL}（${REPO_BRANCH}）"
            if ! git clone --depth 1 --branch "${REPO_BRANCH}" "${REPO_URL}" "${install_dir}"; then
                fail "ZenVis 项目拉取失败，请检查网络、仓库地址和分支后重试。"
            fi

            if ! is_zenvis_project "${install_dir}"; then
                fail "项目已拉取，但目录结构不完整：${install_dir}"
            fi

            PROJECT_DIR="$(cd "${install_dir}" && pwd -P)"
            success "ZenVis 项目已拉取到：${PROJECT_DIR}"
        fi
    fi
fi

step "3" "匹配系统架构并启动服务"

architecture="$(detect_architecture)"
info "检测到系统架构：$(uname -m)，使用镜像架构：${architecture}"
update_architecture_env "${PROJECT_DIR}/deploy/.env" "${architecture}"

cd "${PROJECT_DIR}/deploy"
info "正在启动 ZenVis，首次拉取镜像可能需要较长时间……"
if ! "${COMPOSE_CMD[@]}" up -d; then
    show_compose_diagnostics
    fail "ZenVis 容器启动命令执行失败。"
fi
success "Docker Compose 启动命令已执行。"

step "4" "检查容器状态和 Web 页面"

services="$("${COMPOSE_CMD[@]}" config --services)"
wait_for_containers "${services}"

login_url="http://127.0.0.1:11000"
if ! check_web_page "${login_url}"; then
    show_compose_diagnostics
    fail "所有容器均已运行，但无法访问 ${login_url}。请检查防火墙和端口占用。"
fi
success "Web 页面访问正常：${login_url}"

host_ip="$(detect_host_ip)"
printf '\n%s========================================%s\n' "${COLOR_GREEN}" "${COLOR_RESET}"
printf '%sZenVis 安装成功！%s\n' "${COLOR_GREEN}" "${COLOR_RESET}"
printf '本机登录地址： http://localhost:11000\n'
if [[ -n "${host_ip}" && "${host_ip}" != "127.0.0.1" ]]; then
    printf '局域网登录地址： http://%s:11000\n' "${host_ip}"
fi
printf '\n默认超级管理员账号： super@admin.com\n'
printf '默认机构管理员账号： admin@admin.com\n'
printf '默认密码：             admin@!QAZ2wsx\n'
printf '%s首次登录后请立即修改默认密码。%s\n' "${COLOR_YELLOW}" "${COLOR_RESET}"
printf '%s========================================%s\n' "${COLOR_GREEN}" "${COLOR_RESET}"
