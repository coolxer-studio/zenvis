package com.coolxer.service.dih;

import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.model.dih.vo.SkillChatEntryVo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS
        ).orElseThrow();

        assertThat(policy.mode()).isEqualTo(DihChatExecutionPolicy.Mode.AGENT);
        assertThat(policy.agentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
        assertThat(policy.ragAllowed()).isFalse();
        assertThat(policy.toolsAllowed()).isTrue();
        assertThat(policy.deepThinkAllowed()).isFalse();
        assertThat(policy.effectiveDeepThink(true)).isFalse();
        assertThat(policy.skillIds()).containsExactly("data-analysis-agent");
    }

    @Test
    void dynamicSkillUsesSelectedSkillAndInheritedAgentTools() {
        SkillService skillService = mock(SkillService.class);
        when(skillService.requireEnabledChatEntry("skill:jmr-analysis"))
                .thenReturn(new SkillChatEntryVo(
                        "jmr-analysis",
                        "skill:jmr-analysis",
                        BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS,
                        "僵木蠕研判",
                        "说明",
                        "data-analysis",
                        60
                ));

        DihChatExecutionPolicy policy = DihChatExecutionPolicy.resolve(
                "skill:jmr-analysis",
                skillService
        ).orElseThrow();

        assertThat(policy.isDynamicSkill()).isTrue();
        assertThat(policy.agentType()).isEqualTo(BuiltinAgentSkillRegistry.AGENT_DATA_ANALYSIS);
        assertThat(policy.toolsAllowed()).isTrue();
        assertThat(policy.ragAllowed()).isFalse();
        assertThat(policy.skillIds()).containsExactly("jmr-analysis");
    }

    @Test
    void genericDynamicSkillHasNoToolsOrRag() {
        SkillService skillService = mock(SkillService.class);
        when(skillService.requireEnabledChatEntry("skill:generic"))
                .thenReturn(new SkillChatEntryVo(
                        "generic",
                        "skill:generic",
                        SkillService.GENERIC_SKILL_AGENT_TYPE,
                        "通用技能",
                        null,
                        "magic-stick",
                        1000
                ));

        DihChatExecutionPolicy policy = DihChatExecutionPolicy.resolve(
                "skill:generic",
                skillService
        ).orElseThrow();

        assertThat(policy.agentType()).isEqualTo(SkillService.GENERIC_SKILL_AGENT_TYPE);
        assertThat(policy.toolsAllowed()).isFalse();
        assertThat(policy.ragAllowed()).isFalse();
        assertThat(policy.skillIds()).containsExactly("generic");
    }

    @Test
    void unknownTypeHasNoExecutionPolicy() {
        assertThat(DihChatExecutionPolicy.resolve("unknown")).isEmpty();
    }
}
