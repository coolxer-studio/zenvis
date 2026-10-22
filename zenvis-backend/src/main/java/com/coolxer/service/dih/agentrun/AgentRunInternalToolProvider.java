package com.coolxer.service.dih.agentrun;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.model.dih.agentrun.AgentRunPlan;
import com.coolxer.model.dih.agentrun.AgentRunPlanStep;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 持久化 Run 的内部控制工具。工具只修改 Run 快照，不执行任意系统操作。
 */
public class AgentRunInternalToolProvider implements ToolCallbackProvider {

    private final AgentRunService runService;
    private final ReportDraftService reportDraftService;
    private final AgentRunExecutionState state;
    private final User user;
    private final String agentType;
    private final List<ToolCallback> callbacks;

    public AgentRunInternalToolProvider(AgentRunService runService,
                                        ReportDraftService reportDraftService,
                                        AgentRunExecutionState state,
                                        User user,
                                        String agentType) {
        this.runService = runService;
        this.reportDraftService = reportDraftService;
        this.state = state;
        this.user = user;
        this.agentType = agentType;
        this.callbacks = buildCallbacks();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return callbacks.toArray(ToolCallback[]::new);
    }

    private List<ToolCallback> buildCallbacks() {
        List<ToolCallback> result = new ArrayList<>();
        result.add(callback("run_update_plan",
                "更新当前任务的完整执行计划。每次调整计划后调用。",
                """
                {"type":"object","properties":{"steps":{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"},"title":{"type":"string"},"acceptance":{"type":"string"},"status":{"type":"string","enum":["PENDING","RUNNING","DONE","SKIPPED","BLOCKED"]}},"required":["id","title"]}}},"required":["steps"]}
                """,
                input -> updatePlan(input)));
        result.add(callback("run_update_step",
                "更新一个计划步骤的状态、摘要和证据引用。完成步骤后必须调用。",
                """
                {"type":"object","properties":{"stepId":{"type":"string"},"status":{"type":"string","enum":["PENDING","RUNNING","DONE","SKIPPED","BLOCKED"]},"summary":{"type":"string"},"evidenceRefs":{"type":"array","items":{"type":"object"}}},"required":["stepId","status"]}
                """,
                input -> updateStep(input)));
        result.add(callback("run_request_user",
                "当缺少必须由用户提供的信息时，暂停任务并向用户提问。",
                """
                {"type":"object","properties":{"question":{"type":"string"}},"required":["question"]}
                """,
                input -> requestUser(input)));
        result.add(callback("run_finish",
                "只有在所有计划步骤完成或明确可跳过时调用，提交最终答复摘要。",
                """
                {"type":"object","properties":{"summary":{"type":"string"}},"required":["summary"]}
                """,
                input -> finish(input)));
        if ("agent_report".equals(agentType)) {
            addReportCallbacks(result);
        }
        return result;
    }

    private void addReportCallbacks(List<ToolCallback> result) {
        result.add(callback("report_prepare_document",
                "准备或打开报表草稿。生成多章节报表时必须先调用。",
                """
                {"type":"object","properties":{"documentId":{"type":"string"},"title":{"type":"string"},"format":{"type":"string","enum":["markdown","html"]},"baseRevision":{"type":"integer"},"sourceRefs":{"type":"array","items":{"type":"object"}}},"required":["title"]}
                """,
                input -> prepareReport(input)));
        result.add(callback("report_upsert_section",
                "写入或替换报表的一个章节。单章节不得超过 2400 字符；不要重新输出其他已完成章节。",
                """
                {"type":"object","properties":{"documentId":{"type":"string"},"sectionKey":{"type":"string"},"title":{"type":"string"},"content":{"type":"string"},"order":{"type":"integer"},"sourceRefs":{"type":"array","items":{"type":"object"}}},"required":["documentId","sectionKey","title","content"]}
                """,
                input -> upsertReportSection(input)));
        result.add(callback("report_replace_section",
                "替换报表中已有章节。单章节不得超过 2400 字符。",
                """
                {"type":"object","properties":{"documentId":{"type":"string"},"sectionKey":{"type":"string"},"title":{"type":"string"},"content":{"type":"string"},"order":{"type":"integer"},"sourceRefs":{"type":"array","items":{"type":"object"}}},"required":["documentId","sectionKey","content"]}
                """,
                input -> upsertReportSection(input)));
        result.add(callback("report_reorder_sections",
                "调整报表章节顺序。仅在结构需要调整时调用。",
                """
                {"type":"object","properties":{"documentId":{"type":"string"},"orderedKeys":{"type":"array","items":{"type":"string"}}},"required":["documentId","orderedKeys"]}
                """,
                input -> reorderReport(input)));
        result.add(callback("report_finalize",
                "校验并提交报表草稿，生成新的不可变修订。所有章节写完后调用。",
                """
                {"type":"object","properties":{"documentId":{"type":"string"}},"required":["documentId"]}
                """,
                input -> finalizeReport(input)));
    }

    private Map<String, Object> updatePlan(String input) {
        Map<String, Object> payload = parse(input);
        List<Map<String, Object>> rawSteps = listOfMaps(payload.get("steps"));
        List<AgentRunPlanStep> steps = new ArrayList<>();
        for (Map<String, Object> raw : rawSteps) {
            steps.add(AgentRunPlanStep.builder()
                    .id(stringValue(raw.get("id")))
                    .title(stringValue(raw.get("title")))
                    .acceptance(stringValue(raw.get("acceptance")))
                    .status(StringUtils.hasText(stringValue(raw.get("status")))
                            ? stringValue(raw.get("status")) : "PENDING")
                    .summary(stringValue(raw.get("summary")))
                    .evidenceRefs(listOfMaps(raw.get("evidenceRefs")))
                    .build());
        }
        if (steps.isEmpty()) {
            return error("计划至少需要一个步骤");
        }
        AgentRunPlan plan = AgentRunPlan.builder()
                .goal(firstNonBlank(stringValue(payload.get("goal")),
                        runService.get(state.getRunId(), user).getGoal()))
                .steps(steps)
                .build();
        AgentRun updated = runService.updatePlan(state.getRunId(), plan, state.getStepId());
        state.markPlanUpdated();
        return ok(Map.of("revision", updated.getPlanRevision(), "steps", steps.size()));
    }

    private Map<String, Object> updateStep(String input) {
        Map<String, Object> payload = parse(input);
        String stepId = stringValue(payload.get("stepId"));
        String status = stringValue(payload.get("status"));
        if (!StringUtils.hasText(stepId) || !StringUtils.hasText(status)) {
            return error("stepId 和 status 不能为空");
        }
        runService.updateStep(state.getRunId(), stepId, status,
                stringValue(payload.get("summary")), listOfMaps(payload.get("evidenceRefs")));
        state.markStepUpdated();
        return ok(Map.of("stepId", stepId, "status", status));
    }

    private Map<String, Object> requestUser(String input) {
        String question = stringValue(parse(input).get("question"));
        if (!StringUtils.hasText(question)) {
            return error("question 不能为空");
        }
        runService.markWaitingUser(state.getRunId(), question, state.getStepId());
        state.requestUser(question);
        return ok(Map.of("status", "WAITING_USER"));
    }

    private Map<String, Object> finish(String input) {
        String summary = stringValue(parse(input).get("summary"));
        state.finish(summary);
        return ok(Map.of("status", "FINISH_REQUESTED"));
    }

    private Map<String, Object> prepareReport(String input) {
        Map<String, Object> payload = parse(input);
        Map<String, Object> result = reportDraftService.prepare(
                state.getRunId(),
                state.getStepId(),
                user,
                stringValue(payload.get("documentId")),
                stringValue(payload.get("title")),
                stringValue(payload.get("format")),
                longValue(payload.get("baseRevision")),
                listOfMaps(payload.get("sourceRefs")));
        state.setReportDocumentId(stringValue(result.get("documentId")));
        state.markReportUpdated();
        return ok(result);
    }

    private Map<String, Object> upsertReportSection(String input) {
        Map<String, Object> payload = parse(input);
        String documentId = firstNonBlank(
                stringValue(payload.get("documentId")), state.getReportDocumentId());
        Map<String, Object> result = reportDraftService.upsertSection(
                state.getRunId(),
                state.getStepId(),
                user,
                documentId,
                stringValue(payload.get("sectionKey")),
                stringValue(payload.get("title")),
                stringValue(payload.get("content")),
                integerValue(payload.get("order")),
                listOfMaps(payload.get("sourceRefs")));
        state.markReportUpdated();
        return ok(result);
    }

    private Map<String, Object> reorderReport(String input) {
        Map<String, Object> payload = parse(input);
        Map<String, Object> result = reportDraftService.reorder(
                state.getRunId(),
                state.getStepId(),
                user,
                firstNonBlank(stringValue(payload.get("documentId")), state.getReportDocumentId()),
                listOfStrings(payload.get("orderedKeys")));
        state.markReportUpdated();
        return ok(result);
    }

    private Map<String, Object> finalizeReport(String input) {
        Map<String, Object> payload = parse(input);
        Map<String, Object> result = reportDraftService.finalizeDraft(
                state.getRunId(),
                state.getStepId(),
                user,
                firstNonBlank(stringValue(payload.get("documentId")), state.getReportDocumentId()));
        state.markReportUpdated();
        return ok(result);
    }

    private ToolCallback callback(String name,
                                  String description,
                                  String schema,
                                  Function<String, Map<String, Object>> handler) {
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
        return new JsonCallback(definition, handler);
    }

    private Map<String, Object> parse(String input) {
        if (!StringUtils.hasText(input)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(JacksonUtil.toMap(
                    input, new TypeReference<Map<String, Object>>() {}));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("工具参数不是合法 JSON: " + e.getMessage());
        }
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "error");
        result.put("error", message);
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                map.forEach((key, nested) -> copied.put(String.valueOf(key), nested));
                result.add(copied);
            }
        }
        return result;
    }

    private List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static final class JsonCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final Function<String, Map<String, Object>> handler;

        private JsonCallback(ToolDefinition definition,
                             Function<String, Map<String, Object>> handler) {
            this.definition = definition;
            this.handler = handler;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            try {
                return JacksonUtil.toJson(handler.apply(toolInput));
            } catch (RuntimeException e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("status", "error");
                failure.put("error", e.getMessage() == null ? "工具执行失败" : e.getMessage());
                return JacksonUtil.toJson(failure);
            }
        }
    }
}
