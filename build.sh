#!/bin/bash

IMAGE_NAME="crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/zenvis-backend"
IMAGE_TAG="1.0.1.alpha"
DATE_TAG=$(date +%Y%m%d)
PUSH_IMAGE="${PUSH_IMAGE:-false}"

# 检测系统架构
ARCH=$(uname -m)
echo "检测到系统架构: ${ARCH}"

# 根据架构确定 vector 下载包名称
if [ "$ARCH" = "arm64" ]; then
    BASE_IMAGE_ARCH="arm64"
elif [ "$ARCH" = "x86_64" ]; then
    BASE_IMAGE_ARCH="amd64"
else
    echo "不支持的架构: ${ARCH}"
    exit 1
fi

echo "========== 开始构建zenvis-backend项目 =========="

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
