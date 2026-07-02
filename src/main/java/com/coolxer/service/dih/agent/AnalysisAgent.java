package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AnalysisAgent {

    public static final String AGENT_TYPE = "agent_analysis";

    private final AIChatService chatService;
    private final SkillService skillService;
    private final PromptTemplate systemPromptTemplate;

    public AnalysisAgent(AIChatService chatService,
                         SkillService skillService,
                         @Qualifier("agentAnalysisSystemPromptTemplate") PromptTemplate systemPromptTemplate) {
        this.chatService = chatService;
        this.skillService = skillService;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user,
                             McpToolContext mcpToolContext) {
        String systemPrompt = buildSystemPrompt(mcpToolContext);
        if (mcpToolContext != null && mcpToolContext.hasTools()) {
            return chatService.chatWithSystemPromptAndTools(
                    chatId,
                    model,
                    systemPrompt,
                    prompt,
                    attachments,
                    user,
                    mcpToolContext.toolCallbackProvider()
            );
        }
        return chatService.chatWithSystemPrompt(chatId, model, systemPrompt, prompt, attachments, user);
    }

    private String buildSystemPrompt(McpToolContext mcpToolContext) {
        String systemPrompt = systemPromptTemplate.getTemplate();
        String skillPrompt = skillService.buildEnabledSkillPrompt(AGENT_TYPE);
        if (StringUtils.hasText(skillPrompt)) {
            systemPrompt = systemPrompt + "\n\n【已加载 Skill】\n" + skillPrompt;
        }
        if (mcpToolContext != null && mcpToolContext.hasTools()) {
            systemPrompt = systemPrompt + "\n\n" + mcpToolContext.systemPrompt();
        }
        return systemPrompt;
    }
}
