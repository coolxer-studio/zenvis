package com.coolxer.service.dih.demo;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.dih.DataAccessDemoResponseService;
import com.coolxer.service.dih.DataVisualizationDemoResponseService;
import com.coolxer.service.dih.ReportDemoResponseService;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.ReportAgent;
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

    private final DataAccessDemoResponseService dataAccessDemoResponseService;
    private final DataVisualizationDemoResponseService dataVisualizationDemoResponseService;
    private final ReportDemoResponseService reportDemoResponseService;

    public BuiltinDemoRequestDetector(
            DataAccessDemoResponseService dataAccessDemoResponseService,
            DataVisualizationDemoResponseService dataVisualizationDemoResponseService,
            ReportDemoResponseService reportDemoResponseService) {
        this.dataAccessDemoResponseService = dataAccessDemoResponseService;
        this.dataVisualizationDemoResponseService = dataVisualizationDemoResponseService;
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
        if (DataAccessAgent.AGENT_TYPE.equals(agentType)) {
            return dataAccessDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(agentType)) {
            return dataVisualizationDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user, McpToolContext.empty());
        }
        if (ReportAgent.AGENT_TYPE.equals(agentType)) {
            return reportDemoResponseService.findResponse(
                    chatSession, chatId, prompt, user);
        }
        return Optional.empty();
    }
}
