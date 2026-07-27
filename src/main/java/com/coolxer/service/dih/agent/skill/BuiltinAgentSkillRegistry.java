package com.coolxer.service.dih.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * DIH 内置智能体与 Skill 的固定映射。
 */
public final class BuiltinAgentSkillRegistry {

    public static final String AGENT_DATA_ACCESS = "agent_data_access";
    public static final String AGENT_DATA_VISUALIZATION = "agent_data_visualization";
    public static final String AGENT_DATA_ANALYSIS = "agent_data_analysis";
    public static final String AGENT_CONFIG_MANAGEMENT = "agent_config_management";
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
                    "data-analysis-agent",
                    AGENT_DATA_ANALYSIS,
                    "数据分析",
                    30,
                    "数据分析智能体按数据集准备、分析服务、分析报告三个阶段完成业务数据综合分析。"
            ),
            new BuiltinAgentSkill(
                    "config-management-agent",
                    AGENT_CONFIG_MANAGEMENT,
                    "配置管理",
                    40,
                    "配置管理智能体按配置生成、试验场验证、正式生效三个阶段管理系统配置。"
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

    public static Optional<BuiltinAgentSkill> findBySkillId(String skillId) {
        return AGENT_SKILLS.stream()
                .filter(agentSkill -> agentSkill.skillId().equals(skillId))
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
