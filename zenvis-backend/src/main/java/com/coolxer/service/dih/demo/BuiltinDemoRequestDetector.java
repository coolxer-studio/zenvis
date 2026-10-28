package com.coolxer.service.dih.demo;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.dih.DataPushDemoResponseService;
import com.coolxer.service.dih.MetadataConfigDemoResponseService;
import com.coolxer.service.dih.ReportDemoResponseService;
import com.coolxer.service.dih.VisualizationAppDemoResponseService;
import com.coolxer.service.dih.VisualizationChartDemoResponseService;
import com.coolxer.service.dih.VisualizationDashboardDemoResponseService;
import com.coolxer.service.dih.agent.DataPushServiceAgent;
import com.coolxer.service.dih.agent.MetadataConfigAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.VisualizationAgentTypes;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Recognizes deterministic built-in demos before a persistent Run is created.
 *
 * <p>Demos are guided product fixtures rather than model-driven business tasks.
 * They keep their original lightweight streaming path and must not create an
 * Agent Run that would wait for a configured model or repeat demo side effects.</p>
 */
@Service
public class BuiltinDemoRequestDetector {

    private final MetadataConfigDemoResponseService metadataConfigDemoResponseService;
    private final DataPushDemoResponseService dataPushDemoResponseService;
    private final VisualizationChartDemoResponseService visualizationChartDemoResponseService;
    private final VisualizationAppDemoResponseService visualizationAppDemoResponseService;
    private final VisualizationDashboardDemoResponseService visualizationDashboardDemoResponseService;
    private final ReportDemoResponseService reportDemoResponseService;

    public BuiltinDemoRequestDetector(
            MetadataConfigDemoResponseService metadataConfigDemoResponseService,
            DataPushDemoResponseService dataPushDemoResponseService,
            VisualizationChartDemoResponseService visualizationChartDemoResponseService,
            VisualizationAppDemoResponseService visualizationAppDemoResponseService,
            VisualizationDashboardDemoResponseService visualizationDashboardDemoResponseService,
            ReportDemoResponseService reportDemoResponseService) {
        this.metadataConfigDemoResponseService = metadataConfigDemoResponseService;
        this.dataPushDemoResponseService = dataPushDemoResponseService;
        this.visualizationChartDemoResponseService = visualizationChartDemoResponseService;
        this.visualizationAppDemoResponseService = visualizationAppDemoResponseService;
        this.visualizationDashboardDemoResponseService = visualizationDashboardDemoResponseService;
        this.reportDemoResponseService = reportDemoResponseService;
    }

    public boolean isBuiltinDemoRequest(String agentType,
                                        String chatId,
                                        String prompt,
                                        User user,
                                        ChatSession chatSession) {
        if (StringUtils.isBlank(agentType) || StringUtils.isBlank(prompt)) {
            return false;
        }
        return findResponse(agentType, chatId, prompt, user, chatSession).isPresent();
    }

    private Optional<Flux<String>> findResponse(String agentType,
                                                String chatId,
                                                String prompt,
                                                User user,
                                                ChatSession chatSession) {
        if (MetadataConfigAgent.AGENT_TYPE.equals(agentType)) {
            return metadataConfigDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (DataPushServiceAgent.AGENT_TYPE.equals(agentType)) {
            return dataPushDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (VisualizationAgentTypes.isChart(agentType)) {
            return visualizationChartDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (VisualizationAgentTypes.isApp(agentType)) {
            return visualizationAppDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (VisualizationAgentTypes.isDashboard(agentType)) {
            return visualizationDashboardDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (ReportAgent.AGENT_TYPE.equals(agentType)) {
            return reportDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user);
        }
        return Optional.empty();
    }
}
