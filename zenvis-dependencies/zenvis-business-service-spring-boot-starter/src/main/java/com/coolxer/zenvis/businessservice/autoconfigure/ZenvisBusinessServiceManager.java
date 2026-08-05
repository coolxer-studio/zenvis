package com.coolxer.zenvis.businessservice.autoconfigure;

import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class ZenvisBusinessServiceManager implements BusinessServiceReporter, DisposableBean {

    static final String SERVICE_STARTED = "SERVICE_STARTED";
    static final String SERVICE_STOPPING = "SERVICE_STOPPING";

    private static final Logger log = LoggerFactory.getLogger(ZenvisBusinessServiceManager.class);
    private static final int EVENT_TYPE_MAX_LENGTH = 64;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int MESSAGE_MAX_LENGTH = 4000;
    private static final int TRACE_ID_MAX_LENGTH = 128;
    private static final int HEARTBEAT_METADATA_MAX_BYTES = 16 * 1024;
    private static final int EVENT_DATA_MAX_BYTES = 64 * 1024;
    private static final ZoneId DEFAULT_REPORTING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZenvisBusinessServiceProperties properties;
    private final ZenvisBusinessServiceIdentity identityProvider;
    private final ZenvisBusinessServiceTransport transport;
    private final TaskExecutor eventExecutor;
    private final TaskScheduler heartbeatScheduler;
    private final ObjectMapper objectMapper;
    private final ZoneId reportingZone;
    private final Object dispatchMonitor = new Object();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicBoolean startupEventReported = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final String startupEventId = UUID.randomUUID().toString();
    private final String stoppingEventId = UUID.randomUUID().toString();
    private volatile ScheduledFuture<?> heartbeatTask;

    ZenvisBusinessServiceManager(ZenvisBusinessServiceProperties properties,
                                 ZenvisBusinessServiceIdentity identityProvider,
                                 ZenvisBusinessServiceTransport transport,
                                 TaskExecutor eventExecutor,
                                 TaskScheduler heartbeatScheduler,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.identityProvider = identityProvider;
        this.transport = transport;
        this.eventExecutor = eventExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.objectMapper = objectMapper;
        this.reportingZone = resolveZone(properties.getTimeZone());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        startReporting();
    }

    void startReporting() {
        if (!properties.isEnabled() || !started.compareAndSet(false, true)) {
            return;
        }
        long intervalMillis = Math.max(1L, properties.getHeartbeatIntervalMillis());
        try {
            heartbeatTask = heartbeatScheduler.scheduleWithFixedDelay(
                    this::sendScheduledHeartbeat,
                    Instant.now(),
                    Duration.ofMillis(intervalMillis));
        } catch (RuntimeException e) {
            log.warn("Zenvis 心跳调度启动失败: {}", e.getMessage());
        }
    }

    void sendScheduledHeartbeat() {
        if (!properties.isEnabled() || stopping.get()) {
            return;
        }
        synchronized (dispatchMonitor) {
            if (stopping.get() || !sendHeartbeat(BusinessServiceStatus.UP, "ready")) {
                return;
            }
            if (!startupEventReported.get()) {
                BusinessServiceIdentity identity = identityProvider.current();
                BusinessServiceEventRequest startedEvent = buildEvent(
                        startupEventId,
                        SERVICE_STARTED,
                        BusinessServiceEventSeverity.INFO,
                        identity.serviceName() + " 服务已启动",
                        identity.serviceCode() + " 已启动并完成 Zenvis 心跳注册",
                        null,
                        portData(identity.port()));
                if (transport.reportEvent(startedEvent)) {
                    startupEventReported.set(true);
                    log.info("业务服务已注册到 Zenvis: serviceCode={}, instanceId={}",
                            identity.serviceCode(), identity.instanceId());
                }
            }
        }
    }

    @Override
    public void reportEvent(String eventType,
                            BusinessServiceEventSeverity severity,
                            String title,
                            String message,
                            String traceId,
                            Map<String, Object> data) {
        if (!properties.isEnabled() || stopping.get()) {
            return;
        }
        Map<String, Object> eventData = data == null ? null : new LinkedHashMap<>(data);
        try {
            eventExecutor.execute(() -> dispatchEvent(
                    eventType,
                    severity,
                    title,
                    message,
                    traceId,
                    eventData));
        } catch (RuntimeException e) {
            log.warn("Zenvis 事件队列已满或不可用，丢弃事件: eventType={}, reason={}",
                    eventType, e.getMessage());
        }
    }

    private void dispatchEvent(String eventType,
                               BusinessServiceEventSeverity severity,
                               String title,
                               String message,
                               String traceId,
                               Map<String, Object> data) {
        synchronized (dispatchMonitor) {
            if (stopping.get()) {
                return;
            }
            if (!registered.get() && !sendHeartbeat(BusinessServiceStatus.UP, "ready")) {
                log.warn("跳过 Zenvis 事件上报，实例尚未注册: eventType={}", eventType);
                return;
            }
            transport.reportEvent(buildEvent(
                    UUID.randomUUID().toString(),
                    eventType,
                    severity,
                    title,
                    message,
                    traceId,
                    data));
        }
    }

    @Override
    public void destroy() {
        stopReporting();
    }

    void stopReporting() {
        if (!properties.isEnabled() || !stopping.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> scheduled = heartbeatTask;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        synchronized (dispatchMonitor) {
            if (!registered.get()) {
                return;
            }
            BusinessServiceIdentity identity = identityProvider.current();
            transport.reportEvent(buildEvent(
                    stoppingEventId,
                    SERVICE_STOPPING,
                    BusinessServiceEventSeverity.INFO,
                    identity.serviceName() + " 服务正在停止",
                    identity.serviceCode() + " 正在正常关闭",
                    null,
                    portData(identity.port())));
            sendHeartbeat(BusinessServiceStatus.DOWN, "stopping");
            registered.set(false);
        }
    }

    private boolean sendHeartbeat(BusinessServiceStatus status, String statusMessage) {
        BusinessServiceIdentity identity = identityProvider.current();
        BusinessServiceHeartbeatRequest heartbeat = new BusinessServiceHeartbeatRequest(
                identity.serviceCode(),
                identity.serviceName(),
                identity.instanceId(),
                status,
                statusMessage,
                identity.version(),
                identity.environment(),
                identity.host(),
                identity.port(),
                identity.managementUrl(),
                now(),
                sanitizeData(identity.metadata(), HEARTBEAT_METADATA_MAX_BYTES, "heartbeat metadata"));
        boolean succeeded = transport.reportHeartbeat(heartbeat);
        if (succeeded) {
            registered.set(status != BusinessServiceStatus.DOWN);
        }
        return succeeded;
    }

    private BusinessServiceEventRequest buildEvent(String eventId,
                                                   String eventType,
                                                   BusinessServiceEventSeverity severity,
                                                   String title,
                                                   String message,
                                                   String traceId,
                                                   Map<String, Object> data) {
        BusinessServiceIdentity identity = identityProvider.current();
        String normalizedEventType = normalizeEventType(eventType);
        return new BusinessServiceEventRequest(
                eventId,
                identity.serviceCode(),
                identity.instanceId(),
                normalizedEventType,
                severity == null ? BusinessServiceEventSeverity.ERROR : severity,
                truncate(defaultIfBlank(title, normalizedEventType), TITLE_MAX_LENGTH),
                nullableTruncate(message, MESSAGE_MAX_LENGTH),
                now(),
                nullableTruncate(traceId, TRACE_ID_MAX_LENGTH),
                sanitizeData(data, EVENT_DATA_MAX_BYTES, "event data"));
    }

    private Map<String, Object> sanitizeData(Map<String, Object> data,
                                             int maxBytes,
                                             String description) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(data);
        try {
            if (objectMapper.writeValueAsBytes(copy).length <= maxBytes) {
                return copy;
            }
            return Map.of("truncated", true, "reason", description + " exceeded size limit");
        } catch (JsonProcessingException | RuntimeException e) {
            return Map.of("truncated", true, "reason", description + " could not be serialized");
        }
    }

    private static Map<String, Object> portData(Integer port) {
        return port == null ? null : Map.of("port", port);
    }

    private static String normalizeEventType(String eventType) {
        String normalized = defaultIfBlank(eventType, "UNKNOWN_EVENT")
                .trim()
                .replaceAll("[^A-Za-z0-9._:-]", "_");
        if (!Character.isLetterOrDigit(normalized.charAt(0))) {
            normalized = "EVENT_" + normalized;
        }
        return truncate(normalized, EVENT_TYPE_MAX_LENGTH);
    }

    private String now() {
        return LocalDateTime.now(reportingZone).format(DATE_TIME_FORMATTER);
    }

    private static ZoneId resolveZone(String zoneId) {
        if (!StringUtils.hasText(zoneId)) {
            return DEFAULT_REPORTING_ZONE;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (RuntimeException e) {
            log.warn("无效的 Zenvis 上报时区 {}，使用 Asia/Shanghai", zoneId);
            return DEFAULT_REPORTING_ZONE;
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String nullableTruncate(String value, int maxLength) {
        return StringUtils.hasText(value) ? truncate(value, maxLength) : null;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
