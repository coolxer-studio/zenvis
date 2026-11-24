#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly DEPLOY_DIR="${SCRIPT_DIR}/deploy"
readonly COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
readonly ENV_FILE="${DEPLOY_DIR}/.env"
readonly ENV_EXAMPLE="${DEPLOY_DIR}/.env.example"
readonly ZENVISCTL="${SCRIPT_DIR}/zenvisctl"
readonly QUICK_DEPLOY="${SCRIPT_DIR}/quick-deploy.sh"
readonly DEFAULT_OUTPUT_DIR="${DEPLOY_DIR}/offline"
readonly PACKAGE_FORMAT_VERSION="1"

ARCH=""
OUTPUT=""
PULL=false
FORCE=false
KEEP_WORK=false
WORK_ROOT=""
PARTIAL_OUTPUT=""

info() { printf '[信息] %s\n' "$*"; }
die() { printf '[错误] %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<'EOF'
ZenVis 离线部署包打包工具

用法：
  ./package-offline.sh [选项]

选项：
  --arch <amd64|arm64>  目标 CPU 架构；默认读取 deploy/.env，缺省时检测当前主机
  --pull                缺失或架构不匹配时拉取目标架构镜像
  --output <路径>       输出文件或目录；默认 deploy/offline/
  --force               覆盖已存在的输出文件
  --keep-work           保留临时工作目录，便于排查
  -h, --help            显示帮助

示例：
  ./package-offline.sh --arch arm64
  ./package-offline.sh --arch amd64 --pull --output /data/packages/
EOF
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"
}

detect_architecture() {
    case "$(uname -m)" in
        x86_64|amd64) printf 'amd64\n' ;;
        arm64|aarch64) printf 'arm64\n' ;;
        *) die "不支持的系统架构：$(uname -m)" ;;
    esac
}

env_value() {
    local file="$1"
    local key="$2"
    awk -F= -v key="$key" 'index($0, key "=") == 1 { sub(/^[^=]*=/, ""); print; exit }' "$file"
}

set_env_value() {
    local file="$1"
    local key="$2"
    local value="$3"
    local temp_file="${file}.tmp.$$"

    awk -v key="$key" -v value="$value" '
        BEGIN { found = 0 }
        index($0, key "=") == 1 {
            print key "=" value
            found = 1
            next
        }
        { print }
        END { if (!found) print key "=" value }
    ' "$file" > "$temp_file"
    mv "$temp_file" "$file"
}

ensure_placeholder() {
    local file="$1"
    local key="$2"
    [[ -n "$(env_value "$file" "$key")" ]] \
        || set_env_value "$file" "$key" "__zenvis_offline_placeholder__"
}

ensure_default() {
    local file="$1"
    local key="$2"
    local value="$3"
    [[ -n "$(env_value "$file" "$key")" ]] || set_env_value "$file" "$key" "$value"
}

cleanup() {
    local status="$?"
    if [[ -n "$PARTIAL_OUTPUT" && -f "$PARTIAL_OUTPUT" ]]; then
        rm -f "$PARTIAL_OUTPUT"
    fi
    if [[ -n "$WORK_ROOT" && -d "$WORK_ROOT" ]]; then
        if [[ "$KEEP_WORK" == true ]]; then
            info "临时工作目录：$WORK_ROOT"
        else
            rm -rf "$WORK_ROOT"
        fi
    fi
    return "$status"
}

resolve_output() {
    local output="$1"
    local default_name="$2"
    local output_dir

    if [[ -z "$output" ]]; then
        output="${DEFAULT_OUTPUT_DIR}/${default_name}"
    elif [[ -d "$output" || "$output" == */ ]]; then
        output="${output%/}/${default_name}"
    else
        case "$output" in
            *.tar.gz|*.tgz) ;;
            *) output="${output}.tar.gz" ;;
        esac
    fi

    output_dir="$(dirname "$output")"
    mkdir -p "$output_dir"
    output_dir="$(cd "$output_dir" && pwd -P)"
    printf '%s/%s\n' "$output_dir" "$(basename "$output")"
}

prepare_compose_env() {
    local source_env="$1"
    local compose_env="$2"
    local key

    cp "$source_env" "$compose_env"
    chmod 600 "$compose_env"
    set_env_value "$compose_env" ARCH "$ARCH"

    ensure_default "$compose_env" ZENVIS_WEB_BIND_ADDRESS 0.0.0.0
    ensure_default "$compose_env" ZENVIS_API_BIND_ADDRESS 127.0.0.1
    ensure_default "$compose_env" ZENVIS_INFRA_BIND_ADDRESS 127.0.0.1
    ensure_default "$compose_env" ZENVIS_WEB_PORT 11000
    ensure_default "$compose_env" ZENVIS_API_PORT 11001
    ensure_default "$compose_env" VECTUM_PORT 11002

    for key in KAFKA_CLUSTER_ID REDIS_PASSWORD MYSQL_ROOT_PASSWORD \
        MYSQL_PASSWORD CLICKHOUSE_PASSWORD \
        MINIO_ROOT_PASSWORD VECTUM_AUTH_TOKEN API_BEARER_TOKEN MCP_BEARER_TOKEN \
        ZENVIS_BOOTSTRAP_SUPER_ADMIN_PASSWORD ZENVIS_BOOTSTRAP_ADMIN_PASSWORD; do
        ensure_placeholder "$compose_env" "$key"
    done

    [[ -n "$(env_value "$compose_env" ZENVIS_IMAGE_REGISTRY)" ]] \
        || die "ZENVIS_IMAGE_REGISTRY 为空，无法解析镜像"
    [[ -n "$(env_value "$compose_env" ZENVIS_VERSION)" ]] \
        || set_env_value "$compose_env" ZENVIS_VERSION latest
    [[ -n "$(env_value "$compose_env" VECTUM_VERSION)" ]] \
        || set_env_value "$compose_env" VECTUM_VERSION latest
}

validate_images() {
    local images_list="$1"
    local missing_file="$2"
    local wrong_file="$3"
    local image platform

    : > "$missing_file"
    : > "$wrong_file"
    while IFS= read -r image; do
        [[ -n "$image" ]] || continue
        if ! docker image inspect "$image" >/dev/null 2>&1; then
            printf '%s\n' "$image" >> "$missing_file"
            continue
        fi
        platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image" 2>/dev/null || true)"
        if [[ "$platform" != "linux/${ARCH}" && "$platform" != "linux/${ARCH}/v8" ]]; then
            printf '%s\t%s\n' "$image" "$platform" >> "$wrong_file"
        fi
    done < "$images_list"
}

report_image_problems() {
    local missing_file="$1"
    local wrong_file="$2"
    local image platform

    if [[ -s "$missing_file" ]]; then
        printf '[错误] 以下镜像不存在：\n' >&2
        while IFS= read -r image; do
            printf '  - %s\n' "$image" >&2
        done < "$missing_file"
    fi
    if [[ -s "$wrong_file" ]]; then
        printf '[错误] 以下镜像架构不是 linux/%s：\n' "$ARCH" >&2
        while IFS=$'\t' read -r image platform; do
            printf '  - %s（%s）\n' "$image" "${platform:-unknown}" >&2
        done < "$wrong_file"
    fi
    printf '[错误] 请先执行以下命令，或重新运行并增加 --pull：\n' >&2
    {
        cut -f1 "$wrong_file"
        cat "$missing_file"
    } | sed '/^$/d' | sort -u | while IFS= read -r image; do
        printf '  docker pull --platform linux/%s %s\n' "$ARCH" "$image" >&2
    done
}

pull_images() {
    local missing_file="$1"
    local wrong_file="$2"
    local to_pull="$3"
    local image

    {
        cut -f1 "$wrong_file"
        cat "$missing_file"
    } | sed '/^$/d' | sort -u > "$to_pull"

    while IFS= read -r image; do
        [[ -n "$image" ]] || continue
        info "拉取镜像：${image}（linux/${ARCH}）"
        docker pull --platform "linux/${ARCH}" "$image" >/dev/null \
            || die "镜像拉取失败：${image}"
    done < "$to_pull"
}

image_total_size() {
    local images_list="$1"
    local image size
    local total=0

    while IFS= read -r image; do
        [[ -n "$image" ]] || continue
        size="$(docker image inspect --format '{{.Size}}' "$image")"
        total=$((total + size))
    done < "$images_list"
    printf '%s\n' "$total"
}

check_disk_space() {
    local total_bytes="$1"
    local output_dir="$2"
    local available_kb required_kb

    available_kb="$(df -Pk "$output_dir" | awk 'NR == 2 { print $4 }')"
    [[ -n "$available_kb" ]] || return 0
    required_kb=$(( total_bytes / 1024 * 2 + 1048576 ))
    if (( available_kb < required_kb )); then
        die "磁盘空间不足：需要约 $((required_kb / 1024)) MB，$output_dir 可用 $((available_kb / 1024)) MB"
    fi
}

copy_tracked_config() {
    local stage="$1"
    local tracked_list="$2"
    local file copied=0

    if ! git -C "$SCRIPT_DIR" ls-files -z -- \
        deploy/docker-compose.yml deploy/.env.example deploy/config deploy/open_config \
        > "$tracked_list"; then
        die "读取 Git 跟踪的部署配置失败"
    fi

    while IFS= read -r -d '' file; do
        [[ -f "${SCRIPT_DIR}/${file}" ]] || continue
        mkdir -p "${stage}/$(dirname "$file")"
        cp -p "${SCRIPT_DIR}/${file}" "${stage}/${file}"
        copied=$((copied + 1))
    done < "$tracked_list"

    [[ -f "${stage}/deploy/docker-compose.yml" ]] || die "未能复制 deploy/docker-compose.yml"
    [[ -f "${stage}/deploy/.env.example" ]] || die "未能复制 deploy/.env.example"
    info "已复制 ${copied} 个受版本控制的部署配置文件。"
}

write_manifest() {
    local manifest="$1"
    local created_at="$2"
    local image_count="$3"
    local zenvis_version="$4"
    local vectum_version="$5"
    local image_registry="$6"
    local git_commit="$7"
    local git_dirty="$8"

    {
        printf 'format_version=%s\n' "$PACKAGE_FORMAT_VERSION"
        printf 'created_at=%s\n' "$created_at"
        printf 'arch=%s\n' "$ARCH"
        printf 'zenvis_version=%s\n' "$zenvis_version"
        printf 'vectum_version=%s\n' "$vectum_version"
        printf 'image_registry=%s\n' "$image_registry"
        printf 'image_count=%s\n' "$image_count"
        printf 'git_commit=%s\n' "$git_commit"
        printf 'git_dirty=%s\n' "$git_dirty"
    } > "$manifest"
}

write_readme() {
    local readme="$1"
    local package_archive="$2"
    local package_dir="$3"
    local zenvis_version="$4"
    local vectum_version="$5"
    local created_at="$6"
    local image_count="$7"

    cat > "$readme" <<EOF
# ZenVis 离线部署包

- ZenVis：${zenvis_version}
- Vectum：${vectum_version}
- 架构：${ARCH}
- 生成时间：${created_at}
- 镜像数量：${image_count}

## 目标机要求

- Linux 或 macOS 的 Bash 环境；
- Docker Engine 24+，以及 Docker Compose v2；
- OpenSSL、tar 和 gzip；
- 不需要 Git、curl 或外网。

## 安装

\`\`\`bash
tar -xzf ${package_archive}
cd ${package_dir}
./quick-deploy.sh
\`\`\`

\`quick-deploy.sh\` 会先导入 \`offline/images.tar\`，校验清单中的全部镜像，然后执行
\`compose init\` 和 \`compose up\`，最后等待所有服务健康。

安装过程会重新生成 \`deploy/.env\` 和全部随机凭据，终端只显示一次内置账号密码。
离线包不包含真实凭据，也不包含运行数据。

## 注意事项

- 目标机 CPU 架构必须为 \`${ARCH}\`，否则 \`quick-deploy.sh\` 会在导入镜像前终止；
- 包内只包含 Git 跟踪的部署配置，不包含本机插件展开缓存、自定义 HTML 页面和插件包；
- 迁移已有环境时，先恢复数据库、对象存储和 \`open_config\`，再单独迁移原 \`deploy/.env\`；
- 需要安装插件时，将插件包放入 \`deploy/open_config/plugin-package_config/bootstrap/\` 后再启动。
EOF
}

validate_stage() {
    local stage="$1"
    local env_example="${stage}/deploy/.env.example"
    local manifest="${stage}/offline/manifest.env"

    [[ -f "${stage}/quick-deploy.sh" ]] || die "离线包缺少 quick-deploy.sh"
    [[ -f "${stage}/zenvisctl" ]] || die "离线包缺少 zenvisctl"
    [[ -f "${stage}/deploy/docker-compose.yml" ]] || die "离线包缺少 docker-compose.yml"
    [[ -f "${stage}/offline/images.tar" ]] || die "离线包缺少 offline/images.tar"
    [[ -f "${stage}/offline/images.list" ]] || die "离线包缺少 offline/images.list"
    [[ -f "$manifest" ]] || die "离线包缺少 offline/manifest.env"
    [[ ! -e "${stage}/deploy/.env" ]] || die "离线包不应包含 deploy/.env"

    [[ "$(env_value "$env_example" ARCH)" == "$ARCH" ]] \
        || die "包内 .env.example 的 ARCH 与目标架构不一致"
    [[ "$(env_value "$env_example" ZENVIS_VERSION)" == "$(env_value "$manifest" zenvis_version)" ]] \
        || die "包内 .env.example 的 ZENVIS_VERSION 与 manifest 不一致"
    [[ "$(env_value "$env_example" VECTUM_VERSION)" == "$(env_value "$manifest" vectum_version)" ]] \
        || die "包内 .env.example 的 VECTUM_VERSION 与 manifest 不一致"
    [[ "$(env_value "$env_example" ZENVIS_IMAGE_REGISTRY)" == "$(env_value "$manifest" image_registry)" ]] \
        || die "包内 .env.example 的 ZENVIS_IMAGE_REGISTRY 与 manifest 不一致"
}

main() {
    local source_env default_name output_file output_dir package_dir package_name
    local zenvis_version vectum_version image_registry git_commit git_dirty
    local created_at image_count total_size tracked_list missing_file wrong_file to_pull
    local image save_args=()

    while [[ "$#" -gt 0 ]]; do
        case "$1" in
            --arch)
                [[ "$#" -ge 2 ]] || die "--arch 需要参数"
                ARCH="$2"
                shift 2
                ;;
            --pull) PULL=true; shift ;;
            --output)
                [[ "$#" -ge 2 ]] || die "--output 需要参数"
                OUTPUT="$2"
                shift 2
                ;;
            --force) FORCE=true; shift ;;
            --keep-work) KEEP_WORK=true; shift ;;
            -h|--help) usage; exit 0 ;;
            *) die "未知参数：$1" ;;
        esac
    done

    [[ -f "$COMPOSE_FILE" ]] || die "缺少 Compose 文件：$COMPOSE_FILE"
    [[ -f "$ENV_EXAMPLE" ]] || die "缺少环境模板：$ENV_EXAMPLE"
    [[ -f "$ZENVISCTL" ]] || die "缺少部署工具：$ZENVISCTL"
    [[ -f "$QUICK_DEPLOY" ]] || die "缺少快速部署脚本：$QUICK_DEPLOY"

    require_command docker
    require_command git
    require_command tar
    require_command gzip
    docker info >/dev/null 2>&1 || die "Docker 服务不可用"
    docker compose version >/dev/null 2>&1 || die "需要 Docker Compose v2"

    if [[ -z "$ARCH" ]]; then
        if [[ -f "$ENV_FILE" ]]; then
            ARCH="$(env_value "$ENV_FILE" ARCH)"
        fi
        [[ -n "$ARCH" ]] || ARCH="$(detect_architecture)"
    fi
    case "$ARCH" in
        amd64|arm64) ;;
        *) die "ARCH 只支持 amd64 或 arm64：$ARCH" ;;
    esac

    source_env="$ENV_FILE"
    [[ -f "$source_env" ]] || source_env="$ENV_EXAMPLE"

    zenvis_version="$(env_value "$source_env" ZENVIS_VERSION)"
    [[ -n "$zenvis_version" ]] || zenvis_version=latest
    vectum_version="$(env_value "$source_env" VECTUM_VERSION)"
    [[ -n "$vectum_version" ]] || vectum_version=latest
    image_registry="$(env_value "$source_env" ZENVIS_IMAGE_REGISTRY)"
    [[ -n "$image_registry" ]] || die "ZENVIS_IMAGE_REGISTRY 为空，无法解析镜像"

    default_name="zenvis-offline-${zenvis_version}-${ARCH}-$(date '+%Y%m%d-%H%M%S').tar.gz"
    output_file="$(resolve_output "$OUTPUT" "$default_name")"
    output_dir="$(dirname "$output_file")"
    if [[ -e "$output_file" && "$FORCE" != true ]]; then
        die "输出文件已存在：${output_file}；使用 --force 覆盖"
    fi

    package_name="$(basename "$output_file")"
    package_name="${package_name%.tar.gz}"
    package_name="${package_name%.tgz}"
    WORK_ROOT="$(mktemp -d "${output_dir}/.${package_name}.work.XXXXXX")"
    trap cleanup EXIT
    package_dir="${WORK_ROOT}/${package_name}"
    mkdir -p "${package_dir}/deploy" "${package_dir}/offline"

    info "目标版本：ZenVis ${zenvis_version}，Vectum ${vectum_version}，架构：${ARCH}"
    info "解析 Compose 镜像清单……"
    prepare_compose_env "$source_env" "${WORK_ROOT}/compose.env"
    if ! docker compose --env-file "${WORK_ROOT}/compose.env" -f "$COMPOSE_FILE" config --images \
        | sed '/^[[:space:]]*$/d' | sort -u > "${WORK_ROOT}/images.list"; then
        die "解析 Compose 镜像清单失败"
    fi
    image_count="$(wc -l < "${WORK_ROOT}/images.list" | tr -d ' ')"
    [[ "$image_count" -gt 0 ]] || die "Compose 未解析到任何镜像"

    missing_file="${WORK_ROOT}/missing-images.txt"
    wrong_file="${WORK_ROOT}/wrong-arch-images.txt"
    to_pull="${WORK_ROOT}/images-to-pull.txt"
    validate_images "${WORK_ROOT}/images.list" "$missing_file" "$wrong_file"
    if [[ -s "$missing_file" || -s "$wrong_file" ]]; then
        if [[ "$PULL" == true ]]; then
            pull_images "$missing_file" "$wrong_file" "$to_pull"
            validate_images "${WORK_ROOT}/images.list" "$missing_file" "$wrong_file"
        fi
        if [[ -s "$missing_file" || -s "$wrong_file" ]]; then
            report_image_problems "$missing_file" "$wrong_file"
            die "离线镜像准备不完整"
        fi
    fi

    total_size="$(image_total_size "${WORK_ROOT}/images.list")"
    check_disk_space "$total_size" "$output_dir"

    info "复制离线部署配置……"
    cp -p "$QUICK_DEPLOY" "${package_dir}/quick-deploy.sh"
    cp -p "$ZENVISCTL" "${package_dir}/zenvisctl"
    chmod 755 "${package_dir}/quick-deploy.sh" "${package_dir}/zenvisctl"
    tracked_list="${WORK_ROOT}/tracked-files.list"
    copy_tracked_config "$package_dir" "$tracked_list"

    set_env_value "${package_dir}/deploy/.env.example" ARCH "$ARCH"
    set_env_value "${package_dir}/deploy/.env.example" ZENVIS_VERSION "$zenvis_version"
    set_env_value "${package_dir}/deploy/.env.example" VECTUM_VERSION "$vectum_version"
    set_env_value "${package_dir}/deploy/.env.example" ZENVIS_IMAGE_REGISTRY "$image_registry"
    chmod 644 "${package_dir}/deploy/.env.example"

    cp "${WORK_ROOT}/images.list" "${package_dir}/offline/images.list"
    info "导出 ${image_count} 个镜像，首次打包可能需要数分钟……"
    while IFS= read -r image; do
        [[ -n "$image" ]] || continue
        save_args+=("$image")
    done < "${package_dir}/offline/images.list"
    docker save -o "${package_dir}/offline/images.tar" "${save_args[@]}" \
        || die "镜像导出失败"
    [[ -s "${package_dir}/offline/images.tar" ]] || die "镜像导出结果为空"

    created_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    git_commit="$(git -C "$SCRIPT_DIR" rev-parse --short HEAD 2>/dev/null || printf 'unknown')"
    if [[ -n "$(git -C "$SCRIPT_DIR" status --porcelain --untracked-files=no -- \
        deploy quick-deploy.sh zenvisctl)" ]]; then
        git_dirty=true
    else
        git_dirty=false
    fi
    write_manifest "${package_dir}/offline/manifest.env" "$created_at" "$image_count" \
        "$zenvis_version" "$vectum_version" "$image_registry" "$git_commit" "$git_dirty"
    write_readme "${package_dir}/README-OFFLINE.md" "$(basename "$output_file")" \
        "$package_name" "$zenvis_version" "$vectum_version" "$created_at" "$image_count"
    validate_stage "$package_dir"

    info "压缩离线包……"
    PARTIAL_OUTPUT="${output_file}.partial.$$"
    COPYFILE_DISABLE=1 tar -cf - -C "$WORK_ROOT" "$package_name" | gzip -1 > "$PARTIAL_OUTPUT"
    gzip -t "$PARTIAL_OUTPUT" || die "压缩包校验失败"
    mv -f "$PARTIAL_OUTPUT" "$output_file"
    PARTIAL_OUTPUT=""

    info "离线部署包已生成：$output_file"
    info "文件大小：$(du -h "$output_file" | awk '{ print $1 }')"
    info "目标机执行：tar -xzf $(basename "$output_file") && cd ${package_name} && ./quick-deploy.sh"
}

main "$@"
