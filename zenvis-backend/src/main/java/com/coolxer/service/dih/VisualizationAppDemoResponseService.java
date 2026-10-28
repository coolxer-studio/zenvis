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
public class VisualizationAppDemoResponseService {

    public static final String USER_EVENT_DEMO_TITLE = "用户事件可视化应用演示";
    public static final String PAGE_EXAMPLE_PROMPT =
            VisualizationDemoSupport.PAGE_EXAMPLE_PROMPT;
    public static final String SIDEBAR_APP_EXAMPLE_PROMPT =
            VisualizationDemoSupport.SIDEBAR_APP_EXAMPLE_PROMPT;
    public static final String MENU_EXAMPLE_PROMPT =
            VisualizationDemoSupport.MENU_EXAMPLE_PROMPT;

    private final VisualizationDemoSupport support;

    public VisualizationAppDemoResponseService(ConfigService configService) {
        this.support = new VisualizationDemoSupport(configService, Set.of(
                VisualizationDemoSupport.Scenario.PAGE,
                VisualizationDemoSupport.Scenario.SIDEBAR_APP,
                VisualizationDemoSupport.Scenario.MENU));
    }

    public static boolean isUserEventDemoRequirementPrompt(String prompt) {
        return VisualizationDemoSupport.isAppDemoPrompt(prompt);
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        return support.findResponse(chatSession, chatId, prompt, user, mcpToolContext);
    }
}
