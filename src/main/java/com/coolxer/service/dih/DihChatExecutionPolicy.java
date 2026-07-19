package com.coolxer.service.dih;

import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;

import java.util.List;
import java.util.Optional;

/**
 * DIH 会话类型对应的执行能力边界。
 */
public record DihChatExecutionPolicy(
        String chatType,
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
                        Mode.AGENT,
                        false,
                        true,
                        false,
                        List.of(agent.skillId())
                ));
    }

    public boolean isAgent() {
        return mode == Mode.AGENT;
    }

    public boolean effectiveDeepThink(boolean requested) {
        return deepThinkAllowed && requested;
    }

    public enum Mode {
        QA,
        AGENT
    }
}
