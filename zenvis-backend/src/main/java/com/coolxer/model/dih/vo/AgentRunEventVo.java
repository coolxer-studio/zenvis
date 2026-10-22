package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.AgentRunEvent;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRunEventVo {
    private Long sequence;
    @JsonProperty("run_id")
    private String runId;
    private String type;
    @JsonProperty("step_id")
    private String stepId;
    @JsonProperty("created_at")
    private Date createdAt;
    private Object data;

    public static AgentRunEventVo from(AgentRunEvent event, Object data) {
        return AgentRunEventVo.builder()
                .sequence(event.getSequence())
                .runId(event.getRunId())
                .type(event.getType())
                .stepId(event.getStepId())
                .createdAt(event.getCreateTime() == null ? new Date() : event.getCreateTime())
                .data(data)
                .build();
    }

    public static AgentRunEventVo from(AgentRunEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (event.getPayload() != null && !event.getPayload().isBlank()) {
            try {
                payload = JacksonUtil.toMap(
                        event.getPayload(), new TypeReference<Map<String, Object>>() {});
            } catch (RuntimeException ignored) {
                payload.put("raw", event.getPayload());
            }
        }
        return from(event, payload);
    }
}
