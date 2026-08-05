#!/bin/bash

IMAGE_NAME="crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/vectum"
IMAGE_TAG="latest"
#DATE_TAG=$(date +%Y%m%d)
DATE_TAG="1.0.0.alpha"
VECTOR_VERSION="0.55.0"
VECTOR_BASE_URL="https://packages.timber.io/vector/${VECTOR_VERSION}"
PUSH_IMAGE="${PUSH_IMAGE:-false}"

echo "========== 开始构建vectum项目 =========="

# 检测系统架构
ARCH=$(uname -m)
echo "检测到系统架构: ${ARCH}"

# 根据架构确定 vector 下载包名称
if [ "$ARCH" = "arm64" ]; then
    VECTOR_ARCH="aarch64-unknown-linux-musl"
    BASE_IMAGE_ARCH="arm64"
elif [ "$ARCH" = "x86_64" ]; then
    VECTOR_ARCH="x86_64-unknown-linux-musl"
    BASE_IMAGE_ARCH="amd64"
else
    echo "不支持的架构: ${ARCH}"
    exit 1
fi

VECTOR_PACKAGE="vector-${VECTOR_VERSION}-${VECTOR_ARCH}.tar.gz"
VECTOR_URL="${VECTOR_BASE_URL}/${VECTOR_PACKAGE}"
VECTOR_TAR_PATH="./vector/${VECTOR_PACKAGE}"
VECTOR_EXTRACT_DIR="./vector/vector"

echo "Vector 压缩包: ${VECTOR_PACKAGE}"
echo "下载地址: ${VECTOR_URL}"
echo "解压目标目录: ${VECTOR_EXTRACT_DIR}"

# 创建存放vector压缩包目录
mkdir -p ./vector

# 1. 下载压缩包（不存在才下载）
if [[ -f "${VECTOR_TAR_PATH}" ]]; then
    echo "本地已存在Vector压缩包，跳过下载"
else
    echo "开始下载Vector ${VECTOR_VERSION}..."
    curl -fL -o "${VECTOR_TAR_PATH}" "${VECTOR_URL}"
    echo "Vector下载完成"
fi

# 2. 判断解压目录已存在则跳过解压
if [[ -d "${VECTOR_EXTRACT_DIR}" ]]; then
    echo "检测到已解压vector目录，跳过解压步骤"
else
    echo "开始解压Vector压缩包..."
    mkdir -p ${VECTOR_EXTRACT_DIR}
    tar -xzf "${VECTOR_TAR_PATH}" --strip-components=2 -C ${VECTOR_EXTRACT_DIR}
    echo "Vector解压完成"
fi

echo "=== Vector环境准备完毕 ==="

echo "1. 执行 Maven 打包..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Maven 打包失败!"
    exit 1
fi

echo "2. Maven 打包成功!"

echo "3. 构建 Docker 镜像 (架构: ${BASE_IMAGE_ARCH})..."
docker build \
    --build-arg BASE_IMAGE_ARCH=${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH} .

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
