package com.coolxer.model.dih.agentrun;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunPlan {
    private String goal;
    @Builder.Default
    private List<AgentRunPlanStep> steps = new ArrayList<>();
}
