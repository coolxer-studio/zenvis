package com.coolxer.service.dih;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.dih.mcp.McpToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class MetadataConfigDemoResponseService {

    private static final String DEMO_RESOURCE_ROOT = "demo/metadata-config/";
    public static final String USER_EVENT_DEMO_TITLE = "用户事件元数据配置演示";
    public static final String USER_EVENT_EXAMPLE_PROMPT =
            DemoResponseSupport.loadResource(DEMO_RESOURCE_ROOT, "user-event-requirement.md");

    private static final String DEMO_META_FILE = "user_event.json";
    private static final String DEMO_ENTITY_NAME = "user_event";
    private static final String DEMO_ENTITY_LABEL = "用户事件数据";
    private static final String DEMO_TABLE_NAME = "zenvis.msg_user_event";
    private static final String DEMO_META_CONFIG =
            DemoResponseSupport.loadResource(DEMO_RESOURCE_ROOT, "user-event-meta.json");

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        if (isUserEventDemoRequirementPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(buildMetadataInfoStepsResponse()));
        }
        if (!isDemoSession(chatSession)) {
            return Optional.empty();
        }
        if (isDemoMetadataInfoSubmittedPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(buildMetadataConfigResponse()));
        }
        if (isAbandonMetaConfigPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(abandonMetaConfigResponse()));
        }
        if (isReviseMetaConfigPrompt(prompt)) {
            return Optional.of(DemoResponseSupport.streamResponse(buildRevisedMetadataConfigResponse()));
        }
        if (isApplyMetaConfigPrompt(prompt)) {
            boolean overwriteConfirmed = prompt.contains("覆盖同名元数据配置")
                    || prompt.contains("确认覆盖");
            return Optional.of(DemoResponseSupport.streamAction(
                    () -> applyMetaConfigResponse(mcpToolContext, overwriteConfirmed)));
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
                "用户事件数据接入元数据确认",
                DEMO_META_FILE,
                "用户事件元数据配置演示");
    }

    private String trimForMarker(String value) {
        String normalized = Optional.ofNullable(value).orElse("").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    private boolean isApplyMetaConfigPrompt(String prompt) {
        return prompt.contains("已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统")
                || prompt.contains("我已确认添加配置到系统")
                || prompt.contains("已确认覆盖同名元数据配置");
    }

    private boolean isAbandonMetaConfigPrompt(String prompt) {
        return prompt.contains("我选择放弃本次元数据配置")
                || prompt.contains("我已放弃本次元数据配置");
    }

    private boolean isReviseMetaConfigPrompt(String prompt) {
        return prompt.contains("我需要补充信息继续更新元数据配置")
                || prompt.contains("已补充配置调整要求");
    }

    private boolean isDemoMetadataInfoSubmittedPrompt(String prompt) {
        return prompt.contains("上一条补充信息卡片提交以下结构化补充内容")
                || prompt.contains("用户事件数据接入元数据确认")
                || prompt.contains("元数据关键信息");
    }

    private String buildMetadataInfoStepsResponse() {
        return """
                为了先演示“补充信息后再生成元数据配置”的流程，请确认或调整以下元数据关键信息。

                ```zenvis:info-steps
                {
                  "title": "用户事件数据接入元数据确认",
                  "content": "请确认或补充以下信息后继续生成元数据配置。",
                  "submitLabel": "提交补充信息",
                  "steps": [
                    {
                      "id": "entity_and_table",
                      "title": "实体与表",
                      "description": "确认本次接入的数据实体和表名。",
                      "required": true,
                      "suggestions": [
                        {"label": "用户事件数据 / msg_user_event", "value": "实体中文名为用户事件数据，实体英文名为 user_event，表名为 msg_user_event"},
                        {"label": "用户事件 / msg_user_event", "value": "实体中文名为用户事件，实体英文名为 user_event，表名为 msg_user_event"},
                        {"label": "行为事件 / msg_user_event", "value": "实体中文名为行为事件，实体英文名为 user_event，表名为 msg_user_event"}
                      ],
                      "placeholder": "也可以填写其他实体名称或表名"
                    },
                    {
                      "id": "fields",
                      "title": "字段清单",
                      "description": "确认需要生成到元数据配置中的字段。",
                      "required": true,
                      "suggestions": [
                        {"label": "使用完整用户事件字段", "value": "字段包括 event_id、procid、user、event_type、reliability、detail、tags、server_time；平台记录ID由 zenvis_id 自动维护"},
                        {"label": "保留核心字段", "value": "字段包括 event_id、user、event_type、detail、server_time；平台记录ID由 zenvis_id 自动维护"},
                        {"label": "完整字段并自动建表", "value": "字段包括 event_id、procid、user、event_type、reliability、detail、tags、server_time，并启用自动建表；平台记录ID由 zenvis_id 自动维护"}
                      ],
                      "placeholder": "也可以补充字段名、类型和说明"
                    },
                    {
                      "id": "special_rules",
                      "title": "特殊字段规则",
                      "description": "确认枚举、JSON、数组和时间字段的展示与查询规则。",
                      "required": true,
                      "suggestions": [
                        {"label": "使用推荐规则", "value": "event_type 为枚举，detail 按 JSON 展示，tags 按数组展示，server_time 为 DateTime64(3)"},
                        {"label": "强调事件类型筛选", "value": "event_type 支持登录、点击、浏览、删除、修改、其他，并支持等值和包含筛选"},
                        {"label": "强调时间与明细展示", "value": "server_time 作为默认时间字段，detail 保留完整 JSON 明细，tags 保留字符串数组"}
                      ],
                      "placeholder": "也可以补充枚举值、时间字段或展示规则"
                    }
                  ]
                }
                ```
                """;
    }

    private String buildMetadataConfigResponse() {
        return """
                已根据需求生成元数据配置，请确认后续处理。

                配置摘要：
                - 目标文件：user_event.json
                - 实体：用户事件数据（user_event）
                - 目标表：zenvis.msg_user_event
                - 字段数量：8
                - 时间字段：server_time
                - 自动建表：MergeTree，按 server_time 月分区

                ```zenvis:meta-config
                %s
                ```

                ```zenvis:metadata-config-decision
                {"title":"元数据配置已生成，请选择后续处理","content":"可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。添加时仍需分别通过 config_add 和 config_apply 的 MCP 审批。","fileName":"user_event.json","configKind":"meta","overwrite":false,"actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(DEMO_META_CONFIG.trim());
    }

    private String buildRevisedMetadataConfigResponse() {
        return """
                已根据补充信息重新生成元数据配置，请再次确认后续处理。

                配置摘要：
                - 目标文件：user_event.json
                - 实体：用户事件数据（user_event）
                - 目标表：zenvis.msg_user_event
                - 字段数量：8
                - 时间字段：server_time
                - 自动建表：MergeTree，按 server_time 月分区

                ```zenvis:meta-config
                %s
                ```

                ```zenvis:metadata-config-decision
                {"title":"元数据配置已更新，请选择后续处理","content":"可以添加更新后的配置到系统、放弃本次配置，或继续补充调整要求。添加时仍需分别通过 config_add 和 config_apply 的 MCP 审批。","fileName":"user_event.json","configKind":"meta","overwrite":false,"actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(DEMO_META_CONFIG.trim());
    }

    private String abandonMetaConfigResponse() {
        return """
                已放弃本次元数据配置，未写入系统，也不会自动转入其他智能体。

                ```zenvis:notice
                {"title":"本次配置已放弃","content":"元数据配置流程已结束；如需重新接入用户事件数据，可再次发送元数据配置需求。","level":"info"}
                ```
                """;
    }

    private String applyMetaConfigResponse(McpToolContext mcpToolContext,
                                           boolean overwriteConfirmed) {
        try {
            String treeResult = DemoResponseSupport.callTool(
                    mcpToolContext, "config_tree", Map.of("type", "meta"));
            boolean exists = DemoResponseSupport.containsConfigFile(treeResult, DEMO_META_FILE);
            boolean alreadyApplied = false;
            if (exists) {
                String current = DemoResponseSupport.decodeStringResult(
                        DemoResponseSupport.callTool(
                                mcpToolContext,
                                "config_read",
                                Map.of("type", "meta", "fileName", DEMO_META_FILE)));
                if (jsonEquivalent(DEMO_META_CONFIG, current)) {
                    alreadyApplied = true;
                } else if (!overwriteConfirmed) {
                    return overwriteConfirmationResponse();
                }
            }
            if (!exists) {
                DemoResponseSupport.requireTrueResult("config_add",
                        DemoResponseSupport.callTool(
                                mcpToolContext,
                                "config_add",
                                Map.of(
                                        "type", "meta",
                                        "configDto", Map.of("fileName", DEMO_META_FILE))));
            }
            if (!alreadyApplied) {
                DemoResponseSupport.requireTrueResult("config_apply",
                        DemoResponseSupport.callTool(
                                mcpToolContext,
                                "config_apply",
                                Map.of(
                                        "type", "meta",
                                        "configDto", Map.of(
                                                "fileName", DEMO_META_FILE,
                                                "text", DEMO_META_CONFIG))));
            }
            String readBack = DemoResponseSupport.decodeStringResult(
                    DemoResponseSupport.callTool(
                            mcpToolContext,
                            "config_read",
                            Map.of("type", "meta", "fileName", DEMO_META_FILE)));
            if (!jsonEquivalent(DEMO_META_CONFIG, readBack)) {
                return metaFailureResponse(
                        "config_read",
                        "配置应用后读回内容与演示目标配置不一致，已停止流程。");
            }
        } catch (Exception e) {
            log.error("执行元数据演示 MCP 流程失败: {}", e.getMessage(), e);
            return metaFailureResponse("MCP 工具执行", DemoResponseSupport.safeError(e));
        }

        return """
                元数据配置已通过 MCP 审批完成写入、应用和读回校验，本次元数据配置流程结束。

                ```zenvis:meta-config-record
                {
                  "title": "元数据配置已记录",
                  "fileName": "%s",
                  "entityName": "%s",
                  "entityLabel": "%s",
                  "tableName": "%s",
                  "status": "applied",
                  "config": %s
                }
                ```
                """.formatted(
                DEMO_META_FILE,
                DEMO_ENTITY_NAME,
                DEMO_ENTITY_LABEL,
                DEMO_TABLE_NAME,
                DEMO_META_CONFIG.trim());
    }

    private String overwriteConfirmationResponse() {
        return """
                检测到同名元数据文件 `user_event.json` 已存在且内容不同。
                尚未调用 `config_apply`，请确认是否覆盖现有配置。

                ```zenvis:metadata-config-decision
                {"title":"同名元数据配置已存在，请确认覆盖","content":"覆盖后将使用本次展示的锁定候选重新应用，并通过 config_read 读回校验。","fileName":"user_event.json","configKind":"meta","overwrite":true,"actions":["apply_config","abandon"]}
                ```
                """;
    }

    private String metaFailureResponse(String stage, String error) {
        return """
                ```zenvis:notice
                {"title":"元数据配置写入失败","content":"失败阶段：%s\\n真实错误：%s\\n流程已停止，未生成成功记录。","level":"error"}
                ```
                """.formatted(
                DemoResponseSupport.escapeJson(stage),
                DemoResponseSupport.escapeJson(error));
    }

    private boolean jsonEquivalent(String expected, String actual) throws Exception {
        return JacksonConfig.OBJECT_MAPPER.readTree(expected)
                .equals(JacksonConfig.OBJECT_MAPPER.readTree(actual));
    }
}
