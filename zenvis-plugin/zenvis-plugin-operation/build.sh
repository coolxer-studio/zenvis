#!/usr/bin/env bash
set -euo pipefail

REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CONF_FILE="${REPO_DIR}/build.conf"

if [ ! -f "${CONF_FILE}" ]; then
    echo "缺少构建配置：${CONF_FILE}" >&2
    exit 1
fi

# shellcheck disable=SC1090
source "${CONF_FILE}"
: "${PLUGIN_DIR:?build.conf 必须声明 PLUGIN_DIR}"
: "${API_DIR:=}"
: "${API_ARTIFACT:=}"

REQUESTED_PLUGIN=${1:-${PLUGIN_DIR}}
if [ "${REQUESTED_PLUGIN}" != "${PLUGIN_DIR}" ]; then
    echo "当前仓库只支持构建 ${PLUGIN_DIR}，收到：${REQUESTED_PLUGIN}" >&2
    exit 1
fi

PLUGIN_PATH="${REPO_DIR}/${PLUGIN_DIR}"
INDEX_FILE="${PLUGIN_PATH}/index.json"
if [ ! -f "${INDEX_FILE}" ]; then
    echo "插件描述不存在：${INDEX_FILE}" >&2
    exit 1
fi

PLUGIN_VERSION=$(awk -F '"' '/"version"[[:space:]]*:/ { print $4; exit }' "${INDEX_FILE}")
PACKAGE_NAME=$(awk -F '"' '/"package_name"[[:space:]]*:/ { print $4; exit }' "${INDEX_FILE}")
if [ -z "${PLUGIN_VERSION}" ] || [ -z "${PACKAGE_NAME}" ]; then
    echo "无法从 index.json 读取 version 或 package_name" >&2
    exit 1
fi

if [ -n "${API_DIR}" ]; then
    API_PATH="${REPO_DIR}/${API_DIR}"
    POM_FILE="${API_PATH}/pom.xml"
    if [ ! -f "${POM_FILE}" ] || [ -z "${API_ARTIFACT}" ]; then
        echo "API 工程配置不完整：${API_PATH}" >&2
        exit 1
    fi

    API_VERSION=$(awk '
        /<parent>/ { in_parent=1 }
        /<\/parent>/ { in_parent=0; next }
        !in_parent && /<version>/ {
            line=$0
            sub(/^.*<version>/, "", line)
            sub(/<\/version>.*$/, "", line)
            print line
            exit
        }
    ' "${POM_FILE}")
    if [ "${API_VERSION}" != "${PLUGIN_VERSION}" ]; then
        echo "版本不一致：index.json=${PLUGIN_VERSION}，${API_DIR}/pom.xml=${API_VERSION}" >&2
        exit 1
    fi

    echo "构建动态 API：${API_ARTIFACT}:${API_VERSION}"
    mvn -f "${POM_FILE}" clean package

    BUILT_JAR="${API_PATH}/target/${API_ARTIFACT}-${API_VERSION}.jar"
    if [ ! -f "${BUILT_JAR}" ]; then
        echo "API 产物不存在：${BUILT_JAR}" >&2
        exit 1
    fi
    if command -v unzip >/dev/null 2>&1; then
        JAR_LIST=$(unzip -Z1 "${BUILT_JAR}")
    elif command -v jar >/dev/null 2>&1; then
        JAR_LIST=$(jar tf "${BUILT_JAR}")
    else
        echo "未找到可检查 JAR 的 unzip 或 jar" >&2
        exit 1
    fi
    if printf '%s\n' "${JAR_LIST}" | grep -q '^BOOT-INF/'; then
        echo "动态 API 必须是薄 JAR，不能包含 BOOT-INF" >&2
        exit 1
    fi
    if ! printf '%s\n' "${JAR_LIST}" | grep -Eq '^com/coolxer/plugin/.+\.class$'; then
        echo "动态 API JAR 未包含 com.coolxer.plugin 业务类" >&2
        exit 1
    fi

    mkdir -p "${PLUGIN_PATH}/03_api"
    find "${PLUGIN_PATH}/03_api" -maxdepth 1 -type f -name '*.jar' -delete
    cp "${BUILT_JAR}" "${PLUGIN_PATH}/03_api/"
fi

JAR_COUNT=$(find "${PLUGIN_PATH}/03_api" -maxdepth 1 -type f -name '*.jar' 2>/dev/null | wc -l | tr -d ' ')
if [ "${JAR_COUNT}" -gt 1 ]; then
    echo "03_api 根目录最多允许一个 JAR，当前为 ${JAR_COUNT}" >&2
    exit 1
fi

if command -v gtar >/dev/null 2>&1; then
    TAR_BIN=$(command -v gtar)
elif command -v tar >/dev/null 2>&1; then
    TAR_BIN=$(command -v tar)
else
    echo "未找到 tar 或 gtar" >&2
    exit 1
fi

ARCHIVE_NAME="${PACKAGE_NAME//./-}.tar.gz"
ARCHIVE_PATH="${REPO_DIR}/${ARCHIVE_NAME}"
rm -f "${ARCHIVE_PATH}"

PACKAGE_ENTRIES=()
while IFS= read -r -d '' ENTRY; do
    PACKAGE_ENTRIES+=("${ENTRY#"${PLUGIN_PATH}/"}")
done < <(find "${PLUGIN_PATH}" -mindepth 1 -maxdepth 1 \
    ! -name '.DS_Store' \
    ! -name '.git' \
    ! -name 'api-src' \
    ! -name 'target' \
    ! -name 'build.log' \
    ! -name '*.tar' \
    ! -name '*.tar.gz' \
    -print0)
if [ "${#PACKAGE_ENTRIES[@]}" -eq 0 ]; then
    echo "插件目录没有可打包内容：${PLUGIN_PATH}" >&2
    exit 1
fi

COPYFILE_DISABLE=1 "${TAR_BIN}" -czf "${ARCHIVE_PATH}" \
    --exclude='.DS_Store' \
    --exclude='.git' \
    --exclude='api-src' \
    --exclude='target' \
    --exclude='build.log' \
    --exclude='*.tar' \
    --exclude='*.tar.gz' \
    -C "${PLUGIN_PATH}" "${PACKAGE_ENTRIES[@]}"

ARCHIVE_LIST=$("${TAR_BIN}" -tzf "${ARCHIVE_PATH}")
if ! printf '%s\n' "${ARCHIVE_LIST}" | grep -Fxq 'index.json'; then
    echo "归档根目录缺少 index.json" >&2
    exit 1
fi
if printf '%s\n' "${ARCHIVE_LIST}" | grep -Eq '^(\.|\./)$|^\./'; then
    echo "归档包含 ZenVis 不接受的点目录条目" >&2
    exit 1
fi
if printf '%s\n' "${ARCHIVE_LIST}" | grep -Eq '(^|/)(\.git|api-src|target)(/|$)|(^|/)\.DS_Store$|build\.log$|\.tar\.gz$'; then
    echo "归档包含不允许发布的文件" >&2
    exit 1
fi

echo "构建完成：${ARCHIVE_PATH}"
