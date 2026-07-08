package com.coolxer.lubinsun.service;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.client.LubinsunPlatformClient;
import com.coolxer.lubinsun.config.LubinsunPlatformProperties;
import com.coolxer.lubinsun.entity.LubinsunSkillRunEvent;
import com.coolxer.lubinsun.entity.LubinsunSkillRunTask;
import com.coolxer.lubinsun.model.LubinsunPlatformRunRequest;
import com.coolxer.lubinsun.model.LubinsunSkillRunEventVo;
import com.coolxer.lubinsun.model.LubinsunSkillRunTaskVo;
import com.coolxer.lubinsun.model.LubinsunSipLogLookupResult;
import com.coolxer.lubinsun.model.LubinsunTaskDetailVo;
import com.coolxer.lubinsun.model.LubinsunTaskDto;
import com.coolxer.lubinsun.model.LubinsunTaskSearchDto;
import com.coolxer.lubinsun.model.LubinsunTaskStatus;
import com.coolxer.lubinsun.repository.LubinsunSkillRunEventRepository;
import com.coolxer.lubinsun.repository.LubinsunSkillRunTaskRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LubinsunTaskServiceImpl implements LubinsunTaskService {

    private static final int RECENT_EVENT_LIMIT = 200;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;
    private static final String DEFAULT_IP_TASK_NAME = "SIP IP研判任务";
    private static final String DEFAULT_IP_SKILL = "alert-auto-disposition";
    private static final String DEFAULT_IP_AGENT = "ops";
    private static final String DEFAULT_IP_TASK_TYPE = "skill_run";
    private static final List<LubinsunTaskStatus> POLLING_STATUSES = List.of(
            LubinsunTaskStatus.ACCEPTED,
            LubinsunTaskStatus.RUNNING,
            LubinsunTaskStatus.WAITING_PERMISSION
    );

    private final LubinsunSkillRunTaskRepository taskRepository;
    private final LubinsunSkillRunEventRepository eventRepository;
    private final LubinsunPlatformClient platformClient;
    private final LubinsunPlatformProperties properties;
    private final LubinsunSipLogLookupService sipLogLookupService;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    public LubinsunTaskServiceImpl(LubinsunSkillRunTaskRepository taskRepository,
                                   LubinsunSkillRunEventRepository eventRepository,
                                   LubinsunPlatformClient platformClient,
                                   LubinsunPlatformProperties properties,
                                   LubinsunSipLogLookupService sipLogLookupService) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.platformClient = platformClient;
        this.properties = properties;
        this.sipLogLookupService = sipLogLookupService;
    }

    @Override
    @Transactional
    public LubinsunSkillRunTaskVo create(LubinsunTaskDto dto) {
        checkCreateOrUpdate(dto);
        LubinsunSkillRunTask task = new LubinsunSkillRunTask();
        applyDto(task, dto);
        task.setStatus(LubinsunTaskStatus.DRAFT);
        task.setLastSeq(0L);
        return new LubinsunSkillRunTaskVo(taskRepository.save(task));
    }

    @Override
    @Transactional
    public LubinsunSkillRunTaskVo update(Long id, LubinsunTaskDto dto) {
        checkCreateOrUpdate(dto);
        LubinsunSkillRunTask task = getTask(id);
        checkNotRunning(task, "运行中的 Lubinsun 任务不能修改");
        applyDto(task, dto);
        return new LubinsunSkillRunTaskVo(taskRepository.save(task));
    }

    @Override
    public PageRowsVo<LubinsunSkillRunTaskVo> getPageList(LubinsunTaskSearchDto searchDto) {
        try {
            Pageable pageable = PageRequest.of(searchDto.getPage() - 1, searchDto.getPerPage());
            Page<LubinsunSkillRunTask> page = taskRepository.findByPage(
                    pageable,
                    blankToNull(searchDto.getName()),
                    blankToNull(searchDto.getSkill()),
                    blankToNull(searchDto.getIp()),
                    searchDto.getStatus(),
                    blankToNull(searchDto.getRunId())
            );
            return new PageRowsVo<>(page.getContent().stream().map(LubinsunSkillRunTaskVo::new).toList(), page.getTotalElements());
        } catch (Exception e) {
            log.error("分页查询 Lubinsun 任务失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public LubinsunTaskDetailVo info(Long id) {
        LubinsunSkillRunTask task = getTask(id);
        List<LubinsunSkillRunEventVo> events = eventRepository
                .findByTaskIdOrderBySeqDesc(task.getId(), PageRequest.of(0, RECENT_EVENT_LIMIT))
                .stream()
                .sorted((left, right) -> Long.compare(defaultSeq(left.getSeq()), defaultSeq(right.getSeq())))
                .map(LubinsunSkillRunEventVo::new)
                .toList();
        return new LubinsunTaskDetailVo(new LubinsunSkillRunTaskVo(task), events);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        LubinsunSkillRunTask task = getTask(id);
        checkNotRunning(task, "运行中的 Lubinsun 任务不能删除");
        taskRepository.deleteById(task.getId());
    }

    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    @Transactional
    public LubinsunSkillRunTaskVo execute(Long id) {
        LubinsunSkillRunTask task = getTask(id);
        checkNotRunning(task, "运行中的 Lubinsun 任务不能重复执行");
        try {
            JsonNode response = platformClient.createRun(buildRunRequest(task));
            Date now = new Date();
            task.setExecutedAt(now);
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            task.setCreateResponseJson(toJson(response));
            applyPlatformCreateResponse(task, response);
            if (task.getStatus() == null || task.getStatus() == LubinsunTaskStatus.DRAFT) {
                task.setStatus(LubinsunTaskStatus.ACCEPTED);
            }
            return new LubinsunSkillRunTaskVo(taskRepository.save(task));
        } catch (Exception e) {
            task.setStatus(LubinsunTaskStatus.EXECUTE_FAILED);
            task.setErrorMessage(trimError(e));
            task.setFinishedAt(new Date());
            log.error("执行 Lubinsun 任务失败, id: {}", task.getId(), e);
            return new LubinsunSkillRunTaskVo(taskRepository.save(task));
        }
    }

    @Override
    @Transactional
    public LubinsunSkillRunTaskVo refresh(Long id) {
        LubinsunSkillRunTask task = getTask(id);
        refreshTask(task);
        return new LubinsunSkillRunTaskVo(taskRepository.save(task));
    }

    @Override
    public List<LubinsunSkillRunEventVo> events(Long id) {
        LubinsunSkillRunTask task = getTask(id);
        return eventRepository.findByTaskIdOrderBySeqAsc(task.getId()).stream()
                .map(LubinsunSkillRunEventVo::new)
                .toList();
    }

    @Override
    @Scheduled(fixedDelayString = "${lubinsun.platform.poll-interval-ms:2000}")
    public void pollActiveTasks() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            List<LubinsunSkillRunTask> tasks = taskRepository.findByStatusInAndRunIdIsNotNull(POLLING_STATUSES);
            for (LubinsunSkillRunTask task : tasks) {
                try {
                    refreshTask(task);
                    taskRepository.save(task);
                } catch (Exception e) {
                    log.warn("轮询 Lubinsun 任务失败, id: {}, runId: {}", task.getId(), task.getRunId(), e);
                    task.setErrorMessage(trimError(e));
                    taskRepository.save(task);
                }
            }
        } finally {
            polling.set(false);
        }
    }

    private void refreshTask(LubinsunSkillRunTask task) {
        if (StringUtils.isBlank(task.getRunId())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "任务尚未执行，没有 run_id");
        }
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            return;
        }

        long after = defaultSeq(task.getLastSeq());
        long maxSeq = after;
        List<JsonNode> events = platformClient.getEvents(task.getRunId(), after, properties.getEventLimit());
        for (JsonNode eventNode : events) {
            Long seq = longValue(eventNode, "seq");
            if (seq == null || seq <= after) {
                continue;
            }
            maxSeq = Math.max(maxSeq, seq);
            saveEventIfAbsent(task, eventNode, seq);
        }
        task.setLastSeq(maxSeq);

        JsonNode snapshot = platformClient.getRun(task.getRunId());
        applyPlatformSnapshot(task, snapshot);
        task.setLastPolledAt(new Date());
    }

    private void saveEventIfAbsent(LubinsunSkillRunTask task, JsonNode eventNode, Long seq) {
        Optional<LubinsunSkillRunEvent> existing = eventRepository.findByTaskIdAndSeq(task.getId(), seq);
        if (existing.isPresent()) {
            return;
        }
        LubinsunSkillRunEvent event = new LubinsunSkillRunEvent()
                .setTaskId(task.getId())
                .setRunId(task.getRunId())
                .setSeq(seq)
                .setEventId(textValue(eventNode, "id"))
                .setSessionId(textValue(eventNode, "session_id", "sessionId"))
                .setUserId(textValue(eventNode, "user_id", "userId"))
                .setType(textValue(eventNode, "type"))
                .setDataJson(dataJson(eventNode))
                .setRawJson(toJson(eventNode))
                .setEventCreatedAt(parseDate(textValue(eventNode, "created_at", "createdAt")));
        eventRepository.save(event);
    }

    private LubinsunPlatformRunRequest buildRunRequest(LubinsunSkillRunTask task) {
        return new LubinsunPlatformRunRequest()
                .setSkill(task.getSkill())
                .setInput(prepareInput(task))
                .setMetadata(prepareMetadata(task))
                .setExternalId(blankToNull(task.getExternalId()))
                .setTitle(blankToNull(task.getPlatformTitle()))
                .setTaskType(blankToNull(task.getTaskType()))
                .setAgent(blankToNull(task.getAgent()));
    }

    private JsonNode prepareInput(LubinsunSkillRunTask task) {
        if (StringUtils.isBlank(task.getIp())) {
            return readRequiredJson(task.getInputJson(), "input");
        }

        List<String> targetIps = splitIpList(task.getIp());
        String ip = String.join(",", targetIps);
        LubinsunSipLogLookupResult lookupResult = sipLogLookupService.lookup(ip);
        task.setEventLogsJson(toJson(JacksonConfig.OBJECT_MAPPER.valueToTree(lookupResult.securityEvents())));
        task.setAlarmLogsJson(toJson(JacksonConfig.OBJECT_MAPPER.valueToTree(lookupResult.securityAlarms())));
        Set<String> attackerIps = collectIps(lookupResult, true);
        Set<String> victimIps = collectIps(lookupResult, false);
        Set<String> relatedIps = new LinkedHashSet<>();
        relatedIps.addAll(targetIps);
        relatedIps.addAll(attackerIps);
        relatedIps.addAll(victimIps);

        ObjectNode input = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        ObjectNode event = input.putObject("event");
        event.put("id", "sip-ip-" + ip);
        event.put("type", "sangfor_sip_ip_investigation");
        event.put("severity", "unknown");
        event.put("source", "zenvis");
        event.put("target_ip", ip);
        event.set("target_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(targetIps));
        event.set("relevant_logs", JacksonConfig.OBJECT_MAPPER.valueToTree(relevantLogs(lookupResult)));

        ObjectNode context = input.putObject("context");
        context.put("timezone", "Asia/Shanghai");
        context.put("locale", "zh-CN");
        context.put("time_window", "latest");
        context.put("log_limit", Math.max(1, Math.min(properties.getSipLogLimit(), 1000)));
        context.put("retrieved_at", OffsetDateTime.now().toString());

        input.putArray("assets");
        addIpAssets(input, new LinkedHashSet<>(targetIps), "target_ip", Set.of());
        addIpAssets(input, attackerIps, "attacker_ip", new LinkedHashSet<>(targetIps));
        addIpAssets(input, victimIps, "victim_ip", new LinkedHashSet<>(targetIps));

        ObjectNode evidence = input.putObject("evidence");
        evidence.put("summary", "根据 IP " + ip + " 关联检索深信服 SIP 攻击者和受害者相关安全事件、告警日志。");
        evidence.put("target_ip", ip);
        evidence.set("target_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(targetIps));
        evidence.set("attacker_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(attackerIps));
        evidence.set("victim_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(victimIps));
        evidence.set("related_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(relatedIps));
        evidence.set("matched_ip_fields", JacksonConfig.OBJECT_MAPPER.valueToTree(Map.of(
                "sangfor_sip_security_event", List.of("ip", "src_ip", "dst_ip"),
                "sangfor_sip_security_alarm", List.of("attack_ip", "suffer_ip", "x_forwarded_for")
        )));
        if (StringUtils.isNotBlank(task.getRawLog())) {
            evidence.put("raw_log", task.getRawLog());
        }
        evidence.put("security_event_count", lookupResult.securityEvents().size());
        evidence.put("security_alarm_count", lookupResult.securityAlarms().size());
        evidence.set("sangfor_sip_security_events", JacksonConfig.OBJECT_MAPPER.valueToTree(lookupResult.securityEvents()));
        evidence.set("sangfor_sip_security_alarms", JacksonConfig.OBJECT_MAPPER.valueToTree(lookupResult.securityAlarms()));
        if (!lookupResult.errors().isEmpty()) {
            evidence.set("lookup_errors", JacksonConfig.OBJECT_MAPPER.valueToTree(lookupResult.errors()));
        }

        task.setInputJson(toJson(input));
        return input;
    }

    private List<Map<String, Object>> relevantLogs(LubinsunSipLogLookupResult lookupResult) {
        List<Map<String, Object>> logs = new ArrayList<>();
        appendRelevantLogs(logs, "sangfor_sip_security_event", lookupResult.securityEvents());
        appendRelevantLogs(logs, "sangfor_sip_security_alarm", lookupResult.securityAlarms());
        return logs;
    }

    private void appendRelevantLogs(List<Map<String, Object>> target, String entity, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> log = new java.util.LinkedHashMap<>();
            log.put("_log_entity", entity);
            log.putAll(row);
            target.add(log);
        }
    }

    private void addIpAssets(ObjectNode input, Set<String> ips, String role, Set<String> targetIps) {
        for (String relatedIp : ips) {
            if (StringUtils.isBlank(relatedIp) || targetIps.contains(relatedIp)) {
                continue;
            }
            ObjectNode asset = input.withArray("assets").addObject();
            asset.put("id", role + "-" + relatedIp);
            asset.put("name", relatedIp);
            asset.put("address", relatedIp);
            asset.put("role", role);
        }
    }

    private Set<String> collectIps(LubinsunSipLogLookupResult lookupResult, boolean attacker) {
        Set<String> ips = new LinkedHashSet<>();
        if (attacker) {
            collectIps(lookupResult.securityEvents(), ips, "src_ip");
            collectIps(lookupResult.securityAlarms(), ips, "attack_ip", "x_forwarded_for");
        } else {
            collectIps(lookupResult.securityEvents(), ips, "dst_ip", "ip");
            collectIps(lookupResult.securityAlarms(), ips, "suffer_ip");
        }
        return ips;
    }

    private void collectIps(List<Map<String, Object>> rows, Set<String> ips, String... fields) {
        for (Map<String, Object> row : rows) {
            for (String field : fields) {
                addIpValue(ips, row.get(field));
            }
        }
    }

    private void addIpValue(Set<String> ips, Object value) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return;
        }
        for (String part : value.toString().split("[,\\s]+")) {
            String candidate = blankToNull(part);
            if (candidate != null) {
                ips.add(candidate);
            }
        }
    }

    private JsonNode prepareMetadata(LubinsunSkillRunTask task) {
        if (StringUtils.isNotBlank(task.getMetadataJson())) {
            return readOptionalJson(task.getMetadataJson());
        }
        if (StringUtils.isBlank(task.getIp())) {
            return null;
        }
        List<String> targetIps = splitIpList(task.getIp());
        ObjectNode metadata = JacksonConfig.OBJECT_MAPPER.createObjectNode();
        metadata.put("source_system", "zenvis");
        metadata.put("requested_by", "soc-operator");
        metadata.put("tenant_id", "tenant-demo");
        metadata.put("locale", "zh");
        metadata.put("target_ip", String.join(",", targetIps));
        metadata.set("target_ips", JacksonConfig.OBJECT_MAPPER.valueToTree(targetIps));
        task.setMetadataJson(toJson(metadata));
        return metadata;
    }

    private void applyDto(LubinsunSkillRunTask task, LubinsunTaskDto dto) {
        String ip = blankToNull(dto.getIp());
        boolean ipMode = StringUtils.isNotBlank(ip);
        if (ipMode) {
            ip = String.join(",", splitIpList(ip));
        }
        task.setName(defaultTaskName(dto.getName(), ip));
        task.setSkill(StringUtils.defaultIfBlank(blankToNull(dto.getSkill()), ipMode ? DEFAULT_IP_SKILL : null));
        task.setAgent(StringUtils.defaultIfBlank(blankToNull(dto.getAgent()), ipMode ? DEFAULT_IP_AGENT : null));
        task.setExternalId(blankToNull(dto.getExternalId()));
        task.setPlatformTitle(blankToNull(dto.getTitle()));
        task.setTaskType(StringUtils.defaultIfBlank(blankToNull(dto.getTaskType()), ipMode ? DEFAULT_IP_TASK_TYPE : null));
        task.setIp(ip);
        task.setRawLog(blankToNull(dto.getRawLog()));
        if (StringUtils.isNotBlank(dto.getInputJson())) {
            task.setInputJson(normalizeJson(dto.getInputJson(), "input"));
        } else if (StringUtils.isNotBlank(task.getIp())) {
            task.setInputJson(null);
            task.setEventLogsJson(null);
            task.setAlarmLogsJson(null);
        }
        task.setMetadataJson(normalizeOptionalJson(dto.getMetadataJson()));
    }

    private void applyPlatformCreateResponse(LubinsunSkillRunTask task, JsonNode response) {
        task.setRunId(textValue(response, "run_id", "runId"));
        task.setSessionId(textValue(response, "session_id", "sessionId"));
        task.setPlatformTaskId(textValue(response, "task_id", "taskId"));
        task.setResponseTitle(textValue(response, "title"));
        task.setExternalId(StringUtils.defaultIfBlank(textValue(response, "external_id", "externalId"), task.getExternalId()));
        task.setTaskType(StringUtils.defaultIfBlank(textValue(response, "task_type", "taskType"), task.getTaskType()));
        task.setAgent(StringUtils.defaultIfBlank(textValue(response, "agent"), task.getAgent()));
        task.setPlatformStatus(textValue(response, "status"));
        task.setStatus(LubinsunTaskStatus.fromPlatformStatus(task.getPlatformStatus()));
        task.setSummary(textValue(response, "summary"));
        task.setResultSummary(textValue(response, "result_summary", "resultSummary"));
        task.setKeyPointsJson(nodeJson(response, "key_points", "keyPoints"));
    }

    private void applyPlatformSnapshot(LubinsunSkillRunTask task, JsonNode snapshot) {
        task.setSnapshotJson(toJson(snapshot));
        task.setPlatformStatus(textValue(snapshot, "status"));
        LubinsunTaskStatus status = LubinsunTaskStatus.fromPlatformStatus(task.getPlatformStatus());
        task.setStatus(status);
        task.setSummary(textValue(snapshot, "summary"));
        task.setResultSummary(textValue(snapshot, "result_summary", "resultSummary"));
        task.setKeyPointsJson(nodeJson(snapshot, "key_points", "keyPoints"));
        task.setResultJson(nodeJson(snapshot, "result"));
        task.setPublicResultJson(nodeJson(snapshot, "public_result", "publicResult"));
        task.setPendingPermissionsJson(nodeJson(snapshot, "pending_permissions", "pendingPermissions"));
        if (status.isTerminal()) {
            task.setFinishedAt(new Date());
        }
    }

    private LubinsunSkillRunTask getTask(Long id) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return taskRepository.findById(Math.toIntExact(id))
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "Lubinsun 任务不存在"));
    }

    private static void checkCreateOrUpdate(LubinsunTaskDto dto) {
        if (dto == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        boolean ipMode = StringUtils.isNotBlank(dto.getIp());
        if (ipMode && splitIpList(dto.getIp()).isEmpty()) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "IP不能为空");
        }
        if (!ipMode && (StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getSkill()))) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (!ipMode && StringUtils.isBlank(dto.getInputJson())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "IP或input不能为空");
        }
    }

    private static void checkNotRunning(LubinsunSkillRunTask task, String message) {
        if (task.getStatus() != null && task.getStatus().isRunning()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
        }
    }

    private static String normalizeJson(String json, String fieldName) {
        return toJson(readRequiredJson(json, fieldName));
    }

    private static String normalizeOptionalJson(String json) {
        JsonNode node = readOptionalJson(json);
        return node == null ? null : toJson(node);
    }

    private static JsonNode readRequiredJson(String json, String fieldName) {
        if (StringUtils.isBlank(json)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), fieldName + "不能为空");
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), fieldName + "必须是合法 JSON");
        }
    }

    private static JsonNode readOptionalJson(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "metadata必须是合法 JSON");
        }
    }

    private static String toJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private static String dataJson(JsonNode eventNode) {
        JsonNode data = firstNode(eventNode, "data", "data_json", "dataJson");
        if (data == null || data.isMissingNode() || data.isNull()) {
            return null;
        }
        if (data.isTextual()) {
            return data.asText();
        }
        return toJson(data);
    }

    private static String nodeJson(JsonNode node, String... fieldNames) {
        JsonNode value = firstNode(node, fieldNames);
        return toJson(value);
    }

    private static String textValue(JsonNode node, String... fieldNames) {
        JsonNode value = firstNode(node, fieldNames);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || !value.canConvertToLong()) {
            return null;
        }
        return value.asLong();
    }

    private static Date parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Date.from(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String defaultTaskName(String name, String ip) {
        if (StringUtils.isNotBlank(name) && !DEFAULT_IP_TASK_NAME.equals(name.trim())) {
            return name.trim();
        }
        if (StringUtils.isNotBlank(ip)) {
            return DEFAULT_IP_TASK_NAME + "-" + ip.trim();
        }
        return blankToNull(name);
    }

    private static List<String> splitIpList(String ipExpression) {
        if (StringUtils.isBlank(ipExpression)) {
            return List.of();
        }
        Set<String> ips = new LinkedHashSet<>();
        for (String part : ipExpression.split(",")) {
            String ip = blankToNull(part);
            if (ip != null) {
                ips.add(ip);
            }
        }
        return List.copyOf(ips);
    }

    private static long defaultSeq(Long seq) {
        return seq == null ? 0L : seq;
    }

    private static String trimError(Exception e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return message;
    }
}
