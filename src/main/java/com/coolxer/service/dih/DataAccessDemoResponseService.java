package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.model.dih.Message;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.ToolRuntimeContext;
import com.coolxer.utils.JacksonUtil;
import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Service
public class DataAccessDemoResponseService {

    private static final String VECTUM_RUN_LOG_MARKER_PREFIX = "=== Vectum run ";
    public static final String USER_EVENT_DEMO_TITLE = "用户事件数据接入演示";
    public static final String USER_EVENT_EXAMPLE_PROMPT = """
            # 用户事件数据接入

            ## 1. 数据格式定义

            ### 1.1 实体定义

            | 项目 | 内容 |
            | --- | --- |
            | 实体英文名 | user-event |
            | 实体中文名 | 调试信息 |
            | 数据描述 | 记录用户登录、点击、浏览、删除、修改等行为事件，用于测试验证场景。 |
            | 数据类型 | 用户事件日志 |
            | 目标表名（可选） | msg_user_event |

            ### 1.2 字段清单

            | 字段名 | 样例值 | 中文名 | 字段含义 | 建议类型 | 是否展示 | 查询方式/备注 |
            | --- | --- | --- | --- | --- | --- | --- |
            | event_id | evt-550e8400-e29b-41d4-a716-446655440000 | 事件ID | 业务侧事件标识符 | String | 是 | equal、notequal、in |
            | procid | 104 | 进程id | 产生事件时关联的进程编号 | UInt16 | 是 | greatthan、lessthan、greatequalthan、lessequalthan、between |
            | user | dGVzdC11c2Vy | 用户 | 用户名称或脱敏后的用户标识 | String | 是 | equal、notequal、in |
            | event_type | login | 事件类型 | 用户行为事件类型 | String | 是 | equal、notequal、in；枚举值见关键字段与特殊类型 |
            | reliability | 8.6 | 可信度 | 行为的可信评估结果 | Float64 | 是 | equal、notequal、greatthan、lessthan、greatequalthan、lessequalthan、between |
            | detail | {"method":"POST","path":"/v1/orders","query":"dry_run=false"} | 数据详情 | 事件明细 JSON 数据 | json | 是 | 作为 JSON 展示，不配置查询操作 |
            | tags | ["登录","认证"] | 标记 | 事件标签数组 | Array(String) | 是 | in；作为数组展示 |
            | server_time | 2026-07-08 10:30:00 | 入库时间 | 数据写入或服务端处理时间 | DateTime64(3) | 是 | greatthan、lessthan、greatequalthan、lessequalthan、between |

            ### 1.3 示例数据

            ```json
            {
              "event_type": "login",
              "tags": ["登录", "认证"],
              "event_id": "evt-550e8400-e29b-41d4-a716-446655440000",
              "user": "dGVzdC11c2Vy",
              "procid": 104,
              "reliability": 8.6,
              "detail": {
                "method": "POST",
                "path": "/v1/orders",
                "query": "dry_run=false"
              },
              "server_time": "2026-07-08 10:30:00"
            }
            ```

            ### 1.4 关键字段与特殊类型

            | 项目 | 内容 |
            | --- | --- |
            | 业务标识字段 | event_id；平台记录ID `zenvis_id` 由系统自动生成，不需要配置或写入。 |
            | 排序字段 | server_time |
            | 时间字段 | server_time，格式为 yyyy-MM-dd HH:mm:ss |
            | 枚举字段 | event_type：登录=login、点击=click、浏览=view、删除=delete、修改=modify、其他=other |
            | 数组字段 | tags：Array(String) |
            | JSON 字段 | detail：JSON，包含 method、path、query 等请求上下文 |
            | 其他特殊字段 | reliability 为 0.0 到 10.0 的数值评分 |

            ## 2. 数据来源、解析清洗映射与推送规则

            ### 2.1 数据来源定义

            | 项目 | 内容 |
            | --- | --- |
            | 数据源类型 | demo_logs |
            | 连接信息 | 无，使用定时生成的演示日志。 |
            | 认证方式 | 无 |
            | 输入格式 | JSON 文本 |
            | 输入样例 | {"event_type":"login","tags":["登录","认证"]}、{"event_type":"click","tags":[]}、{"event_type":"view","tags":[]}、{"event_type":"delete","tags":["已认证"]}、{"event_type":"modify","tags":["重要","有风险"]} |

            ### 2.2 解析、清洗与映射规则

            | 项目 | 内容 |
            | --- | --- |
            | 解析规则 | 将输入日志中的 message 按 JSON 解析为事件对象。 |
            | 字段映射 | 保留 event_type、tags；自动补齐 event_id、user、procid、reliability、detail、server_time；不映射平台字段 zenvis_id、zenvis_insert_time。 |
            | 清洗规则 | 不过滤，全部保留；ClickHouse 写入时跳过未知字段。 |
            | 转换规则 | event_id 使用业务事件标识；user 使用随机字节的 base64 字符串；procid 生成 100 到 110 的整数；reliability 生成 0.0 到 10.0 的浮点数；detail 固定为 {"method":"POST","path":"/v1/orders","query":"dry_run=false"}；server_time 使用当前时间格式化为 yyyy-MM-dd HH:mm:ss。 |
            | 异常数据处理 | 同时输出到 console，编码为 JSON，便于调试观察。 |

            ### 2.3 推送规则

            | 数据类型或条件 | 对应实体 | 说明 |
            | --- | --- | --- |
            | 全部用户事件数据 | user-event / 调试信息 | 写入 msg_user_event 表；目标库默认为系统的 zenvis 库。 |

           """;

    private static final String DEMO_META_FILE = "user_event.json";
    private static final String DEMO_ENTITY_NAME = "user_event";
    private static final String DEMO_ENTITY_LABEL = "调试信息";
    private static final String DEMO_TABLE_NAME = "zenvis.msg_user_event";
    private static final String DEMO_PUSH_MARK_PREFIX = "data-access-demo:user-event:";
    private static final String DEMO_PUSH_NAME = "用户事件数据推送服务";
    private static final String DEMO_PUSH_DESCRIPTION =
            "定时生成用户事件 JSON demo 日志并写入 ZenVis ClickHouse msg_user_event 表";
    private static final String GENERATE_DEMO_PUSH_CONFIG_ACTION = "data_access.generate_demo_push_config";
    private static final String CREATE_DEMO_PUSH_TASK_ACTION = "data_access.create_demo_push_task";
    private static final int DEMO_STREAM_CHUNK_SIZE = 18;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(55);

    private static final String DEMO_META_CONFIG = """
            {
              "entity": [
                {
                  "id": 1,
                  "name": "user_event",
                  "label": "调试信息",
                  "description": "用户行为事件数据，用于记录用户登录、点击、浏览、删除、修改等行为事件。",
                  "table_name": "zenvis.msg_user_event",
                  "data_source": "clickhouse",
                  "auto_create": {
                    "engine": "MergeTree",
                    "order_by": [
                      "event_id",
                      "server_time"
                    ],
                    "partition_by": "toYYYYMM(server_time)"
                  }
                }
              ],
              "attribute": [
                {
                  "id": 1,
                  "entity": "user_event",
                  "name": "event_id",
                  "label": "事件ID",
                  "description": "业务侧事件唯一标识符",
                  "column_name": "event_id",
                  "column_type": "String",
                  "operators": [
                    "equal",
                    "notequal",
                    "in"
                  ],
                  "display_selected": true
                },
                {
                  "id": 2,
                  "entity": "user_event",
                  "name": "procid",
                  "label": "进程id",
                  "description": "产生事件时关联的进程编号",
                  "column_name": "procid",
                  "column_type": "UInt16",
                  "operators": [
                    "greatthan",
                    "lessthan",
                    "greatequalthan",
                    "lessequalthan",
                    "between"
                  ],
                  "display_selected": true
                },
                {
                  "id": 3,
                  "entity": "user_event",
                  "name": "user",
                  "label": "用户",
                  "description": "用户名称或脱敏后的用户标识",
                  "column_name": "user",
                  "column_type": "String",
                  "operators": [
                    "equal",
                    "notequal",
                    "in"
                  ],
                  "display_selected": true
                },
                {
                  "id": 4,
                  "entity": "user_event",
                  "name": "event_type",
                  "label": "事件类型",
                  "description": "用户行为事件类型",
                  "column_name": "event_type",
                  "column_type": "String",
                  "operators": [
                    "equal",
                    "notequal",
                    "in"
                  ],
                  "display_selected": true,
                  "enums": [
                    "login",
                    "click",
                    "view",
                    "delete",
                    "modify",
                    "other"
                  ]
                },
                {
                  "id": 5,
                  "entity": "user_event",
                  "name": "reliability",
                  "label": "可信度",
                  "description": "行为的可信评估结果",
                  "column_name": "reliability",
                  "column_type": "Float64",
                  "operators": [
                    "equal",
                    "notequal",
                    "greatthan",
                    "lessthan",
                    "greatequalthan",
                    "lessequalthan",
                    "between"
                  ],
                  "display_selected": true
                },
                {
                  "id": 6,
                  "entity": "user_event",
                  "name": "detail",
                  "label": "数据详情",
                  "description": "事件明细 JSON 数据",
                  "column_name": "detail",
                  "column_type": "JSON",
                  "operators": [],
                  "display_selected": true,
                  "display_type": "json"
                },
                {
                  "id": 7,
                  "entity": "user_event",
                  "name": "tags",
                  "label": "标记",
                  "description": "事件标签数组",
                  "column_name": "tags",
                  "column_type": "Array(String)",
                  "operators": [
                    "in"
                  ],
                  "display_selected": true,
                  "display_type": "array"
                },
                {
                  "id": 8,
                  "entity": "user_event",
                  "name": "server_time",
                  "label": "入库时间",
                  "description": "数据写入或服务端处理时间",
                  "column_name": "server_time",
                  "column_type": "DateTime64(3)",
                  "operators": [
                    "greatthan",
                    "lessthan",
                    "greatequalthan",
                    "lessequalthan",
                    "between"
                  ],
                  "display_selected": true
                }
              ],
              "operator": [
                {
                  "name": "equal",
                  "description": "等于"
                },
                {
                  "name": "notequal",
                  "description": "不等于"
                },
                {
                  "name": "match",
                  "description": "模糊匹配"
                },
                {
                  "name": "greatthan",
                  "description": "大于"
                },
                {
                  "name": "greatequalthan",
                  "description": "大于等于"
                },
                {
                  "name": "lessthan",
                  "description": "小于"
                },
                {
                  "name": "lessequalthan",
                  "description": "小于等于"
                },
                {
                  "name": "between",
                  "description": "介于"
                },
                {
                  "name": "in",
                  "description": "包含"
                }
              ]
            }
            """;

    private static final String DEMO_PUSH_CONFIG = """
            [sources.generator_demo_logs]
              type = "demo_logs"
              format = "shuffle"
              lines = [
                "{\\"event_type\\":\\"login\\",\\"tags\\":[\\"登录\\",\\"认证\\"]}",
                "{\\"event_type\\":\\"click\\",\\"tags\\":[]}",
                "{\\"event_type\\":\\"view\\",\\"tags\\":[]}",
                "{\\"event_type\\":\\"delete\\",\\"tags\\":[\\"已认证\\"]}",
                "{\\"event_type\\":\\"modify\\",\\"tags\\":[\\"重要\\",\\"有风险\\"]}"
              ]
              interval = 5

            [transforms.parse_json]
              inputs = ["generator_demo_logs"]
              type = "remap"
              source = '''
                . = parse_json!(string!(.message))
                .event_id = uuid_v4()
                .user = encode_base64(random_bytes(16))
                .procid = random_int(100, 110)
                .reliability = random_float(0.0, 10.0)
                .detail = parse_json!("{\\"method\\":\\"POST\\",\\"path\\":\\"/v1/orders\\",\\"query\\":\\"dry_run=false\\"}")
                .server_time = format_timestamp!(now(), format: "%Y-%m-%d %H:%M:%S")
              '''

            [sinks.my_clickhouse_sink]
              type = "clickhouse"
              inputs = ["parse_json"]
              endpoint = "http://clickhouse-service:8123"
              database = "zenvis"
              table = "msg_user_event"
              auth.strategy = "basic"
              auth.user = "default"
              auth.password = "${CLICKHOUSE_PASSWORD}"
              skip_unknown_fields = true

            [sinks.console]
              inputs = ["parse_json"]
              type = "console"
              encoding.codec = "json"
            """;

    public Optional<Flux<String>> findResponse(ChatSession chatSession,
                                               String chatId,
                                               String prompt,
                                               User user,
                                               McpToolContext mcpToolContext) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        if (isUserEventDemoRequirementPrompt(prompt)) {
            return Optional.of(streamResponse(buildMetadataInfoStepsResponse()));
        }
        if (isDemoSession(chatSession) && isDemoMetadataInfoSubmittedPrompt(prompt)) {
            return Optional.of(streamResponse(buildMetadataConfigResponse()));
        }
        if (isDemoSession(chatSession) && isAbandonMetaConfigPrompt(prompt)) {
            return Optional.of(streamResponse(abandonMetaConfigResponse()));
        }
        if (isDemoSession(chatSession) && isReviseMetaConfigPrompt(prompt)) {
            return Optional.of(streamResponse(buildRevisedMetadataConfigResponse()));
        }
        if (isDemoSession(chatSession) && isCancelCreatePushTaskPrompt(prompt)) {
            return Optional.of(streamResponse(cancelCreatePushTaskResponse()));
        }
        if (isDemoSession(chatSession) && isCreatePushTaskPrompt(prompt)) {
            return Optional.of(streamAction(() -> createPushTaskResponse(chatId, mcpToolContext)));
        }
        if (isDemoSession(chatSession) && isApplyMetaConfigPrompt(prompt)) {
            return Optional.of(streamAction(() -> applyMetaConfigResponse(mcpToolContext)));
        }
        if (isDemoSession(chatSession) && isCancelGeneratePushConfigPrompt(prompt)) {
            return Optional.of(streamResponse(cancelGeneratePushConfigResponse()));
        }
        if (isDemoSession(chatSession) && isGeneratePushConfigPrompt(prompt)) {
            return Optional.of(streamResponse(buildPushConfigResponse()));
        }
        return Optional.empty();
    }

    private Flux<String> streamAction(Callable<String> action) {
        return Mono.fromCallable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamResponse);
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        return Flux.fromIterable(splitResponseChunks(response))
                .delayElements(DEMO_STREAM_DELAY);
    }

    private List<String> splitResponseChunks(String response) {
        List<String> chunks = new java.util.ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int nextLineBreak = response.indexOf('\n', index);
            int limit = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            int end;
            if (nextLineBreak >= index && nextLineBreak < limit) {
                end = nextLineBreak + 1;
            } else {
                end = limit;
            }
            chunks.add(response.substring(index, end));
            index = end;
        }
        return chunks;
    }

    public static boolean isUserEventDemoRequirementPrompt(String prompt) {
        return USER_EVENT_EXAMPLE_PROMPT.trim()
                .equals(Optional.ofNullable(prompt).orElse("").trim());
    }

    private boolean isApplyMetaConfigPrompt(String prompt) {
        return prompt.contains("已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统");
    }

    private boolean isAbandonMetaConfigPrompt(String prompt) {
        return prompt.contains("我选择放弃本次元数据配置")
                || prompt.contains("已放弃本次元数据配置");
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

    private boolean isCreatePushTaskPrompt(String prompt) {
        return prompt.contains("已确认创建用户事件数据推送服务")
                || prompt.contains("数据推送配置创建并启动数据推送服务");
    }

    private boolean isCancelCreatePushTaskPrompt(String prompt) {
        return prompt.contains("已取消创建用户事件数据推送服务")
                || prompt.contains("数据推送配置已生成但未添加到系统");
    }

    private boolean isGeneratePushConfigPrompt(String prompt) {
        return prompt.contains("已确认继续生成用户事件数据推送服务配置")
                || prompt.contains("继续生成数据推送服务配置");
    }

    private boolean isCancelGeneratePushConfigPrompt(String prompt) {
        return prompt.contains("已取消生成用户事件数据推送服务配置")
                || prompt.contains("演示到元数据配置阶段结束");
    }

    private boolean isDemoSession(ChatSession chatSession) {
        if (chatSession == null || !StringUtils.hasText(chatSession.getMessages())) {
            return false;
        }
        try {
            List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
            });
            return messages.stream()
                    .map(Message::getContent)
                    .filter(StringUtils::hasText)
                    .anyMatch(content -> isUserEventDemoRequirementPrompt(content)
                            || content.contains("user_event.json")
                            || content.contains("data-access-demo:user-event:"));
        } catch (Exception e) {
            log.warn("判断数据接入演示会话失败: {}", e.getMessage(), e);
            return false;
        }
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
                        {
                          "label": "调试信息 / msg_user_event",
                          "value": "实体中文名为调试信息，实体英文名为 user_event，表名为 msg_user_event"
                        },
                        {
                          "label": "用户事件 / msg_user_event",
                          "value": "实体中文名为用户事件，实体英文名为 user_event，表名为 msg_user_event"
                        },
                        {
                          "label": "行为事件 / msg_user_event",
                          "value": "实体中文名为行为事件，实体英文名为 user_event，表名为 msg_user_event"
                        }
                      ],
                      "placeholder": "也可以填写其他实体名称或表名"
                    },
                    {
                      "id": "fields",
                      "title": "字段清单",
                      "description": "确认需要生成到元数据配置中的字段。",
                      "required": true,
                      "suggestions": [
                        {
                          "label": "使用完整用户事件字段",
                          "value": "字段包括 event_id、procid、user、event_type、reliability、detail、tags、server_time；平台记录ID由 zenvis_id 自动维护"
                        },
                        {
                          "label": "保留核心字段",
                          "value": "字段包括 event_id、user、event_type、detail、server_time；平台记录ID由 zenvis_id 自动维护"
                        },
                        {
                          "label": "完整字段并自动建表",
                          "value": "字段包括 event_id、procid、user、event_type、reliability、detail、tags、server_time，并启用自动建表；平台记录ID由 zenvis_id 自动维护"
                        }
                      ],
                      "placeholder": "也可以补充字段名、类型和说明"
                    },
                    {
                      "id": "special_rules",
                      "title": "特殊字段规则",
                      "description": "确认枚举、JSON、数组和时间字段的展示与查询规则。",
                      "required": true,
                      "suggestions": [
                        {
                          "label": "使用推荐规则",
                          "value": "event_type 为枚举，detail 按 JSON 展示，tags 按数组展示，server_time 为 DateTime64(3)"
                        },
                        {
                          "label": "强调事件类型筛选",
                          "value": "event_type 支持登录、点击、浏览、删除、修改、其他，并支持等值和包含筛选"
                        },
                        {
                          "label": "强调时间与明细展示",
                          "value": "server_time 作为默认时间字段，detail 保留完整 JSON 明细，tags 保留字符串数组"
                        }
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
                - 实体：调试信息（user_event）
                - 目标表：zenvis.msg_user_event
                - 字段数量：8
                - 时间字段：server_time
                - 自动建表：MergeTree，按 server_time 月分区

                ```zenvis:meta-config
                %s
                ```

                ```zenvis:data-access-decision
                {"title":"元数据配置已生成，请选择后续处理","content":"可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。添加时仍需分别通过 config_add 和 config_apply 的 MCP 审批。","fileName":"user_event.json","configKind":"meta","overwrite":false,"actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(DEMO_META_CONFIG.trim());
    }

    private String abandonMetaConfigResponse() {
        return """
                已放弃本次元数据配置，未写入系统，也不会继续创建数据推送服务。

                ```zenvis:notice
                {"title":"本次配置已放弃","content":"元数据配置流程已结束；如需重新接入用户事件数据，可再次发送数据接入需求。","level":"info"}
                ```
                """;
    }

    private String buildRevisedMetadataConfigResponse() {
        return """
                已根据补充信息重新生成元数据配置，请再次确认后续处理。

                配置摘要：
                - 目标文件：user_event.json
                - 实体：调试信息（user_event）
                - 目标表：zenvis.msg_user_event
                - 字段数量：8
                - 时间字段：server_time
                - 自动建表：MergeTree，按 server_time 月分区

                ```zenvis:meta-config
                %s
                ```

                ```zenvis:data-access-decision
                {"title":"元数据配置已更新，请选择后续处理","content":"可以添加更新后的配置到系统、放弃本次配置，或继续补充调整要求。添加时仍需分别通过 config_add 和 config_apply 的 MCP 审批。","fileName":"user_event.json","configKind":"meta","overwrite":false,"actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(DEMO_META_CONFIG.trim());
    }

    private String applyMetaConfigResponse(McpToolContext mcpToolContext) {
        try {
            String treeResult = callTool(
                    mcpToolContext,
                    "config_tree",
                    Map.of("type", "meta"));
            List<ConfigVo> tree = JacksonConfig.OBJECT_MAPPER.readValue(
                    treeResult,
                    new TypeReference<List<ConfigVo>>() {
                    });
            if (!containsConfigFile(tree, DEMO_META_FILE)) {
                String addResult = callTool(
                        mcpToolContext,
                        "config_add",
                        Map.of(
                                "type", "meta",
                                "configDto", Map.of("fileName", DEMO_META_FILE)));
                requireTrueResult("config_add", addResult);
            }

            String applyResult = callTool(
                    mcpToolContext,
                    "config_apply",
                    Map.of(
                            "type", "meta",
                            "configDto", Map.of(
                                    "fileName", DEMO_META_FILE,
                                    "text", DEMO_META_CONFIG)));
            requireTrueResult("config_apply", applyResult);

            String readResult = callTool(
                    mcpToolContext,
                    "config_read",
                    Map.of("type", "meta", "fileName", DEMO_META_FILE));
            String readBack = decodeStringResult(readResult);
            if (!jsonEquivalent(DEMO_META_CONFIG, readBack)) {
                return metaFailureResponse(
                        "config_read",
                        "配置应用后读回内容与演示目标配置不一致，已停止后续流程。");
            }
        } catch (Exception e) {
            log.error("执行用户事件演示元数据 MCP 流程失败: {}", e.getMessage(), e);
            return metaFailureResponse("MCP 工具执行", safeError(e));
        }

        return """
                元数据配置已通过 MCP 审批完成写入、应用和读回校验。现在可以继续生成数据推送服务配置。

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

                ```zenvis:confirm
                {"title":"是否生成数据推送服务配置","content":"确认后将基于演示数据来源、解析清洗映射规则和推送规则生成固定配置；此步骤不会写入系统，也不调用 AI 模型。","action":"%s"}
                ```
                """.formatted(
                DEMO_META_FILE,
                DEMO_ENTITY_NAME,
                DEMO_ENTITY_LABEL,
                DEMO_TABLE_NAME,
                DEMO_META_CONFIG.trim(),
                GENERATE_DEMO_PUSH_CONFIG_ACTION
        );
    }

    private String metaFailureResponse(String stage, String error) {
        return """
                ```zenvis:notice
                {"title":"元数据配置写入失败","content":"失败阶段：%s\\n真实错误：%s\\n演示流程已停止，未生成成功记录。","level":"error"}
                ```
                """.formatted(escapeJson(stage), escapeJson(error));
    }

    private String buildPushConfigResponse() {
        return """
                已生成数据推送服务配置，请确认后再添加到系统。

                数据推送配置摘要：
                - 服务名称：用户事件数据推送服务
                - 数据源：demo_logs 定时生成用户事件 JSON
                - 解析清洗：解析 JSON，补齐业务字段 event_id、user、procid、reliability、detail、server_time；不写入平台字段 zenvis_id
                - 推送规则：所有用户事件数据写入已确认实体 user_event
                - 写入目标：ZenVis ClickHouse 表 zenvis.msg_user_event，同时输出到 console 便于调试

                ```toml
                %s
                ```

                ```zenvis:confirm
                {"title":"数据推送配置已生成，请确认添加到系统","content":"确认后将调用 push_task_create_and_start，并由平台弹出 MCP 审批；批准后才会创建并启动用户事件数据推送服务。取消则不会写入系统。","action":"%s"}
                ```
                """.formatted(
                DEMO_PUSH_CONFIG.trim(),
                CREATE_DEMO_PUSH_TASK_ACTION
        );
    }

    private String cancelGeneratePushConfigResponse() {
        return """
                已取消生成数据推送服务配置，本次演示停留在元数据配置已添加并应用的阶段。

                ```zenvis:notice
                {"title":"已取消生成数据推送配置","content":"不会生成数据推送服务配置，也不会创建或启动数据推送服务。后续如需继续，可重新确认生成数据推送服务配置。","level":"info"}
                ```
                """;
    }

    private String cancelCreatePushTaskResponse() {
        return """
                已取消创建数据推送服务，已生成的数据推送配置不会添加到系统。

                ```zenvis:notice
                {"title":"已取消创建数据推送服务","content":"不会创建或启动用户事件数据推送服务；元数据配置保持已应用状态。","level":"info"}
                ```
                """;
    }

    private String createPushTaskResponse(String chatId, McpToolContext mcpToolContext) {
        String sourceMark = DEMO_PUSH_MARK_PREFIX + (StringUtils.hasText(chatId) ? chatId : "default");
        String createResult = null;
        try {
            callTool(
                    mcpToolContext,
                    "push_task_detect_format",
                    Map.of("content", DEMO_PUSH_CONFIG));

            List<PushTaskVo> tasks = listPushTasks(mcpToolContext, sourceMark);
            if (tasks.size() > 1) {
                return runtimeFailureResponse(
                        DEMO_PUSH_CONFIG,
                        "未取得",
                        sourceMark,
                        "conflict",
                        "创建前冲突检查",
                        null,
                        "同一 sourceMark 存在多个任务，确定性演示不会自动删除冲突任务。");
            }

            if (tasks.isEmpty()) {
                createResult = callTool(
                        mcpToolContext,
                        "push_task_create_and_start",
                        Map.of("request", Map.of(
                                "name", DEMO_PUSH_NAME,
                                "description", DEMO_PUSH_DESCRIPTION,
                                "config", DEMO_PUSH_CONFIG,
                                "source", "SYSTEM",
                                "mark", sourceMark)));
            }

            tasks = listPushTasks(mcpToolContext, sourceMark);
            if (tasks.size() != 1) {
                return runtimeFailureResponse(
                        DEMO_PUSH_CONFIG,
                        "未取得",
                        sourceMark,
                        "unknown",
                        "创建后查询",
                        null,
                        createResult == null
                                ? "未查询到唯一的演示数据推送任务。"
                                : "创建工具结果：" + describeToolResult(createResult)
                                + "；创建后未查询到唯一任务。");
            }

            PushTaskVo task = tasks.get(0);
            String taskId = task.getId() == null ? "" : String.valueOf(task.getId());
            String config = StringUtils.hasText(task.getConfig()) ? task.getConfig() : DEMO_PUSH_CONFIG;
            Map<String, Object> logResult = readLog(mcpToolContext, task, sourceMark);
            String status = String.valueOf(logResult.getOrDefault(
                    "taskStatus",
                    StringUtils.hasText(task.getStatus()) ? task.getStatus() : "unknown"));
            String systemLog = String.valueOf(logResult.getOrDefault("content", ""));
            boolean truncated = Boolean.parseBoolean(String.valueOf(
                    logResult.getOrDefault("truncated", false)));

            if (!"running".equalsIgnoreCase(status) || containsRuntimeError(systemLog)) {
                return runtimeFailureResponse(
                        config,
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
                    escapeJson(taskId),
                    escapeJson(sourceMark),
                    escapeJson(StringUtils.hasText(task.getName()) ? task.getName() : DEMO_PUSH_NAME),
                    escapeJson(StringUtils.hasText(task.getDescription())
                            ? task.getDescription() : DEMO_PUSH_DESCRIPTION),
                    JacksonUtil.toJson(config)
            );
        } catch (Exception e) {
            log.error("执行用户事件演示数据推送 MCP 流程失败: {}", e.getMessage(), e);
            return runtimeFailureResponse(
                    DEMO_PUSH_CONFIG,
                    "未取得",
                    sourceMark,
                    "unknown",
                    "MCP 工具执行",
                    null,
                    createResult == null ? safeError(e)
                            : describeToolResult(createResult) + "；" + safeError(e));
        }
    }

    private List<PushTaskVo> listPushTasks(McpToolContext mcpToolContext, String sourceMark) throws Exception {
        String result = callTool(
                mcpToolContext,
                "push_task_list_by_source_mark",
                Map.of("sourceMark", sourceMark));
        return JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                new TypeReference<List<PushTaskVo>>() {
                });
    }

    private Map<String, Object> readLog(McpToolContext mcpToolContext,
                                        PushTaskVo task,
                                        String sourceMark) throws Exception {
        String result = callTool(
                mcpToolContext,
                "push_task_get_log",
                Map.of(
                        "taskId", task.getId(),
                        "sourceMark", sourceMark,
                        "logType", "system"));
        return JacksonConfig.OBJECT_MAPPER.readValue(
                result,
                new TypeReference<Map<String, Object>>() {
                });
    }

    private String runtimeFailureResponse(String config,
                                          String taskId,
                                          String sourceMark,
                                          String status,
                                          String stage,
                                          String systemLog,
                                          String reason) {
        return runtimeFailureResponse(
                config,
                taskId,
                sourceMark,
                status,
                stage,
                systemLog,
                systemLog != null && systemLog.length() > 2_000,
                reason);
    }

    private String runtimeFailureResponse(String config,
                                          String taskId,
                                          String sourceMark,
                                          String status,
                                          String stage,
                                          String systemLog,
                                          boolean truncated,
                                          String reason) {
        String sanitizedLog = StringUtils.hasText(systemLog)
                ? sanitizeSystemLog(systemLog)
                : "任务日志不可取得";
        return """
                数据推送服务未确认成功。

                ```toml
                %s
                ```

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
                config.trim(),
                escapeJson(taskId),
                escapeJson(sourceMark),
                escapeJson(status),
                escapeJson(stage),
                truncated,
                escapeJson(sanitizedLog),
                escapeJson(reason));
    }

    private String callTool(McpToolContext mcpToolContext,
                            String toolName,
                            Map<String, Object> arguments) {
        if (mcpToolContext == null
                || mcpToolContext.toolCallbackProvider() == null
                || mcpToolContext.toolCallbackProvider().getToolCallbacks() == null) {
            throw new IllegalStateException("演示所需 MCP 工具上下文不可用");
        }
        ToolCallback callback = Arrays.stream(mcpToolContext.toolCallbackProvider().getToolCallbacks())
                .filter(tool -> tool != null
                        && tool.getToolDefinition() != null
                        && toolName.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("演示所需 MCP 工具不可用：" + toolName));

        Map<String, Object> context = new LinkedHashMap<>();
        McpInvocationContext invocationContext = mcpToolContext.invocationContext();
        if (invocationContext != null) {
            context.put(McpInvocationContext.TOOL_CONTEXT_KEY, invocationContext);
        }
        ToolRuntimeContext runtimeContext = mcpToolContext.toolRuntimeContext();
        if (runtimeContext != null) {
            context.put(ToolRuntimeContext.TOOL_CONTEXT_KEY, runtimeContext);
        }
        return callback.call(JacksonUtil.toJson(arguments), new ToolContext(context));
    }

    private void requireTrueResult(String toolName, String result) throws Exception {
        JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
        if (node != null && (node.isBoolean() && node.booleanValue()
                || node.isTextual() && Boolean.parseBoolean(node.textValue()))) {
            return;
        }
        throw new IllegalStateException(toolName + " 未成功：" + describeToolResult(result));
    }

    private String decodeStringResult(String result) throws Exception {
        JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
        return node != null && node.isTextual() ? node.textValue() : result;
    }

    private boolean jsonEquivalent(String expected, String actual) throws Exception {
        return JacksonConfig.OBJECT_MAPPER.readTree(expected)
                .equals(JacksonConfig.OBJECT_MAPPER.readTree(actual));
    }

    private boolean containsConfigFile(List<ConfigVo> nodes, String fileName) {
        if (nodes == null) {
            return false;
        }
        for (ConfigVo node : nodes) {
            if (node == null) {
                continue;
            }
            if (fileName.equals(node.getFileName()) || containsConfigFile(node.getNodes(), fileName)) {
                return true;
            }
        }
        return false;
    }

    private String describeToolResult(String result) {
        if (!StringUtils.hasText(result)) {
            return "工具未返回结果";
        }
        try {
            JsonNode node = JacksonConfig.OBJECT_MAPPER.readTree(result);
            if (node != null && node.isObject()) {
                String status = node.path("status").asText("");
                String message = node.path("message").asText("");
                if (StringUtils.hasText(status) || StringUtils.hasText(message)) {
                    return "status=" + status + (StringUtils.hasText(message) ? "，" + message : "");
                }
            }
        } catch (Exception ignored) {
            // 使用受限的纯文本摘要。
        }
        return result.length() <= 500 ? result : result.substring(0, 500) + "...";
    }

    private boolean containsRuntimeError(String logContent) {
        if (!StringUtils.hasText(logContent)) {
            return false;
        }
        int markerIndex = logContent.lastIndexOf(VECTUM_RUN_LOG_MARKER_PREFIX);
        String currentRunLog = markerIndex >= 0
                ? logContent.substring(markerIndex + VECTUM_RUN_LOG_MARKER_PREFIX.length())
                : logContent;
        String normalized = currentRunLog.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("error")
                || normalized.contains("failed")
                || normalized.contains("fatal")
                || normalized.contains("panic");
    }

    private String sanitizeSystemLog(String logContent) {
        String sanitized = StringUtils.trimWhitespace(
                StringUtils.hasText(logContent) ? logContent : "空日志");
        sanitized = sanitized.replaceAll(
                "(?i)\\bBearer\\s+[^\\s,;]+",
                "Bearer ***");
        sanitized = sanitized.replaceAll(
                "(?i)([\"']?(?:password|passwd|token|secret|api[_-]?key|access[_-]?key|authorization)"
                        + "[\"']?\\s*[:=]\\s*[\"']?)([^\\s,;\"']+)",
                "$1***");
        if (sanitized.length() > 2_000) {
            return sanitized.substring(sanitized.length() - 2_000);
        }
        return sanitized;
    }

    private String safeError(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return StringUtils.hasText(message) ? sanitizeSystemLog(message) : "未知错误";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

}
