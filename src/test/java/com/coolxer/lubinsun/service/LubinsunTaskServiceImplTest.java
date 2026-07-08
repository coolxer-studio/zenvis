package com.coolxer.lubinsun.service;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.client.LubinsunPlatformClient;
import com.coolxer.lubinsun.config.LubinsunPlatformProperties;
import com.coolxer.lubinsun.entity.LubinsunSkillRunEvent;
import com.coolxer.lubinsun.entity.LubinsunSkillRunTask;
import com.coolxer.lubinsun.model.LubinsunPlatformRunRequest;
import com.coolxer.lubinsun.model.LubinsunSkillRunTaskVo;
import com.coolxer.lubinsun.model.LubinsunSipLogLookupResult;
import com.coolxer.lubinsun.model.LubinsunTaskDto;
import com.coolxer.lubinsun.model.LubinsunTaskStatus;
import com.coolxer.lubinsun.repository.LubinsunSkillRunEventRepository;
import com.coolxer.lubinsun.repository.LubinsunSkillRunTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LubinsunTaskServiceImplTest {

    private FakePlatformClient platformClient;
    private FakeSipLogLookupService sipLogLookupService;
    private TaskRepositoryState taskState;
    private EventRepositoryState eventState;
    private LubinsunTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        LubinsunPlatformProperties properties = new LubinsunPlatformProperties();
        properties.setEventLimit(200);
        platformClient = new FakePlatformClient();
        sipLogLookupService = new FakeSipLogLookupService();
        taskState = new TaskRepositoryState();
        eventState = new EventRepositoryState();
        service = new LubinsunTaskServiceImpl(
                taskState.repository(),
                eventState.repository(),
                platformClient,
                properties,
                sipLogLookupService
        );
    }

    @Test
    void createStoresDraftWithoutCallingPlatform() {
        LubinsunSkillRunTaskVo task = service.create(dto());

        assertThat(task.getStatus()).isEqualTo(LubinsunTaskStatus.DRAFT);
        assertThat(task.getSkill()).isEqualTo("alert-auto-disposition");
        assertThat(task.getInputJson()).isEqualTo("{\"event\":{\"id\":\"a1\"}}");
        assertThat(platformClient.createRunCalls).isZero();
    }

    @Test
    void createWithIpStoresDraftWithoutCallingPlatform() {
        LubinsunSkillRunTaskVo task = service.create(ipDto());

        assertThat(task.getStatus()).isEqualTo(LubinsunTaskStatus.DRAFT);
        assertThat(task.getIp()).isEqualTo("10.0.0.12,10.0.0.13");
        assertThat(task.getName()).isEqualTo("SIP IP研判任务-10.0.0.12,10.0.0.13");
        assertThat(task.getSkill()).isEqualTo("alert-auto-disposition");
        assertThat(task.getRawLog()).contains("WAF raw log");
        assertThat(task.getInputJson()).isNull();
        assertThat(platformClient.createRunCalls).isZero();
    }

    @Test
    void executeCallsPlatformAndStoresRunIdentity() throws Exception {
        taskState.put(savedDraft());
        platformClient.createRunResponse = readJson("""
                {
                  "run_id": "run-1",
                  "session_id": "run-1",
                  "task_id": "skill-run-1",
                  "status": "accepted",
                  "result_summary": "已接收"
                }
                """);

        LubinsunSkillRunTaskVo result = service.execute(7L);

        assertThat(result.getRunId()).isEqualTo("run-1");
        assertThat(result.getPlatformTaskId()).isEqualTo("skill-run-1");
        assertThat(result.getStatus()).isEqualTo(LubinsunTaskStatus.ACCEPTED);
        assertThat(result.getResultSummary()).isEqualTo("已接收");
        assertThat(platformClient.createRunCalls).isEqualTo(1);
        assertThat(platformClient.lastRunRequest.getSkill()).isEqualTo("alert-auto-disposition");
    }

    @Test
    void executeIpTaskBuildsInputFromSipLogs() throws Exception {
        taskState.put(savedIpDraft());
        sipLogLookupService.result = new LubinsunSipLogLookupResult(
                List.of(Map.of(
                        "ip", "10.0.0.12",
                        "src_ip", "203.0.113.12",
                        "dst_ip", "10.0.0.12",
                        "log_time", "2026-06-28 10:00:00",
                        "event_type", "threat_signal"
                )),
                List.of(Map.of(
                        "attack_ip", "203.0.113.13",
                        "suffer_ip", "10.0.0.12",
                        "last_time", "2026-06-28 10:01:00",
                        "alert_id", "alert-001"
                )),
                List.of()
        );
        platformClient.createRunResponse = readJson("""
                {
                  "run_id": "run-ip-1",
                  "status": "accepted"
                }
                """);

        LubinsunSkillRunTaskVo result = service.execute(7L);

        JsonNode input = platformClient.lastRunRequest.getInput();
        assertThat(sipLogLookupService.lastLookupIp).isEqualTo("10.0.0.12,10.0.0.13");
        assertThat(input.at("/event/target_ip").asText()).isEqualTo("10.0.0.12,10.0.0.13");
        assertThat(input.at("/event/target_ips").toString()).contains("10.0.0.12", "10.0.0.13");
        assertThat(input.at("/event/relevant_logs").isArray()).isTrue();
        assertThat(input.at("/event/relevant_logs").size()).isEqualTo(2);
        assertThat(input.at("/event/relevant_logs").toString())
                .contains("sangfor_sip_security_event", "sangfor_sip_security_alarm", "threat_signal", "alert-001");
        assertThat(input.at("/evidence/security_event_count").asInt()).isEqualTo(1);
        assertThat(input.at("/evidence/security_alarm_count").asInt()).isEqualTo(1);
        assertThat(input.at("/evidence/sangfor_sip_security_events/0/event_type").asText()).isEqualTo("threat_signal");
        assertThat(input.at("/evidence/attacker_ips").toString()).contains("203.0.113.12", "203.0.113.13");
        assertThat(input.at("/evidence/victim_ips").toString()).contains("10.0.0.12");
        assertThat(input.at("/evidence/raw_log").asText()).contains("WAF raw log");
        assertThat(input.at("/evidence/matched_ip_fields/sangfor_sip_security_event").toString()).contains("src_ip", "dst_ip");
        assertThat(input.at("/assets").toString()).contains("attacker_ip");
        assertThat(result.getInputJson()).contains("sangfor_sip_security_events");
        assertThat(result.getEventLogs().size()).isEqualTo(1);
        assertThat(result.getAlarmLogs().size()).isEqualTo(1);
    }

    @Test
    void refreshOnlyStoresNewEventsAndUpdatesSnapshot() throws Exception {
        LubinsunSkillRunTask task = savedDraft()
                .setRunId("run-1")
                .setStatus(LubinsunTaskStatus.RUNNING)
                .setLastSeq(0L);
        taskState.put(task);
        platformClient.events = List.of(
                readJson("{\"seq\":1,\"id\":\"evt-1\",\"type\":\"platform.run.received\",\"data_json\":\"{\\\"skill\\\":\\\"alert\\\"}\"}"),
                readJson("{\"seq\":2,\"id\":\"evt-2\",\"type\":\"session.next.prompted\"}")
        );
        eventState.existingEvents.put("7:2", new LubinsunSkillRunEvent());
        platformClient.snapshot = readJson("""
                {
                  "status": "completed",
                  "result_summary": "完成",
                  "result": {"status": "pending_platform_execution"},
                  "pending_permissions": []
                }
                """);

        LubinsunSkillRunTaskVo result = service.refresh(7L);

        assertThat(result.getStatus()).isEqualTo(LubinsunTaskStatus.COMPLETED);
        assertThat(result.getLastSeq()).isEqualTo(2L);
        assertThat(result.getResultSummary()).isEqualTo("完成");
        assertThat(eventState.savedEvents).hasSize(1);
        assertThat(eventState.savedEvents.get(0).getSeq()).isEqualTo(1L);
        assertThat(eventState.savedEvents.get(0).getDataJson()).isEqualTo("{\"skill\":\"alert\"}");
    }

    @Test
    void terminalTaskRefreshDoesNotCallPlatform() {
        LubinsunSkillRunTask task = savedDraft()
                .setRunId("run-1")
                .setStatus(LubinsunTaskStatus.COMPLETED);
        taskState.put(task);

        service.refresh(7L);

        assertThat(platformClient.getEventsCalls).isZero();
        assertThat(platformClient.getRunCalls).isZero();
    }

    @Test
    void executeFailureIsStoredOnTask() {
        taskState.put(savedDraft());
        platformClient.createRunException = new IllegalStateException("Lubinsun Integration Token 未配置");

        LubinsunSkillRunTaskVo result = service.execute(7L);

        assertThat(result.getStatus()).isEqualTo(LubinsunTaskStatus.EXECUTE_FAILED);
        assertThat(result.getErrorMessage()).contains("Integration Token");
    }

    @Test
    void pollActiveTasksOnlyRequestsActiveStatuses() {
        service.pollActiveTasks();

        assertThat(taskState.lastPolledStatuses).containsExactly(
                LubinsunTaskStatus.ACCEPTED,
                LubinsunTaskStatus.RUNNING,
                LubinsunTaskStatus.WAITING_PERMISSION
        );
    }

    private static LubinsunTaskDto dto() {
        LubinsunTaskDto dto = new LubinsunTaskDto();
        dto.setName("测试任务");
        dto.setSkill("alert-auto-disposition");
        dto.setAgent("ops");
        dto.setInputJson("{\"event\":{\"id\":\"a1\"}}");
        dto.setMetadataJson("{\"source_system\":\"zenvis\"}");
        return dto;
    }

    private static LubinsunTaskDto ipDto() {
        LubinsunTaskDto dto = new LubinsunTaskDto();
        dto.setIp("10.0.0.12, 10.0.0.13,10.0.0.12");
        dto.setRawLog("WAF raw log /admin/login failed");
        return dto;
    }

    private static LubinsunSkillRunTask savedDraft() {
        LubinsunSkillRunTask task = new LubinsunSkillRunTask()
                .setName("测试任务")
                .setSkill("alert-auto-disposition")
                .setAgent("ops")
                .setInputJson("{\"event\":{\"id\":\"a1\"}}")
                .setMetadataJson("{\"source_system\":\"zenvis\"}")
                .setStatus(LubinsunTaskStatus.DRAFT)
                .setLastSeq(0L);
        task.setId(7);
        return task;
    }

    private static LubinsunSkillRunTask savedIpDraft() {
        LubinsunSkillRunTask task = new LubinsunSkillRunTask()
                .setName("IP研判任务")
                .setSkill("alert-auto-disposition")
                .setIp("10.0.0.12,10.0.0.13")
                .setRawLog("WAF raw log /admin/login failed")
                .setStatus(LubinsunTaskStatus.DRAFT)
                .setLastSeq(0L);
        task.setId(7);
        return task;
    }

    private static JsonNode readJson(String json) throws Exception {
        return JacksonConfig.OBJECT_MAPPER.readTree(json);
    }

    private static final class FakePlatformClient extends LubinsunPlatformClient {
        private int createRunCalls;
        private int getEventsCalls;
        private int getRunCalls;
        private LubinsunPlatformRunRequest lastRunRequest;
        private JsonNode createRunResponse;
        private RuntimeException createRunException;
        private List<JsonNode> events = List.of();
        private JsonNode snapshot;

        private FakePlatformClient() {
            super(new RestTemplate(), new LubinsunPlatformProperties(), JacksonConfig.OBJECT_MAPPER);
        }

        @Override
        public JsonNode createRun(LubinsunPlatformRunRequest request) {
            createRunCalls++;
            lastRunRequest = request;
            if (createRunException != null) {
                throw createRunException;
            }
            return createRunResponse;
        }

        @Override
        public List<JsonNode> getEvents(String runId, long after, int limit) {
            getEventsCalls++;
            return events;
        }

        @Override
        public JsonNode getRun(String runId) {
            getRunCalls++;
            return snapshot;
        }
    }

    private static final class FakeSipLogLookupService implements LubinsunSipLogLookupService {
        private LubinsunSipLogLookupResult result = new LubinsunSipLogLookupResult(List.of(), List.of(), List.of());
        private String lastLookupIp;

        @Override
        public LubinsunSipLogLookupResult lookup(String ip) {
            lastLookupIp = ip;
            return result;
        }
    }

    private static final class TaskRepositoryState {
        private final Map<Integer, LubinsunSkillRunTask> tasks = new HashMap<>();
        private List<LubinsunTaskStatus> lastPolledStatuses = List.of();

        private void put(LubinsunSkillRunTask task) {
            tasks.put(task.getId(), task);
        }

        @SuppressWarnings("unchecked")
        private LubinsunSkillRunTaskRepository repository() {
            return (LubinsunSkillRunTaskRepository) Proxy.newProxyInstance(
                    LubinsunSkillRunTaskRepository.class.getClassLoader(),
                    new Class<?>[]{LubinsunSkillRunTaskRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            LubinsunSkillRunTask task = (LubinsunSkillRunTask) args[0];
                            if (task.getId() == null) {
                                task.setId(tasks.size() + 1);
                            }
                            tasks.put(task.getId(), task);
                            yield task;
                        }
                        case "findById" -> Optional.ofNullable(tasks.get(((Number) args[0]).intValue()));
                        case "deleteById" -> {
                            tasks.remove(((Number) args[0]).intValue());
                            yield null;
                        }
                        case "findByStatusInAndRunIdIsNotNull" -> {
                            lastPolledStatuses = new ArrayList<>((Collection<LubinsunTaskStatus>) args[0]);
                            yield tasks.values().stream()
                                    .filter(task -> task.getRunId() != null)
                                    .filter(task -> lastPolledStatuses.contains(task.getStatus()))
                                    .toList();
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class EventRepositoryState {
        private final Map<String, LubinsunSkillRunEvent> existingEvents = new HashMap<>();
        private final List<LubinsunSkillRunEvent> savedEvents = new ArrayList<>();

        private LubinsunSkillRunEventRepository repository() {
            return (LubinsunSkillRunEventRepository) Proxy.newProxyInstance(
                    LubinsunSkillRunEventRepository.class.getClassLoader(),
                    new Class<?>[]{LubinsunSkillRunEventRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> {
                            LubinsunSkillRunEvent event = (LubinsunSkillRunEvent) args[0];
                            savedEvents.add(event);
                            existingEvents.put(event.getTaskId() + ":" + event.getSeq(), event);
                            yield event;
                        }
                        case "findByTaskIdAndSeq" -> Optional.ofNullable(existingEvents.get(args[0] + ":" + args[1]));
                        case "findByTaskIdOrderBySeqAsc", "findByTaskIdOrderBySeqDesc" -> savedEvents;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
