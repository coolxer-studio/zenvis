package com.coolxer.model.dih.dto;

import com.coolxer.model.dih.ChatAttachment;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一 Run 交互响应。定位字段由服务端交互记录提供。
 */
@Data
public class AgentRunInteractionResponseDto {
    private String message;
    private List<ChatAttachment> attachments;
    private String action;
    private List<Map<String, Object>> answers;
    @JsonAlias("stateRevision")
    private Integer stateRevision;
    private String revision;
    private String decision;
    private String comment;
}
