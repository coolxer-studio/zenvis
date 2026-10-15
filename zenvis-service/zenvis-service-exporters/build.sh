#!/bin/bash
#
# ZenVis Exporters 镜像构建脚本。
# 按当前系统架构下载各 Exporter 官方发行版二进制到 ./dist，再构建镜像。
# 参考 zenvis-service-vectum/build.sh 的架构与推送约定。
#
set -euo pipefail

IMAGE_NAME="crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/zenvis-service-exporters"
IMAGE_TAG="1.0.0"
DATE_TAG="$(date +%Y%m%d)"
PUSH_IMAGE="${PUSH_IMAGE:-false}"

# Exporter 版本（升级时同步修改资源命名模板）
MYSQLD_EXPORTER_VERSION="0.18.0"
REDIS_EXPORTER_VERSION="1.86.0"
KAFKA_EXPORTER_VERSION="1.9.0"

DIST_DIR="./dist"
TARBALL_DIR="${DIST_DIR}/tarballs"

echo "========== 开始构建 zenvis-service-exporters =========="

case "$(uname -m)" in
    x86_64|amd64)  BASE_IMAGE_ARCH="amd64" ;;
    arm64|aarch64) BASE_IMAGE_ARCH="arm64" ;;
    *) echo "不支持的架构: $(uname -m)"; exit 1 ;;
esac
echo "检测到系统架构: $(uname -m) -> ${BASE_IMAGE_ARCH}"

mkdir -p "${DIST_DIR}" "${TARBALL_DIR}"

fetch() { # <url> <dest>
    local url="$1" dest="$2"
    if [[ -f "${dest}" ]]; then
        echo "本地已存在 $(basename "${dest}")，跳过下载"
    else
        echo "下载 ${url}"
        curl -fL --retry 3 -o "${dest}" "${url}"
    fi
}

extract_bin() { # <tarball> <member> <out_basename>
    local tarball="$1" member="$2" out="$3"
    echo "解压 ${member} -> ${DIST_DIR}/${out}"
    tar -xzf "${tarball}" -C "${DIST_DIR}" --strip-components=1 "${member}"
    chmod 0755 "${DIST_DIR}/${out}"
}

# ---------- mysqld_exporter ----------
MYSQLD_ASSET="mysqld_exporter-${MYSQLD_EXPORTER_VERSION}.linux-${BASE_IMAGE_ARCH}"
MYSQLD_TAR="${TARBALL_DIR}/${MYSQLD_ASSET}.tar.gz"
fetch "https://github.com/prometheus/mysqld_exporter/releases/download/v${MYSQLD_EXPORTER_VERSION}/${MYSQLD_ASSET}.tar.gz" "${MYSQLD_TAR}"
extract_bin "${MYSQLD_TAR}" "${MYSQLD_ASSET}/mysqld_exporter" "mysqld_exporter"

# ---------- redis_exporter ----------
REDIS_ASSET="redis_exporter-v${REDIS_EXPORTER_VERSION}.linux-${BASE_IMAGE_ARCH}"
REDIS_TAR="${TARBALL_DIR}/${REDIS_ASSET}.tar.gz"
fetch "https://github.com/oliver006/redis_exporter/releases/download/v${REDIS_EXPORTER_VERSION}/${REDIS_ASSET}.tar.gz" "${REDIS_TAR}"
extract_bin "${REDIS_TAR}" "${REDIS_ASSET}/redis_exporter" "redis_exporter"

# ---------- kafka_exporter ----------
KAFKA_ASSET="kafka_exporter-${KAFKA_EXPORTER_VERSION}.linux-${BASE_IMAGE_ARCH}"
KAFKA_TAR="${TARBALL_DIR}/${KAFKA_ASSET}.tar.gz"
fetch "https://github.com/danielqsj/kafka_exporter/releases/download/v${KAFKA_EXPORTER_VERSION}/${KAFKA_ASSET}.tar.gz" "${KAFKA_TAR}"
extract_bin "${KAFKA_TAR}" "${KAFKA_ASSET}/kafka_exporter" "kafka_exporter"

echo "=== Exporter 二进制准备完毕 ==="
ls -l "${DIST_DIR}"

echo "构建 Docker 镜像 (架构: ${BASE_IMAGE_ARCH})..."
docker build \
    --build-arg BASE_IMAGE_ARCH="${BASE_IMAGE_ARCH}" \
    -t "${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}" \
    -t "${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}" \
    .

if [[ "${PUSH_IMAGE}" == "true" ]]; then
    echo "推送镜像到 Docker Registry..."
    docker push "${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}"
    docker push "${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}"
else
    echo "跳过镜像推送 (PUSH_IMAGE=false)"
fi

echo "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}"
echo "日期标签: ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}"
echo "========== 构建完成 =========="
