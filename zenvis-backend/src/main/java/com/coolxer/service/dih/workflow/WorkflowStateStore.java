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
