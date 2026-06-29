package com.coolxer.service.dih.agent;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class DataAccessAgent {

    public static final String AGENT_TYPE = "agent_data_access";

    public static final String REQUIRED_SKILL_ID = "data-access-agent";

    private final AIChatService chatService;
    private final SkillService skillService;
    private final PromptTemplate systemPromptTemplate;

    public DataAccessAgent(AIChatService chatService,
                           SkillService skillService,
                           @Qualifier("agentDataAccessSystemPromptTemplate") PromptTemplate systemPromptTemplate) {
        this.chatService = chatService;
        this.skillService = skillService;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user) {
        return chatService.chatWithSystemPrompt(chatId, model, buildSystemPrompt(), prompt, attachments, user);
    }

    private String buildSystemPrompt() {
        String systemPrompt = systemPromptTemplate.getTemplate();
        String skillPrompt = skillService.buildRequiredSkillPrompt(AGENT_TYPE, List.of(REQUIRED_SKILL_ID));
        if (StringUtils.hasText(skillPrompt)) {
            return systemPrompt + "\n\n【已加载 Skill】\n" + skillPrompt;
        }
        return systemPrompt;
    }
}
