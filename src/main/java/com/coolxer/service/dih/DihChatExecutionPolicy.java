package com.coolxer.service.dih;

import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.model.dih.vo.SkillChatEntryVo;

import java.util.List;
import java.util.Optional;

/**
 * DIH 会话类型对应的执行能力边界。
 */
public record DihChatExecutionPolicy(
        String chatType,
        String agentType,
        Mode mode,
        boolean ragAllowed,
        boolean toolsAllowed,
        boolean deepThinkAllowed,
        List<String> skillIds
) {

    public static final String TYPE_ASK = "ask";

    public static Optional<DihChatExecutionPolicy> resolve(String chatType) {
        if (TYPE_ASK.equals(chatType)) {
            return Optional.of(new DihChatExecutionPolicy(
                    TYPE_ASK,
                    TYPE_ASK,
                    Mode.QA,
                    true,
                    false,
                    true,
                    List.of()
            ));
        }
        return BuiltinAgentSkillRegistry.findByAgentType(chatType)
                .map(agent -> new DihChatExecutionPolicy(
                        agent.agentType(),
                        agent.agentType(),
                        Mode.AGENT,
                        false,
                        true,
                        false,
                        List.of(agent.skillId())
                ));
    }

    public static Optional<DihChatExecutionPolicy> resolve(String chatType, SkillService skillService) {
        Optional<DihChatExecutionPolicy> staticPolicy = resolve(chatType);
        if (staticPolicy.isPresent() || !SkillService.isDynamicChatType(chatType) || skillService == null) {
            return staticPolicy;
        }
        SkillChatEntryVo entry = skillService.requireEnabledChatEntry(chatType);
        boolean genericSkill = SkillService.GENERIC_SKILL_AGENT_TYPE.equals(entry.getAgentType());
        return Optional.of(new DihChatExecutionPolicy(
                entry.getChatType(),
                entry.getAgentType(),
                Mode.AGENT,
                false,
                !genericSkill,
                false,
                List.of(entry.getSkillId())
        ));
    }

    public boolean isAgent() {
        return mode == Mode.AGENT;
    }

    public boolean isDynamicSkill() {
        return SkillService.isDynamicChatType(chatType);
    }

    public boolean effectiveDeepThink(boolean requested) {
        return deepThinkAllowed && requested;
    }

    public enum Mode {
        QA,
        AGENT
    }
}
