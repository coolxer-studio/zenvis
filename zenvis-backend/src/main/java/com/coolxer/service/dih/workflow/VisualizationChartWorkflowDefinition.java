package com.coolxer.service.dih.workflow;

import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class VisualizationChartWorkflowDefinition extends AbstractVisualizationWorkflowDefinition {

    @Override
    public String agentType() {
        return VisualizationAgentTypes.CHART;
    }

    @Override
    public String workflowType() {
        return "visualization_chart";
    }

    @Override
    public String defaultObjectType() {
        return "chart";
    }

    @Override
    protected boolean writable() {
        return false;
    }

    @Override
    protected Set<String> domainPartTypes() {
        return Set.of("visualization-chart-record");
    }

    @Override
    protected Set<String> domainRecordTypes() {
        return Set.of("visualization-chart-record");
    }
}
