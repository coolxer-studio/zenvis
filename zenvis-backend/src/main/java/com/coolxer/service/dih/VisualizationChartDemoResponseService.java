package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.Set;

@Service
public class VisualizationChartDemoResponseService {

    public static final String USER_EVENT_DEMO_TITLE = "用户事件临时图表演示";
    public static final String CHART_EXAMPLE_PROMPT =
            VisualizationDemoSupport.CHART_EXAMPLE_PROMPT;

    private final VisualizationDemoSupport support;

    public VisualizationChartDemoResponseService(ConfigService configService) {
        this.support = new VisualizationDemoSupport(
                configService, Set.of(VisualizationDemoSupport.Scenario.CHART));
    }

    public static boolean isUserEventDemoRequirementPrompt(String prompt) {
        return VisualizationDemoSupport.isChartDemoPrompt(prompt);
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        return support.findResponse(chatSession, chatId, prompt, user, mcpToolContext);
    }
}
