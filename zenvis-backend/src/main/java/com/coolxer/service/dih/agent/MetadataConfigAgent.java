package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.ContextCompactionObserver;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.mcp.McpToolContext;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class MetadataConfigAgent {

    public static final String AGENT_TYPE = "agent_metadata_config";

    private final PromptDrivenAgentRuntime agentRuntime;
    private final PromptTemplate systemPromptTemplate;

    public MetadataConfigAgent(PromptDrivenAgentRuntime agentRuntime,
                               @Qualifier("agentMetadataConfigSystemPromptTemplate")
                               PromptTemplate systemPromptTemplate) {
        this.agentRuntime = agentRuntime;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public Flux<String> chat(String chatId, String model, String prompt,
                             List<ChatAttachment> attachments, User user) {
        return chat(chatId, model, prompt, attachments, user, McpToolContext.empty());
    }

    public Flux<String> chat(String chatId, String model, String prompt,
                             List<ChatAttachment> attachments, User user,
                             McpToolContext mcpToolContext) {
        return chat(
                chatId,
                model,
                prompt,
                attachments,
                user,
                List.of(BuiltinAgentSkillRegistry.findByAgentType(AGENT_TYPE)
                        .orElseThrow().skillId()),
                mcpToolContext
        );
    }

    public Flux<String> chat(String chatId,
                             String model,
                             String prompt,
                             List<ChatAttachment> attachments,
                             User user,
                             List<String> skillIds,
                             McpToolContext mcpToolContext) {
        return chat(chatId, model, prompt, attachments, user, skillIds, mcpToolContext,
                ContextCompactionObserver.NOOP);
    }

    public Flux<String> chat(String chatId,
                             String model,
                             String prompt,
                             List<ChatAttachment> attachments,
                             User user,
                             List<String> skillIds,
                             McpToolContext mcpToolContext,
                             ContextCompactionObserver compactionObserver) {
        return agentRuntime.chat(
                AGENT_TYPE,
                skillIds,
                systemPromptTemplate,
                chatId,
                model,
                prompt,
                attachments,
                user,
                mcpToolContext,
                compactionObserver
        );
    }
}
