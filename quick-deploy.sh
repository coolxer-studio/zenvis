#!/usr/bin/env bash

set -Eeuo pipefail

readonly DEFAULT_REPO_URL="https://gitee.com/coolxer-studio/zenvis.git"
readonly DEFAULT_REPO_BRANCH="feature/1.1.0.alpha"

REPO_URL="${ZENVIS_REPO_URL:-$DEFAULT_REPO_URL}"
REPO_BRANCH="${ZENVIS_REPO_BRANCH:-$DEFAULT_REPO_BRANCH}"
PROJECT_DIR=""

die() { printf '[错误] %s\n' "$*" >&2; exit 1; }
info() { printf '[信息] %s\n' "$*"; }

is_project() {
    [[ -x "$1/zenvisctl" && -f "$1/deploy/docker-compose.yml" ]]
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
    exit 0
fi
[[ "$#" -eq 0 ]] || die "不支持位置参数"

if PROJECT_DIR="$(find_project "$PWD")"; then
    info "使用当前 ZenVis 项目：$PROJECT_DIR"
else
    command -v git >/dev/null 2>&1 || die "未安装 Git"
    install_dir="${ZENVIS_INSTALL_DIR:-${PWD}/zenvis}"
    if [[ -e "$install_dir" ]]; then
        is_project "$install_dir" || die "目标目录已存在且不是 ZenVis 项目：$install_dir"
    else
        info "正在拉取 ${REPO_URL}（${REPO_BRANCH}）"
        git clone --depth 1 --branch "$REPO_BRANCH" "$REPO_URL" "$install_dir"
    fi
    PROJECT_DIR="$(cd "$install_dir" && pwd -P)"
fi

"${PROJECT_DIR}/zenvisctl" compose init
"${PROJECT_DIR}/zenvisctl" compose up
