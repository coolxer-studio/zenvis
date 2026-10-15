# ZenVis 基础设施 Prometheus Exporter 集合容器
#
# 单容器内同时运行 MySQL、Redis（含 redis-stack 多目标）与 Kafka 的官方 Exporter。
# ClickHouse 与 MinIO 使用其内置的原生 Prometheus 指标端点（详见 README）。
#
# 二进制由 build.sh 按当前架构下载到 ./dist 后 COPY 进镜像，
# 基础镜像与二进制均按 BASE_IMAGE_ARCH（amd64 / arm64）区分。

ARG BASE_IMAGE_ARCH=none
FROM crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/alpine:3.20-${BASE_IMAGE_ARCH}

ARG BASE_IMAGE_ARCH
LABEL org.opencontainers.image.title="zenvis-service-exporters" \
      org.opencontainers.image.description="ZenVis infrastructure Prometheus exporters (MySQL / Redis / Redis-Stack / Kafka)" \
      org.opencontainers.image.arch="${BASE_IMAGE_ARCH}"

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

# supervisor 负责多进程托管，curl 用于健康检查，tzdata 用于时区。
RUN apk add --no-cache supervisor curl tzdata \
    && addgroup -S exporter \
    && adduser -S -G exporter -h /home/exporter exporter \
    && mkdir -p /etc/zenvis-exporters /var/log/zenvis-exporters \
    && chown -R exporter:exporter /etc/zenvis-exporters /var/log/zenvis-exporters

COPY dist/mysqld_exporter /usr/local/bin/mysqld_exporter
COPY dist/redis_exporter /usr/local/bin/redis_exporter
COPY dist/kafka_exporter /usr/local/bin/kafka_exporter
COPY config/mysqld_exporter.cnf.template /etc/zenvis-exporters/mysqld_exporter.cnf.template
COPY supervisord.conf.template /etc/zenvis-exporters/supervisord.conf.template
COPY scripts/entrypoint.sh /usr/local/bin/entrypoint.sh

RUN chmod 0755 /usr/local/bin/mysqld_exporter \
               /usr/local/bin/redis_exporter \
               /usr/local/bin/kafka_exporter \
               /usr/local/bin/entrypoint.sh \
    && chmod 0644 /etc/zenvis-exporters/mysqld_exporter.cnf.template \
                  /etc/zenvis-exporters/supervisord.conf.template

# 9104: mysqld_exporter  9121: redis_exporter  9308: kafka_exporter
EXPOSE 9104 9121 9308

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=5 \
    CMD ["sh", "-c", "supervisorctl -c /etc/zenvis-exporters/supervisord.conf status >/dev/null 2>&1 && ! supervisorctl -c /etc/zenvis-exporters/supervisord.conf status | grep -Eq 'FATAL|BACKOFF|EXITED|STOPPED'"]

USER exporter

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
