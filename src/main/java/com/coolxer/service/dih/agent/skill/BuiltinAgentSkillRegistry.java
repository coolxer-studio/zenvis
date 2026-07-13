package com.coolxer.service.dih.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * DIH 内置智能体与 Skill 的固定映射。
 */
public final class BuiltinAgentSkillRegistry {

    public static final String AGENT_DATA_ACCESS = "agent_data_access";
    public static final String AGENT_DATA_VISUALIZATION = "agent_data_visualization";
    public static final String AGENT_ANALYSIS = "agent_analysis";
    public static final String AGENT_DISPOSE = "agent_dispose";
    public static final String AGENT_REPORT = "agent_report";

    private static final List<BuiltinAgentSkill> AGENT_SKILLS = List.of(
            new BuiltinAgentSkill(
                    "data-access-agent",
                    AGENT_DATA_ACCESS,
                    "数据接入",
                    10,
                    "数据接入智能体能力正在建设中，当前 Skill 仅用于入口占位。"
            ),
            new BuiltinAgentSkill(
                    "data-visualization-agent",
                    AGENT_DATA_VISUALIZATION,
                    "数据可视化",
                    20,
                    "数据可视化智能体基于元数据实体生成临时图表、低代码页面/应用、静态 HTML、数据看板和菜单配置。"
            ),
            new BuiltinAgentSkill(
                    "analysis-agent",
                    AGENT_ANALYSIS,
                    "研判分析",
                    30,
                    "研判分析智能体根据用户提供的告警信息，按日志聚合、沙箱研判、分析结论三阶段完成综合研判。"
            ),
            new BuiltinAgentSkill(
                    "dispose-agent",
                    AGENT_DISPOSE,
                    "策略控制",
                    40,
                    "策略控制智能体可生成采集、标记评分和处置策略，并通过配置管理 MCP 完成受控发布。"
            ),
            new BuiltinAgentSkill(
                    "report-agent",
                    AGENT_REPORT,
                    "报表制作",
                    50,
                    "报表制作智能体可汇总各类智能体分析结果生成文档，并支持自然语言持续修改。"
            )
    );

    private BuiltinAgentSkillRegistry() {
    }

    public static List<BuiltinAgentSkill> list() {
        return AGENT_SKILLS;
    }

    public static Optional<BuiltinAgentSkill> findByAgentType(String agentType) {
        return AGENT_SKILLS.stream()
                .filter(agentSkill -> agentSkill.agentType().equals(agentType))
                .findFirst();
    }

    public static boolean isBuiltinAgentType(String agentType) {
        return findByAgentType(agentType).isPresent();
    }

    public record BuiltinAgentSkill(
            String skillId,
            String agentType,
            String label,
            int order,
            String placeholderMessage
    ) {
    }
}
