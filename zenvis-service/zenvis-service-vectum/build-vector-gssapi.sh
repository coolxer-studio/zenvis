#!/bin/bash
# Build a Vector release with Kerberos/GSSAPI support for the current Linux architecture.
#
# The official musl builds do not enable librdkafka's GSSAPI provider, so Vectum
# needs its own glibc build. The primary build uses Vector's `vendored` feature
# (libsasl2 is bundled into the binary); if that fails we fall back to the
# dynamic `gssapi` feature and the runtime image must provide
# libsasl2-modules-gssapi-mit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VECTOR_VERSION="${VECTOR_VERSION:-0.62.0}"
VECTOR_REPO="${VECTOR_REPO:-https://github.com/vectordotdev/vector.git}"
VECTOR_BUILD_ROOT="${VECTOR_BUILD_ROOT:-${SCRIPT_DIR}/vector/.build}"
VECTOR_SOURCE="${VECTOR_SOURCE:-${VECTOR_BUILD_ROOT}/vector-src}"
VECTOR_CACHE="${VECTOR_CACHE:-${VECTOR_BUILD_ROOT}/cache}"
VECTOR_OUTPUT="${VECTOR_OUTPUT:-${SCRIPT_DIR}/vector/vector/bin/vector}"
# auto: 先尝试 vendored（自带 libsasl2/krb5），失败后回退 dynamic；
# vendored: 只构建静态版本；dynamic: 只构建依赖系统 libsasl2 的版本。
GSSAPI_MODE="${VECTOR_GSSAPI_MODE:-auto}"
# 16GB 内存机器上 Vector 的 fat LTO 链接会被 OOM kill；默认关闭 LTO。
# 可选 false / thin / fat。
VECTOR_LTO="${VECTOR_LTO:-false}"

case "$(uname -m)" in
    arm64|aarch64)
        RUST_TARGET="aarch64-unknown-linux-gnu"
        BASE_IMAGE_ARCH="arm64"
        PLATFORM="linux/arm64"
        ;;
    x86_64|amd64)
        RUST_TARGET="x86_64-unknown-linux-gnu"
        BASE_IMAGE_ARCH="amd64"
        PLATFORM="linux/amd64"
        ;;
    *)
        echo "不支持的构建架构: $(uname -m)" >&2
        exit 1
        ;;
esac

BUILDER_IMAGE="${VECTOR_BUILDER_IMAGE:-crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/eclipse-temurin:17-jdk-${BASE_IMAGE_ARCH}}"

mkdir -p "$VECTOR_BUILD_ROOT" "$VECTOR_CACHE/cargo" "$VECTOR_CACHE/target" "$(dirname "$VECTOR_OUTPUT")"

if [ ! -d "$VECTOR_SOURCE/.git" ]; then
    echo ">>> 克隆 Vector v${VECTOR_VERSION} 到 ${VECTOR_SOURCE}"
    rm -rf "$VECTOR_SOURCE"
    git clone --depth 1 --branch "v${VECTOR_VERSION}" "$VECTOR_REPO" "$VECTOR_SOURCE"
else
    echo ">>> 复用 Vector 源码: ${VECTOR_SOURCE}"
fi

echo ">>> 构建 Vector ${VECTOR_VERSION} (${RUST_TARGET}, GSSAPI)"
docker run --rm \
    --platform "$PLATFORM" \
    -v "${VECTOR_SOURCE}:/src/vector" \
    -v "${VECTOR_CACHE}/cargo:/root/.cargo" \
    -v "${VECTOR_CACHE}/target:/src/vector/target" \
    -v "${SCRIPT_DIR}:/work" \
    -w /src/vector \
    -e VECTOR_RUST_TARGET="$RUST_TARGET" \
    -e GSSAPI_MODE="$GSSAPI_MODE" \
    -e VECTOR_LTO="$VECTOR_LTO" \
    "$BUILDER_IMAGE" \
    bash -lc '
        set -euo pipefail
        apt-get update
        apt-get install -y --no-install-recommends \
            ca-certificates curl git build-essential pkg-config libssl-dev \
            libsasl2-dev libkrb5-dev cmake perl python3 protobuf-compiler clang
        git config --global --add safe.directory /src/vector
        if ! command -v cargo >/dev/null 2>&1; then
            curl --proto "=https" --tlsv1.2 -sSf https://sh.rustup.rs \
                | sh -s -- -y --profile minimal
        fi
        . "$HOME/.cargo/env"
        case "$VECTOR_LTO" in
          false)
            export CARGO_PROFILE_RELEASE_LTO=false
            export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=16
            ;;
          thin)
            export CARGO_PROFILE_RELEASE_LTO=thin
            export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=8
            ;;
          fat) ;;
          *)
            echo "不支持的 VECTOR_LTO: $VECTOR_LTO" >&2
            exit 1
            ;;
        esac
        rustup show
        case "$GSSAPI_MODE" in
          dynamic)
            echo ">>> 按 VECTOR_GSSAPI_MODE=dynamic 构建"
            cargo build --release --no-default-features \
                --features "target-${VECTOR_RUST_TARGET},gssapi"
            echo "dynamic" > /work/vector/.build/gssapi-mode
            ;;
          vendored)
            echo ">>> 按 VECTOR_GSSAPI_MODE=vendored 构建"
            cargo build --release --no-default-features \
                --features "target-${VECTOR_RUST_TARGET},vendored"
            echo "vendored" > /work/vector/.build/gssapi-mode
            ;;
          auto)
            echo ">>> 尝试 vendored GSSAPI 构建"
            if cargo build --release --no-default-features \
                --features "target-${VECTOR_RUST_TARGET},vendored"; then
                echo "vendored" > /work/vector/.build/gssapi-mode
            else
                echo ">>> vendored 构建失败，回退动态 GSSAPI 构建"
                cargo build --release --no-default-features \
                    --features "target-${VECTOR_RUST_TARGET},gssapi"
                echo "dynamic" > /work/vector/.build/gssapi-mode
            fi
            ;;
          *)
            echo "不支持的 VECTOR_GSSAPI_MODE: $GSSAPI_MODE" >&2
            exit 1
            ;;
        esac
        cp target/release/vector /work/vector/vector/bin/vector
    '

chmod +x "$VECTOR_OUTPUT"
echo ">>> 在 Linux 容器内校验 Vector 二进制"
# macOS 宿主机无法直接执行 Linux 二进制，统一放进目标平台容器验证。
docker run --rm --platform "$PLATFORM" \
    -v "${VECTOR_OUTPUT}:/usr/local/bin/vector:ro" \
    "$BUILDER_IMAGE" \
    bash -lc '
        set -euo pipefail
        apt-get update >/dev/null
        apt-get install -y --no-install-recommends \
            libsasl2-2 libsasl2-modules-gssapi-mit krb5-user >/dev/null
        /usr/local/bin/vector --version
    '

TMP_CONFIG="$(mktemp)"
cat > "$TMP_CONFIG" <<'EOF'
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
CHECK_OUTPUT="$(docker run --rm --platform "$PLATFORM" \
    -v "${VECTOR_OUTPUT}:/usr/local/bin/vector:ro" \
    -v "${TMP_CONFIG}:/tmp/vector-gssapi-check.yaml:ro" \
    "$BUILDER_IMAGE" \
    bash -lc '
        apt-get update >/dev/null
        apt-get install -y --no-install-recommends \
            libsasl2-2 libsasl2-modules-gssapi-mit krb5-user >/dev/null
        timeout 8 vector --config /tmp/vector-gssapi-check.yaml 2>&1
    ' 2>&1)"
CHECK_STATUS=$?
set -e
rm -f "$TMP_CONFIG"

if printf '%s' "$CHECK_OUTPUT" | grep -q "No provider for SASL mechanism GSSAPI"; then
    echo "构建出的 Vector 仍不支持 GSSAPI：" >&2
    printf '%s\n' "$CHECK_OUTPUT" >&2
    exit 1
fi
if [ "$CHECK_STATUS" -eq 0 ]; then
    echo "GSSAPI 探测意外成功退出（未连接 broker 时不应成功），请检查测试配置。" >&2
    exit 1
fi

echo ">>> Vector GSSAPI 构建完成: ${VECTOR_OUTPUT}"
