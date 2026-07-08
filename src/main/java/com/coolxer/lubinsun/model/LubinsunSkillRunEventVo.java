package com.coolxer.lubinsun.model;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.entity.LubinsunSkillRunEvent;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class LubinsunSkillRunEventVo implements Serializable {

    private Integer id;
    private Integer taskId;
    private String runId;
    private Long seq;
    private String eventId;
    private String sessionId;
    private String userId;
    private String type;
    private JsonNode data;
    private JsonNode raw;
    private Date eventCreatedAt;
    private Date createTime;

    public LubinsunSkillRunEventVo(LubinsunSkillRunEvent event) {
        this.id = event.getId();
        this.taskId = event.getTaskId();
        this.runId = event.getRunId();
        this.seq = event.getSeq();
        this.eventId = event.getEventId();
        this.sessionId = event.getSessionId();
        this.userId = event.getUserId();
        this.type = event.getType();
        this.data = readJson(event.getDataJson());
        this.raw = readJson(event.getRawJson());
        this.eventCreatedAt = event.getEventCreatedAt();
        this.createTime = event.getCreateTime();
    }

    private static JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            return JacksonConfig.OBJECT_MAPPER.getNodeFactory().textNode(json);
        }
    }
}
