package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import com.coolxer.commons.enums.BusinessServiceReportedStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.BusinessServiceEvent;
import com.coolxer.dao.mysql.entity.BusinessServiceInstance;
import com.coolxer.dao.mysql.repository.BusinessServiceEventRepository;
import com.coolxer.dao.mysql.repository.BusinessServiceInstanceRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.BusinessServiceEventDto;
import com.coolxer.model.system.dto.BusinessServiceEventSearchDto;
import com.coolxer.model.system.dto.BusinessServiceHeartbeatDto;
import com.coolxer.model.system.dto.BusinessServiceInstanceSearchDto;
import com.coolxer.model.system.vo.BusinessServiceEventAckVo;
import com.coolxer.model.system.vo.BusinessServiceEventVo;
import com.coolxer.model.system.vo.BusinessServiceHeartbeatAckVo;
import com.coolxer.model.system.vo.BusinessServiceInstanceVo;
import com.coolxer.model.system.vo.BusinessServiceSummaryVo;
import com.coolxer.service.system.BusinessServiceRegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class BusinessServiceRegistryServiceImpl implements BusinessServiceRegistryService {

    private static final int MAX_METADATA_BYTES = 16 * 1024;
    private static final int MAX_EVENT_DATA_BYTES = 64 * 1024;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private BusinessServiceInstanceRepository instanceRepository;

    @Autowired
    private BusinessServiceEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.business-service.offline-threshold-seconds:90}")
    private long offlineThresholdSeconds;

    @Value("${app.business-service.event-retention-days:30}")
    private long eventRetentionDays;

    @Value("${app.business-service.instance-retention-days:30}")
    private long instanceRetentionDays;

    private Clock clock = Clock.systemDefaultZone();

    @Override
    public BusinessServiceHeartbeatAckVo reportHeartbeat(BusinessServiceHeartbeatDto dto, String remoteAddress) {
        Date receivedAt = now();
        String metadata = serializeMap(dto.getMetadata(), MAX_METADATA_BYTES, "metadata");
        String serviceCode = dto.getServiceCode().trim();
        String instanceId = dto.getInstanceId().trim();

        Optional<BusinessServiceInstance> existing = instanceRepository
                .findByServiceCodeAndInstanceId(serviceCode, instanceId);
        boolean registered = existing.isEmpty();
        BusinessServiceInstance instance = existing.orElseGet(BusinessServiceInstance::new);
        applyHeartbeat(instance, dto, serviceCode, instanceId, metadata, remoteAddress, receivedAt, registered);

        try {
            instance = instanceRepository.saveAndFlush(instance);
        } catch (DataIntegrityViolationException duplicateRegistration) {
            instance = instanceRepository.findByServiceCodeAndInstanceId(serviceCode, instanceId)
                    .orElseThrow(() -> duplicateRegistration);
            registered = false;
            applyHeartbeat(instance, dto, serviceCode, instanceId, metadata, remoteAddress, receivedAt, false);
            instance = instanceRepository.save(instance);
        }

        BusinessServiceEffectiveStatus effectiveStatus = effectiveStatus(instance, receivedAt);
        log.debug("业务应用服务心跳已接收: serviceCode={}, instanceId={}, registered={}, status={}",
                serviceCode, instanceId, registered, effectiveStatus);
        return new BusinessServiceHeartbeatAckVo(
                serviceCode,
                instanceId,
                registered,
                receivedAt,
                effectiveStatus,
                normalizedOfflineThresholdSeconds());
    }

    @Override
    public BusinessServiceEventAckVo reportEvent(BusinessServiceEventDto dto, String remoteAddress) {
        String eventId = dto.getEventId().trim();
        String serviceCode = dto.getServiceCode().trim();
        String instanceId = dto.getInstanceId().trim();
        String data = serializeMap(dto.getData(), MAX_EVENT_DATA_BYTES, "data");

        Optional<BusinessServiceEvent> existing = eventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            return duplicateEventAck(existing.get(), serviceCode, instanceId);
        }

        BusinessServiceInstance instance = instanceRepository.findByServiceCodeAndInstanceId(serviceCode, instanceId)
                .orElseThrow(() -> new ApiException(404, "业务应用服务实例尚未通过心跳注册"));
        Date acceptedAt = now();
        BusinessServiceEvent event = new BusinessServiceEvent()
                .setEventId(eventId)
                .setServiceInstanceId(instance.getId())
                .setServiceCode(serviceCode)
                .setInstanceId(instanceId)
                .setEventType(dto.getEventType().trim())
                .setSeverity(dto.getSeverity())
                .setTitle(dto.getTitle().trim())
                .setMessage(StringUtils.trimToNull(dto.getMessage()))
                .setOccurredTime(dto.getOccurredAt() == null ? acceptedAt : dto.getOccurredAt())
                .setTraceId(StringUtils.trimToNull(dto.getTraceId()))
                .setData(data)
                .setRemoteAddress(normalizeRemoteAddress(remoteAddress));

        try {
            event = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException duplicateEvent) {
            BusinessServiceEvent persisted = eventRepository.findByEventId(eventId)
                    .orElseThrow(() -> duplicateEvent);
            return duplicateEventAck(persisted, serviceCode, instanceId);
        }

        instance.setLastEventTime(acceptedAt);
        instanceRepository.save(instance);
        log.debug("业务应用服务事件已接收: eventId={}, serviceCode={}, instanceId={}",
                eventId, serviceCode, instanceId);
        return new BusinessServiceEventAckVo(eventId, acceptedAt, false);
    }

    @Override
    public BusinessServiceSummaryVo summary() {
        Date checkedAt = now();
        Date cutoff = offlineCutoff(checkedAt);
        long up = instanceRepository.countByLastHeartbeatTimeGreaterThanEqualAndReportedStatus(
                cutoff, BusinessServiceReportedStatus.UP);
        long degraded = instanceRepository.countByLastHeartbeatTimeGreaterThanEqualAndReportedStatus(
                cutoff, BusinessServiceReportedStatus.DEGRADED);
        long down = instanceRepository.countByLastHeartbeatTimeGreaterThanEqualAndReportedStatus(
                cutoff, BusinessServiceReportedStatus.DOWN);
        long offline = instanceRepository.countByLastHeartbeatTimeBefore(cutoff);
        return new BusinessServiceSummaryVo(
                instanceRepository.countDistinctServices(),
                instanceRepository.count(),
                up,
                degraded,
                down,
                offline,
                down + offline,
                eventRepository.countByCreateTimeGreaterThanEqual(
                        Date.from(checkedAt.toInstant().minus(24, ChronoUnit.HOURS))),
                checkedAt);
    }

    @Override
    public PageRowsVo<BusinessServiceInstanceVo> getInstancePage(BusinessServiceInstanceSearchDto condition) {
        BusinessServiceInstanceSearchDto search = condition == null
                ? new BusinessServiceInstanceSearchDto() : condition;
        Pageable pageable = pageRequest(search.getPage(), search.getPerPage());
        boolean statusFiltered = search.getStatus() != null;
        boolean offline = search.getStatus() == BusinessServiceEffectiveStatus.OFFLINE;
        BusinessServiceReportedStatus reportedStatus = statusFiltered && !offline
                ? BusinessServiceReportedStatus.valueOf(search.getStatus().name()) : null;
        Date checkedAt = now();
        Page<BusinessServiceInstance> page = instanceRepository.findByPage(
                pageable,
                blankToNull(search.getKeyword()),
                blankToNull(search.getEnvironment()),
                statusFiltered,
                offline,
                reportedStatus,
                offlineCutoff(checkedAt));
        return new PageRowsVo<>(page.getContent().stream()
                .map(instance -> toInstanceVo(instance, checkedAt))
                .toList(), page.getTotalElements());
    }

    @Override
    public BusinessServiceInstanceVo getInstance(Integer id) {
        BusinessServiceInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "业务应用服务实例不存在"));
        return toInstanceVo(instance, now());
    }

    @Override
    public PageRowsVo<BusinessServiceEventVo> getEventPage(BusinessServiceEventSearchDto condition) {
        BusinessServiceEventSearchDto search = condition == null
                ? new BusinessServiceEventSearchDto() : condition;
        if (search.getStartTime() != null && search.getEndTime() != null
                && search.getStartTime().after(search.getEndTime())) {
            throw new ApiException(400, "开始时间不能晚于结束时间");
        }
        Page<BusinessServiceEvent> page = eventRepository.findByPage(
                pageRequest(search.getPage(), search.getPerPage()),
                blankToNull(search.getKeyword()),
                blankToNull(search.getServiceCode()),
                blankToNull(search.getInstanceId()),
                search.getSeverity(),
                blankToNull(search.getEventType()),
                search.getStartTime(),
                search.getEndTime());
        return new PageRowsVo<>(page.getContent().stream().map(this::toEventVo).toList(),
                page.getTotalElements());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void cleanupExpiredData() {
        Instant instant = clock.instant();
        Date eventCutoff = Date.from(instant.minus(Math.max(eventRetentionDays, 1), ChronoUnit.DAYS));
        Date instanceCutoff = Date.from(instant.minus(Math.max(instanceRetentionDays, 1), ChronoUnit.DAYS));
        int deletedEvents = eventRepository.deleteExpiredEvents(eventCutoff);
        int deletedInstances = instanceRepository.deleteStaleInstances(instanceCutoff);
        if (deletedEvents > 0 || deletedInstances > 0) {
            log.info("业务应用服务历史数据清理完成: events={}, instances={}",
                    deletedEvents, deletedInstances);
        }
    }

    private void applyHeartbeat(BusinessServiceInstance instance,
                                BusinessServiceHeartbeatDto dto,
                                String serviceCode,
                                String instanceId,
                                String metadata,
                                String remoteAddress,
                                Date receivedAt,
                                boolean registered) {
        instance.setServiceCode(serviceCode)
                .setServiceName(dto.getServiceName().trim())
                .setInstanceId(instanceId)
                .setReportedStatus(dto.getStatus())
                .setStatusMessage(StringUtils.trimToNull(dto.getStatusMessage()))
                .setLastHeartbeatTime(receivedAt)
                .setReportedHeartbeatTime(dto.getHeartbeatTime())
                .setRemoteAddress(normalizeRemoteAddress(remoteAddress));
        if (registered || instance.getFirstHeartbeatTime() == null) {
            instance.setFirstHeartbeatTime(receivedAt);
        }
        if (dto.getVersion() != null) {
            instance.setVersion(StringUtils.trimToNull(dto.getVersion()));
        }
        if (dto.getEnvironment() != null) {
            instance.setEnvironment(StringUtils.trimToNull(dto.getEnvironment()));
        }
        if (dto.getHost() != null) {
            instance.setHost(StringUtils.trimToNull(dto.getHost()));
        }
        if (dto.getPort() != null) {
            instance.setPort(dto.getPort());
        }
        if (dto.getManagementUrl() != null) {
            instance.setManagementUrl(StringUtils.trimToNull(dto.getManagementUrl()));
        }
        if (dto.getMetadata() != null) {
            instance.setMetadata(metadata);
        }
    }

    private BusinessServiceEventAckVo duplicateEventAck(BusinessServiceEvent event,
                                                         String serviceCode,
                                                         String instanceId) {
        if (!serviceCode.equals(event.getServiceCode()) || !instanceId.equals(event.getInstanceId())) {
            throw new ApiException(409, "event_id 已被其他业务应用服务实例使用");
        }
        Date acceptedAt = event.getCreateTime() == null ? now() : event.getCreateTime();
        return new BusinessServiceEventAckVo(event.getEventId(), acceptedAt, true);
    }

    private BusinessServiceInstanceVo toInstanceVo(BusinessServiceInstance instance, Date checkedAt) {
        BusinessServiceEffectiveStatus status = effectiveStatus(instance, checkedAt);
        long secondsSinceHeartbeat = instance.getLastHeartbeatTime() == null ? -1
                : Math.max(0, Duration.between(instance.getLastHeartbeatTime().toInstant(),
                checkedAt.toInstant()).getSeconds());
        return new BusinessServiceInstanceVo()
                .setId(instance.getId())
                .setServiceCode(instance.getServiceCode())
                .setServiceName(instance.getServiceName())
                .setInstanceId(instance.getInstanceId())
                .setReportedStatus(instance.getReportedStatus())
                .setEffectiveStatus(status)
                .setOnline(status != BusinessServiceEffectiveStatus.OFFLINE)
                .setSecondsSinceHeartbeat(secondsSinceHeartbeat)
                .setStatusMessage(instance.getStatusMessage())
                .setVersion(instance.getVersion())
                .setEnvironment(instance.getEnvironment())
                .setHost(instance.getHost())
                .setPort(instance.getPort())
                .setManagementUrl(instance.getManagementUrl())
                .setMetadata(parseMap(instance.getMetadata()))
                .setRemoteAddress(instance.getRemoteAddress())
                .setFirstHeartbeatTime(instance.getFirstHeartbeatTime())
                .setLastHeartbeatTime(instance.getLastHeartbeatTime())
                .setReportedHeartbeatTime(instance.getReportedHeartbeatTime())
                .setLastEventTime(instance.getLastEventTime())
                .setCreateTime(instance.getCreateTime())
                .setUpdateTime(instance.getUpdateTime());
    }

    private BusinessServiceEventVo toEventVo(BusinessServiceEvent event) {
        return new BusinessServiceEventVo()
                .setId(event.getId())
                .setEventId(event.getEventId())
                .setServiceInstanceId(event.getServiceInstanceId())
                .setServiceCode(event.getServiceCode())
                .setInstanceId(event.getInstanceId())
                .setEventType(event.getEventType())
                .setSeverity(event.getSeverity())
                .setTitle(event.getTitle())
                .setMessage(event.getMessage())
                .setOccurredTime(event.getOccurredTime())
                .setTraceId(event.getTraceId())
                .setData(parseMap(event.getData()))
                .setRemoteAddress(event.getRemoteAddress())
                .setCreateTime(event.getCreateTime());
    }

    private BusinessServiceEffectiveStatus effectiveStatus(BusinessServiceInstance instance, Date checkedAt) {
        if (instance.getLastHeartbeatTime() == null
                || instance.getLastHeartbeatTime().before(offlineCutoff(checkedAt))) {
            return BusinessServiceEffectiveStatus.OFFLINE;
        }
        return BusinessServiceEffectiveStatus.valueOf(instance.getReportedStatus().name());
    }

    private Date offlineCutoff(Date checkedAt) {
        return Date.from(checkedAt.toInstant().minusSeconds(normalizedOfflineThresholdSeconds()));
    }

    private long normalizedOfflineThresholdSeconds() {
        return Math.max(offlineThresholdSeconds, 1);
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    private Pageable pageRequest(int page, int perPage) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(perPage, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage - 1, safeSize);
    }

    private String serializeMap(Map<String, Object> value, int maxBytes, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            if (bytes.length > maxBytes) {
                throw new ApiException(400, fieldName + " 不能超过 " + maxBytes + " 字节");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new ApiException(400, fieldName + " 不是有效的 JSON 对象");
        }
    }

    private Map<String, Object> parseMap(String value) {
        if (StringUtils.isBlank(value)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("业务应用服务扩展数据解析失败", e);
            return Collections.emptyMap();
        }
    }

    private String normalizeRemoteAddress(String remoteAddress) {
        return StringUtils.abbreviate(StringUtils.trimToEmpty(remoteAddress), 64);
    }

    private String blankToNull(String value) {
        return StringUtils.trimToNull(value);
    }
}
