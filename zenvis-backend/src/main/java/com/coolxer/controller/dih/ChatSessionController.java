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
import static com.coolxer.service.dih.VisualizationAppDemoResponseService.MENU_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.VisualizationAppDemoResponseService.PAGE_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.VisualizationAppDemoResponseService.SIDEBAR_APP_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.VisualizationChartDemoResponseService.CHART_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.VisualizationDashboardDemoResponseService.DASHBOARD_EXAMPLE_PROMPT;

/**
 * 会话管理
 */
@Tag(name = "会话管理")
@RestController
@RequestMapping("/api/v1/dih/chat-session")
public class ChatSessionController extends BaseController {

    private static final String METADATA_CONFIG_TEMPLATE_DOWNLOAD_URL = "/zenvis/system-files/metadata-config-requirement-template.md";

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
    private static final String PROLOGUE_AGENT_METADATA_CONFIG = "我是元数据配置智能体，只负责 Meta 元数据配置的生成、写入和读回校验。\n" +
            "我会先检查现有 Meta，生成完整候选并等待你确认；同名不同内容时会单独请求覆盖确认，写入后必须读回一致才算成功。\n" +
            "你可以先下载并填写 [元数据配置需求模板](" + METADATA_CONFIG_TEMPLATE_DOWNLOAD_URL + ")，填写完成后作为 `.md` 附件上传，我会读取文档内容帮助生成并生效 Meta。";
    private static final String PROLOGUE_AGENT_METADATA_CONFIG_EXAMPLE_INTRO = "> 下面是可复制的用户事件元数据配置需求样例。\n 复制后粘贴到对话框发送，即可体验元数据配置全流程。";
    private static final String METADATA_CONFIG_USER_EVENT_EXAMPLE_PROMPT =
            com.coolxer.service.dih.MetadataConfigDemoResponseService.USER_EVENT_EXAMPLE_PROMPT;
    private static final String PROLOGUE_AGENT_DATA_PUSH_SERVICE = "我是数据推送服务智能体，只负责基于已发布 Meta 创建、诊断和修复 PushTask。\n" +
            "Meta 对我始终只读：我会先通过 config_tree 和 config_read 校验目标 Meta；缺失时会停止写入并引导你前往元数据配置。\n" +
            "创建任务前会展示完整 Vectum 配置并等待确认，执行后必须读回任务状态和 system 日志。";
    private static final String PROLOGUE_AGENT_DATA_PUSH_SERVICE_EXAMPLE_INTRO = "> 下面是基于已发布 user_event Meta 创建数据推送服务的样例。\n 复制后粘贴到对话框发送，即可体验只读 Meta 校验、配置确认和真实 MCP 审批流程。";
    private static final String DATA_PUSH_USER_EVENT_EXAMPLE_PROMPT =
            com.coolxer.service.dih.DataPushDemoResponseService.USER_EVENT_EXAMPLE_PROMPT;
    private static final String PROLOGUE_AGENT_VISUALIZATION_CHART = "我是临时图表智能体，只基于已发布 Meta 查询真实数据并生成临时 ECharts 图表。\n" +
            "我不会写入配置、创建应用、看板或菜单；只有经过查询方案确认和真实数据校验的图表才能加入本次会话图表库。";
    private static final String PROLOGUE_AGENT_VISUALIZATION_APP = "我是可视化应用智能体，只基于已发布 Meta 生成低代码单页、侧边栏应用或静态 HTML 页面，并按需创建应用菜单。\n" +
            "配置写入前会先锁定并展示确认卡，写入后必须读回一致；我不会创建数据大屏。";
    private static final String PROLOGUE_AGENT_VISUALIZATION_DASHBOARD = "我是数据大屏智能体，只基于已发布 Meta 生成低代码或静态 HTML 看板，并创建看板入口和配置管理菜单。\n" +
            "我不会写入 Meta，也不会生成普通可视化应用页面；Dashboard 与菜单都必须真实创建并读回。";
    private static final String PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO = "可以点击下面的示例快速填入提示词。";
    private static final String PROLOGUE_AGENT_REPORT = "我是报告智能体，专注于高效生成专业分析报告。\n" +
            " 通过智能编辑器，快速整合分析过程中的数据、图表与结论，实现内容自动生成与文案优化。\n" +
            " 支持从图表库一键插入临时图表高清快照，助您快速产出结构清晰、内容详实的高质量分析报告。";
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
        if ("agent_metadata_config".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_METADATA_CONFIG
                    + "\n\n"
                    + PROLOGUE_AGENT_METADATA_CONFIG_EXAMPLE_INTRO
                    + "\n\n````markdown\n"
                    + METADATA_CONFIG_USER_EVENT_EXAMPLE_PROMPT
                    + "\n````";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_METADATA_CONFIG)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_METADATA_CONFIG_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("code")
                            .title("用户事件元数据配置需求样例")
                            .language("markdown")
                            .content(METADATA_CONFIG_USER_EVENT_EXAMPLE_PROMPT)
                            .metadata(Map.of("defaultCollapsed", true))
                            .build()
            ));
            return message;
        }
        if ("agent_data_push_service".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_DATA_PUSH_SERVICE
                    + "\n\n"
                    + PROLOGUE_AGENT_DATA_PUSH_SERVICE_EXAMPLE_INTRO
                    + "\n\n````markdown\n"
                    + DATA_PUSH_USER_EVENT_EXAMPLE_PROMPT
                    + "\n````";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_PUSH_SERVICE)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_DATA_PUSH_SERVICE_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("code")
                            .title("用户事件数据推送服务需求样例")
                            .language("markdown")
                            .content(DATA_PUSH_USER_EVENT_EXAMPLE_PROMPT)
                            .metadata(Map.of("defaultCollapsed", true))
                            .build()
            ));
            return message;
        }
        if ("agent_visualization_chart".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_VISUALIZATION_CHART
                    + "\n\n"
                    + PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO
                    + "\n\n"
                    + "临时图表";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_CHART)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("临时图表示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "用户事件临时图表", "prompt", CHART_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        if ("agent_visualization_app".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_VISUALIZATION_APP
                    + "\n\n"
                    + PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO
                    + "\n\n"
                    + "单页面应用｜带侧边栏应用｜添加菜单";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_APP)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("可视化应用示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(
                                            Map.of("label", "单页面应用", "prompt", PAGE_EXAMPLE_PROMPT),
                                            Map.of("label", "带侧边栏应用", "prompt", SIDEBAR_APP_EXAMPLE_PROMPT),
                                            Map.of("label", "添加菜单", "prompt", MENU_EXAMPLE_PROMPT)
                                    )
                            ))
                            .build()
            ));
            return message;
        }
        if ("agent_visualization_dashboard".equals(normalizeType(type))) {
            String content = PROLOGUE_AGENT_VISUALIZATION_DASHBOARD
                    + "\n\n"
                    + PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO
                    + "\n\n"
                    + "数据大屏";
            Message message = new Message("ai", content);
            message.setParts(List.of(
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_DASHBOARD)
                            .build(),
                    ChatMessagePart.builder()
                            .type("markdown")
                            .content(PROLOGUE_AGENT_VISUALIZATION_EXAMPLE_INTRO)
                            .build(),
                    ChatMessagePart.builder()
                            .type("prompt-suggestions")
                            .title("数据大屏示例")
                            .metadata(Map.of(
                                    "examples",
                                    List.of(Map.of(
                                            "label", "用户事件数据大屏",
                                            "prompt", DASHBOARD_EXAMPLE_PROMPT))
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
            case "agent_metadata_config" -> PROLOGUE_AGENT_METADATA_CONFIG;
            case "agent_data_push_service" -> PROLOGUE_AGENT_DATA_PUSH_SERVICE;
            case "agent_visualization_chart" -> PROLOGUE_AGENT_VISUALIZATION_CHART;
            case "agent_visualization_app" -> PROLOGUE_AGENT_VISUALIZATION_APP;
            case "agent_visualization_dashboard" -> PROLOGUE_AGENT_VISUALIZATION_DASHBOARD;
            case "agent_report" -> PROLOGUE_AGENT_REPORT;
            default -> PROLOGUE_DEFAULT;
        };
    }

}
