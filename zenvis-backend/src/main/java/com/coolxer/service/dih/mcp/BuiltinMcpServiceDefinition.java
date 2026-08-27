package com.coolxer.service.dih.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable catalog of ZenVis built-in MCP services.
 */
public enum BuiltinMcpServiceDefinition {

    RETRIEVAL(
            "retrieval",
            "数据检索与分析",
            "提供检索元数据、只读实体查询和统计分析工具",
            List.of(
                    "retrieval_search",
                    "retrieval_create_rule",
                    "retrieval_update_rule",
                    "retrieval_delete_rule",
                    "retrieval_list_rule",
                    "retrieval_list_entity",
                    "retrieval_list_attribute",
                    "retrieval_list_candidate",
                    "retrieval_list_display_entity",
                    "retrieval_list_display_attribute",
                    "entity_overview",
                    "entity_summary",
                    "entity_trend",
                    "entity_distribution",
                    "entity_aggregate",
                    "entity_histogram",
                    "entity_scatter",
                    "entity_value_statistics",
                    "entity_relations",
                    "entity_relation_timeline",
                    "entity_list",
                    "entity_view"
            )),

    ENTITY(
            "entity",
            "实体数据写入",
            "提供动态实体记录新增、更新和删除工具",
            List.of(
                    "entity_add",
                    "entity_delete",
                    "entity_bulk_delete",
                    "entity_update",
                    "entity_bulk_update"
            )),

    CONFIG(
            "config",
            "配置管理",
            "提供配置目录、Schema、读取、校验和写入工具",
            List.of(
                    "config_tree",
                    "config_schema",
                    "config_read",
                    "config_apply",
                    "config_add",
                    "config_ensure_root",
                    "config_validate"
            )),

    PUSH_TASK(
            "push-task",
            "数据推送任务",
            "提供受管数据推送任务的创建、诊断、修复和删除工具",
            List.of(
                    "push_task_create_and_start",
                    "push_task_list_by_source_mark",
                    "push_task_delete_by_source_mark",
                    "push_task_get_log",
                    "push_task_repair_and_restart",
                    "push_task_detect_format"
            )),

    VISUALIZATION(
            "visualization",
            "可视化管理",
            "提供看板和菜单的查询、创建、更新与删除工具",
            List.of(
                    "dashboard_create",
                    "dashboard_update",
                    "dashboard_bulk_update",
                    "dashboard_delete",
                    "dashboard_bulk_delete",
                    "dashboard_list",
                    "dashboard_list_all",
                    "dashboard_view",
                    "dashboard_type_options",
                    "menu_create",
                    "menu_update",
                    "menu_bulk_update",
                    "menu_delete",
                    "menu_bulk_delete",
                    "menu_update_order",
                    "menu_list",
                    "menu_list_all",
                    "menu_view",
                    "menu_parent_options",
                    "menu_type_options",
                    "menu_level_options"
            )),

    ANALYSIS_TASK(
            "analysis-task",
            "AI 分析任务",
            "提供一次性和周期 AI 分析任务管理工具",
            List.of(
                    "analysis_task_create",
                    "analysis_task_update",
                    "analysis_task_delete",
                    "analysis_task_bulk_delete",
                    "analysis_task_list",
                    "analysis_task_view",
                    "analysis_task_enqueue",
                    "analysis_task_cancel",
                    "analysis_task_run_once",
                    "analysis_task_queue_status",
                    "analysis_task_schedule_create",
                    "analysis_task_schedule_update",
                    "analysis_task_schedule_set_enabled",
                    "analysis_task_schedule_delete",
                    "analysis_task_schedule_list",
                    "analysis_task_schedule_view"
            ));

    public static final String BASE_PATH = "/mcp";

    private static final Map<String, BuiltinMcpServiceDefinition> BY_CODE;

    private static final Map<String, BuiltinMcpServiceDefinition> BY_TOOL;

    static {
        Map<String, BuiltinMcpServiceDefinition> byCode = new LinkedHashMap<>();
        Map<String, BuiltinMcpServiceDefinition> byTool = new LinkedHashMap<>();
        for (BuiltinMcpServiceDefinition service : values()) {
            if (byCode.put(service.code, service) != null) {
                throw new IllegalStateException("内置 MCP 服务代码重复: " + service.code);
            }
            for (String toolName : service.toolNames) {
                BuiltinMcpServiceDefinition previous = byTool.put(toolName, service);
                if (previous != null) {
                    throw new IllegalStateException(
                            "内置 MCP 工具重复分组: " + toolName + " ("
                                    + previous.code + ", " + service.code + ")");
                }
            }
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
        BY_TOOL = Collections.unmodifiableMap(byTool);
    }

    private final String code;

    private final String name;

    private final String description;

    private final List<String> toolNames;

    private final Set<String> toolNameSet;

    BuiltinMcpServiceDefinition(String code,
                                String name,
                                String description,
                                List<String> toolNames) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.toolNames = List.copyOf(toolNames);
        this.toolNameSet = Collections.unmodifiableSet(new LinkedHashSet<>(toolNames));
        if (this.toolNames.size() != this.toolNameSet.size()) {
            throw new IllegalArgumentException("内置 MCP 服务存在重复工具: " + code);
        }
    }

    public String code() {
        return code;
    }

    public String serviceName() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<String> toolNames() {
        return toolNames;
    }

    public boolean containsTool(String toolName) {
        return toolNameSet.contains(toolName);
    }

    public String sseEndpoint() {
        return BASE_PATH + "/" + code + "/sse";
    }

    public String messageEndpoint() {
        return BASE_PATH + "/" + code + "/message";
    }

    public String serverName() {
        return "zenvis-" + code + "-mcp";
    }

    public static Optional<BuiltinMcpServiceDefinition> findByCode(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code.trim()));
    }

    public static Optional<BuiltinMcpServiceDefinition> findByTool(String toolName) {
        return Optional.ofNullable(toolName == null ? null : BY_TOOL.get(toolName));
    }

    public static Set<String> allToolNames() {
        return BY_TOOL.keySet();
    }

    public static List<BuiltinMcpServiceDefinition> orderedValues() {
        return List.of(values());
    }
}
