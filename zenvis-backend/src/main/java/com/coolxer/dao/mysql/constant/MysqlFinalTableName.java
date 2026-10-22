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
     * AI会话
     */
    public static final String T_AI_CHAT_SESSION = TABLE_PREFIX + "ai_chat_session";

    /**
     * 持久化 DIH Agent Run
     */
    public static final String T_AI_AGENT_RUN = TABLE_PREFIX + "ai_agent_run";

    /**
     * Agent Run 只追加事件日志
     */
    public static final String T_AI_AGENT_RUN_EVENT = TABLE_PREFIX + "ai_agent_run_event";

    /**
     * Agent Run 运行中追加输入（steer）
     */
    public static final String T_AI_AGENT_RUN_INPUT = TABLE_PREFIX + "ai_agent_run_input";

    /**
     * 报表工作文档
     */
    public static final String T_AI_REPORT_DOCUMENT = TABLE_PREFIX + "ai_report_document";

    /**
     * 报表分章节草稿
     */
    public static final String T_AI_REPORT_DRAFT = TABLE_PREFIX + "ai_report_draft";

    /**
     * 报表文档修订
     */
    public static final String T_AI_REPORT_REVISION = TABLE_PREFIX + "ai_report_revision";

    /**
     * 报表不可变归档
     */
    public static final String T_AI_REPORT_ARTIFACT = TABLE_PREFIX + "ai_report_artifact";

    /**
     * AI分析任务
     */
    public static final String T_AI_ANALYSIS_TASK = TABLE_PREFIX + "ai_analysis_task";

    /**
     * AI分析任务与 Skill 关联
     */
    public static final String T_AI_ANALYSIS_TASK_SKILL = TABLE_PREFIX + "ai_analysis_task_skill";

    /**
     * AI分析周期任务配置
     */
    public static final String T_AI_ANALYSIS_TASK_SCHEDULE = TABLE_PREFIX + "ai_analysis_task_schedule";

    /**
     * AI分析周期任务配置与 Skill 关联
     */
    public static final String T_AI_ANALYSIS_TASK_SCHEDULE_SKILL = TABLE_PREFIX + "ai_analysis_task_schedule_skill";

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

    /**
     * RAG数据集表
     */
    public static final String T_RAG_DATASET = TABLE_PREFIX + "rag_dataset";

    /**
     * RAG知识库表
     */
    public static final String T_RAG_KNOWLEDGE_BASE = TABLE_PREFIX + "rag_knowledge_base";

    /**
     * RAG知识库与数据集关联表
     */
    public static final String T_RAG_KNOWLEDGE_BASE_DATASET = TABLE_PREFIX + "rag_knowledge_base_dataset";

    /**
     * RAG检索审计日志表
     */
    public static final String T_RAG_RETRIEVAL_LOG = TABLE_PREFIX + "rag_retrieval_log";


}
