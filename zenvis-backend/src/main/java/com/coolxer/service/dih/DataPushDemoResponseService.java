package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class DataPushDemoResponseService {

    private static final String DEMO_RESOURCE_ROOT = "demo/data-push/";
    public static final String USER_EVENT_DEMO_TITLE = "用户事件数据推送服务演示";
    public static final String USER_EVENT_EXAMPLE_PROMPT =
            DemoResponseSupport.loadResource(
                    DEMO_RESOURCE_ROOT, "user-event-push-requirement.md");

    private static final String DEMO_META_FILE = "user_event.json";
    private static final String DEMO_ENTITY_NAME = "user_event";
    private static final String DEMO_TABLE_NAME = "zenvis.msg_user_event";
    private static final String DEMO_PUSH_MARK_PREFIX = "data-push-demo:user-event:";
    private static final String DEMO_PUSH_NAME = "用户事件数据推送服务";
    private static final String DEMO_PUSH_DESCRIPTION =
            "定时生成用户事件 JSON demo 日志并写入 ZenVis ClickHouse msg_user_event 表";
    private static final String DEMO_PUSH_CONFIG =
            DemoResponseSupport.loadResource(DEMO_RESOURCE_ROOT, "user-event-push.toml");

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        if (isUserEventDemoRequirementPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(buildInfoStepsResponse()));
        }
        if (!isDemoSession(chatSession)) {
            return Optional.empty();
        }
        if (isDemoInfoSubmittedPrompt(prompt) || isRevisePushConfigPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamAction(
                    () -> buildPushConfigResponse(chatId, mcpToolContext)));
        }
        if (isCancelGeneratePushConfigPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(cancelGeneratePushConfigResponse()));
        }
        if (isCreatePushTaskPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamAction(
                    () -> createPushTaskResponse(chatId, mcpToolContext)));
        }
        if (isCancelCreatePushTaskPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(cancelCreatePushTaskResponse()));
        }
        return Optional.empty();
    }

    public static boolean isUserEventDemoRequirementPrompt(String prompt) {
        return USER_EVENT_EXAMPLE_PROMPT.trim()
                .equals(Optional.ofNullable(prompt).orElse("").trim());
    }

    private boolean isDemoSession(ChatSession chatSession) {
        return DemoResponseSupport.sessionContains(
                chatSession,
                trimForMarker(USER_EVENT_EXAMPLE_PROMPT),
                "用户事件数据推送配置确认",
                DEMO_META_FILE,
                DEMO_PUSH_MARK_PREFIX);
    }

    private String trimForMarker(String value) {
        String normalized = Optional.ofNullable(value).orElse("").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    private boolean isDemoInfoSubmittedPrompt(String prompt) {
        return prompt.contains("上一条补充信息卡片提交以下结构化补充内容")
                || prompt.contains("用户事件数据推送配置确认")
                || prompt.contains("数据推送关键信息");
    }

    private boolean isCreatePushTaskPrompt(String prompt) {
        return prompt.contains("已确认创建用户事件数据推送服务")
                || prompt.contains("已确认创建并启动数据推送服务");
    }

    private boolean isCancelCreatePushTaskPrompt(String prompt) {
        return prompt.contains("已取消创建用户事件数据推送服务")
                || prompt.contains("已取消创建本次数据推送服务");
    }

    private boolean isRevisePushConfigPrompt(String prompt) {
        return prompt.contains("我需要补充信息继续更新数据推送配置")
                || prompt.contains("已补充数据推送配置调整要求");
    }

    private boolean isCancelGeneratePushConfigPrompt(String prompt) {
        return prompt.contains("已取消生成用户事件数据推送服务配置")
                || prompt.contains("已取消本次数据推送服务");
    }

    private String buildInfoStepsResponse() {
        return """
                请先确认本次数据推送服务要使用的已发布 Meta 和任务信息。

                ```zenvis:info-steps
                {
                  "title": "用户事件数据推送配置确认",
                  "content": "推送服务只读取已发布 Meta，不会创建或修改 Meta。",
                  "submitLabel": "提交补充信息",
                  "steps": [
                    {
                      "id": "published_meta",
                      "title": "已发布 Meta",
                      "description": "选择本次推送要依赖的 Meta 文件。",
                      "required": true,
                      "suggestions": [
                        {"label": "user_event.json", "value": "user_event.json"},
                        {"label": "仅使用已发布 user_event 实体", "value": "使用已发布 user_event.json 中的 user_event 实体"},
                        {"label": "先校验 Meta 再生成", "value": "请先只读校验 user_event.json，再生成数据推送配置"}
                      ],
                      "placeholder": "填写已发布 Meta 文件名"
                    },
                    {
                      "id": "source_format",
                      "title": "数据来源与输入格式",
                      "description": "说明数据源和输入格式。",
                      "required": true,
                      "suggestions": [
                        {"label": "demo_logs JSON", "value": "使用 demo_logs 定时生成 JSON 日志"},
                        {"label": "Kafka JSON", "value": "使用 Kafka JSON 消息"},
                        {"label": "文件日志", "value": "使用文件日志"}
                      ],
                      "placeholder": "描述数据源与输入格式"
                    },
                    {
                      "id": "target_mapping",
                      "title": "解析映射与推送目标",
                      "description": "确认字段映射和目标 ClickHouse 表。",
                      "required": true,
                      "suggestions": [
                        {"label": "user_event / msg_user_event", "value": "解析 JSON 并写入 zenvis.msg_user_event，同时输出到 console"},
                        {"label": "按 Meta 字段映射", "value": "严格按已发布 user_event Meta 字段映射并写入 msg_user_event"},
                        {"label": "保留完整事件字段", "value": "保留 event_type、tags，并补齐 event_id、user、procid、reliability、detail、server_time"}
                      ],
                      "placeholder": "描述字段映射和目标表"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildPushConfigResponse(String chatId, McpToolContext mcpToolContext) {
        MetaValidation validation = validateMeta(mcpToolContext);
        if (!validation.present()) {
            return missingMetaResponse();
        }
        String sourceMark = DEMO_PUSH_MARK_PREFIX
                + (StringUtils.hasText(chatId) ? chatId : "default");
        String requestJson = JacksonUtil.toJson(Map.of(
                "name", DEMO_PUSH_NAME,
                "description", DEMO_PUSH_DESCRIPTION,
                "source", "SYSTEM",
                "mark", sourceMark));
        return """
                已通过只读工具校验已发布 Meta，并生成数据推送服务配置，请确认后再创建。

                Meta 引用：
                - 文件：%s
                - 内容摘要：%s
                - 目标实体：user_event / 用户事件数据
                - 目标表：zenvis.msg_user_event

                数据推送配置摘要：
                - 数据源：demo_logs 定时生成用户事件 JSON
                - 解析清洗：解析 JSON，补齐业务字段；不写入平台字段 zenvis_id、zenvis_insert_time
                - 推送规则：所有用户事件数据写入 msg_user_event，同时输出到 console

                ```toml
                %s
                ```

                ```zenvis:data-push-service-decision
                {"title":"数据推送配置已生成，请确认创建","content":"确认后将调用 push_task_create_and_start，并由平台弹出 MCP 审批；批准后才会创建并启动用户事件数据推送服务。Meta 全程只读。","action":"data_push.confirm_plan","configKind":"push_task","sourceMark":"%s","request":%s,"metaRef":{"fileName":"%s","contentDigest":"%s"},"actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(
                DEMO_META_FILE,
                validation.contentDigest(),
                DEMO_PUSH_CONFIG.trim(),
                DemoResponseSupport.escapeJson(sourceMark),
                requestJson,
                DEMO_META_FILE,
                validation.contentDigest());
    }

    private String createPushTaskResponse(String chatId, McpToolContext mcpToolContext) {
        String sourceMark = DEMO_PUSH_MARK_PREFIX
                + (StringUtils.hasText(chatId) ? chatId : "default");
        String createResult = null;
        MetaValidation validation = validateMeta(mcpToolContext);
        if (!validation.present()) {
            return missingMetaResponse();
        }
        try {
            DemoResponseSupport.callTool(
                    mcpToolContext,
                    "push_task_detect_format",
                    Map.of("content", DEMO_PUSH_CONFIG));

            List<PushTaskVo> tasks = DemoResponseSupport.listPushTasks(
                    mcpToolContext, sourceMark);
            if (tasks.size() > 1) {
                return runtimeFailureResponse(
                        "未取得",
                        sourceMark,
                        "conflict",
                        "创建前冲突检查",
                        null,
                        false,
                        "同一 sourceMark 存在多个任务，确定性演示不会自动删除冲突任务。");
            }
            if (tasks.isEmpty()) {
                createResult = DemoResponseSupport.callTool(
                        mcpToolContext,
                        "push_task_create_and_start",
                        Map.of("request", Map.of(
                                "name", DEMO_PUSH_NAME,
                                "description", DEMO_PUSH_DESCRIPTION,
                                "config", DEMO_PUSH_CONFIG,
                                "source", "SYSTEM",
                                "mark", sourceMark)));
            }
            tasks = DemoResponseSupport.listPushTasks(mcpToolContext, sourceMark);
            if (tasks.size() != 1) {
                return runtimeFailureResponse(
                        "未取得",
                        sourceMark,
                        "unknown",
                        "创建后查询",
                        null,
                        false,
                        createResult == null
                                ? "未查询到唯一的演示数据推送任务。"
                                : "创建工具结果：" + DemoResponseSupport.describeToolResult(createResult)
                                + "；创建后未查询到唯一任务。");
            }
            PushTaskVo task = tasks.get(0);
            String taskId = task.getId() == null ? "" : String.valueOf(task.getId());
            String config = StringUtils.hasText(task.getConfig()) ? task.getConfig() : DEMO_PUSH_CONFIG;
            Map<String, Object> logResult = DemoResponseSupport.readSystemLog(
                    mcpToolContext, task, sourceMark);
            String status = String.valueOf(logResult.getOrDefault(
                    "taskStatus",
                    StringUtils.hasText(task.getStatus()) ? task.getStatus() : "unknown"));
            String systemLog = String.valueOf(logResult.getOrDefault("content", ""));
            boolean truncated = Boolean.parseBoolean(String.valueOf(
                    logResult.getOrDefault("truncated", false)));
            if (!"running".equalsIgnoreCase(status)
                    || DemoResponseSupport.containsRuntimeError(systemLog)) {
                return runtimeFailureResponse(
                        taskId,
                        sourceMark,
                        status,
                        "启动或运行检查",
                        systemLog,
                        truncated,
                        "任务状态不是 running，或最新 system 日志仍包含本轮错误。");
            }
            return """
                    数据推送服务已通过 MCP 审批创建，并完成状态与 system 日志检查。

                    ```zenvis:vectum-task-record
                    {
                      "title": "数据推送服务已创建",
                      "taskId": "%s",
                      "sourceMark": "%s",
                      "name": "%s",
                      "description": "%s",
                      "status": "running",
                      "config": %s
                    }
                    ```
                    """.formatted(
                    DemoResponseSupport.escapeJson(taskId),
                    DemoResponseSupport.escapeJson(sourceMark),
                    DemoResponseSupport.escapeJson(StringUtils.hasText(task.getName())
                            ? task.getName() : DEMO_PUSH_NAME),
                    DemoResponseSupport.escapeJson(StringUtils.hasText(task.getDescription())
                            ? task.getDescription() : DEMO_PUSH_DESCRIPTION),
                    JacksonUtil.toJson(config));
        } catch (Exception e) {
            log.error("执行数据推送演示 MCP 流程失败: {}", e.getMessage(), e);
            return runtimeFailureResponse(
                    "未取得",
                    sourceMark,
                    "unknown",
                    "MCP 工具执行",
                    null,
                    false,
                    createResult == null ? DemoResponseSupport.safeError(e)
                            : DemoResponseSupport.describeToolResult(createResult)
                            + "；" + DemoResponseSupport.safeError(e));
        }
    }

    private MetaValidation validateMeta(McpToolContext mcpToolContext) {
        try {
            String treeResult = DemoResponseSupport.callTool(
                    mcpToolContext, "config_tree", Map.of("type", "meta"));
            if (!DemoResponseSupport.containsConfigFile(treeResult, DEMO_META_FILE)) {
                return MetaValidation.missing();
            }
            String content = DemoResponseSupport.decodeStringResult(
                    DemoResponseSupport.callTool(
                            mcpToolContext,
                            "config_read",
                            Map.of("type", "meta", "fileName", DEMO_META_FILE)));
            if (!hasExpectedEntityAndAttributes(content)) {
                return MetaValidation.missing();
            }
            return new MetaValidation(true, DemoResponseSupport.digest(content));
        } catch (Exception e) {
            log.warn("校验数据推送演示 Meta 失败: {}", e.getMessage());
            return MetaValidation.missing();
        }
    }

    private boolean hasExpectedEntityAndAttributes(String content) {
        try {
            JsonNode root = JacksonConfig.OBJECT_MAPPER.readTree(content);
            JsonNode entities = root.path("entity");
            boolean hasEntity = false;
            if (entities.isArray()) {
                for (JsonNode entity : entities) {
                    if (DEMO_ENTITY_NAME.equals(entity.path("name").asText())
                            && DEMO_TABLE_NAME.equals(entity.path("table_name").asText())) {
                        hasEntity = true;
                        break;
                    }
                }
            }
            if (!hasEntity) {
                return false;
            }
            JsonNode attributes = root.path("attribute");
            if (!attributes.isArray()) {
                return false;
            }
            for (JsonNode attribute : attributes) {
                if (DEMO_ENTITY_NAME.equals(attribute.path("entity").asText())
                        && "server_time".equals(attribute.path("name").asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String missingMetaResponse() {
        return """
                未找到可用于本任务的已发布 Meta，数据推送服务流程已停止，未调用任何写工具。

                ```zenvis:data-push-service-decision
                {"title":"缺少已发布 Meta，无法创建数据推送服务","content":"目标 Meta 为 user_event.json，实体 user_event，目标表 zenvis.msg_user_event，需要字段 event_id、user、event_type、detail、tags、server_time。请先前往元数据配置智能体完成并发布 Meta。","kind":"missing_meta","handoff":{"targetAgentType":"agent_metadata_config","label":"前往元数据配置","prompt":"请为 user_event.json 创建并发布 Meta 配置：实体 user_event（用户事件数据），目标表 zenvis.msg_user_event，字段包括 event_id、user、event_type、detail、tags、server_time。完成后我会回到数据推送服务继续创建任务。"},"actions":[]}
                ```
                """;
    }

    private String cancelGeneratePushConfigResponse() {
        return """
                已取消生成数据推送服务配置。

                ```zenvis:notice
                {"title":"已取消生成数据推送配置","content":"不会创建或启动数据推送服务，也不会修改任何 Meta。","level":"info"}
                ```
                """;
    }

    private String cancelCreatePushTaskResponse() {
        return """
                已取消创建数据推送服务，已生成的数据推送配置不会添加到系统。

                ```zenvis:notice
                {"title":"已取消创建数据推送服务","content":"不会创建或启动用户事件数据推送服务；已发布 Meta 保持只读且不会被修改。","level":"info"}
                ```
                """;
    }

    private String runtimeFailureResponse(String taskId,
                                          String sourceMark,
                                          String status,
                                          String stage,
                                          String systemLog,
                                          boolean truncated,
                                          String reason) {
        String sanitizedLog = StringUtils.hasText(systemLog)
                ? DemoResponseSupport.sanitizeSystemLog(systemLog)
                : "任务日志不可取得";
        return """
                数据推送服务未确认成功。

                ```zenvis:notice
                {"title":"数据推送任务运行失败","content":"任务 ID：%s\\nsourceMark：%s\\n状态：%s\\n失败阶段：%s","level":"error"}
                ```

                ```zenvis:notice
                {"title":"数据推送任务日志","content":"日志类型：system\\n是否截断：%s\\n最新相关日志：\\n%s","level":"warning"}
                ```

                ```zenvis:notice
                {"title":"失败原因与下一步","content":"分类：确定性演示执行失败\\n失败原因：%s\\n配置修改：无\\n下一步：保留固定演示配置并停止，不调用 AI 模型猜测或修改。","level":"warning"}
                ```
                """.formatted(
                DemoResponseSupport.escapeJson(taskId),
                DemoResponseSupport.escapeJson(sourceMark),
                DemoResponseSupport.escapeJson(status),
                DemoResponseSupport.escapeJson(stage),
                truncated,
                DemoResponseSupport.escapeJson(sanitizedLog),
                DemoResponseSupport.escapeJson(reason));
    }

    private record MetaValidation(boolean present, String contentDigest) {
        private static MetaValidation missing() {
            return new MetaValidation(false, "");
        }
    }
}
