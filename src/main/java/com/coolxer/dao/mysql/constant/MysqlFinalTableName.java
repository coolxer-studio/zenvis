package com.coolxer.dao.mysql.constant;

/**
 * 表名定义
 */
public class MysqlFinalTableName {

    private MysqlFinalTableName() {
    }

    private static final String TABLE_PREFIX = "t_";

    /**
     * 数据检索规则表
     */
    public static final String T_RETRIEVAL_RULE = TABLE_PREFIX + "retrieval_rule";

    /**
     * 数据共享-推送任务表
     */
    public static final String T_SYS_PUSH_TASK = TABLE_PREFIX + "sys_push_task";

    /**
     * 业务应用服务实例表
     */
    public static final String T_SYS_BUSINESS_SERVICE_INSTANCE = TABLE_PREFIX + "sys_business_service_instance";

    /**
     * 业务应用服务事件表
     */
    public static final String T_SYS_BUSINESS_SERVICE_EVENT = TABLE_PREFIX + "sys_business_service_event";

    /**
     * 菜单表
     */
    public static final String T_SYS_MENU = TABLE_PREFIX + "sys_menu";

    /**
     * 插件表
     */
    public static final String T_SYS_PLUGIN = TABLE_PREFIX + "sys_plugin";

    /**
     * 用户自定义看板
     */
    public static final String T_SYSTEM_DASHBOARD = TABLE_PREFIX + "sys_dashboard";

    /**
     * 角色表
     */
    public static final String T_SYS_ROLE = TABLE_PREFIX + "sys_role";

    /**
     * 角色权限关系表
     */
    public static final String T_SYS_ROLE_PERMISSION = TABLE_PREFIX + "sys_role_permission";

    /**
     * 用户表
     */
    public static final String T_SYS_USERS = TABLE_PREFIX + "sys_users";


    /**
     * 用户角色关系表
     */
    public static final String T_SYS_USER_ROLE = TABLE_PREFIX + "sys_user_role";

    /**
     * 资产规则表
     */
    public static final String T_SHARE_ASSET_RULE = TABLE_PREFIX + "asset_rule";

    /**
     * 运营看板表
     */
    public static final String T_SHARE_OPERATION_BOARD = TABLE_PREFIX + "operation_board";

    /**
     * AI会话
     */
    public static final String T_AI_CHAT_SESSION = TABLE_PREFIX + "ai_chat_session";

    /**
     * AI分析任务
     */
    public static final String T_AI_ANALYSIS_TASK = TABLE_PREFIX + "ai_analysis_task";

    /**
     * AI分析任务与 Skill 关联
     */
    public static final String T_AI_ANALYSIS_TASK_SKILL = TABLE_PREFIX + "ai_analysis_task_skill";

    /**
     * MCP客户端服务配置
     */
    public static final String T_AI_MCP_SERVER = TABLE_PREFIX + "ai_mcp_server";

    /**
     * MCP工具审批策略
     */
    public static final String T_AI_MCP_TOOL_POLICY = TABLE_PREFIX + "ai_mcp_tool_policy";

    /**
     * MCP工具调用与审批审计
     */
    public static final String T_AI_MCP_INVOCATION = TABLE_PREFIX + "ai_mcp_invocation";

    /**
     * DIH聊天会话内MCP工具授权
     */
    public static final String T_AI_MCP_CHAT_TOOL_GRANT = TABLE_PREFIX + "ai_mcp_chat_tool_grant";

    /**
     * AI分析任务当前执行的 MCP 工具授权
     */
    public static final String T_AI_MCP_TASK_TOOL_GRANT = TABLE_PREFIX + "ai_mcp_task_tool_grant";

    /**
     * 系统信息表
     */
    public static final String T_SYS_INFO = TABLE_PREFIX + "sys_info";


}
