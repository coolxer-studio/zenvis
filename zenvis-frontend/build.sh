#!/bin/bash

IMAGE_NAME="crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/zenvis-frontend"
DATE_TAG=$(date +%Y%m%d)
PUSH_IMAGE="${PUSH_IMAGE:-false}"

# 读取 ZENVIS_VERSION：优先 deploy/.env，回退 deploy/.env.example
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../deploy/.env"
ENV_EXAMPLE="${SCRIPT_DIR}/../deploy/.env.example"

if [ -f "$ENV_FILE" ]; then
    set -a; source "$ENV_FILE"; set +a
elif [ -f "$ENV_EXAMPLE" ]; then
    set -a; source "$ENV_EXAMPLE"; set +a
fi

IMAGE_TAG="${ZENVIS_VERSION:-1.0.0}"

# 同步 package.json version 为 IMAGE_TAG
CURRENT_PKG_VERSION=$(node -e "console.log(require('${SCRIPT_DIR}/package.json').version)" 2>/dev/null || echo "0.0.0")
if [ "$CURRENT_PKG_VERSION" != "$IMAGE_TAG" ]; then
    node -e "
const fs = require('fs');
const pkgPath = '${SCRIPT_DIR}/package.json';
const pkg = JSON.parse(fs.readFileSync(pkgPath));
pkg.version = '${IMAGE_TAG}';
fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n');
"
    echo "已同步 package.json version : ${CURRENT_PKG_VERSION} → ${IMAGE_TAG}"
fi

echo "========== 开始构建 zenvis-frontend (版本 ${IMAGE_TAG}) =========="

# 检测系统架构
ARCH=$(uname -m)
echo "检测到系统架构: ${ARCH}"

# 根据架构确定基础镜像
if [ "$ARCH" = "arm64" ]; then
    BASE_IMAGE_ARCH="arm64"
elif [ "$ARCH" = "x86_64" ]; then
    BASE_IMAGE_ARCH="amd64"
else
    echo "不支持的架构: ${ARCH}"
    exit 1
fi

echo "1. 执行 yarn 打包..."
cd "${SCRIPT_DIR}" && yarn build:pro
if [ $? -ne 0 ]; then
    echo "yarn 打包失败!"
    exit 1
fi

echo "2. yarn 打包成功!"

echo "3. 构建 Docker 镜像..."
docker build \
    --build-arg BASE_IMAGE_ARCH=${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH} \
    "${SCRIPT_DIR}"

if [ $? -ne 0 ]; then
    echo "Docker 构建失败!"
    exit 1
fi

echo "4. Docker 镜像构建成功!"
echo "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}"
echo "日期标签: ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}"
echo "架构: ${BASE_IMAGE_ARCH}"

if [ "${PUSH_IMAGE}" = "true" ]; then
    echo "5. 推送镜像到 Docker Registry..."
    docker push ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}
    docker push ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}
    echo "6. 镜像推送成功!"
else
    echo "5. 跳过镜像推送 (PUSH_IMAGE=false)"
fi
