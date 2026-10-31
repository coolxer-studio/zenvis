package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AgentRunInteractionResponseVo {
    private boolean accepted;
    private boolean duplicate;
    private String interactionId;
    private String partStatus;
    private String summary;
    private AgentRunVo run;
}
