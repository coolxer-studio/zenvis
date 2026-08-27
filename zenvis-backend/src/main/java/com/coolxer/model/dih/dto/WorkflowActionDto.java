package com.coolxer.model.dih.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowActionDto {

    @NotBlank
    @JsonAlias("chat_id")
    private String chatId;

    @NotBlank
    @JsonAlias("message_id")
    private String messageId;

    @NotBlank
    @JsonAlias("part_id")
    private String partId;

    @NotBlank
    @JsonAlias("workflow_id")
    private String workflowId;

    @NotBlank
    private String action;

    private List<Map<String, Object>> answers;

    private String revision;
}
