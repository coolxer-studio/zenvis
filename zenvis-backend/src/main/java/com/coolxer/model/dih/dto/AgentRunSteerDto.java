package com.coolxer.model.dih.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.coolxer.model.dih.ChatAttachment;
import lombok.Data;

import java.util.List;

@Data
public class AgentRunSteerDto {
    private String message;
    private List<ChatAttachment> attachments;
    @JsonAlias("clientMessageId")
    private String clientMessageId;
}
