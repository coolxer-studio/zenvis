package com.coolxer.service.dih.agent;

import java.util.Set;

/**
 * Public contracts shared by the three independent visualization agents.
 */
public final class VisualizationAgentTypes {

    public static final String CHART = "agent_visualization_chart";
    public static final String APP = "agent_visualization_app";
    public static final String DASHBOARD = "agent_visualization_dashboard";

    public static final Set<String> ALL = Set.of(CHART, APP, DASHBOARD);
    public static final Set<String> WRITABLE = Set.of(APP, DASHBOARD);

    private VisualizationAgentTypes() {
    }

    public static boolean isVisualization(String agentType) {
        return agentType != null && ALL.contains(agentType);
    }

    public static boolean isChart(String agentType) {
        return CHART.equals(agentType);
    }

    public static boolean isApp(String agentType) {
        return APP.equals(agentType);
    }

    public static boolean isDashboard(String agentType) {
        return DASHBOARD.equals(agentType);
    }

    public static String actionPrefix(String agentType) {
        if (isChart(agentType)) {
            return "visualization_chart";
        }
        if (isApp(agentType)) {
            return "visualization_app";
        }
        if (isDashboard(agentType)) {
            return "visualization_dashboard";
        }
        throw new IllegalArgumentException("不支持的智能体类型：" + agentType);
    }

    public static String selectEntityAction(String agentType) {
        return actionPrefix(agentType) + ".select_entity_from_meta";
    }

    public static String confirmQueryPlanAction(String agentType) {
        return actionPrefix(agentType) + ".confirm_query_plan";
    }

    public static String executeQueryAction(String agentType) {
        return actionPrefix(agentType) + ".execute_approved_query";
    }

    public static String addChartLibraryAction(String agentType) {
        return actionPrefix(agentType) + ".add_chart_library";
    }

    public static String confirmPlanAction(String agentType) {
        return actionPrefix(agentType) + ".confirm_plan";
    }

    public static String confirmOverwriteAction(String agentType) {
        return actionPrefix(agentType) + ".confirm_overwrite";
    }

    public static String confirmMenuAction(String agentType) {
        return actionPrefix(agentType) + ".confirm_menu";
    }

    public static String decisionPartType(String agentType) {
        return actionPrefix(agentType).replace('_', '-') + "-decision";
    }

    public static String recordPartType(String agentType) {
        return actionPrefix(agentType).replace('_', '-') + "-record";
    }

    public static String extraDataKey(String agentType) {
        if (isChart(agentType)) {
            return "visualizationChart";
        }
        if (isApp(agentType)) {
            return "visualizationApp";
        }
        if (isDashboard(agentType)) {
            return "visualizationDashboard";
        }
        throw new IllegalArgumentException("不支持的智能体类型：" + agentType);
    }
}
