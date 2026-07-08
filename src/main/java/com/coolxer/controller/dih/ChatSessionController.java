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
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.utils.JacksonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    private static final String PROLOGUE_DEFAULT = "我是数智助手（X-Sage），可以解答系统相关运营问题，有什么问题尽管提问吧！";
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
            | id | 550e8400-e29b-41d4-a716-446655440000 | 事件id | 测试事件唯一标识符 | String | 是 | equal、notequal、in |
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
              "id": "550e8400-e29b-41d4-a716-446655440000",
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
            | 唯一标识字段 | id |
            | 排序字段 | id、server_time |
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
            | 字段映射 | 保留 event_type、tags；自动补齐 id、user、procid、reliability、detail、server_time。 |
            | 清洗规则 | 不过滤，全部保留；ClickHouse 写入时跳过未知字段。 |
            | 转换规则 | id 使用 UUID；user 使用随机字节的 base64 字符串；procid 生成 100 到 110 的整数；reliability 生成 0.0 到 10.0 的浮点数；detail 固定为 {"method":"POST","path":"/v1/orders","query":"dry_run=false"}；server_time 使用当前时间格式化为 yyyy-MM-dd HH:mm:ss。 |
            | 异常数据处理 | 同时输出到 console，编码为 JSON，便于调试观察。 |

            ### 2.3 推送规则

            | 数据类型或条件 | 对应实体 | 说明 |
            | --- | --- | --- |
            | 全部用户事件数据 | user-event / 调试信息 | 写入 msg_user_event 表；目标库默认为系统的 zenvis 库。 |

           """;
    private static final String PROLOGUE_AGENT_INSPECT = "我是巡检智能体，专注于通过只读 Retrieval MCP 查询多源日志数据并完成巡检分析。\n" +
            " 我会先确认真实可用的实体、字段、统计维度和时间范围，再调用检索、计数、趋势、分布等只读工具获取证据。\n" +
            " 我只输出文本或 Markdown 格式的巡检结论、数据摘要、异常线索和后续建议，不直接访问数据库、不生成图表消息或落库操作。";
    private static final String PROLOGUE_AGENT_ANALYSIS = "我是研判智能体，专注于风险事件的深度分析与等级评估。\n" +
            " 通过数据聚合、情报关联、规则匹配及动态执行等多维度研判手段，精准评估风险等级合理性。\n" +
            " 所有研判过程均调用外部工具进行证据链验证，所有分析依据与取证结果将完整存档，确保研判结论可追溯、可复现。";
    private static final String PROLOGUE_AGENT_DISPOSE = "我是策略智能体，负责系统策略的全生命周期管理。\n" +
            " 涵盖探针数据采集、动态标记引擎、处置响应、设备指纹、风险评定、数据推送及可视化等策略配置。\n" +
            " 所有策略变更需经管理员审批后生效，确保系统配置安全可控、合规有效。";
    private static final String PROLOGUE_AGENT_REPORT = "我是报告智能体，专注于高效生成专业分析报告。\n" +
            " 通过智能编辑器，快速整合分析过程中的数据、图表与结论，实现内容自动生成与文案优化。\n" +
            " 支持一键导入分析素材，助您快速产出结构清晰、内容详实的高质量分析报告。";

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
        return new Message("ai", resolvePrologue(type));
    }

    private String resolvePrologue(String type) {
        return switch (normalizeType(type)) {
            case "agent_data_access" -> PROLOGUE_AGENT_DATA_ACCESS;
            case "agent_inspect" -> PROLOGUE_AGENT_INSPECT;
            case "agent_analysis" -> PROLOGUE_AGENT_ANALYSIS;
            case "agent_dispose" -> PROLOGUE_AGENT_DISPOSE;
            case "agent_report" -> PROLOGUE_AGENT_REPORT;
            default -> PROLOGUE_DEFAULT;
        };
    }

}
