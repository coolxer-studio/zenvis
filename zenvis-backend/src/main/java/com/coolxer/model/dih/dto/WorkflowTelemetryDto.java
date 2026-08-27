package com.coolxer.model.dih.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowTelemetryDto {

    @NotBlank
    @JsonAlias("chat_id")
    private String chatId;

    @NotBlank
    @JsonAlias("workflow_id")
    private String workflowId;

    @NotBlank
    private String event;

    private String detail;
}
