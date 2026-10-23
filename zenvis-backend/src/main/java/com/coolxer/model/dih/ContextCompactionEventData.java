package com.coolxer.model.dih;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes one real context compaction step visible in an Agent Run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ContextCompactionEventData {

    public static final String STAGE_RUN_CONTEXT = "run-context";
    public static final String STAGE_MODEL_INPUT = "model-input";
    public static final String STAGE_HISTORY_WINDOW = "history-window";
    public static final String STAGE_ATTACHMENT_MEMORY = "attachment-memory";

    private String compactionId;
    private String stage;
    private String reason;
    private Integer beforeTokens;
    private Integer afterTokens;
    private Integer savedTokens;
    private Integer beforeChars;
    private Integer afterChars;
    private Integer budgetTokens;
    private Integer budgetChars;
    private Integer omittedMessages;
    private Integer retainedMessages;
    private Integer truncatedMessages;
    private Integer omittedAttachments;
    private Integer includedAttachments;
    private String summaryPreview;
}
