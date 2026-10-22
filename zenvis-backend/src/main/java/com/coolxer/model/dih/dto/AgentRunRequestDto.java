package com.coolxer.model.dih.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.coolxer.model.dih.ChatAttachment;
import lombok.Data;

import java.util.List;

@Data
public class AgentRunRequestDto {
    @JsonAlias("chatId")
    private String chatId;
    private String type;
    private String message;
    private String model;
    private List<ChatAttachment> attachments;
    @JsonAlias("reportAction")
    private ReportActionDto reportAction;
    @JsonAlias("clientMessageId")
    private String clientMessageId;
}
