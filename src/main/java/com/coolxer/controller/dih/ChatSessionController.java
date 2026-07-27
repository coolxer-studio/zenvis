package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.model.dih.vo.SkillChatConfigVo;
import com.coolxer.model.dih.vo.SkillChatPromptSuggestionVo;
import com.coolxer.model.dih.vo.SkillVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.ConfigManagementDemoResponseService;
import com.coolxer.service.dih.DataAnalysisDemoResponseService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.utils.JacksonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT;

/**
 * 会话管理
 */
@Tag(name = "会话管理")
@RestController
@RequestMapping("/api/v1/dih/chat-session")
public class ChatSessionController extends BaseController {

    private static final String DATA_ACCESS_TEMPLATE_DOWNLOAD_URL = "/zenvis/system-files/data-access-requirement-template.md";

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SkillService skillService;

    @PostMapping({"/add"})
    public ResponseWrap<?> add(@RequestBody ChatSessionDto chatSessionDto) {
        try {
            User currentUser = getSessionUser();
            if (chatSessionService.create(chatSessionDto, currentUser) != null) {
                return ResponseWrap.success("创建成功");
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/{id}"})
    public ResponseWrap<?> delete(@PathVariable("id") Long id) {
        try {
            User currentUser = getSessionUser();
            chatSessionService.delete(id, currentUser);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping({"/bulk/{ids}"})
    public ResponseWrap<?> bulkDelete(@PathVariable("ids") List<Long> ids) {
        try {
            User currentUser = getSessionUser();
            chatSessionService.deleteByIds(ids, currentUser);
            return ResponseWrap.success("删除成功");
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping({"/{id}/update"})
    public ResponseWrap<?> update(@PathVariable("id") Long id, @RequestBody ChatSessionDto chatSessionDto) {
        try {
            User currentUser = getSessionUser();
            if (chatSessionService.update(id, chatSessionDto, currentUser)) {
                return ResponseWrap.success("修改成功");
            } else
                return ResponseWrap.fail();
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list/pin"})
    public ResponseWrap<?> listPin() {
        try {
            User currentUser = getSessionUser();
            List<ChatSessionVo> chatSessionVoList = chatSessionService.getPinList(currentUser);
            return ResponseWrap.success(chatSessionVoList);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/list"})
    public ResponseWrap<?> list(ChatSessionSearchDto chatSessionSearchDto) {
        try {
            User currentUser = getSessionUser();
            PageRowsVo<ChatSessionVo> pageDataVo = chatSessionService.getPageList(chatSessionSearchDto, currentUser);
            return ResponseWrap.success(pageDataVo);
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping({"/{id}/view"})
    public ResponseWrap<ChatSessionVo> query(@PathVariable("id") Long id) {
        try {
            User currentUser = getSessionUser();
            ChatSessionVo chatSessionVo = chatSessionService.info(id, currentUser);
            if (chatSessionVo == null) {
                return ResponseWrap.fail();
            } else {
                return ResponseWrap.success(chatSessionVo);
            }
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    private static final String PROLOGUE_DEFAULT = "我是数智助手，可以解答系统相关运营问题，有什么问题尽管提问吧！";
    private static final String PROLOGUE_AGENT_DATA_ACCESS = "我是数据接入智能体，只处理数据接入相关工作，主要包括两件事：元数据配置和数据推送服务。\n" +
            "默认会先完成元数据配置，配置成功生效后，再根据你的明确要求添加数据推送服务。\n" +
            "你可以先下载并填写 [数据接入需求模板](" + DATA_ACCESS_TEMPLATE_DOWNLOAD_URL + ")，填写完成后作为 `.md` 附件上传，我会读取文档内容帮助生成并生效配置。";
    private static final String PROLOGUE_AGENT_DATA_ACCESS_EXAMPLE_INTRO = "> 下面是可复制的用户事件数据接入需求样例。\n 复制后粘贴到对话框发送，即可按模板体验元数据配置和数据推送服务创建流程。";
    private static final String DATA_ACCESS_USER_EVENT_EXAMPLE_PROMPT = """
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
    private static final String PROLOGUE_AGENT_DATA_VISUALIZATION = "我是数据可视化智能体，建立在数据接入产生的元数据实体之上，可以生成临时图表、低代码页面/应用、静态 HTML 页面、数据看板和菜单配置。\n" +
            "我会先确认目标类型、实体字段、时间范围、统计维度和实现方式；涉及写入 open_config、创建菜单或看板时，会先给出确认卡，只有你确认后才写入系统。";
    private static final String PROLOGUE_AGENT_DATA_VISUALIZATION_EXAMPLE_INTRO = "可以点击下面的示例快速填入提示词。";
    private static final String DATA_VISUALIZATION_CHART_EXAMPLE_PROMPT = "请查看用户事件数据的上报情况，并生成一个临时性的可视化图表。";
    private static final String DATA_VISUALIZATION_PAGE_EXAMPLE_PROMPT = "请根据用户事件数据生成一个单页面应用。";
    private static final String DATA_VISUALIZATION_APP_EXAMPLE_PROMPT = "请生成一个带侧边栏的用户事件数据应用。";
    private static final String DATA_VISUALIZATION_DASHBOARD_EXAMPLE_PROMPT = "请生成一个用户事件数据看板。";
    private static final String PROLOGUE_AGENT_DATA_ANALYSIS = "我是数据分析智能体，面向用户提供的业务需求完成综合数据分析并输出分析结果。\n" +
            "我会按三个阶段工作：先准备并确认系统内相关实体数据集，再通过 MCP 提交给独立分析服务，最后形成包含分析目标、分析过程和分析结论的报告。\n" +
            "如果需求或数据范围不明确，我会先补充询问必要字段；如果缺少分析服务 MCP 能力，我会明确说明缺失项，不伪造分析结果。";
    private static final String PROLOGUE_AGENT_DATA_ANALYSIS_EXAMPLE_INTRO = "可以点击下面的示例快速填入数据分析需求。";
    private static final String DATA_ANALYSIS_EXAMPLE_PROMPT =
            DataAnalysisDemoResponseService.DATA_ANALYSIS_EXAMPLE_PROMPT;
    private static final String PROLOGUE_AGENT_CONFIG_MANAGEMENT = "我是配置管理智能体，负责根据用户需求生成符合系统要求的配置。\n" +
            "我会按三个阶段工作：先生成配置并记录到右侧配置记录，再按需进入试验场验证，验证成功且你认可后再下发到系统正式生效。\n" +
            "所有正式生效动作都会先确认，并经过平台 MCP 审批和写后读回校验。";
    private static final String PROLOGUE_AGENT_CONFIG_MANAGEMENT_EXAMPLE_INTRO = "可以点击下面的示例快速填入配置管理需求。";
    private static final String CONFIG_MANAGEMENT_EXAMPLE_PROMPT =
            ConfigManagementDemoResponseService.CONFIG_MANAGEMENT_EXAMPLE_PROMPT;
    private static final String PROLOGUE_AGENT_REPORT = "我是报告智能体，专注于高效生成专业分析报告。\n" +
            " 通过智能编辑器，快速整合分析过程中的数据、图表与结论，实现内容自动生成与文案优化。\n" +
            " 支持一键导入分析素材，助您快速产出结构清晰、内容详实的高质量分析报告。";
    private static final String PROLOGUE_AGENT_REPORT_EXAMPLE_INTRO = "可以点击下面的示例快速填入提示词，并生成可编辑报表草稿。";

    @GetMapping({"/{sessionId}/session"})
    public ResponseWrap<ChatSessionVo> sessionInfo(@PathVariable("sessionId") String sessionId, @RequestParam(value = "type", required = false) String type) {
        try {
            User currentUser = getSessionUser();
            ChatSession chatSession = chatSessionService.getChatSessionBySessionId(sessionId, currentUser);
            if (chatSession == null) {
                // 返回默认会话开头语模版
                chatSession = new ChatSession();
                chatSession.setTitle("新建会话");
                chatSession.setSessionId(sessionId);
                chatSession.setType(normalizeType(type));
                chatSession.setMessages(JacksonUtil.toJson(List.of(buildPrologueMessage(chatSession.getType()))));
            }
            return ResponseWrap.success(new ChatSessionVo(chatSession));
        } catch (Exception e) {
            return ResponseWrap.fail(e);
        }
    }

    private String normalizeType(String type) {
        return type == null || type.isBlank() ? "ask" : type;
    }

    private Message buildPrologueMessage(String type) {
        if (SkillService.isDynamicChatType(normalizeType(type))) {
            return buildDynamicSkillPrologueMessage(type);
        }
        if ("agent_data_access".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_DATA_ACCESS
                    + "\n\n"
                    + PROLOGUE_AGENT_DATA_ACCESS_EXAMPLE_INTRO
                    + "\n\n````markdown\n"
                    + DATA_ACCESS_USER_EVENT_EXAMPLE_PROMPT
                    + "\n````";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_ACCESS)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_ACCESS_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("code")
                            .title("用户事件数据接入需求样例")
                            .language("markdown")
                            .content(DATA_ACCESS_USER_EVENT_EXAMPLE_PROMPT)
                            .metadata(Map.of("defaultCollapsed", true))
                            .build()
            ));
            return message;
        }
        if ("agent_data_visualization".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_DATA_VISUALIZATION
                    + "\n\n"
                    + PROLOGUE_AGENT_DATA_VISUALIZATION_EXAMPLE_INTRO
                    + "\n\n"
                    + "临时图表｜单页面应用｜带侧边栏应用｜数据看板";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_VISUALIZATION)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_VISUALIZATION_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("用户事件数据可视化示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "临时图表", "prompt", DATA_VISUALIZATION_CHART_EXAMPLE_PROMPT),
                                            Map.of("label", "单页面应用", "prompt", DATA_VISUALIZATION_PAGE_EXAMPLE_PROMPT),
                                            Map.of("label", "带侧边栏应用", "prompt", DATA_VISUALIZATION_APP_EXAMPLE_PROMPT),
                                            Map.of("label", "数据看板", "prompt", DATA_VISUALIZATION_DASHBOARD_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        if ("agent_data_analysis".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_DATA_ANALYSIS
                    + "\n\n"
                    + PROLOGUE_AGENT_DATA_ANALYSIS_EXAMPLE_INTRO
                    + "\n\n"
                    + "用户事件异常波动分析";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_ANALYSIS)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_ANALYSIS_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("数据分析示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "用户事件异常波动分析", "prompt", DATA_ANALYSIS_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        if ("agent_config_management".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_CONFIG_MANAGEMENT
                    + "\n\n"
                    + PROLOGUE_AGENT_CONFIG_MANAGEMENT_EXAMPLE_INTRO
                    + "\n\n"
                    + "系统信息展示配置调整";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_CONFIG_MANAGEMENT)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_CONFIG_MANAGEMENT_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("配置管理示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "系统信息展示配置调整", "prompt", CONFIG_MANAGEMENT_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        if ("agent_report".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_REPORT
                    + "\n\n"
                    + PROLOGUE_AGENT_REPORT_EXAMPLE_INTRO
                    + "\n\n"
                    + "用户事件分析报告｜运营周报｜风险事件复盘｜可视化结论归档报告";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_REPORT)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_REPORT_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("报表生成示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "用户事件分析报告", "prompt", REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT),
                                            Map.of("label", "运营周报", "prompt", REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT),
                                            Map.of("label", "风险事件复盘", "prompt", REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT),
                                            Map.of("label", "可视化结论归档报告", "prompt", REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        return new Message("ai", resolvePrologue(type));
    }

    private Message buildDynamicSkillPrologueMessage(String type) {
        try {
            SkillVo skill = skillService.requireEnabledChatSkill(normalizeType(type));
            SkillChatConfigVo chat = skill.getChat();
            String label = StringUtils.defaultIfBlank(chat.getLabel(), skill.getName());
            String prologue = StringUtils.defaultIfBlank(
                    chat.getPrologue(),
                    StringUtils.defaultIfBlank(skill.getDescription(), "我是" + label + "助手，请告诉我你希望处理的任务。")
            );
            List<SkillChatPromptSuggestionVo> suggestions = chat.getPromptSuggestions() == null
                    ? List.of()
                    : chat.getPromptSuggestions().stream()
                    .filter(item -> item != null
                            && StringUtils.isNotBlank(item.getLabel())
                            && StringUtils.isNotBlank(item.getPrompt()))
                    .toList();
            Message message = new Message("ai", prologue);
            if (suggestions.isEmpty()) {
                message.setParts(List.of(
                        ChatMessagePart.builder()
                                .type("markdown")
                                .content(prologue)
                                .build()
                ));
                return message;
            }
            List<Map<String, String>> examples = suggestions.stream()
                    .map(item -> Map.of("label", item.getLabel(), "prompt", item.getPrompt()))
                    .toList();
            message.setContent(prologue + "\n\n" + String.join(
                    "｜",
                    suggestions.stream().map(SkillChatPromptSuggestionVo::getLabel).toList()
            ));
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(prologue)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title(label + "示例")
                            .metadata(Map.of("examples", examples))
                            .build()
            ));
            return message;
        } catch (IllegalArgumentException e) {
            return new Message("ai", "当前 Skill 已停用或不存在，请返回 DIH 选择其他可用技能。");
        }
    }

    private String resolvePrologue(String type) {
        return switch (normalizeType(type)) {
            case "agent_data_access" -> PROLOGUE_AGENT_DATA_ACCESS;
            case "agent_data_visualization" -> PROLOGUE_AGENT_DATA_VISUALIZATION;
            case "agent_data_analysis" -> PROLOGUE_AGENT_DATA_ANALYSIS;
            case "agent_config_management" -> PROLOGUE_AGENT_CONFIG_MANAGEMENT;
            case "agent_report" -> PROLOGUE_AGENT_REPORT;
            default -> PROLOGUE_DEFAULT;
        };
    }

}
