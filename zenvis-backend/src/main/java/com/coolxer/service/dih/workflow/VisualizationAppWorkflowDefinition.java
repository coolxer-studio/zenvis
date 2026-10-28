package com.coolxer.service.dih.workflow;

import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VisualizationAppWorkflowDefinition extends AbstractVisualizationWorkflowDefinition {

    @Override
    public String agentType() {
        return VisualizationAgentTypes.APP;
    }

    @Override
    public String workflowType() {
        return "visualization_app";
    }

    @Override
    public String defaultObjectType() {
        return "data_application";
    }

    @Override
    protected boolean writable() {
        return true;
    }

    @Override
    protected Set<String> domainPartTypes() {
        return Set.of("visualization-app-record");
    }

    @Override
    protected Set<String> domainRecordTypes() {
        return Set.of("visualization-app-record");
    }

    @Override
    protected String objectTypeForRecord(String partType) {
        return "data_application";
    }
}
