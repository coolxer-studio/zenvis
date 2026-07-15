package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import com.coolxer.commons.enums.BusinessServiceEventSeverity;
import com.coolxer.commons.enums.BusinessServiceReportedStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.BusinessServiceEvent;
import com.coolxer.dao.mysql.entity.BusinessServiceInstance;
import com.coolxer.dao.mysql.repository.BusinessServiceEventRepository;
import com.coolxer.dao.mysql.repository.BusinessServiceInstanceRepository;
import com.coolxer.model.system.dto.BusinessServiceEventDto;
import com.coolxer.model.system.dto.BusinessServiceHeartbeatDto;
import com.coolxer.model.system.vo.BusinessServiceEventAckVo;
import com.coolxer.model.system.vo.BusinessServiceHeartbeatAckVo;
import com.coolxer.model.system.vo.BusinessServiceInstanceVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceRegistryServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Mock
    private BusinessServiceInstanceRepository instanceRepository;

    @Mock
    private BusinessServiceEventRepository eventRepository;

    private BusinessServiceRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessServiceRegistryServiceImpl();
        ReflectionTestUtils.setField(service, "instanceRepository", instanceRepository);
        ReflectionTestUtils.setField(service, "eventRepository", eventRepository);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "offlineThresholdSeconds", 90L);
        ReflectionTestUtils.setField(service, "eventRetentionDays", 30L);
        ReflectionTestUtils.setField(service, "instanceRetentionDays", 30L);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstHeartbeatRegistersInstanceAndUsesServerReceiveTime() {
        when(instanceRepository.findByServiceCodeAndInstanceId("order-api", "order-api-1"))
                .thenReturn(Optional.empty());
        when(instanceRepository.saveAndFlush(any(BusinessServiceInstance.class)))
                .thenAnswer(invocation -> {
                    BusinessServiceInstance instance = invocation.getArgument(0);
                    instance.setId(11);
                    return instance;
                });

        BusinessServiceHeartbeatDto dto = heartbeat("order-api", "order-api-1",
                BusinessServiceReportedStatus.UP);
        dto.setHeartbeatTime(Date.from(NOW.minus(2, ChronoUnit.DAYS)));
        dto.setMetadata(Map.of("region", "cn-east"));

        BusinessServiceHeartbeatAckVo result = service.reportHeartbeat(dto, "10.0.0.8");

        assertThat(result.isRegistered()).isTrue();
        assertThat(result.getReceivedAt()).isEqualTo(Date.from(NOW));
        assertThat(result.getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.UP);
        assertThat(result.getOfflineAfterSeconds()).isEqualTo(90);

        ArgumentCaptor<BusinessServiceInstance> captor = ArgumentCaptor.forClass(BusinessServiceInstance.class);
        verify(instanceRepository).saveAndFlush(captor.capture());
        BusinessServiceInstance saved = captor.getValue();
        assertThat(saved.getServiceCode()).isEqualTo("order-api");
        assertThat(saved.getInstanceId()).isEqualTo("order-api-1");
        assertThat(saved.getFirstHeartbeatTime()).isEqualTo(Date.from(NOW));
        assertThat(saved.getLastHeartbeatTime()).isEqualTo(Date.from(NOW));
        assertThat(saved.getReportedHeartbeatTime()).isEqualTo(Date.from(NOW.minus(2, ChronoUnit.DAYS)));
        assertThat(saved.getMetadata()).contains("cn-east");
    }

    @Test
    void concurrentFirstHeartbeatReusesWinnerWithoutDuplicateInstance() {
        BusinessServiceInstance winner = registeredInstance(21, "billing", "billing-1",
                BusinessServiceReportedStatus.UP, NOW.minusSeconds(5));
        Date originalFirstHeartbeat = winner.getFirstHeartbeatTime();
        when(instanceRepository.findByServiceCodeAndInstanceId("billing", "billing-1"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(instanceRepository.saveAndFlush(any(BusinessServiceInstance.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate unique key"));
        when(instanceRepository.save(winner)).thenReturn(winner);

        BusinessServiceHeartbeatDto dto = heartbeat("billing", "billing-1",
                BusinessServiceReportedStatus.DEGRADED);
        BusinessServiceHeartbeatAckVo result = service.reportHeartbeat(dto, "10.0.0.9");

        assertThat(result.isRegistered()).isFalse();
        assertThat(result.getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.DEGRADED);
        assertThat(winner.getFirstHeartbeatTime()).isEqualTo(originalFirstHeartbeat);
        assertThat(winner.getLastHeartbeatTime()).isEqualTo(Date.from(NOW));
        verify(instanceRepository).save(winner);
    }

    @Test
    void effectiveStatusSupportsReportedStatesAndExactOfflineBoundary() {
        when(instanceRepository.findById(1)).thenReturn(Optional.of(registeredInstance(
                1, "demo", "up", BusinessServiceReportedStatus.UP, NOW)));
        when(instanceRepository.findById(2)).thenReturn(Optional.of(registeredInstance(
                2, "demo", "degraded", BusinessServiceReportedStatus.DEGRADED, NOW)));
        when(instanceRepository.findById(3)).thenReturn(Optional.of(registeredInstance(
                3, "demo", "down", BusinessServiceReportedStatus.DOWN, NOW.minusSeconds(90))));
        when(instanceRepository.findById(4)).thenReturn(Optional.of(registeredInstance(
                4, "demo", "offline", BusinessServiceReportedStatus.UP,
                NOW.minusMillis(90_001))));

        assertThat(service.getInstance(1).getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.UP);
        assertThat(service.getInstance(2).getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.DEGRADED);
        assertThat(service.getInstance(3).getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.DOWN);
        BusinessServiceInstanceVo offline = service.getInstance(4);
        assertThat(offline.getEffectiveStatus()).isEqualTo(BusinessServiceEffectiveStatus.OFFLINE);
        assertThat(offline.isOnline()).isFalse();
    }

    @Test
    void eventRequiresRegistrationButOfflineInstanceMayStillReport() {
        BusinessServiceEventDto missingEvent = event("evt-missing", "unknown", "unknown-1");
        when(eventRepository.findByEventId("evt-missing")).thenReturn(Optional.empty());
        when(instanceRepository.findByServiceCodeAndInstanceId("unknown", "unknown-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportEvent(missingEvent, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(404);

        BusinessServiceInstance offlineInstance = registeredInstance(31, "orders", "orders-1",
                BusinessServiceReportedStatus.UP, NOW.minus(10, ChronoUnit.MINUTES));
        BusinessServiceEventDto acceptedEvent = event("evt-offline", "orders", "orders-1");
        acceptedEvent.setOccurredAt(Date.from(NOW.minus(7, ChronoUnit.DAYS)));
        when(eventRepository.findByEventId("evt-offline")).thenReturn(Optional.empty());
        when(instanceRepository.findByServiceCodeAndInstanceId("orders", "orders-1"))
                .thenReturn(Optional.of(offlineInstance));
        when(eventRepository.saveAndFlush(any(BusinessServiceEvent.class)))
                .thenAnswer(invocation -> {
                    BusinessServiceEvent saved = invocation.getArgument(0);
                    saved.setId(41);
                    return saved;
                });

        BusinessServiceEventAckVo result = service.reportEvent(acceptedEvent, "127.0.0.1");

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.getAcceptedAt()).isEqualTo(Date.from(NOW));
        assertThat(offlineInstance.getLastEventTime()).isEqualTo(Date.from(NOW));
        ArgumentCaptor<BusinessServiceEvent> eventCaptor = ArgumentCaptor.forClass(BusinessServiceEvent.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOccurredTime())
                .isEqualTo(Date.from(NOW.minus(7, ChronoUnit.DAYS)));
        verify(instanceRepository).save(offlineInstance);
    }

    @Test
    void duplicateEventIsIdempotentForSameInstanceAndConflictsAcrossInstances() {
        BusinessServiceEvent persisted = new BusinessServiceEvent()
                .setEventId("evt-100")
                .setServiceCode("orders")
                .setInstanceId("orders-1");
        persisted.setCreateTime(Date.from(NOW.minusSeconds(10)));
        when(eventRepository.findByEventId("evt-100")).thenReturn(Optional.of(persisted));

        BusinessServiceEventAckVo duplicate = service.reportEvent(
                event("evt-100", "orders", "orders-1"), "127.0.0.1");
        assertThat(duplicate.isDuplicate()).isTrue();
        assertThat(duplicate.getAcceptedAt()).isEqualTo(persisted.getCreateTime());

        assertThatThrownBy(() -> service.reportEvent(
                event("evt-100", "orders", "orders-2"), "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(409);
    }

    @Test
    void extensionPayloadLimitsAreEnforcedByUtf8ByteLength() {
        BusinessServiceHeartbeatDto heartbeat = heartbeat("large", "large-1",
                BusinessServiceReportedStatus.UP);
        heartbeat.setMetadata(Map.of("text", "中".repeat(6_000)));

        assertThatThrownBy(() -> service.reportHeartbeat(heartbeat, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("metadata")
                .extracting("code")
                .isEqualTo(400);

        BusinessServiceEventDto event = event("evt-large", "large", "large-1");
        event.setData(Map.of("text", "中".repeat(22_000)));
        assertThatThrownBy(() -> service.reportEvent(event, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("data")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void cleanupUsesServerTimeAndDeletesEventsBeforeStaleInstances() {
        when(eventRepository.deleteExpiredEvents(any(Date.class))).thenReturn(2);
        when(instanceRepository.deleteStaleInstances(any(Date.class))).thenReturn(1);

        service.cleanupExpiredData();

        Date cutoff = Date.from(NOW.minus(30, ChronoUnit.DAYS));
        InOrder order = inOrder(eventRepository, instanceRepository);
        order.verify(eventRepository).deleteExpiredEvents(eq(cutoff));
        order.verify(instanceRepository).deleteStaleInstances(eq(cutoff));
    }

    private BusinessServiceHeartbeatDto heartbeat(String serviceCode, String instanceId,
                                                   BusinessServiceReportedStatus status) {
        BusinessServiceHeartbeatDto dto = new BusinessServiceHeartbeatDto();
        dto.setServiceCode(serviceCode);
        dto.setServiceName(serviceCode + " service");
        dto.setInstanceId(instanceId);
        dto.setStatus(status);
        return dto;
    }

    private BusinessServiceEventDto event(String eventId, String serviceCode, String instanceId) {
        BusinessServiceEventDto dto = new BusinessServiceEventDto();
        dto.setEventId(eventId);
        dto.setServiceCode(serviceCode);
        dto.setInstanceId(instanceId);
        dto.setEventType("DEPLOYMENT");
        dto.setSeverity(BusinessServiceEventSeverity.INFO);
        dto.setTitle("deployment completed");
        return dto;
    }

    private BusinessServiceInstance registeredInstance(Integer id, String serviceCode, String instanceId,
                                                       BusinessServiceReportedStatus status,
                                                       Instant lastHeartbeat) {
        BusinessServiceInstance instance = new BusinessServiceInstance()
                .setServiceCode(serviceCode)
                .setServiceName(serviceCode + " service")
                .setInstanceId(instanceId)
                .setReportedStatus(status)
                .setFirstHeartbeatTime(Date.from(lastHeartbeat))
                .setLastHeartbeatTime(Date.from(lastHeartbeat));
        instance.setId(id);
        return instance;
    }
}
