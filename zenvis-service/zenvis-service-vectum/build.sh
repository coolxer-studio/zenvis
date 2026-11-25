#!/bin/bash
set -euo pipefail

IMAGE_NAME="crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/zenvis-service-vectum"
IMAGE_TAG="${IMAGE_TAG:-1.0.3}"
DATE_TAG=$(date +%Y%m%d)
VECTOR_VERSION="${VECTOR_VERSION:-0.62.0}"
PUSH_IMAGE="${PUSH_IMAGE:-false}"

echo "========== 开始构建vectum项目 =========="

ARCH=$(uname -m)
echo "检测到系统架构: ${ARCH}"

if [ "$ARCH" = "arm64" ]; then
    BASE_IMAGE_ARCH="arm64"
elif [ "$ARCH" = "x86_64" ]; then
    BASE_IMAGE_ARCH="amd64"
else
    echo "不支持的架构: ${ARCH}"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VECTOR_BIN="${SCRIPT_DIR}/vector/vector/bin/vector"

if [ "${REBUILD_VECTOR:-false}" = "true" ] || [ ! -x "$VECTOR_BIN" ]; then
    echo ">>> 构建支持 GSSAPI 的 Vector ${VECTOR_VERSION}"
    VECTOR_VERSION="$VECTOR_VERSION" "${SCRIPT_DIR}/build-vector-gssapi.sh"
fi

if [ ! -x "$VECTOR_BIN" ]; then
    echo "缺少 Vector 可执行文件: ${VECTOR_BIN}" >&2
    echo "请先执行 ./build-vector-gssapi.sh" >&2
    exit 1
fi

if ! LC_ALL=C grep -a -q "${VECTOR_VERSION}" "$VECTOR_BIN"; then
    echo "Vector 版本不是 ${VECTOR_VERSION}，请设置 REBUILD_VECTOR=true 重新构建。" >&2
    exit 1
fi

echo "1. 执行 Maven 打包..."
mvn clean package -DskipTests
echo "2. Maven 打包成功!"

echo "3. 构建 Docker 镜像 (架构: ${BASE_IMAGE_ARCH})..."
docker build \
    --build-arg BASE_IMAGE_ARCH=${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH} \
    -t ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH} \
    "${SCRIPT_DIR}"

VECTOR_IMAGE_VERSION="$(docker run --rm --platform "linux/${BASE_IMAGE_ARCH}" \
    --entrypoint /vector/bin/vector "${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}" --version)"
echo "镜像内 Vector 版本: ${VECTOR_IMAGE_VERSION}"
if ! printf '%s' "$VECTOR_IMAGE_VERSION" | grep -q "${VECTOR_VERSION}"; then
    echo "镜像内 Vector 版本与 ${VECTOR_VERSION} 不一致，构建失败。" >&2
    exit 1
fi

GSSAPI_CHECK_CONFIG="$(mktemp)"
cat > "$GSSAPI_CHECK_CONFIG" <<'EOF'
data_dir: /tmp/vector-gssapi-check
sources:
  gssapi_check:
    type: kafka
    bootstrap_servers: 127.0.0.1:1
    group_id: gssapi-check
    topics: ["gssapi-check"]
    librdkafka_options:
      security.protocol: SASL_PLAINTEXT
      sasl.mechanism: GSSAPI
      sasl.kerberos.service.name: kafka
      sasl.kerberos.principal: vectum@EXAMPLE.COM
      sasl.kerberos.keytab: /etc/krb5/nonexistent.keytab
sinks:
  out:
    type: console
    inputs: ["gssapi_check"]
    encoding:
      codec: json
EOF
set +e
GSSAPI_CHECK_OUTPUT="$(docker run --rm -v "${GSSAPI_CHECK_CONFIG}:/tmp/gssapi-check.yaml:ro" \
    --entrypoint /usr/bin/timeout \
    "${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}" \
    8 /vector/bin/vector --config /tmp/gssapi-check.yaml 2>&1)"
set -e
rm -f "$GSSAPI_CHECK_CONFIG"
if printf '%s' "$GSSAPI_CHECK_OUTPUT" | grep -q "No provider for SASL mechanism GSSAPI"; then
    echo "镜像内 Vector 不支持 GSSAPI，构建失败。" >&2
    printf '%s\n' "$GSSAPI_CHECK_OUTPUT" >&2
    exit 1
fi

echo "4. Docker 镜像构建成功!"
echo "镜像名称: ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}"
echo "日期标签: ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}"

if [ "${PUSH_IMAGE}" = "true" ]; then
    echo "5. 推送镜像到 Docker Registry..."
    docker push ${IMAGE_NAME}:${IMAGE_TAG}-${BASE_IMAGE_ARCH}
    docker push ${IMAGE_NAME}:${DATE_TAG}-${BASE_IMAGE_ARCH}
    echo "6. 镜像推送成功!"
else
    echo "5. 跳过镜像推送 (PUSH_IMAGE=false)"
fi
