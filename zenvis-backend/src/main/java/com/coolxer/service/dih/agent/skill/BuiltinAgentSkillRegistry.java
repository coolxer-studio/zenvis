package com.coolxer.service.dih.agent.skill;

import java.util.List;
import java.util.Optional;

/**
 * DIH 内置智能体与 Skill 的固定映射。
 */
public final class BuiltinAgentSkillRegistry {

    public static final String AGENT_METADATA_CONFIG = "agent_metadata_config";
    public static final String AGENT_DATA_PUSH_SERVICE = "agent_data_push_service";
    public static final String AGENT_VISUALIZATION_CHART = "agent_visualization_chart";
    public static final String AGENT_VISUALIZATION_APP = "agent_visualization_app";
    public static final String AGENT_VISUALIZATION_DASHBOARD = "agent_visualization_dashboard";
    public static final String AGENT_REPORT = "agent_report";

    private static final List<BuiltinAgentSkill> AGENT_SKILLS = List.of(
            new BuiltinAgentSkill(
                    "metadata-config-agent",
                    AGENT_METADATA_CONFIG,
                    "元数据配置",
                    10,
                    "元数据配置智能体负责生成、写入并读回校验 Meta 元数据配置。"
            ),
            new BuiltinAgentSkill(
                    "data-push-service-agent",
                    AGENT_DATA_PUSH_SERVICE,
                    "数据推送服务",
                    11,
                    "数据推送服务智能体负责基于已发布 Meta 创建、诊断和修复 PushTask。"
            ),
            new BuiltinAgentSkill(
                    "visualization-chart-agent",
                    AGENT_VISUALIZATION_CHART,
                    "临时图表",
                    20,
                    "临时图表智能体基于已发布 Meta 查询真实数据并生成可加入图表库的 ECharts 快照。"
            ),
            new BuiltinAgentSkill(
                    "visualization-app-agent",
                    AGENT_VISUALIZATION_APP,
                    "可视化应用",
                    21,
                    "可视化应用智能体生成低代码单页、侧边栏应用和静态 HTML 页面，并按需发布应用菜单。"
            ),
            new BuiltinAgentSkill(
                    "visualization-dashboard-agent",
                    AGENT_VISUALIZATION_DASHBOARD,
                    "数据大屏",
                    22,
                    "数据大屏智能体生成低代码或静态 HTML 看板，并完成配置、看板与菜单读回。"
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
