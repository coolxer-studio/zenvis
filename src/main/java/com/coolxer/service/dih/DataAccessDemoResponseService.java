package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DataAccessDemoResponseService {

    public static final String USER_EVENT_DEMO_TITLE = "用户事件数据接入演示";

    private static final String DEMO_META_FILE = "user_event.json";
    private static final String DEMO_ENTITY_NAME = "user_event";
    private static final String DEMO_ENTITY_LABEL = "调试信息";
    private static final String DEMO_TABLE_NAME = "zenvis.msg_user_event";
    private static final String DEMO_PUSH_MARK_PREFIX = "data-access-demo:user-event:";
    private static final String DEMO_PUSH_NAME = "用户事件数据推送服务";
    private static final String DEMO_PUSH_DESCRIPTION = "定时生成用户事件 JSON demo 日志并写入 ZenVis ClickHouse msg_user_event 表";
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
              auth.password = "SFGEfSVVMcUHCBCjKmzJ"
              skip_unknown_fields = true

            [sinks.console]
              inputs = ["parse_json"]
              type = "console"
              encoding.codec = "json"
            """;

    private final ConfigService configService;
    private final PushTaskService pushTaskService;

    public DataAccessDemoResponseService(ConfigService configService, PushTaskService pushTaskService) {
        this.configService = configService;
        this.pushTaskService = pushTaskService;
    }

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
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
        if (isDemoSession(chatSession) && isApplyMetaConfigPrompt(prompt)) {
            return Optional.of(streamResponse(applyMetaConfigResponse()));
        }
        if (isDemoSession(chatSession) && isCancelGeneratePushConfigPrompt(prompt)) {
            return Optional.of(streamResponse(cancelGeneratePushConfigResponse()));
        }
        if (isDemoSession(chatSession) && isGeneratePushConfigPrompt(prompt)) {
            return Optional.of(streamResponse(buildPushConfigResponse()));
        }
        if (isDemoSession(chatSession) && isCancelCreatePushTaskPrompt(prompt)) {
            return Optional.of(streamResponse(cancelCreatePushTaskResponse()));
        }
        if (isDemoSession(chatSession) && isCreatePushTaskPrompt(prompt)) {
            return Optional.of(streamResponse(createPushTaskResponse(chatId)));
        }
        return Optional.empty();
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
        return prompt.contains("# 用户事件数据接入")
                && prompt.contains("msg_user_event")
                && prompt.contains("demo_logs")
                && prompt.contains("event_type")
                && prompt.contains("server_time")
                && prompt.contains("reliability");
    }

    private boolean isApplyMetaConfigPrompt(String prompt) {
        return prompt.contains("已确认并授权添加上一轮已生成并展示的 meta 元数据配置到系统")
                || prompt.contains("已确认添加配置到系统");
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
                {"title":"元数据配置已生成，请选择后续处理","content":"可以添加配置到系统、放弃本次配置，或补充调整要求继续更新配置。","actions":["apply_config","abandon","revise"]}
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
                {"title":"元数据配置已更新，请选择后续处理","content":"可以添加更新后的配置到系统、放弃本次配置，或继续补充调整要求。","actions":["apply_config","abandon","revise"]}
                ```
                """.formatted(DEMO_META_CONFIG.trim());
    }

    private String applyMetaConfigResponse() {
        try {
            configService.ensureRootPath("meta");
            if (!configService.fileExistsInConfigPath("meta", DEMO_META_FILE)) {
                configService.addFile("meta", DEMO_META_FILE);
            }
            ConfigDto configDto = new ConfigDto();
            configDto.setFileName(DEMO_META_FILE);
            configDto.setText(DEMO_META_CONFIG);
            configService.modifyConfig("meta", configDto);
            configService.applyConfig("meta", configDto);
        } catch (Exception e) {
            log.error("写入用户事件演示元数据配置失败: {}", e.getMessage(), e);
            return """
                    ```zenvis:notice
                    {"title":"元数据配置写入失败","content":"元数据配置写入或应用失败，请检查配置目录、ClickHouse 连接和后端日志后重试。","level":"error"}
                    ```
                    """;
        }

        return """
                元数据配置已添加并应用，读回校验通过。现在可以继续生成数据推送服务配置。

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
                {"title":"是否生成数据推送服务配置","content":"确认后将基于已确认的数据来源、解析清洗映射规则和推送规则，生成用户事件数据推送服务配置；此步骤不会写入系统。","action":"%s"}
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
                {"title":"数据推送配置已生成，请确认添加到系统","content":"确认后才会创建并启动用户事件数据推送服务；取消则不会写入系统。","action":"%s"}
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

    private String createPushTaskResponse(String chatId) {
        String sourceMark = DEMO_PUSH_MARK_PREFIX + (StringUtils.hasText(chatId) ? chatId : "default");
        PushTaskVo task = findExistingTask(sourceMark);
        if (task == null) {
            PushTaskDto pushTaskDto = new PushTaskDto();
            pushTaskDto.setName(DEMO_PUSH_NAME);
            pushTaskDto.setDescription(DEMO_PUSH_DESCRIPTION);
            pushTaskDto.setConfig(DEMO_PUSH_CONFIG);
            pushTaskDto.setSource("SYSTEM");
            pushTaskDto.setMark(sourceMark);
            try {
                boolean created = pushTaskService.createAndStart(pushTaskDto);
                if (created) {
                    task = findExistingTask(sourceMark);
                }
            } catch (Exception e) {
                log.error("创建用户事件演示数据推送服务失败: {}", e.getMessage(), e);
            }
        }

        if (task == null) {
            return """
                    数据推送配置已生成，但创建或启动服务失败，请检查 Vectum 服务状态后重试。

                    ```toml
                    %s
                    ```

                    ```zenvis:notice
                    {"title":"数据推送服务创建失败","content":"未能创建或查询到用户事件数据推送服务，请确认 Vectum 服务可用。","level":"error"}
                    ```
                    """.formatted(DEMO_PUSH_CONFIG.trim());
        }

        String taskId = task.getId() == null ? "" : String.valueOf(task.getId());
        String status = StringUtils.hasText(task.getStatus()) ? task.getStatus() : "running";
        String config = StringUtils.hasText(task.getConfig()) ? task.getConfig() : DEMO_PUSH_CONFIG;

        return """
                数据推送服务已创建并校验通过。

                ```zenvis:vectum-task-record
                {
                  "title": "数据推送服务已创建",
                  "taskId": "%s",
                  "sourceMark": "%s",
                  "name": "%s",
                  "description": "%s",
                  "status": "%s",
                  "config": %s
                }
                ```
                """.formatted(
                escapeJson(taskId),
                escapeJson(sourceMark),
                escapeJson(StringUtils.hasText(task.getName()) ? task.getName() : DEMO_PUSH_NAME),
                escapeJson(StringUtils.hasText(task.getDescription()) ? task.getDescription() : DEMO_PUSH_DESCRIPTION),
                escapeJson(status),
                JacksonUtil.toJson(config)
        );
    }

    private PushTaskVo findExistingTask(String sourceMark) {
        try {
            List<PushTaskVo> tasks = pushTaskService.findBySourceMark(sourceMark);
            if (tasks != null && !tasks.isEmpty()) {
                return tasks.get(0);
            }
        } catch (Exception e) {
            log.warn("查询用户事件演示数据推送服务失败: {}", e.getMessage(), e);
        }
        return null;
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
