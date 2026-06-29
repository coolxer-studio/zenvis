package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.mcp.McpClientService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class McpAgent {

    public static final String AGENT_TYPE = "agent_mcp";

    private static final String SYSTEM_PROMPT = """
            你是 ZenVis 的 MCP 工具调用 Agent。
            你可以使用已启用的外部 MCP 服务工具来查询数据、执行操作或获取上下文。
            当用户的问题需要外部系统信息时，优先选择语义最匹配的 MCP 工具。
            调用工具前先确认必要参数；如果参数不足，请向用户追问，不要编造参数。
            对具有写入、删除、执行任务等副作用的工具，先用自然语言说明将要执行的动作并请求用户确认。
            工具返回后，请用中文归纳结果，保留关键字段、异常信息和下一步建议。
            """;

    private final AIChatService chatService;
    private final McpClientService mcpClientService;

    public McpAgent(AIChatService chatService, McpClientService mcpClientService) {
        this.chatService = chatService;
        this.mcpClientService = mcpClientService;
    }

    public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user) {
        if (!mcpClientService.hasAvailableTools()) {
            return Flux.just("当前没有启用且连接成功的 MCP 服务，请先在 MCP 服务管理中配置并刷新服务。");
        }
        return chatService.chatWithSystemPromptAndTools(
                chatId,
                model,
                buildSystemPrompt(),
                prompt,
                attachments,
                user,
                mcpClientService.getToolCallbackProvider()
        );
    }

    private String buildSystemPrompt() {
        String mcpPrompt = mcpClientService.buildEnabledMcpPrompt();
        if (StringUtils.hasText(mcpPrompt)) {
            return SYSTEM_PROMPT + "\n\n【已连接 MCP 服务与工具】\n" + mcpPrompt;
        }
        return SYSTEM_PROMPT;
    }
}
