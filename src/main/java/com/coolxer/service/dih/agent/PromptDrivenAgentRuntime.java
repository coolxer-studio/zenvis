package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.AgentCapabilityUnavailableException;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 通用 prompt 驱动 Agent 运行器。
 */
@Service
public class PromptDrivenAgentRuntime {

    private final AIChatService chatService;
    private final SkillService skillService;

    public PromptDrivenAgentRuntime(AIChatService chatService, SkillService skillService) {
        this.chatService = chatService;
        this.skillService = skillService;
    }

    public Flux<String> chat(String agentType,
                             PromptTemplate systemPromptTemplate,
                             String chatId,
                             String model,
                             String prompt,
                             List<ChatAttachment> attachments,
                             User user,
                             McpToolContext mcpToolContext) {
        return chat(agentType, List.of(), systemPromptTemplate, chatId, model, prompt,
                attachments, user, mcpToolContext);
    }

    public Flux<String> chat(String agentType,
                             List<String> skillIds,
                             PromptTemplate systemPromptTemplate,
                             String chatId,
                             String model,
                             String prompt,
                             List<ChatAttachment> attachments,
                             User user,
                             McpToolContext mcpToolContext) {
        try {
            String systemPrompt = buildSystemPrompt(agentType, skillIds, systemPromptTemplate, mcpToolContext);
            return chatService.agentChat(
                    chatId,
                    model,
                    systemPrompt,
                    prompt,
                    attachments,
                    user,
                    mcpToolContext
            );
        } catch (IllegalArgumentException e) {
            return Flux.error(new AgentCapabilityUnavailableException(
                    "智能体能力不可用：" + e.getMessage(),
                    e
            ));
        }
    }

    private String buildSystemPrompt(String agentType,
                                     List<String> skillIds,
                                     PromptTemplate systemPromptTemplate,
                                     McpToolContext mcpToolContext) {
        String systemPrompt = systemPromptTemplate.getTemplate();
        String skillPrompt = skillService.buildAgentSkillPrompt(agentType, skillIds);
        if (StringUtils.hasText(skillPrompt)) {
            systemPrompt = systemPrompt + "\n\n【已加载 Skill】\n" + skillPrompt;
        }
        if (mcpToolContext != null && mcpToolContext.hasTools()) {
            systemPrompt = systemPrompt + "\n\n" + mcpToolContext.systemPrompt();
        }
        return systemPrompt;
    }
}
