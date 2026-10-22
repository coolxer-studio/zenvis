package com.coolxer.model.dih.agentrun;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunPlanStep {
    private String id;
    private String title;
    private String acceptance;
    private String status;
    private String summary;
    @Builder.Default
    private List<Map<String, Object>> evidenceRefs = new ArrayList<>();
}
