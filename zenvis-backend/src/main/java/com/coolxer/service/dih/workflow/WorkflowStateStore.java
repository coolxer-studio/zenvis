package com.coolxer.service.dih.workflow;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowStateStore {

    public static final String RESERVED_KEY = "agentWorkflows";

    private static final String WORKFLOWS_KEY = "workflows";

    private static final String ACTIVE_BY_AGENT_KEY = "activeByAgent";

    public Optional<AgentWorkflowState> loadActive(ChatSession chatSession, String agentType) {
        if (chatSession == null || !StringUtils.hasText(agentType)) {
            return Optional.empty();
        }
        Map<String, Object> root = parse(chatSession.getExtraData());
        Map<String, Object> section = map(root.get(RESERVED_KEY));
        String workflowId = string(map(section.get(ACTIVE_BY_AGENT_KEY)).get(agentType));
        return load(section, workflowId);
    }

    public boolean hasWorkflowSection(ChatSession chatSession) {
        return chatSession != null
                && parse(chatSession.getExtraData()).containsKey(RESERVED_KEY);
    }

    public Optional<AgentWorkflowState> load(ChatSession chatSession, String workflowId) {
        if (chatSession == null) {
            return Optional.empty();
        }
        return load(map(parse(chatSession.getExtraData()).get(RESERVED_KEY)), workflowId);
    }

    public void upsert(ChatSession chatSession, AgentWorkflowState state) {
        if (chatSession == null || state == null || !StringUtils.hasText(state.getWorkflowId())) {
            return;
        }
        Map<String, Object> root = parse(chatSession.getExtraData());
        Map<String, Object> section = new LinkedHashMap<>(map(root.get(RESERVED_KEY)));
        Map<String, Object> workflows = new LinkedHashMap<>(map(section.get(WORKFLOWS_KEY)));
        Object existing = workflows.get(state.getWorkflowId());
        if (revision(existing) > state.getStateRevision()) {
            return;
        }
        workflows.put(state.getWorkflowId(), JacksonUtil.toMap(state));
        section.put(WORKFLOWS_KEY, workflows);
        Map<String, Object> activeByAgent =
                new LinkedHashMap<>(map(section.get(ACTIVE_BY_AGENT_KEY)));
        if (StringUtils.hasText(state.getAgentType())
                && !"completed".equals(state.getStatus())
                && !"cancelled".equals(state.getStatus())) {
            activeByAgent.put(state.getAgentType(), state.getWorkflowId());
        } else if (StringUtils.hasText(state.getAgentType())
                && state.getWorkflowId().equals(string(activeByAgent.get(state.getAgentType())))) {
            activeByAgent.remove(state.getAgentType());
        }
        section.put(ACTIVE_BY_AGENT_KEY, activeByAgent);
        root.put(RESERVED_KEY, section);
        chatSession.setExtraData(JacksonUtil.toJson(root));
    }

    public String preserveReserved(String currentExtraData, String clientExtraData) {
        Map<String, Object> current = parse(currentExtraData);
        Map<String, Object> incoming = parse(clientExtraData);
        if (current.containsKey(RESERVED_KEY)) {
            incoming.put(RESERVED_KEY, current.get(RESERVED_KEY));
        } else {
            incoming.remove(RESERVED_KEY);
        }
        return JacksonUtil.toJson(incoming);
    }

    /**
     * Merges a possibly stale in-memory workflow snapshot with the current one.
     * Tool calls persist their own workflow updates while the surrounding chat run
     * still holds the session object loaded at the start of the turn. Comparing
     * state revisions prevents that stale snapshot from rolling back a successful
     * validation and its locked candidate digests.
     */
    public String mergeReserved(String currentExtraData, String incomingExtraData) {
        Map<String, Object> current = parse(currentExtraData);
        Map<String, Object> incoming = parse(incomingExtraData);
        Map<String, Object> merged = new LinkedHashMap<>(current);
        incoming.forEach((key, value) -> {
            if (!RESERVED_KEY.equals(key)) {
                merged.put(key, value);
            }
        });
        if (current.containsKey(RESERVED_KEY) || incoming.containsKey(RESERVED_KEY)) {
            merged.put(RESERVED_KEY, mergeSection(
                    map(current.get(RESERVED_KEY)), map(incoming.get(RESERVED_KEY))));
        }
        return JacksonUtil.toJson(merged);
    }

    private Map<String, Object> mergeSection(Map<String, Object> current,
                                             Map<String, Object> incoming) {
        Map<String, Object> section = new LinkedHashMap<>(current);
        Map<String, Object> currentWorkflows = map(current.get(WORKFLOWS_KEY));
        Map<String, Object> incomingWorkflows = map(incoming.get(WORKFLOWS_KEY));
        Map<String, Object> workflows = new LinkedHashMap<>(currentWorkflows);
        incomingWorkflows.forEach((workflowId, value) -> {
            if (!(value instanceof Map<?, ?>)) {
                return;
            }
            if (!workflows.containsKey(workflowId)
                    || revision(value) > revision(workflows.get(workflowId))) {
                workflows.put(workflowId, value);
            }
        });
        if (!workflows.isEmpty()) {
            section.put(WORKFLOWS_KEY, workflows);
        }

        Map<String, Object> active = new LinkedHashMap<>(
                map(current.get(ACTIVE_BY_AGENT_KEY)));
        map(incoming.get(ACTIVE_BY_AGENT_KEY)).forEach((agentType, workflowId) -> {
            if (!(workflowId instanceof String id) || !StringUtils.hasText(id)) {
                return;
            }
            String currentId = string(active.get(agentType));
            if (!StringUtils.hasText(currentId)
                    || (!currentId.equals(id)
                    && revision(workflows.get(id)) > revision(workflows.get(currentId)))) {
                active.put(agentType, id);
            }
        });
        active.entrySet().removeIf(entry -> {
            Object workflow = workflows.get(string(entry.getValue()));
            String status = workflow instanceof Map<?, ?> map
                    ? string(map.get("status")) : "";
            return "completed".equals(status) || "cancelled".equals(status);
        });
        section.put(ACTIVE_BY_AGENT_KEY, active);
        return section;
    }

    private long revision(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return -1L;
        }
        Object revision = map.get("stateRevision");
        if (revision == null) {
            // Workflow snapshots are serialized by the global Jackson mapper with
            // snake_case names, while card metadata uses camelCase.
            revision = map.get("state_revision");
        }
        if (revision instanceof Number number) {
            return number.longValue();
        }
        if (revision != null) {
            try {
                return Long.parseLong(revision.toString());
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }

    public Map<String, Object> parse(String value) {
        if (!StringUtils.hasText(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(JacksonUtil.toMap(
                    value, new TypeReference<Map<String, Object>>() {
                    }));
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) source);
    }

    private Optional<AgentWorkflowState> load(
            Map<String, Object> section,
            String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return Optional.empty();
        }
        Object raw = map(section.get(WORKFLOWS_KEY)).get(workflowId);
        if (!(raw instanceof Map<?, ?>)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JacksonConfig.OBJECT_MAPPER.convertValue(
                    raw, AgentWorkflowState.class));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String string(Object value) {
        return value instanceof String text ? text : "";
    }
}
