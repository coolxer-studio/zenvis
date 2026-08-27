package com.coolxer.service.dih.demo;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores deterministic demo routing state in the server-owned session extension.
 */
@Service
public class AgentDemoStateStore {

    public static final String RESERVED_KEY = "agentDemos";

    private static final String ACTIVE_BY_AGENT_KEY = "activeByAgent";

    private static final String DEMOS_KEY = "demos";

    public void activate(ChatSession chatSession, String demoId, String agentType) {
        activate(chatSession, demoId, agentType, "active");
    }

    public void activate(
            ChatSession chatSession,
            String demoId,
            String agentType,
            String stage) {
        if (chatSession == null
                || !StringUtils.hasText(demoId)
                || !StringUtils.hasText(agentType)) {
            return;
        }
        Map<String, Object> root = parse(chatSession.getExtraData());
        Map<String, Object> section = map(root.get(RESERVED_KEY));
        Map<String, Object> demos = map(section.get(DEMOS_KEY));
        Map<String, Object> state = map(demos.get(demoId));
        String now = Instant.now().toString();
        state.put("demoId", demoId);
        state.put("agentType", agentType);
        state.putIfAbsent("startedAt", now);
        state.put("updatedAt", now);
        state.put("stage", StringUtils.hasText(stage) ? stage : "active");
        demos.put(demoId, state);
        section.put(DEMOS_KEY, demos);

        Map<String, Object> activeByAgent = map(section.get(ACTIVE_BY_AGENT_KEY));
        activeByAgent.put(agentType, demoId);
        section.put(ACTIVE_BY_AGENT_KEY, activeByAgent);
        root.put(RESERVED_KEY, section);
        chatSession.setExtraData(JacksonUtil.toJson(root));
    }

    public Optional<String> activeDemoId(ChatSession chatSession, String agentType) {
        if (chatSession == null || !StringUtils.hasText(agentType)) {
            return Optional.empty();
        }
        Map<String, Object> section = map(parse(chatSession.getExtraData()).get(RESERVED_KEY));
        Object value = map(section.get(ACTIVE_BY_AGENT_KEY)).get(agentType);
        return value instanceof String demoId && StringUtils.hasText(demoId)
                ? Optional.of(demoId)
                : Optional.empty();
    }

    public boolean isActive(ChatSession chatSession) {
        if (chatSession == null) {
            return false;
        }
        Map<String, Object> section = map(parse(chatSession.getExtraData()).get(RESERVED_KEY));
        return !map(section.get(ACTIVE_BY_AGENT_KEY)).isEmpty();
    }

    public boolean isActive(
            ChatSession chatSession,
            String demoId,
            String agentType) {
        return StringUtils.hasText(demoId)
                && activeDemoId(chatSession, agentType)
                .filter(demoId::equals)
                .isPresent();
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

    private Map<String, Object> parse(String value) {
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
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) source);
    }
}
