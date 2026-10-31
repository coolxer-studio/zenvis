package com.coolxer.model.dih.agentrun;

import com.coolxer.dao.mysql.entity.AgentRun;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 持久化 Run 的唯一待处理用户交互。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunInteraction {

    public static final String KIND_USER_INPUT = "user_input";
    public static final String KIND_CARD_DECISION = "card_decision";
    public static final String KIND_MCP_APPROVAL = "mcp_approval";

    private String interactionId;
    private String kind;
    private String messageId;
    private String partId;
    private String workflowId;
    private Integer stateRevision;
    private String requestId;
    private String title;
    private String prompt;
    private List<String> allowedActions;
    private Date createdAt;

    public static AgentRunInteraction fromRun(AgentRun run) {
        if (run == null) {
            return null;
        }
        if (StringUtils.isNotBlank(run.getInteractionJson())) {
            try {
                AgentRunInteraction interaction = JacksonUtil.toObject(
                        run.getInteractionJson(), AgentRunInteraction.class);
                if (interaction != null && StringUtils.isNotBlank(interaction.getInteractionId())) {
                    return interaction;
                }
            } catch (RuntimeException ignored) {
                // Fall through to the legacy pending action representation.
            }
        }
        return legacyFromRun(run);
    }

    private static AgentRunInteraction legacyFromRun(AgentRun run) {
        if (StringUtils.isBlank(run.getPendingAction())) {
            return null;
        }
        Map<String, Object> action;
        try {
            action = JacksonUtil.toMap(run.getPendingAction(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (RuntimeException ignored) {
            return null;
        }
        String type = text(action.get("type"));
        String interactionId = legacyId(run.getRunId(), run.getPendingAction());
        if ("approval".equals(type)) {
            String requestId = text(action.get("requestId"));
            if (StringUtils.isBlank(requestId)) {
                return null;
            }
            return AgentRunInteraction.builder()
                    .interactionId(interactionId)
                    .kind(KIND_MCP_APPROVAL)
                    .requestId(requestId)
                    .title("MCP 工具审批")
                    .prompt("该工具需要你的明确许可后才能执行。")
                    .createdAt(new Date())
                    .build();
        }
        String question = text(action.get("question"));
        if (!"user".equals(type) && StringUtils.isBlank(question)) {
            return null;
        }
        return AgentRunInteraction.builder()
                .interactionId(interactionId)
                .kind(KIND_USER_INPUT)
                .title("需要补充信息")
                .prompt(StringUtils.defaultIfBlank(question, run.getPausedReason()))
                .createdAt(new Date())
                .build();
    }

    public static String legacyId(String runId, String content) {
        String raw = StringUtils.defaultString(runId) + ":" + StringUtils.defaultString(content);
        return "legacy-" + UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
