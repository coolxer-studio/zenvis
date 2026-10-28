package com.coolxer.service.dih.workflow;

import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VisualizationDashboardWorkflowDefinition extends AbstractVisualizationWorkflowDefinition {

    @Override
    public String agentType() {
        return VisualizationAgentTypes.DASHBOARD;
    }

    @Override
    public String workflowType() {
        return "visualization_dashboard";
    }

    @Override
    public String defaultObjectType() {
        return "dashboard";
    }

    @Override
    protected boolean writable() {
        return true;
    }

    @Override
    protected Set<String> domainPartTypes() {
        return Set.of("visualization-dashboard-record");
    }

    @Override
    protected Set<String> domainRecordTypes() {
        return Set.of("visualization-dashboard-record");
    }

    @Override
    protected String objectTypeForRecord(String partType) {
        return "dashboard";
    }
}
