package com.coolxer.service.dih.agent;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.vo.SkillChatEntryVo;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualizationAgentContractTest {

    @Test
    void registryExposesThreeIndependentVisualizationAgents() {
        assertThat(BuiltinAgentSkillRegistry.list())
                .filteredOn(agent -> VisualizationAgentTypes.ALL.contains(agent.agentType()))
                .extracting(BuiltinAgentSkillRegistry.BuiltinAgentSkill::agentType,
                        BuiltinAgentSkillRegistry.BuiltinAgentSkill::skillId,
                        BuiltinAgentSkillRegistry.BuiltinAgentSkill::label,
                        BuiltinAgentSkillRegistry.BuiltinAgentSkill::order)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_chart", "visualization-chart-agent", "临时图表", 20),
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_app", "visualization-app-agent", "可视化应用", 21),
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_dashboard", "visualization-dashboard-agent", "数据大屏", 22));
    }

    @Test
    void chatEntriesContainOnlyTheThreeNewVisualizationEntries() {
        SkillService service = newSkillService();
        service.reload();

        List<SkillChatEntryVo> entries = service.getChatEntries(true).stream()
                .filter(entry -> entry.getChatType() != null
                        && entry.getChatType().startsWith("agent_visualization_"))
                .toList();

        assertThat(entries)
                .extracting(SkillChatEntryVo::getChatType,
                        SkillChatEntryVo::getLabel, SkillChatEntryVo::getIcon,
                        SkillChatEntryVo::getOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_chart", "临时图表", "data-analysis", 20),
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_app", "可视化应用", "monitor", 21),
                        org.assertj.core.groups.Tuple.tuple(
                                "agent_visualization_dashboard", "数据大屏", "operation", 22));
        assertThat(entries).extracting(SkillChatEntryVo::getChatType)
                .doesNotContain("agent_data_visualization");
    }

    @Test
    void legacyVisualizationAgentAndSkillCannotResolve() {
        SkillService service = newSkillService();
        service.reload();

        assertThat(BuiltinAgentSkillRegistry.isBuiltinAgentType(
                "agent_data_visualization")).isFalse();
        assertThat(service.isBuiltinAgentEnabled("agent_data_visualization")).isFalse();
        assertThatThrownBy(() -> service.requireEnabledChatEntry(
                "skill:data-visualization-agent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicProtocolsAndSessionPartitionsAreUniquePerAgent() {
        assertThat(VisualizationAgentTypes.decisionPartType(VisualizationAgentTypes.CHART))
                .isEqualTo("visualization-chart-decision");
        assertThat(VisualizationAgentTypes.decisionPartType(VisualizationAgentTypes.APP))
                .isEqualTo("visualization-app-decision");
        assertThat(VisualizationAgentTypes.decisionPartType(VisualizationAgentTypes.DASHBOARD))
                .isEqualTo("visualization-dashboard-decision");
        assertThat(VisualizationAgentTypes.recordPartType(VisualizationAgentTypes.CHART))
                .isEqualTo("visualization-chart-record");
        assertThat(VisualizationAgentTypes.recordPartType(VisualizationAgentTypes.APP))
                .isEqualTo("visualization-app-record");
        assertThat(VisualizationAgentTypes.recordPartType(VisualizationAgentTypes.DASHBOARD))
                .isEqualTo("visualization-dashboard-record");
        assertThat(VisualizationAgentTypes.extraDataKey(VisualizationAgentTypes.CHART))
                .isEqualTo("visualizationChart");
        assertThat(VisualizationAgentTypes.extraDataKey(VisualizationAgentTypes.APP))
                .isEqualTo("visualizationApp");
        assertThat(VisualizationAgentTypes.extraDataKey(VisualizationAgentTypes.DASHBOARD))
                .isEqualTo("visualizationDashboard");
    }

    private SkillService newSkillService() {
        CustomWebConfig config = new CustomWebConfig();
        ReflectionTestUtils.setField(config, "skillPath",
                Path.of("../deploy/open_config/skill_config").toAbsolutePath().toString());
        return new SkillService(config, JacksonConfig.OBJECT_MAPPER.copy());
    }
}
