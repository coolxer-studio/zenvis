#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEFAULT_REPO_URL="https://gitee.com/coolxer-studio/zenvis.git"
readonly DEFAULT_REPO_BRANCH="main"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-}")" && pwd -P)"

REPO_URL="${ZENVIS_REPO_URL:-$DEFAULT_REPO_URL}"
REPO_BRANCH="${ZENVIS_REPO_BRANCH:-$DEFAULT_REPO_BRANCH}"
PROJECT_DIR=""

die() { printf '[错误] %s\n' "$*" >&2; exit 1; }
info() { printf '[信息] %s\n' "$*"; }

detect_architecture() {
    case "$(uname -m)" in
        x86_64|amd64) printf 'amd64\n' ;;
        arm64|aarch64) printf 'arm64\n' ;;
        *) die "不支持的系统架构：$(uname -m)" ;;
    esac
}

manifest_value() {
    local file="$1"
    local key="$2"
    awk -F= -v key="$key" 'index($0, key "=") == 1 { sub(/^[^=]*=/, ""); print; exit }' "$file"
}

load_offline_images() {
    local project_dir="$1"
    local offline_dir="${project_dir}/offline"
    local images_tar="${offline_dir}/images.tar"
    local images_list="${offline_dir}/images.list"
    local manifest="${offline_dir}/manifest.env"
    local package_arch host_arch image count=0 format_version

    [[ -f "$images_tar" ]] || return 0
    command -v docker >/dev/null 2>&1 || die "检测到离线镜像，但未安装 Docker"
    [[ -f "$images_list" ]] || die "离线镜像包不完整：缺少 ${images_list}"
    [[ -f "$manifest" ]] || die "离线镜像包不完整：缺少 ${manifest}"
    [[ -s "$images_tar" ]] || die "离线镜像文件为空：${images_tar}"
    [[ -s "$images_list" ]] || die "离线镜像清单为空：${images_list}"

    format_version="$(manifest_value "$manifest" format_version)"
    [[ "$format_version" == "1" ]] || die "不支持的离线包格式：${format_version:-unknown}"
    package_arch="$(manifest_value "$manifest" arch)"
    [[ -n "$package_arch" ]] || die "离线包 manifest 缺少 arch"
    host_arch="$(detect_architecture)"
    if [[ "$package_arch" != "$host_arch" ]]; then
        die "离线包架构为 ${package_arch}，当前主机为 ${host_arch}；请使用对应架构的离线包"
    fi

    info "检测到离线镜像包，开始导入：${images_tar}"
    docker load -i "$images_tar" || die "离线镜像导入失败：${images_tar}"

    while IFS= read -r image; do
        [[ -n "$image" ]] || continue
        docker image inspect "$image" >/dev/null 2>&1 \
            || die "离线镜像导入后仍缺少：${image}"
        count=$((count + 1))
    done < "$images_list"
    [[ "$count" -gt 0 ]] || die "离线镜像清单为空：${images_list}"
    info "离线镜像导入完成，共 ${count} 个。"
}

is_project() {
    [[ -f "$1/zenvisctl" && -f "$1/deploy/docker-compose.yml" ]]
}

run_zenvisctl() {
    local project_dir="$1"
    shift
    if [[ -x "${project_dir}/zenvisctl" ]]; then
        "${project_dir}/zenvisctl" "$@"
    else
        bash "${project_dir}/zenvisctl" "$@"
    fi
}

find_project() {
    local candidate="$1"
    local parent
    [[ -d "$candidate" ]] || return 1
    candidate="$(cd "$candidate" && pwd -P)"
    while true; do
        if is_project "$candidate"; then
            printf '%s\n' "$candidate"
            return
        fi
        parent="$(dirname "$candidate")"
        [[ "$parent" != "$candidate" ]] || return 1
        candidate="$parent"
    done
}

if [[ "${1:-}" == -h || "${1:-}" == --help ]]; then
    printf '%s\n' '用法：curl -fsSL <quick-deploy.sh URL> | bash'
    printf '%s\n' '变量：ZENVIS_REPO_URL、ZENVIS_REPO_BRANCH、ZENVIS_INSTALL_DIR'
    printf '%s\n' '离线包：解压后直接执行 ./quick-deploy.sh，脚本会自动导入 offline/images.tar'
    exit 0
fi
[[ "$#" -eq 0 ]] || die "不支持位置参数"

if PROJECT_DIR="$(find_project "$SCRIPT_DIR")"; then
    info "使用当前 ZenVis 项目：$PROJECT_DIR"
elif PROJECT_DIR="$(find_project "$PWD")"; then
    info "使用当前 ZenVis 项目：$PROJECT_DIR"
else
    if [[ -f "${SCRIPT_DIR}/offline/images.tar" || -f "${PWD}/offline/images.tar" ]]; then
        die "检测到离线镜像，但未找到完整的 ZenVis 项目文件；请进入解压后的离线包目录执行 ./quick-deploy.sh"
    fi
    command -v git >/dev/null 2>&1 || die "未安装 Git"
    install_dir="${ZENVIS_INSTALL_DIR:-${PWD}/zenvis}"
    if [[ -e "$install_dir" ]]; then
        is_project "$install_dir" || die "目标目录已存在且不是 ZenVis 项目：$install_dir"
    else
        info "正在拉取 ${REPO_URL}（${REPO_BRANCH}）"
        git clone --depth 1 --branch "$REPO_BRANCH" "$REPO_URL" "$install_dir" \
            || die "拉取 ZenVis 仓库失败。离线环境请使用离线包，并进入解压目录执行 ./quick-deploy.sh"
    fi
    PROJECT_DIR="$(cd "$install_dir" && pwd -P)"
fi

load_offline_images "$PROJECT_DIR"
run_zenvisctl "$PROJECT_DIR" compose init
run_zenvisctl "$PROJECT_DIR" compose up
