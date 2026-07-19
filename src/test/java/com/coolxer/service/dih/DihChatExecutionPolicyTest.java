package com.coolxer.service.dih;

import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DihChatExecutionPolicyTest {

    @Test
    void askAllowsRagAndDeepThinkingButNoToolsOrSkills() {
        DihChatExecutionPolicy policy = DihChatExecutionPolicy.resolve("ask").orElseThrow();

        assertThat(policy.mode()).isEqualTo(DihChatExecutionPolicy.Mode.QA);
        assertThat(policy.ragAllowed()).isTrue();
        assertThat(policy.toolsAllowed()).isFalse();
        assertThat(policy.deepThinkAllowed()).isTrue();
        assertThat(policy.effectiveDeepThink(true)).isTrue();
        assertThat(policy.skillIds()).isEmpty();
    }

    @Test
    void builtinAgentAllowsExplicitSkillAndToolsButNoRagOrDeepThinking() {
        DihChatExecutionPolicy policy = DihChatExecutionPolicy.resolve(
                BuiltinAgentSkillRegistry.AGENT_ANALYSIS
        ).orElseThrow();

        assertThat(policy.mode()).isEqualTo(DihChatExecutionPolicy.Mode.AGENT);
        assertThat(policy.ragAllowed()).isFalse();
        assertThat(policy.toolsAllowed()).isTrue();
        assertThat(policy.deepThinkAllowed()).isFalse();
        assertThat(policy.effectiveDeepThink(true)).isFalse();
        assertThat(policy.skillIds()).containsExactly("analysis-agent");
    }

    @Test
    void unknownTypeHasNoExecutionPolicy() {
        assertThat(DihChatExecutionPolicy.resolve("unknown")).isEmpty();
    }
}
