package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.service.dih.agent.AnalysisAgent;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.DisposeAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.coolxer.service.dih.AnalysisDemoResponseService.ANALYSIS_DEMO_TITLE;
import static com.coolxer.service.dih.AnalysisDemoResponseService.ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT;
import static com.coolxer.service.dih.ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;

class DihChatApplicationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchIncludesDataVisualizationChartLibrary() {
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                (AnalysisAgent) null,
                (DisposeAgent) null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                null,
                null,
                null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .content("登录趋势图")
                .metadata(Map.of(
                        "id", "login-trend",
                        "name", "登录趋势图",
                        "chartType", "line",
                        "entity", "user_event",
                        "api", "/api/v1/entity/user_event/list"
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service,
                "buildStructuredExtraDataPatch",
                List.of(part)
        );

        assertThat(patch).isNotNull();
        Map<String, Object> dataVisualization = (Map<String, Object>) patch.get("dataVisualization");
        assertThat(dataVisualization).isNotNull();
        List<Map<String, Object>> chartLibrary = (List<Map<String, Object>>) dataVisualization.get("chartLibrary");
        assertThat(chartLibrary).hasSize(1);
        assertThat(chartLibrary.get(0))
                .containsEntry("id", "login-trend")
                .containsEntry("name", "登录趋势图")
                .containsEntry("chartType", "line")
                .containsEntry("entity", "user_event");
    }

    @Test
    void reportDemoChatUsesTemplateWithoutCallingModelAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        CountingReportAgent reportAgent = new CountingReportAgent();
        ThrowingChatModel titleModel = new ThrowingChatModel();

        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                null,
                new ReportDemoResponseService(),
                (AnalysisAgent) null,
                (DisposeAgent) null,
                reportAgent,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatDto chatDto = new ChatDto();
        chatDto.setType(ReportAgent.AGENT_TYPE);
        chatDto.setChatId("report-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setMessage(REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT);
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response).contains("zenvis:report-document-config");
        assertThat(reportAgent.calls.get()).isZero();
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle()).isEqualTo(ReportDemoResponseService.REPORT_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"report\"")
                .contains("\"currentDocument\"")
                .contains("用户事件数据分析报告");
    }

    @Test
    void analysisDemoChatUsesTemplateWithoutCallingModelAgent() {
        FakeChatSessionService sessionService = new FakeChatSessionService();
        ThrowingAIBaseService baseService = new ThrowingAIBaseService();
        CountingAnalysisAgent analysisAgent = new CountingAnalysisAgent();
        ThrowingChatModel titleModel = new ThrowingChatModel();

        DihChatApplicationService service = new DihChatApplicationService(
                null,
                baseService,
                sessionService,
                null,
                null,
                new AnalysisDemoResponseService(),
                null,
                analysisAgent,
                (DisposeAgent) null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                new ChatMessagePartParser(),
                null,
                new ChatTitleService(titleModel),
                null,
                new EnabledSkillService(),
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatDto chatDto = new ChatDto();
        chatDto.setType(AnalysisAgent.AGENT_TYPE);
        chatDto.setChatId("analysis-demo-chat");
        chatDto.setModel("unsupported-model-should-not-be-checked");
        chatDto.setMessage(ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT);
        chatDto.setResponseFormat(DihChatApplicationService.RESPONSE_FORMAT_EVENTS);

        String response = String.join("", service.chat(chatDto, null).collectList().block());

        assertThat(response)
                .contains("zenvis:analysis-record")
                .contains("analysis_demo.confirm_log_aggregation")
                .contains("log_aggregation")
                .doesNotContain("sandbox_analysis")
                .doesNotContain("zenvis:report-document-config");
        assertThat(analysisAgent.calls.get()).isZero();
        assertThat(baseService.isModelSupportedCalls.get()).isZero();
        assertThat(baseService.resolveChatModelCalls.get()).isZero();
        assertThat(titleModel.calls.get()).isZero();
        assertThat(sessionService.session.getTitle()).isEqualTo(ANALYSIS_DEMO_TITLE);
        assertThat(sessionService.session.getExtraData())
                .contains("\"analysis\"")
                .contains("\"aggregatedLogs\"")
                .doesNotContain("\"sandboxResults\"")
                .doesNotContain("\"conclusionTimeline\"")
                .doesNotContain("\"report\"");
    }

    private static class ThrowingAIBaseService extends AIBaseService {
        private final AtomicInteger isModelSupportedCalls = new AtomicInteger();
        private final AtomicInteger resolveChatModelCalls = new AtomicInteger();

        private ThrowingAIBaseService() {
            super("", "", "");
        }

        @Override
        public boolean isModelSupported(String model) {
            isModelSupportedCalls.incrementAndGet();
            throw new AssertionError("报表示例不应校验后台模型");
        }

        @Override
        public String resolveChatModel(String requestedModel, boolean deepThinking, boolean hasImageAttachment) {
            resolveChatModelCalls.incrementAndGet();
            throw new AssertionError("报表示例不应选择后台模型");
        }
    }

    private static class CountingReportAgent extends ReportAgent {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingReportAgent() {
            super(null, null);
        }

        @Override
        public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user,
                                 McpToolContext mcpToolContext) {
            calls.incrementAndGet();
            throw new AssertionError("报表示例不应调用 ReportAgent");
        }
    }

    private static class CountingAnalysisAgent extends AnalysisAgent {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingAnalysisAgent() {
            super(null, null);
        }

        @Override
        public Flux<String> chat(String chatId, String model, String prompt, List<ChatAttachment> attachments, User user,
                                 McpToolContext mcpToolContext) {
            calls.incrementAndGet();
            throw new AssertionError("研判示例不应调用 AnalysisAgent");
        }
    }

    private static class EnabledSkillService extends SkillService {
        private EnabledSkillService() {
            super(null, new ObjectMapper());
        }

        @Override
        public boolean isBuiltinAgentType(String agentType) {
            return true;
        }

        @Override
        public boolean isBuiltinAgentEnabled(String agentType) {
            return true;
        }
    }

    private static class ThrowingChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            throw new AssertionError("报表示例标题不应调用模型");
        }
    }

    private static class FakeChatSessionService implements ChatSessionService {
        private final List<Message> messages = new ArrayList<>();
        private ChatSession session;

        @Override
        public List<ChatSessionVo> findAll() {
            return List.of();
        }

        @Override
        public ChatSession create(ChatSessionDto chatSessionDto, User currentUser) {
            return null;
        }

        @Override
        public Boolean update(Long id, ChatSessionDto chatSessionDto, User currentUser) {
            if (session != null && chatSessionDto.getExtraData() != null) {
                session.setExtraData(chatSessionDto.getExtraData());
            }
            return true;
        }

        @Override
        public void delete(Long id, User currentUser) {
        }

        @Override
        public void deleteByIds(List<Long> ids, User currentUser) {
        }

        @Override
        public ChatSessionVo info(Long id, User currentUser) {
            return null;
        }

        @Override
        public List<ChatSessionVo> getPinList(User currentUser) {
            return List.of();
        }

        @Override
        public PageRowsVo<ChatSessionVo> getPageList(ChatSessionSearchDto chatSessionSearchDto, User currentUser) {
            return null;
        }

        @Override
        public ChatSession getChatSessionBySessionId(String chatId, User currentUser) {
            return session != null && chatId.equals(session.getSessionId()) ? session : null;
        }

        @Override
        public ChatSession appendMessage(String chatId, ChatSessionDto createDefaults, Message message, User currentUser) {
            if (session == null) {
                session = new ChatSession()
                        .setSessionId(chatId)
                        .setTitle(createDefaults.getTitle())
                        .setType(createDefaults.getType())
                        .setDeepThink(createDefaults.getDeepThink())
                        .setOnlineSearch(createDefaults.getOnlineSearch());
                session.setId(1);
            }
            messages.add(message);
            session.setMessages(JacksonUtil.toJson(messages));
            return session;
        }

        @Override
        public ChatSession appendMessage(ChatSession chatSession, Message message, User currentUser) {
            messages.add(message);
            chatSession.setMessages(JacksonUtil.toJson(messages));
            return chatSession;
        }
    }
}
