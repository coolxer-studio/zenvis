package com.coolxer.service.dih.workflow;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AgentWorkflowState {

    public static final String VERSION = "1";

    private String workflowId;

    private String workflowVersion = VERSION;

    private String agentType;

    private String workflowType;

    private String objectType;

    private AgentWorkflowStep step;

    private String status = "active";

    private String messageId;

    private String partId;

    private String artifactId;

    private long stateRevision;

    private Map<String, Object> context = new LinkedHashMap<>();

    private List<Map<String, Object>> evidenceRefs = new ArrayList<>();

    private List<Map<String, Object>> failures = new ArrayList<>();

    private String createdAt;

    private String updatedAt;
}
