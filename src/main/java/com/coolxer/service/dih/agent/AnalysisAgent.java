package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AnalysisAgent {

    public static final String AGENT_TYPE = "agent_analysis";

    private final PromptDrivenAgentRuntime agentRuntime;
    private final PromptTemplate systemPromptTemplate;

    public AnalysisAgent(PromptDrivenAgentRuntime agentRuntime,
                         @Qualifier("agentAnalysisSystemPromptTemplate") PromptTemplate systemPromptTemplate) {
        this.agentRuntime = agentRuntime;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user,
                             McpToolContext mcpToolContext) {
        return agentRuntime.chat(AGENT_TYPE, systemPromptTemplate, chatId, model, prompt, attachments, user, mcpToolContext);
    }
}
