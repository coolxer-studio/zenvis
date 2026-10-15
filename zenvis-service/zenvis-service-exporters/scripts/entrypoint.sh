#!/usr/bin/env sh
#
# ZenVis Exporters 容器入口脚本。
# 职责：校验环境变量 -> 用环境变量渲染配置模板 -> 以 supervisord 拉起所有 exporter。
#
set -eu

CONFIG_DIR="/etc/zenvis-exporters"
SUPERVISORD_CONF="${CONFIG_DIR}/supervisord.conf"
MYSQLD_CNF="${CONFIG_DIR}/mysqld_exporter.cnf"

log() { printf '[entrypoint] %s\n' "$*"; }
die() { printf '[entrypoint][错误] %s\n' "$*" >&2; exit 1; }

# ---------- 默认值 ----------
: "${MYSQL_HOST:=mysql-service}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_EXPORTER_PORT:=9104}"
: "${REDIS_HOST:=redis-service}"
: "${REDIS_PORT:=6379}"
: "${REDIS_EXPORTER_PORT:=9121}"
: "${KAFKA_BOOTSTRAP_SERVERS:=kafka-service:9092}"
: "${KAFKA_EXPORTER_PORT:=9308}"
: "${TZ:=Asia/Shanghai}"
export TZ

# ---------- 校验凭据 ----------
if [ -z "${MYSQL_EXPORTER_PASSWORD:-}" ]; then
    if [ -n "${MYSQL_ROOT_PASSWORD:-}" ]; then
        MYSQL_EXPORTER_USER="${MYSQL_EXPORTER_USER:-root}"
        MYSQL_EXPORTER_PASSWORD="${MYSQL_ROOT_PASSWORD}"
    else
        die "缺少 MYSQL_EXPORTER_PASSWORD 或 MYSQL_ROOT_PASSWORD，无法连接 MySQL"
    fi
fi
: "${MYSQL_EXPORTER_USER:=root}"

[ -n "${REDIS_PASSWORD:-}" ] || die "缺少 REDIS_PASSWORD，无法连接 Redis / Redis-Stack"

# ---------- 模板渲染 ----------
# render <src> <dst> [TOKEN VALUE]...
# 使用 __TOKEN__ 占位符，并对替换值中的 \ & | 做转义，避免特殊字符破坏 sed 表达式。
render() {
    src="$1"
    dst="$2"
    shift 2
    cp "${src}" "${dst}"
    while [ "$#" -ge 2 ]; do
        token="$1"
        value="$2"
        shift 2
        escaped="$(printf '%s' "${value}" | sed -e 's/[\\&|]/\\&/g')"
        sed -i "s|${token}|${escaped}|g" "${dst}"
    done
}

render "${CONFIG_DIR}/mysqld_exporter.cnf.template" "${MYSQLD_CNF}" \
    "__MYSQL_EXPORTER_USER__" "${MYSQL_EXPORTER_USER}" \
    "__MYSQL_EXPORTER_PASSWORD__" "${MYSQL_EXPORTER_PASSWORD}"
chmod 0600 "${MYSQLD_CNF}"

render "${CONFIG_DIR}/supervisord.conf.template" "${SUPERVISORD_CONF}" \
    "__MYSQL_HOST__" "${MYSQL_HOST}" \
    "__MYSQL_PORT__" "${MYSQL_PORT}" \
    "__MYSQLD_EXPORTER_PORT__" "${MYSQL_EXPORTER_PORT}" \
    "__REDIS_HOST__" "${REDIS_HOST}" \
    "__REDIS_PORT__" "${REDIS_PORT}" \
    "__REDIS_PASSWORD__" "${REDIS_PASSWORD}" \
    "__REDIS_EXPORTER_PORT__" "${REDIS_EXPORTER_PORT}" \
    "__KAFKA_BOOTSTRAP_SERVERS__" "${KAFKA_BOOTSTRAP_SERVERS}" \
    "__KAFKA_EXPORTER_PORT__" "${KAFKA_EXPORTER_PORT}"

log "配置渲染完成"
log "  mysqld_exporter  : :${MYSQL_EXPORTER_PORT} -> ${MYSQL_HOST}:${MYSQL_PORT} (user=${MYSQL_EXPORTER_USER})"
log "  redis_exporter   : :${REDIS_EXPORTER_PORT} -> redis://${REDIS_HOST}:${REDIS_PORT}（多目标请用 /scrape?target=）"
log "  kafka_exporter   : :${KAFKA_EXPORTER_PORT} -> ${KAFKA_BOOTSTRAP_SERVERS}"
log "  ClickHouse/MinIO : 使用各自内置原生指标端点，由 Prometheus 直连（见 README）"
log "启动 supervisord..."

exec /usr/bin/supervisord -n -c "${SUPERVISORD_CONF}"
