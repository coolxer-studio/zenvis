package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.ChatStreamEvent;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.service.dih.agent.AnalysisAgent;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.DisposeAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.dih.mcp.McpApprovalEvent;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.model.dih.vo.McpApprovalVo;
import com.coolxer.commons.enums.McpInvocationChannel;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.model.system.vo.DashboardVo;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * DIH 聊天应用编排服务。
 */
@Slf4j
@Service
public class DihChatApplicationService {

    public static final String RESPONSE_FORMAT_EVENTS = "events";

    private static final String LEGACY_MCP_AGENT_TYPE = "mcp_agent";
    private static final String LEGACY_MCP_AGENT_TYPE_ALIAS = "agent_mcp";
    private static final String CHAT_ERROR_MESSAGE = "抱歉，回复失败，请稍后重试~";
    private final AIChatService chatService;
    private final AIBaseService baseService;
    private final ChatSessionService chatSessionService;
    private final DataAccessDemoResponseService dataAccessDemoResponseService;
    private final DataVisualizationDemoResponseService dataVisualizationDemoResponseService;
    private final AnalysisDemoResponseService analysisDemoResponseService;
    private final DisposeDemoResponseService disposeDemoResponseService;
    private final ReportDemoResponseService reportDemoResponseService;
    private final AnalysisAgent analysisAgent;
    private final DisposeAgent disposeAgent;
    private final ReportAgent reportAgent;
    private final DataAccessAgent dataAccessAgent;
    private final DataVisualizationAgent dataVisualizationAgent;
    private final ChatMessagePartParser chatMessagePartParser;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatTitleService chatTitleService;
    private final AgentMcpToolService agentMcpToolService;
    private final SkillService skillService;
    private final ConfigService configService;
    private final PushTaskService pushTaskService;
    private final DashboardService dashboardService;
    private final MenuService menuService;

    @Autowired(required = false)
    private McpApprovalService mcpApprovalService;

    public DihChatApplicationService(AIChatService chatService,
                                     AIBaseService baseService,
                                     ChatSessionService chatSessionService,
                                     DataAccessDemoResponseService dataAccessDemoResponseService,
                                     DataVisualizationDemoResponseService dataVisualizationDemoResponseService,
                                     AnalysisDemoResponseService analysisDemoResponseService,
                                     DisposeDemoResponseService disposeDemoResponseService,
                                     ReportDemoResponseService reportDemoResponseService,
                                     AnalysisAgent analysisAgent,
                                     DisposeAgent disposeAgent,
                                     ReportAgent reportAgent,
                                     DataAccessAgent dataAccessAgent,
                                     DataVisualizationAgent dataVisualizationAgent,
                                     ChatMessagePartParser chatMessagePartParser,
                                     ChatAttachmentService chatAttachmentService,
                                     ChatTitleService chatTitleService,
                                     AgentMcpToolService agentMcpToolService,
                                     SkillService skillService,
                                     ConfigService configService,
                                     PushTaskService pushTaskService,
                                     DashboardService dashboardService,
                                     MenuService menuService) {
        this.chatService = chatService;
        this.baseService = baseService;
        this.chatSessionService = chatSessionService;
        this.dataAccessDemoResponseService = dataAccessDemoResponseService;
        this.dataVisualizationDemoResponseService = dataVisualizationDemoResponseService;
        this.analysisDemoResponseService = analysisDemoResponseService;
        this.disposeDemoResponseService = disposeDemoResponseService;
        this.reportDemoResponseService = reportDemoResponseService;
        this.analysisAgent = analysisAgent;
        this.disposeAgent = disposeAgent;
        this.reportAgent = reportAgent;
        this.dataAccessAgent = dataAccessAgent;
        this.dataVisualizationAgent = dataVisualizationAgent;
        this.chatMessagePartParser = chatMessagePartParser;
        this.chatAttachmentService = chatAttachmentService;
        this.chatTitleService = chatTitleService;
        this.agentMcpToolService = agentMcpToolService;
        this.skillService = skillService;
        this.configService = configService;
        this.pushTaskService = pushTaskService;
        this.dashboardService = dashboardService;
        this.menuService = menuService;
    }

    public Flux<String> chat(ChatDto chatDto, User currentUser) {
        boolean eventStream = isEventStream(chatDto);
        if (chatDto == null) {
            return errorResponse(eventStream, "消息内容或附件不能为空。");
        }
        String chatType = normalizeChatType(chatDto.getType());
        Optional<DihChatExecutionPolicy> policyOptional = DihChatExecutionPolicy.resolve(chatType);
        if (policyOptional.isEmpty()) {
            return errorResponse(eventStream, "会话类型不支持。");
        }
        DihChatExecutionPolicy executionPolicy = policyOptional.get();
        boolean effectiveDeepThink = executionPolicy.effectiveDeepThink(
                BooleanUtils.isTrue(chatDto.getDeepThink())
        );
        if (executionPolicy.isAgent() && BooleanUtils.isTrue(chatDto.getDeepThink())) {
            log.debug("忽略智能体深度思考参数: chatType={}, chatId={}", chatType, chatDto.getChatId());
        }

        if (executionPolicy.isAgent() && !skillService.isBuiltinAgentEnabled(chatType)) {
            return errorResponse(
                    eventStream,
                    "智能体能力不可用：以下 Skill 不存在或未启用: "
                            + String.join(", ", executionPolicy.skillIds())
            );
        }

        String model = chatDto.getModel();
        String userMessage = resolveUserMessage(chatDto);
        String chatId = chatDto.getChatId();
        if (!StringUtils.hasText(chatId)) {
            return errorResponse(eventStream, "会话ID不能为空。");
        }
        if (!StringUtils.hasText(userMessage)) {
            return errorResponse(eventStream, "消息内容或附件不能为空。");
        }
        Optional<Flux<String>> analysisDemoResponse = findAnalysisDemoResponse(
                chatType,
                chatId,
                userMessage,
                currentUser,
                null
        );
        if (analysisDemoResponse.isPresent()) {
            ChatSession chatSession = appendUserMessage(
                    chatDto, chatType, userMessage, currentUser, effectiveDeepThink);
            return emitAndSaveTextResponse(
                    analysisDemoResponse.get(),
                    chatSession,
                    currentUser,
                    eventStream,
                    new AtomicReference<>(MessageType.TEXT),
                    effectiveDeepThink
            );
        }
        Optional<Flux<String>> reportDemoResponse = findReportDemoResponse(
                chatType,
                chatId,
                userMessage,
                currentUser,
                null
        );
        if (reportDemoResponse.isPresent()) {
            ChatSession chatSession = appendUserMessage(
                    chatDto, chatType, userMessage, currentUser, effectiveDeepThink);
            return emitAndSaveTextResponse(
                    reportDemoResponse.get(),
                    chatSession,
                    currentUser,
                    eventStream,
                    new AtomicReference<>(MessageType.TEXT),
                    effectiveDeepThink
            );
        }
        Optional<Flux<String>> disposeDemoResponse = findDisposeDemoResponse(
                chatType,
                chatId,
                userMessage,
                currentUser,
                null
        );
        if (disposeDemoResponse.isPresent()) {
            ChatSession chatSession = appendUserMessage(
                    chatDto, chatType, userMessage, currentUser, effectiveDeepThink);
            return emitAndSaveTextResponse(
                    disposeDemoResponse.get(),
                    chatSession,
                    currentUser,
                    eventStream,
                    new AtomicReference<>(MessageType.TEXT),
                    effectiveDeepThink
            );
        }
        if (!baseService.isModelSupported(model)) {
            return errorResponse(eventStream, "Input model not support.");
        }

        boolean hasImageAttachment = chatAttachmentService.hasImageAttachment(chatDto.getAttachments());
        model = baseService.resolveChatModel(
                model,
                effectiveDeepThink,
                hasImageAttachment
        );
        McpToolContext mcpToolContext = executionPolicy.toolsAllowed() && !hasImageAttachment
                ? agentMcpToolService.resolve(chatType)
                : McpToolContext.empty();
        McpToolLogStream mcpToolLogStream = McpToolLogStream.disabled();
        String turnId = UUID.randomUUID().toString();
        if (mcpToolContext.hasTools()) {
            mcpToolLogStream = McpToolLogStream.create();
            mcpToolContext = mcpToolContext.withInvocationContext(new McpInvocationContext(
                    McpInvocationChannel.CHAT_AGENT,
                    currentUser == null ? null : currentUser.getId(),
                    chatId,
                    turnId,
                    chatType,
                    null,
                    null,
                    mcpToolLogStream::emitApproval
            ));
            mcpToolContext = mcpToolContext.withToolCallbackProvider(
                    new McpToolCallLoggingProvider(mcpToolContext.toolCallbackProvider(), mcpToolLogStream::emit)
            );
        }

        String prompt = chatAttachmentService.appendAttachmentContext(userMessage, chatDto.getAttachments(), currentUser);
        ChatSession chatSession = appendUserMessage(
                chatDto, chatType, userMessage, currentUser, effectiveDeepThink);

        AtomicReference<MessageType> messageType = new AtomicReference<>(MessageType.TEXT);

        String resolvedModel = model;
        McpToolContext resolvedMcpToolContext = mcpToolContext;
        Flux<String> fluxResponse = Flux.defer(() -> dispatchChat(
                chatType,
                chatId,
                resolvedModel,
                prompt,
                chatDto,
                currentUser,
                chatSession,
                resolvedMcpToolContext,
                executionPolicy,
                effectiveDeepThink,
                messageType
        ));
        if (mcpToolLogStream.enabled()) {
            McpToolLogStream finalMcpToolLogStream = mcpToolLogStream;
            fluxResponse = Flux.merge(
                    finalMcpToolLogStream.flux(),
                    fluxResponse.doFinally(signalType -> {
                        if (signalType == reactor.core.publisher.SignalType.CANCEL && mcpApprovalService != null) {
                            mcpApprovalService.cancelTurn(turnId, currentUser == null ? null : currentUser.getId());
                        }
                        finalMcpToolLogStream.complete();
                    })
            );
        }

        return emitAndSaveTextResponse(
                fluxResponse,
                chatSession,
                currentUser,
                eventStream,
                messageType,
                effectiveDeepThink,
                mcpToolLogStream
        );
    }

    public boolean isEventStream(ChatDto chatDto) {
        return chatDto != null && RESPONSE_FORMAT_EVENTS.equals(chatDto.getResponseFormat());
    }

    private Flux<String> dispatchChat(String chatType,
                                      String chatId,
                                      String model,
                                      String prompt,
                                      ChatDto chatDto,
                                      User currentUser,
                                      ChatSession chatSession,
                                      McpToolContext mcpToolContext,
                                      DihChatExecutionPolicy executionPolicy,
                                      boolean effectiveDeepThink,
                                      AtomicReference<MessageType> messageType) {
        if (DataAccessAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            Optional<Flux<String>> demoResponse = dataAccessDemoResponseService.findResponse(
                    chatSession,
                    chatId,
                    prompt,
                    currentUser
            );
            if (demoResponse.isPresent()) {
                return demoResponse.get();
            }
            return dataAccessAgent.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    executionPolicy.skillIds(),
                    mcpToolContext
            );
        }
        if (AnalysisAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            Optional<Flux<String>> demoResponse = findAnalysisDemoResponse(
                    chatType,
                    chatId,
                    prompt,
                    currentUser,
                    chatSession
            );
            if (demoResponse.isPresent()) {
                return demoResponse.get();
            }
            return analysisAgent.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    executionPolicy.skillIds(),
                    mcpToolContext
            );
        }
        if (DisposeAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            Optional<Flux<String>> demoResponse = findDisposeDemoResponse(
                    chatType,
                    chatId,
                    prompt,
                    currentUser,
                    chatSession
            );
            if (demoResponse.isPresent()) {
                return demoResponse.get();
            }
            return disposeAgent.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    executionPolicy.skillIds(),
                    mcpToolContext
            );
        }
        if (ReportAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            Optional<Flux<String>> demoResponse = findReportDemoResponse(
                    chatType,
                    chatId,
                    prompt,
                    currentUser,
                    chatSession
            );
            if (demoResponse.isPresent()) {
                return demoResponse.get();
            }
            return reportAgent.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    executionPolicy.skillIds(),
                    mcpToolContext
            );
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            Optional<Flux<String>> demoResponse = dataVisualizationDemoResponseService.findResponse(
                    chatSession,
                    chatId,
                    prompt,
                    currentUser
            );
            if (demoResponse.isPresent()) {
                return demoResponse.get();
            }
            return dataVisualizationAgent.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    executionPolicy.skillIds(),
                    mcpToolContext
            );
        }
        if (isPlaceholderBuiltinAgent(chatType)) {
            messageType.set(MessageType.TEXT);
            return Flux.just(skillService.getBuiltinAgentPlaceholder(chatType));
        }
        messageType.set(MessageType.TEXT);
        if (!executionPolicy.ragAllowed()) {
            return Flux.error(new IllegalStateException("智能体类型没有对应的执行器。"));
        }
        return chatService.qaChat(
                chatId,
                model,
                prompt,
                chatDto.getAttachments(),
                currentUser,
                effectiveDeepThink
        );
    }

    private Optional<Flux<String>> findReportDemoResponse(String chatType,
                                                          String chatId,
                                                          String prompt,
                                                          User currentUser,
                                                          ChatSession chatSession) {
        if (!ReportAgent.AGENT_TYPE.equals(chatType) || reportDemoResponseService == null) {
            return Optional.empty();
        }
        return reportDemoResponseService.findResponse(chatSession, chatId, prompt, currentUser);
    }

    private Optional<Flux<String>> findAnalysisDemoResponse(String chatType,
                                                            String chatId,
                                                            String prompt,
                                                            User currentUser,
                                                            ChatSession chatSession) {
        if (!AnalysisAgent.AGENT_TYPE.equals(chatType) || analysisDemoResponseService == null) {
            return Optional.empty();
        }
        return analysisDemoResponseService.findResponse(chatSession, chatId, prompt, currentUser);
    }

    private Optional<Flux<String>> findDisposeDemoResponse(String chatType,
                                                           String chatId,
                                                           String prompt,
                                                           User currentUser,
                                                           ChatSession chatSession) {
        if (!DisposeAgent.AGENT_TYPE.equals(chatType) || disposeDemoResponseService == null) {
            return Optional.empty();
        }
        return disposeDemoResponseService.findResponse(chatSession, chatId, prompt, currentUser);
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested) {
        return emitAndSaveTextResponse(fluxResponse, chatSession, currentUser, eventStream,
                messageType, deepThinkRequested, McpToolLogStream.disabled());
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested,
                                                 McpToolLogStream toolActivityStream) {
        StringBuilder modelResponse = new StringBuilder();
        if (eventStream) {
            return fluxResponse
                    .handle((value, sink) -> {
                        McpApprovalEvent approvalEvent = McpToolLogStream.parseApprovalEvent(value);
                        if (approvalEvent != null) {
                            toolActivityStream.recordApprovalPosition(
                                    approvalEvent.data() == null ? null : approvalEvent.data().getRequestId(),
                                    modelResponse.length());
                            sink.next(toNdjson(ChatStreamEvent.approval(approvalEvent.event(), approvalEvent.data())));
                            return;
                        }
                        modelResponse.append(value);
                        sink.next(toNdjson(ChatStreamEvent.delta(value)));
                    })
                    .cast(String.class)
                    .concatWith(Flux.defer(() -> {
                        Message aiMessage = saveAiResponse(
                                chatSession,
                                currentUser,
                                modelResponse.toString(),
                                messageType.get(),
                                true,
                                deepThinkRequested,
                                toolActivityStream.approvalParts()
                        );
                        return Flux.just(toNdjson(ChatStreamEvent.done(aiMessage)));
                    }))
                    .onErrorResume(e -> {
                        log.error("聊天事件流返回失败: {}", e.getMessage(), e);
                        String errorMessage = resolveChatErrorMessage(e);
                        persistErrorResponse(chatSession, currentUser, errorMessage);
                        return Flux.just(toNdjson(ChatStreamEvent.error(errorMessage)));
                    });
        }

        return fluxResponse.filter(value -> McpToolLogStream.parseApprovalEvent(value) == null)
                .doOnNext(modelResponse::append)
                .doOnComplete(() -> saveAiResponse(chatSession, currentUser, modelResponse.toString(),
                        messageType.get(), false, false, toolActivityStream.approvalParts()))
                .onErrorResume(e -> {
                    log.error("聊天返回失败: {}", e.getMessage(), e);
                    String errorMessage = resolveChatErrorMessage(e);
                    persistErrorResponse(chatSession, currentUser, errorMessage);
                    return Flux.just(errorMessage);
                });
    }

    private ChatSession appendUserMessage(ChatDto chatDto,
                                          String chatType,
                                          String userMessage,
                                          User currentUser,
                                          boolean effectiveDeepThink) {
        ChatSessionDto chatSessionDto = new ChatSessionDto();
        chatSessionDto.setSessionId(chatDto.getChatId());
        if (chatSessionService.getChatSessionBySessionId(chatDto.getChatId(), currentUser) == null) {
            chatSessionDto.setTitle(chatTitleService.generateTitle(userMessage));
        }
        chatSessionDto.setType(chatType);
        chatSessionDto.setDeepThink(effectiveDeepThink);
        chatSessionDto.setOnlineSearch(chatDto.getOnlineSearch());
        return chatSessionService.appendMessage(
                chatDto.getChatId(),
                chatSessionDto,
                createUserMessage(userMessage, chatDto.getAttachments()),
                currentUser
        );
    }

    private void persistErrorResponse(ChatSession chatSession, User currentUser, String errorMessage) {
        if (chatSession == null) {
            return;
        }
        try {
            Message message = new Message("ai", errorMessage, MessageType.TEXT);
            message.setIsError(true);
            chatSessionService.appendMessage(chatSession, message, currentUser);
        } catch (Exception e) {
            log.error("保存错误响应到会话失败: {}", e.getMessage(), e);
        }
    }

    private String resolveChatErrorMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AgentCapabilityUnavailableException
                    && StringUtils.hasText(current.getMessage())) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return CHAT_ERROR_MESSAGE;
    }

    private Flux<String> errorResponse(boolean eventStream, String message) {
        if (eventStream) {
            return Flux.just(toNdjson(ChatStreamEvent.error(message)));
        }
        return Flux.just(message);
    }

    private boolean isPlaceholderBuiltinAgent(String chatType) {
        return false;
    }

    private String resolveUserMessage(ChatDto chatDto) {
        if (chatDto == null) {
            return "";
        }
        if (StringUtils.hasText(chatDto.getMessage())) {
            return chatDto.getMessage().trim();
        }
        if (chatDto.getAttachments() != null && !chatDto.getAttachments().isEmpty()) {
            return "请分析上传的附件内容。";
        }
        return "";
    }

    private String normalizeChatType(String type) {
        if (!StringUtils.hasText(type)
                || LEGACY_MCP_AGENT_TYPE.equals(type)
                || LEGACY_MCP_AGENT_TYPE_ALIAS.equals(type)) {
            return DihChatExecutionPolicy.TYPE_ASK;
        }
        return type;
    }

    private Message createUserMessage(String content, List<ChatAttachment> attachments) {
        Message message = new Message("user", content);
        if (attachments != null && !attachments.isEmpty()) {
            message.setAttachments(attachments);
        }
        return message;
    }

    private Message saveAiResponse(ChatSession chatSession, User currentUser, String content, MessageType type, boolean withParts, boolean deepThinkRequested) {
        return saveAiResponse(chatSession, currentUser, content, type, withParts, deepThinkRequested, List.of());
    }

    private Message saveAiResponse(ChatSession chatSession,
                                   User currentUser,
                                   String content,
                                   MessageType type,
                                   boolean withParts,
                                   boolean deepThinkRequested,
                                   List<ChatMessagePart> supplementalParts) {
        Message aiMessage = new Message("ai", content, type);
        List<ChatMessagePart> parts = List.of();
        if (withParts) {
            String parsableContent = insertSupplementalMarkers(content, supplementalParts);
            parts = new ArrayList<>(chatMessagePartParser.parse(parsableContent, type));
            parts = mergeSupplementalParts(content, parts, supplementalParts);
            if (deepThinkRequested && parts.stream().noneMatch(part -> "thinking".equals(part.getType()))) {
                parts.add(0, ChatMessagePart.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .type("thinking")
                        .title("思考过程")
                        .content("已完成深度思考，当前模型未返回可展示的思考过程。")
                        .status("completed")
                        .build());
            }
            aiMessage.setParts(parts);
        }
        if (chatSession == null) {
            return aiMessage;
        }
        try {
            ChatSession savedSession = chatSessionService.appendMessage(chatSession, aiMessage, currentUser);
            mergeStructuredExtraData(savedSession, parts, currentUser);
            log.info("保存AI响应到会话，消息类型: {}, 富消息片段: {}", aiMessage.getType(), withParts);
        } catch (Exception e) {
            log.error("保存模型响应到会话失败: {}", e.getMessage(), e);
        }
        return aiMessage;
    }

    private String insertSupplementalMarkers(String content, List<ChatMessagePart> supplementalParts) {
        if (supplementalParts == null || supplementalParts.isEmpty()
                || supplementalParts.stream().anyMatch(part -> part.getMetadata() == null
                || !(part.getMetadata().get("contentOffset") instanceof Number))) {
            return content;
        }
        StringBuilder marked = new StringBuilder(content);
        List<ChatMessagePart> ordered = supplementalParts.stream()
                .sorted(java.util.Comparator.comparingInt(part ->
                        ((Number) part.getMetadata().get("contentOffset")).intValue()))
                .toList();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ChatMessagePart part = ordered.get(i);
            int offset = Math.max(0, Math.min(marked.length(),
                    ((Number) part.getMetadata().get("contentOffset")).intValue()));
            Map<String, Object> marker = new LinkedHashMap<>(part.getMetadata());
            marker.put("id", part.getId());
            marker.put("title", part.getTitle());
            marker.put("content", part.getContent());
            marker.put("status", part.getStatus());
            marked.insert(offset, "\n```zenvis:mcp-approval\n" + JacksonUtil.toJson(marker) + "\n```\n");
        }
        return marked.toString();
    }

    private List<ChatMessagePart> mergeSupplementalParts(String content,
                                                         List<ChatMessagePart> parsedParts,
                                                         List<ChatMessagePart> supplementalParts) {
        if (supplementalParts == null || supplementalParts.isEmpty()) {
            return parsedParts;
        }
        java.util.Set<String> parsedApprovalIds = parsedParts.stream()
                .filter(part -> "mcp-approval".equals(part.getType()))
                .map(ChatMessagePart::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (supplementalParts.stream().allMatch(part -> parsedApprovalIds.contains(part.getId()))) {
            return parsedParts;
        }
        boolean plainMarkdown = parsedParts.stream().allMatch(part -> "markdown".equals(part.getType()));
        boolean hasOffsets = supplementalParts.stream().allMatch(part -> part.getMetadata() != null
                && part.getMetadata().get("contentOffset") instanceof Number);
        if (!plainMarkdown || !hasOffsets) {
            List<ChatMessagePart> merged = new ArrayList<>(supplementalParts);
            merged.addAll(parsedParts);
            return merged;
        }

        List<ChatMessagePart> merged = new ArrayList<>();
        int cursor = 0;
        for (ChatMessagePart approval : supplementalParts.stream()
                .sorted(java.util.Comparator.comparingInt(part ->
                        ((Number) part.getMetadata().get("contentOffset")).intValue()))
                .toList()) {
            int offset = Math.max(cursor, Math.min(content.length(),
                    ((Number) approval.getMetadata().get("contentOffset")).intValue()));
            if (offset > cursor) {
                merged.add(ChatMessagePart.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .type("markdown")
                        .content(content.substring(cursor, offset))
                        .build());
            }
            merged.add(approval);
            cursor = offset;
        }
        if (cursor < content.length()) {
            merged.add(ChatMessagePart.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type("markdown")
                    .content(content.substring(cursor))
                    .build());
        }
        return merged;
    }

    private void mergeStructuredExtraData(ChatSession chatSession, List<ChatMessagePart> parts, User currentUser) {
        Map<String, Object> patch = buildStructuredExtraDataPatch(parts);
        if (chatSession == null || patch == null || patch.isEmpty()) {
            return;
        }
        Map<String, Object> extraData = new LinkedHashMap<>(parseJsonObject(chatSession.getExtraData()));
        mergeSectionRecords(extraData, patch, "dataAccess", List.of("metadataConfigs", "dataPushServices"));
        mergeSectionRecords(extraData, patch, "dataVisualization", List.of(
                "chartLibrary",
                "visualizationConfigs",
                "dashboardConfigs",
                "menuConfigs"
        ));
        mergeSectionRecords(extraData, patch, "analysis", List.of(
                "records",
                "aggregatedLogs",
                "sandboxResults",
                "conclusionTimeline"
        ));
        mergeSectionRecords(extraData, patch, "policy", List.of("records"));
        mergeReportRecords(extraData, patch);

        String extraDataJson = JacksonUtil.toJson(extraData);
        ChatSessionDto chatSessionDto = new ChatSessionDto();
        chatSessionDto.setExtraData(extraDataJson);
        chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
        chatSession.setExtraData(extraDataJson);
    }

    private Map<String, Object> buildStructuredExtraDataPatch(List<ChatMessagePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        Map<String, Object> dataAccessPatch = buildDataAccessExtraDataPatch(parts);
        if (dataAccessPatch != null && !dataAccessPatch.isEmpty()) {
            patch.putAll(dataAccessPatch);
        }
        Map<String, Object> dataVisualizationPatch = buildDataVisualizationExtraDataPatch(parts);
        if (dataVisualizationPatch != null && !dataVisualizationPatch.isEmpty()) {
            patch.putAll(dataVisualizationPatch);
        }
        Map<String, Object> reportPatch = buildReportExtraDataPatch(parts);
        if (reportPatch != null && !reportPatch.isEmpty()) {
            patch.putAll(reportPatch);
        }
        Map<String, Object> analysisPatch = buildAnalysisExtraDataPatch(parts);
        if (analysisPatch != null && !analysisPatch.isEmpty()) {
            patch.putAll(analysisPatch);
        }
        Map<String, Object> policyPatch = buildPolicyExtraDataPatch(parts);
        if (policyPatch != null && !policyPatch.isEmpty()) {
            patch.putAll(policyPatch);
        }
        return patch.isEmpty() ? null : patch;
    }

    private Map<String, Object> buildPolicyExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (ChatMessagePart part : parts) {
            if (!"policy-record".equals(part.getType())) {
                continue;
            }
            Map<String, Object> record = buildPolicyRecord(part);
            if (!record.isEmpty()) {
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            return null;
        }

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("records", records);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policy", policy);
        return metadata;
    }

    private Map<String, Object> buildPolicyRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        String policyType = firstNonBlank(
                stringValue(raw, "policyType", null),
                stringValue(raw, "policy_type", null),
                stringValue(raw, "type", null)
        );
        String configType = firstNonBlank(
                stringValue(raw, "configType", null),
                stringValue(raw, "config_type", null),
                policyConfigType(policyType)
        );
        String fileName = firstNonBlank(
                stringValue(raw, "fileName", null),
                stringValue(raw, "file_name", null),
                stringValue(raw, "targetFile", null)
        );
        String id = firstNonBlank(
                stringValue(raw, "recordId", null),
                stringValue(raw, "record_id", null),
                stringValue(raw, "id", null),
                configType != null && fileName != null ? configType + ":" + fileName : null,
                java.util.UUID.randomUUID().toString()
        );

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("recordId", id);
        record.put("policyType", firstNonBlank(policyType, "disposal"));
        record.put("changeDescription", firstNonBlank(
                stringValue(raw, "changeDescription", null),
                stringValue(raw, "change_description", null),
                stringValue(raw, "description", null),
                part.getContent()
        ));
        record.put("changeMode", firstNonBlank(
                stringValue(raw, "changeMode", null),
                stringValue(raw, "change_mode", null),
                stringValue(raw, "operation", null),
                "add"
        ));
        record.put("configType", configType);
        record.put("fileName", fileName);
        record.put("oldConfig", raw.getOrDefault("oldConfig", raw.getOrDefault("old_config", "")));
        record.put("newConfig", raw.getOrDefault("newConfig", raw.getOrDefault("new_config", Map.of())));
        record.put("validationStatus", firstNonBlank(
                stringValue(raw, "validationStatus", null),
                stringValue(raw, "validation_status", null),
                "unverified"
        ));
        record.put("effectiveStatus", firstNonBlank(
                stringValue(raw, "effectiveStatus", null),
                stringValue(raw, "effective_status", null),
                "no"
        ));
        record.put("trialResult", raw.getOrDefault("trialResult", raw.getOrDefault("trial_result", Map.of())));
        record.put("applyResult", raw.getOrDefault("applyResult", raw.getOrDefault("apply_result", Map.of())));
        record.put("updatedAt", firstNonBlank(
                stringValue(raw, "updatedAt", null),
                stringValue(raw, "updated_at", null),
                java.time.OffsetDateTime.now().toString()
        ));
        record.put("source", "agent_dispose");
        record.put("raw", raw);
        return record;
    }

    private String policyConfigType(String policyType) {
        return switch (StringUtils.hasText(policyType) ? policyType : "") {
            case "collection" -> "checker";
            case "tagging" -> "rating";
            case "disposal" -> "punish";
            default -> null;
        };
    }

    private Map<String, Object> buildAnalysisExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<Map<String, Object>> aggregatedLogs = new ArrayList<>();
        List<Map<String, Object>> sandboxResults = new ArrayList<>();
        List<Map<String, Object>> conclusionTimeline = new ArrayList<>();
        for (ChatMessagePart part : parts) {
            if (!"analysis-record".equals(part.getType())) {
                continue;
            }
            Map<String, Object> record = buildAnalysisRecord(part);
            records.add(record);
            String stage = stringValue(record, "stage", "");
            Map<String, Object> raw = mapValue(record.get("raw"));
            if ("log_aggregation".equals(stage)) {
                aggregatedLogs.addAll(extractAnalysisLogRecords(raw));
            } else if ("sandbox_analysis".equals(stage)) {
                sandboxResults.add(buildSandboxAnalysisResult(record, raw));
            } else if ("report_output".equals(stage)) {
                conclusionTimeline.addAll(extractAnalysisConclusionTimeline(record, raw));
            }
        }
        if (records.isEmpty()) {
            return null;
        }

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("records", records);
        if (!aggregatedLogs.isEmpty()) {
            analysis.put("aggregatedLogs", aggregatedLogs);
        }
        if (!sandboxResults.isEmpty()) {
            analysis.put("sandboxResults", sandboxResults);
        }
        if (!conclusionTimeline.isEmpty()) {
            analysis.put("conclusionTimeline", conclusionTimeline);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("analysis", analysis);
        return metadata;
    }

    private Map<String, Object> buildDataAccessExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> metadataConfigs = new ArrayList<>();
        List<Map<String, Object>> dataPushServices = new ArrayList<>();
        for (ChatMessagePart part : parts) {
            if ("metadata-config-record".equals(part.getType())) {
                Map<String, Object> record = buildMetaConfigRecord(part, stringValue(part.getMetadata(), "status", "applied"));
                if (isMetaConfigRecordPresent(record)) {
                    metadataConfigs.add(record);
                }
            } else if ("data-push-service-record".equals(part.getType())) {
                Map<String, Object> record = buildDataPushServiceRecord(part);
                if (isDataPushServiceRecordPresent(record)) {
                    dataPushServices.add(record);
                }
            }
        }
        if (metadataConfigs.isEmpty() && dataPushServices.isEmpty()) {
            return null;
        }

        Map<String, Object> dataAccess = new LinkedHashMap<>();
        if (!metadataConfigs.isEmpty()) {
            dataAccess.put("metadataConfigs", metadataConfigs);
        }
        if (!dataPushServices.isEmpty()) {
            dataAccess.put("dataPushServices", dataPushServices);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("dataAccess", dataAccess);
        return metadata;
    }

    private Map<String, Object> buildDataVisualizationExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> chartLibrary = new ArrayList<>();
        List<Map<String, Object>> visualizationConfigs = new ArrayList<>();
        List<Map<String, Object>> dashboardConfigs = new ArrayList<>();
        List<Map<String, Object>> menuConfigs = new ArrayList<>();

        for (ChatMessagePart part : parts) {
            if ("visualization-chart-record".equals(part.getType())) {
                chartLibrary.add(buildVisualizationChartRecord(part));
            } else if ("visualization-config-record".equals(part.getType())) {
                Map<String, Object> record = buildVisualizationConfigRecord(part);
                if (isVisualizationConfigRecordPresent(record)) {
                    visualizationConfigs.add(record);
                }
            } else if ("dashboard-config-record".equals(part.getType())) {
                Map<String, Object> record = buildDashboardConfigRecord(part);
                if (isDashboardConfigRecordPresent(record)) {
                    dashboardConfigs.add(record);
                }
            } else if ("menu-config-record".equals(part.getType())) {
                Map<String, Object> record = buildMenuConfigRecord(part);
                if (isMenuConfigRecordPresent(record)) {
                    menuConfigs.add(record);
                }
            }
        }
        if (chartLibrary.isEmpty() && visualizationConfigs.isEmpty()
                && dashboardConfigs.isEmpty() && menuConfigs.isEmpty()) {
            return null;
        }

        Map<String, Object> dataVisualization = new LinkedHashMap<>();
        if (!chartLibrary.isEmpty()) {
            dataVisualization.put("chartLibrary", chartLibrary);
        }
        if (!visualizationConfigs.isEmpty()) {
            dataVisualization.put("visualizationConfigs", visualizationConfigs);
        }
        if (!dashboardConfigs.isEmpty()) {
            dataVisualization.put("dashboardConfigs", dashboardConfigs);
        }
        if (!menuConfigs.isEmpty()) {
            dataVisualization.put("menuConfigs", menuConfigs);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("dataVisualization", dataVisualization);
        return metadata;
    }

    private Map<String, Object> buildReportExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> documents = new ArrayList<>();
        List<Map<String, Object>> artifacts = new ArrayList<>();
        Map<String, Object> currentDocument = null;

        for (ChatMessagePart part : parts) {
            if (!isReportDocumentPart(part)) {
                continue;
            }
            Map<String, Object> document = buildReportDocumentRecord(part);
            documents.add(document);
            artifacts.add(buildReportArtifactRecord(document));
            currentDocument = document;
        }

        if (documents.isEmpty() && artifacts.isEmpty() && currentDocument == null) {
            return null;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        if (currentDocument != null) {
            report.put("currentDocument", currentDocument);
        }
        if (!documents.isEmpty()) {
            report.put("documents", documents);
        }
        if (!artifacts.isEmpty()) {
            report.put("artifacts", artifacts);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("report", report);
        return metadata;
    }

    private Map<String, Object> buildAnalysisRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        String stage = firstNonBlank(stringValue(raw, "stage", null), "report_output");
        String title = firstNonBlank(
                stringValue(raw, "title", null),
                part.getTitle(),
                defaultAnalysisStageTitle(stage)
        );
        String id = firstNonBlank(
                stringValue(raw, "recordId", null),
                stringValue(raw, "record_id", null),
                stringValue(raw, "id", null),
                stage
        );

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("recordId", id);
        record.put("stage", stage);
        record.put("status", stringValue(raw, "status", "completed"));
        record.put("title", title);
        record.put("content", firstNonBlank(
                stringValue(raw, "content", null),
                stringValue(raw, "message", null),
                stringValue(raw, "description", null),
                part.getContent()
        ));
        record.put("startedAt", firstNonBlank(stringValue(raw, "startedAt", null), stringValue(raw, "started_at", null)));
        record.put("completedAt", firstNonBlank(stringValue(raw, "completedAt", null), stringValue(raw, "completed_at", null)));
        record.put("alarm", raw.getOrDefault("alarm", Map.of()));
        record.put("evidenceCount", raw.getOrDefault("evidenceCount", raw.getOrDefault("evidence_count", 0)));
        record.put("riskLevel", firstNonBlank(stringValue(raw, "riskLevel", null), stringValue(raw, "risk_level", null)));
        record.put("confidence", raw.get("confidence"));
        record.put("keyFindings", raw.getOrDefault("keyFindings", raw.getOrDefault("key_findings", List.of())));
        record.put("recommendations", raw.getOrDefault("recommendations", List.of()));
        record.put("sandboxTaskId", firstNonBlank(stringValue(raw, "sandboxTaskId", null), stringValue(raw, "sandbox_task_id", null)));
        record.put("toolNames", raw.getOrDefault("toolNames", raw.getOrDefault("tool_names", List.of())));
        record.put("source", "agent_analysis");
        record.put("raw", raw);
        return record;
    }

    private String defaultAnalysisStageTitle(String stage) {
        return switch (StringUtils.hasText(stage) ? stage : "") {
            case "log_aggregation" -> "日志聚合";
            case "sandbox_analysis" -> "研判分析";
            case "report_output" -> "输出分析结论";
            default -> "研判记录";
        };
    }

    private List<Map<String, Object>> extractAnalysisLogRecords(Map<String, Object> raw) {
        List<Map<String, Object>> logs = firstNonEmptyListOfMaps(
                raw.get("logs"),
                raw.get("aggregatedLogs"),
                raw.get("aggregated_logs"),
                raw.get("relatedLogs"),
                raw.get("related_logs")
        );
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < logs.size(); i++) {
            Map<String, Object> logRecord = new LinkedHashMap<>(logs.get(i));
            logRecord.putIfAbsent("id", firstNonBlank(
                    stringValue(logRecord, "id", null),
                    stringValue(logRecord, "logId", null),
                    stringValue(logRecord, "log_id", null),
                    stringValue(logRecord, "eventId", null),
                    stringValue(logRecord, "event_id", null),
                    "analysis-log-" + (i + 1)
            ));
            normalized.add(logRecord);
        }
        return normalized;
    }

    private Map<String, Object> buildSandboxAnalysisResult(Map<String, Object> record, Map<String, Object> raw) {
        Object result = firstNonNull(
                raw.get("sandboxResult"),
                raw.get("sandbox_result"),
                raw.get("result"),
                raw.get("jsonResult"),
                raw.get("json_result")
        );
        Map<String, Object> sandboxRecord = new LinkedHashMap<>();
        sandboxRecord.put("id", firstNonBlank(
                stringValue(raw, "sandboxTaskId", null),
                stringValue(raw, "sandbox_task_id", null),
                stringValue(record, "recordId", null),
                java.util.UUID.randomUUID().toString()
        ));
        sandboxRecord.put("taskId", firstNonBlank(stringValue(raw, "sandboxTaskId", null), stringValue(raw, "sandbox_task_id", null)));
        sandboxRecord.put("status", stringValue(record, "status", "completed"));
        sandboxRecord.put("title", firstNonBlank(stringValue(record, "title", null), "沙箱研判结果"));
        sandboxRecord.put("completedAt", stringValue(record, "completedAt", ""));
        sandboxRecord.put("result", result == null ? raw : result);
        sandboxRecord.put("raw", raw);
        return sandboxRecord;
    }

    private List<Map<String, Object>> extractAnalysisConclusionTimeline(Map<String, Object> record, Map<String, Object> raw) {
        List<Map<String, Object>> timeline = firstNonEmptyListOfMaps(
                raw.get("timeline"),
                raw.get("conclusionTimeline"),
                raw.get("conclusion_timeline")
        );
        if (!timeline.isEmpty()) {
            return normalizeAnalysisTimeline(timeline, record);
        }

        List<Map<String, Object>> generated = new ArrayList<>();
        addAnalysisTimelineItem(generated, "analysis_target", "分析目标", firstNonBlank(
                stringValue(raw, "analysisTarget", null),
                stringValue(raw, "analysis_target", null)
        ), record);
        addAnalysisTimelineItem(generated, "analysis_process", "分析过程", firstNonBlank(
                stringValue(raw, "analysisProcess", null),
                stringValue(raw, "analysis_process", null),
                stringValue(record, "content", null)
        ), record);
        addAnalysisTimelineItem(generated, "analysis_conclusion", "分析结论", firstNonBlank(
                stringValue(raw, "analysisConclusion", null),
                stringValue(raw, "analysis_conclusion", null),
                stringValue(raw, "conclusion", null)
        ), record);
        Object recommendationValue = firstNonNull(raw.get("disposalSuggestion"), raw.get("disposal_suggestion"), raw.get("recommendations"));
        addAnalysisTimelineItem(generated, "disposal_recommendation", "处置建议", timelineContent(recommendationValue), record);
        return generated;
    }

    private List<Map<String, Object>> normalizeAnalysisTimeline(List<Map<String, Object>> timeline, Map<String, Object> record) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < timeline.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>(timeline.get(i));
            item.putIfAbsent("id", firstNonBlank(stringValue(item, "id", null), "analysis-timeline-" + (i + 1)));
            item.putIfAbsent("time", firstNonBlank(
                    stringValue(item, "time", null),
                    stringValue(item, "completedAt", null),
                    stringValue(record, "completedAt", null),
                    stringValue(record, "startedAt", null)
            ));
            item.putIfAbsent("title", "分析结论");
            item.putIfAbsent("content", firstNonBlank(stringValue(item, "content", null), stringValue(item, "description", null)));
            normalized.add(item);
        }
        return normalized;
    }

    private void addAnalysisTimelineItem(List<Map<String, Object>> timeline,
                                         String id,
                                         String title,
                                         String content,
                                         Map<String, Object> record) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("content", content);
        item.put("time", firstNonBlank(stringValue(record, "completedAt", null), stringValue(record, "startedAt", null)));
        item.put("type", "success");
        timeline.add(item);
    }

    private String timelineContent(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Map<?, ?> map
                            ? firstNonBlank(
                                    map.get("content") == null ? "" : String.valueOf(map.get("content")),
                                    map.get("title") == null ? "" : String.valueOf(map.get("title")),
                                    map.get("name") == null ? "" : String.valueOf(map.get("name"))
                            )
                            : String.valueOf(item))
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }
        return String.valueOf(value);
    }

    private boolean isReportDocumentPart(ChatMessagePart part) {
        if (part == null) {
            return false;
        }
        if ("report-document".equals(part.getType())) {
            return true;
        }
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        return "config".equals(part.getType()) && "report-document".equals(stringValue(raw, "configKind", null));
    }

    private Map<String, Object> buildReportDocumentRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        String format = firstNonBlank(
                stringValue(raw, "format", null),
                stringValue(raw, "language", null),
                part.getLanguage(),
                "markdown"
        );
        String title = firstNonBlank(
                stringValue(raw, "title", null),
                part.getTitle(),
                extractMarkdownTitle(part.getContent()),
                "报表文档"
        );
        String version = firstNonBlank(stringValue(raw, "version", null), "v1.0.0");
        String updatedAt = firstNonBlank(stringValue(raw, "updatedAt", null), java.time.OffsetDateTime.now().toString());
        String id = firstNonBlank(
                stringValue(raw, "documentId", null),
                stringValue(raw, "document_id", null),
                stringValue(raw, "recordId", null),
                stringValue(raw, "id", null),
                java.util.UUID.randomUUID().toString()
        );

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("documentId", id);
        record.put("title", title);
        record.put("name", title);
        record.put("format", format);
        record.put("version", version);
        record.put("status", stringValue(raw, "status", "generated"));
        record.put("source", "agent_report");
        record.put("updatedAt", updatedAt);
        record.put("content", part.getContent());
        record.put("outline", raw.getOrDefault("outline", List.of()));
        record.put("sourceAttachments", raw.getOrDefault("sourceAttachments", List.of()));
        record.put("raw", raw);
        return record;
    }

    private Map<String, Object> buildReportArtifactRecord(Map<String, Object> document) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        String id = firstNonBlank(
                stringValue(document, "artifactId", null),
                stringValue(document, "id", null),
                java.util.UUID.randomUUID().toString()
        );
        artifact.put("id", id);
        artifact.put("artifactId", id);
        artifact.put("documentId", stringValue(document, "documentId", id));
        artifact.put("name", stringValue(document, "title", "报表文档"));
        artifact.put("title", stringValue(document, "title", "报表文档"));
        artifact.put("format", stringValue(document, "format", "markdown"));
        artifact.put("version", stringValue(document, "version", "v1.0.0"));
        artifact.put("status", "generated");
        artifact.put("createdAt", stringValue(document, "updatedAt", java.time.OffsetDateTime.now().toString()));
        artifact.put("content", stringValue(document, "content", ""));
        return artifact;
    }

    private Map<String, Object> buildVisualizationChartRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = baseVisualizationRecord(raw, part, "临时可视化图表");
        record.put("chartType", firstNonBlank(stringValue(raw, "chartType", null), stringValue(raw, "chart_type", null)));
        record.put("entity", firstNonBlank(stringValue(raw, "entity", null), stringValue(raw, "entityName", null)));
        record.put("api", firstNonBlank(stringValue(raw, "api", null), stringValue(raw, "apiUrl", null), stringValue(raw, "url", null)));
        record.put("status", stringValue(raw, "status", "temporary"));
        record.put("source", "session");
        Object config = raw.get("config");
        record.put("config", config == null ? parseJsonObject(part.getContent()) : config);
        record.put("raw", raw);
        return record;
    }

    private Map<String, Object> buildVisualizationConfigRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = baseVisualizationRecord(raw, part, "可视化配置");
        String configKind = firstNonBlank(
                stringValue(raw, "configKind", null),
                stringValue(raw, "kind", null),
                stringValue(raw, "type", null)
        );
        String configIndex = firstNonBlank(stringValue(raw, "configIndex", null), stringValue(raw, "config_index", null));
        String configType = firstNonBlank(
                stringValue(raw, "configType", null),
                stringValue(raw, "config_type", null),
                configIndex
        );
        String fileName = firstNonBlank(
                stringValue(raw, "fileName", null),
                stringValue(raw, "file_name", null),
                defaultVisualizationConfigFile(configKind)
        );
        record.put("configKind", configKind);
        record.put("configType", configType);
        record.put("configIndex", configIndex);
        record.put("fileName", fileName);
        record.put("routeName", visualizationRouteName(configKind));
        record.put("menuParams", firstNonBlank(configIndex, configType));
        record.put("status", stringValue(raw, "status", "applied"));
        record.put("source", "open_config");
        record.put("content", part.getContent());
        record.put("raw", raw);
        return record;
    }

    private Map<String, Object> buildDashboardConfigRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = baseVisualizationRecord(raw, part, "数据看板配置");
        String dashboardId = firstNonBlank(stringValue(raw, "dashboardId", null), stringValue(raw, "dashboard_id", null), stringValue(raw, "id", null));
        String code = firstNonBlank(stringValue(raw, "code", null), stringValue(raw, "dashboardCode", null), stringValue(raw, "dashboard_code", null));
        record.put("dashboardId", dashboardId);
        record.put("code", code);
        record.put("dashboardType", firstNonBlank(stringValue(raw, "dashboardType", null), stringValue(raw, "type", null)));
        record.put("url", stringValue(raw, "url", ""));
        record.put("configIndex", firstNonBlank(stringValue(raw, "configIndex", null), stringValue(raw, "config_index", null)));
        record.put("htmlPath", firstNonBlank(stringValue(raw, "htmlPath", null), stringValue(raw, "html_path", null)));
        record.put("status", stringValue(raw, "status", "created"));
        record.put("source", "dashboard");
        record.put("raw", raw);
        return record;
    }

    private Map<String, Object> buildMenuConfigRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = baseVisualizationRecord(raw, part, "菜单配置");
        String menuId = firstNonBlank(stringValue(raw, "menuId", null), stringValue(raw, "menu_id", null), stringValue(raw, "id", null));
        record.put("menuId", menuId);
        record.put("route", stringValue(raw, "route", ""));
        record.put("params", stringValue(raw, "params", ""));
        record.put("menuType", firstNonBlank(stringValue(raw, "menuType", null), stringValue(raw, "type", null)));
        record.put("source", firstNonBlank(stringValue(raw, "source", null), "menu"));
        record.put("sourceKey", stringValue(raw, "source", null));
        record.put("status", stringValue(raw, "status", "created"));
        record.put("raw", raw);
        return record;
    }

    private Map<String, Object> baseVisualizationRecord(Map<String, Object> raw, ChatMessagePart part, String defaultName) {
        Map<String, Object> record = new LinkedHashMap<>();
        String name = firstNonBlank(stringValue(raw, "name", null), stringValue(raw, "title", null), part.getContent(), defaultName);
        record.put("id", firstNonBlank(
                stringValue(raw, "recordId", null),
                stringValue(raw, "record_id", null),
                stringValue(raw, "id", null),
                stringValue(raw, "fileName", null),
                stringValue(raw, "configIndex", null),
                stringValue(raw, "code", null),
                stringValue(raw, "dashboardId", null),
                stringValue(raw, "menuId", null),
                name,
                java.util.UUID.randomUUID().toString()
        ));
        record.put("name", name);
        record.put("description", stringValue(raw, "description", ""));
        return record;
    }

    private String defaultVisualizationConfigFile(String configKind) {
        if ("low-code-app".equals(configKind)) {
            return "site.json";
        }
        if ("html-page".equals(configKind) || "static-html".equals(configKind)) {
            return null;
        }
        return "index.json";
    }

    private String visualizationRouteName(String configKind) {
        if ("low-code-app".equals(configKind)) {
            return "low-code-app";
        }
        if ("html-page".equals(configKind) || "static-html".equals(configKind)) {
            return "html-page";
        }
        return "low-code-page";
    }

    private boolean isVisualizationConfigRecordPresent(Map<String, Object> record) {
        String configType = stringValue(record, "configType", null);
        String fileName = stringValue(record, "fileName", null);
        if (!StringUtils.hasText(configType) || !StringUtils.hasText(fileName)) {
            log.warn("忽略未验证的可视化配置记录：缺少 configType/fileName，record={}", record);
            return false;
        }
        try {
            boolean exists = configService.fileExistsInConfigPath(configType, fileName);
            if (!exists) {
                log.warn("忽略未验证的可视化配置记录：{}_config 中不存在文件 {}", configType, fileName);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的可视化配置记录：校验 {}/{} 失败: {}", configType, fileName, e.getMessage(), e);
            return false;
        }
    }

    private boolean isDashboardConfigRecordPresent(Map<String, Object> record) {
        String dashboardId = stringValue(record, "dashboardId", null);
        String code = stringValue(record, "code", null);
        String name = stringValue(record, "name", null);
        try {
            if (StringUtils.hasText(dashboardId)) {
                try {
                    DashboardVo dashboard = dashboardService.info(Long.parseLong(dashboardId));
                    if (dashboard != null) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("数据看板记录 dashboardId 不是数字，将继续按 code/name 校验：{}", dashboardId);
                }
            }
            List<DashboardVo> dashboards = dashboardService.findAll();
            boolean matched = dashboards != null && dashboards.stream().anyMatch(item ->
                    (StringUtils.hasText(code) && code.equals(item.getCode()))
                            || (StringUtils.hasText(name) && name.equals(item.getName())));
            if (!matched) {
                log.warn("忽略未验证的数据看板记录：未找到 dashboardId/code/name 对应看板，record={}", record);
            }
            return matched;
        } catch (Exception e) {
            log.warn("忽略未验证的数据看板记录：校验失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isMenuConfigRecordPresent(Map<String, Object> record) {
        String menuId = stringValue(record, "menuId", null);
        String name = stringValue(record, "name", null);
        String source = stringValue(record, "sourceKey", null);
        try {
            if (StringUtils.hasText(menuId)) {
                try {
                    MenuVo menu = menuService.info(Long.parseLong(menuId));
                    if (menu != null) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("菜单配置记录 menuId 不是数字，将继续按 name/source 校验：{}", menuId);
                }
            }
            List<MenuVo> menus = menuService.findAll();
            boolean matched = menus != null && menus.stream().anyMatch(item ->
                    (StringUtils.hasText(name) && name.equals(item.getName()))
                            || (StringUtils.hasText(source) && source.equals(item.getSource())));
            if (!matched) {
                log.warn("忽略未验证的菜单配置记录：未找到 menuId/name/source 对应菜单，record={}", record);
            }
            return matched;
        } catch (Exception e) {
            log.warn("忽略未验证的菜单配置记录：校验失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private void mergeSectionRecords(Map<String, Object> extraData,
                                     Map<String, Object> patch,
                                     String sectionKey,
                                     List<String> recordKeys) {
        Map<String, Object> section = mapValue(extraData.get(sectionKey));
        Map<String, Object> patchSection = mapValue(patch.get(sectionKey));
        for (String recordKey : recordKeys) {
            mergeRecordList(section, patchSection, recordKey);
        }
        if (!section.isEmpty()) {
            extraData.put(sectionKey, section);
        }
    }

    private void mergeRecordList(Map<String, Object> dataAccess,
                                 Map<String, Object> patchDataAccess,
                                 String key) {
        List<Map<String, Object>> records = listOfMaps(dataAccess.get(key));
        for (Map<String, Object> record : listOfMaps(patchDataAccess.get(key))) {
            upsertRecord(records, record);
        }
        if (!records.isEmpty()) {
            dataAccess.put(key, records);
        }
    }

    private void mergeReportRecords(Map<String, Object> extraData, Map<String, Object> patch) {
        Map<String, Object> report = mapValue(extraData.get("report"));
        Map<String, Object> patchReport = mapValue(patch.get("report"));
        if (patchReport.isEmpty()) {
            return;
        }

        mergeRecordList(report, patchReport, "documents");
        mergeRecordList(report, patchReport, "artifacts");
        Map<String, Object> currentDocument = mapValue(patchReport.get("currentDocument"));
        if (!currentDocument.isEmpty()) {
            report.put("currentDocument", currentDocument);
        }
        if (!report.isEmpty()) {
            extraData.put("report", report);
        }
    }

    private void upsertRecord(List<Map<String, Object>> records, Map<String, Object> record) {
        String id = firstNonBlank(
                stringValue(record, "id", null),
                stringValue(record, "documentId", null),
                stringValue(record, "artifactId", null),
                stringValue(record, "fileName", null),
                stringValue(record, "configType", null),
                stringValue(record, "configIndex", null),
                stringValue(record, "code", null),
                stringValue(record, "dashboardId", null),
                stringValue(record, "menuId", null),
                stringValue(record, "taskId", null),
                stringValue(record, "version", null),
                stringValue(record, "name", null)
        );
        if (id != null) {
            records.removeIf(item -> id.equals(firstNonBlank(
                    stringValue(item, "id", null),
                    stringValue(item, "documentId", null),
                    stringValue(item, "artifactId", null),
                    stringValue(item, "fileName", null),
                    stringValue(item, "configType", null),
                    stringValue(item, "configIndex", null),
                    stringValue(item, "code", null),
                    stringValue(item, "dashboardId", null),
                    stringValue(item, "menuId", null),
                    stringValue(item, "taskId", null),
                    stringValue(item, "version", null),
                    stringValue(item, "name", null)
            )));
        }
        records.add(record);
    }

    private String extractMarkdownTitle(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return null;
    }

    private Map<String, Object> buildMetaConfigRecord(ChatMessagePart part, String defaultStatus) {
        Map<String, Object> record = new LinkedHashMap<>();
        Map<String, Object> metadata = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> config = mapFromValue(metadata.get("config"));
        if (config.isEmpty()) {
            config = parseJsonObject(part.getContent());
        }
        Map<String, Object> entity = firstObject(config.get("entity"));

        String entityName = firstNonBlank(
                stringValue(metadata, "entityName", null),
                stringValue(entity, "name", null),
                stringValue(entity, "id", null)
        );
        String entityLabel = firstNonBlank(
                stringValue(metadata, "entityLabel", null),
                stringValue(entity, "label", null),
                entityName
        );
        String fileName = firstNonBlank(
                stringValue(metadata, "fileName", null),
                stringValue(metadata, "targetFile", null),
                entityName == null ? null : entityName + ".json",
                stringValue(metadata, "defaultFileName", "meta_config/<entity>.json")
        );

        record.put("id", firstNonBlank(stringValue(metadata, "id", null), fileName, java.util.UUID.randomUUID().toString()));
        record.put("name", firstNonBlank(entityLabel, fileName, "元数据配置"));
        record.put("fileName", fileName);
        record.put("entityName", entityName);
        record.put("entityLabel", entityLabel);
        record.put("tableName", firstNonBlank(stringValue(metadata, "tableName", null), stringValue(entity, "table_name", null)));
        record.put("fieldCount", listSize(config.get("attribute")));
        record.put("status", stringValue(metadata, "status", defaultStatus));
        record.put("source", "message");
        record.put("content", part.getContent());
        if (!config.isEmpty()) {
            record.put("config", config);
        }
        return record;
    }

    private boolean isMetaConfigRecordPresent(Map<String, Object> record) {
        String fileName = stringValue(record, "fileName", null);
        if (!StringUtils.hasText(fileName)) {
            log.warn("忽略未验证的元数据配置记录：缺少 fileName，record={}", record);
            return false;
        }
        try {
            boolean exists = configService.fileExistsInConfigPath("meta", fileName);
            if (!exists) {
                log.warn("忽略未验证的元数据配置记录：meta_config 中不存在文件 {}", fileName);
                return false;
            }
            String content = configService.readFile("meta", fileName);
            if (!StringUtils.hasText(content)) {
                log.warn("忽略未验证的元数据配置记录：文件 {} 内容为空或不可读", fileName);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的元数据配置记录：校验文件 {} 失败: {}", fileName, e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Object> buildDataPushServiceRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = new LinkedHashMap<>();
        String id = firstNonBlank(stringValue(raw, "id", null), stringValue(raw, "taskId", null), stringValue(raw, "task_id", null));
        String name = firstNonBlank(stringValue(raw, "name", null), stringValue(raw, "taskName", null), stringValue(raw, "task_name", null), part.getContent());
        String sourceMark = firstNonBlank(stringValue(raw, "sourceMark", null), stringValue(raw, "source_mark", null), stringValue(raw, "mark", null));
        record.put("id", firstNonBlank(id, java.util.UUID.randomUUID().toString()));
        record.put("name", firstNonBlank(name, "Vectum 数据推送服务"));
        record.put("description", stringValue(raw, "description", ""));
        record.put("status", stringValue(raw, "status", "created"));
        record.put("source", "vectum");
        record.put("taskId", id);
        record.put("sourceMark", sourceMark);
        record.put("config", raw.get("config"));
        record.put("raw", raw);
        return record;
    }

    private boolean isDataPushServiceRecordPresent(Map<String, Object> record) {
        String sourceMark = stringValue(record, "sourceMark", null);
        if (!StringUtils.hasText(sourceMark)) {
            log.warn("忽略未验证的数据推送服务记录：缺少 sourceMark/mark，record={}", record);
            return false;
        }
        try {
            List<PushTaskVo> tasks = pushTaskService.findBySourceMark(sourceMark);
            if (tasks == null || tasks.isEmpty()) {
                log.warn("忽略未验证的数据推送服务记录：未查询到 sourceMark={} 的推送任务", sourceMark);
                return false;
            }
            String taskId = stringValue(record, "taskId", null);
            String name = stringValue(record, "name", null);
            if (StringUtils.hasText(taskId)) {
                boolean matchedById = tasks.stream()
                        .anyMatch(task -> task.getId() != null && taskId.equals(String.valueOf(task.getId())));
                if (!matchedById) {
                    log.warn("忽略未验证的数据推送服务记录：sourceMark={} 下不存在 taskId={}", sourceMark, taskId);
                    return false;
                }
            } else if (StringUtils.hasText(name)) {
                boolean matchedByName = tasks.stream().anyMatch(task -> name.equals(task.getName()));
                if (!matchedByName) {
                    log.warn("忽略未验证的数据推送服务记录：sourceMark={} 下不存在 name={}", sourceMark, name);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的数据推送服务记录：校验 sourceMark={} 失败: {}", sourceMark, e.getMessage(), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        try {
            Object parsed = com.coolxer.configuration.JacksonConfig.OBJECT_MAPPER.readValue(content, Object.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapFromValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private List<Map<String, Object>> firstNonEmptyListOfMaps(Object... values) {
        if (values == null) {
            return new ArrayList<>();
        }
        for (Object value : values) {
            List<Map<String, Object>> records = listOfMaps(value);
            if (!records.isEmpty()) {
                return records;
            }
        }
        return new ArrayList<>();
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstObject(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private String stringValue(Map<String, Object> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        return value == null || !StringUtils.hasText(value.toString()) ? fallback : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String toNdjson(ChatStreamEvent event) {
        return JacksonUtil.toJson(event) + "\n";
    }

    private static class McpToolLogStream {

        private static final String APPROVAL_EVENT_PREFIX = "\u001ezenvis-mcp-approval:";

        private final Sinks.Many<String> sink;

        private final Map<String, McpApprovalVo> approvalStates;

        private final Map<String, Integer> approvalOffsets;

        private McpToolLogStream(Sinks.Many<String> sink,
                                 Map<String, McpApprovalVo> approvalStates,
                                 Map<String, Integer> approvalOffsets) {
            this.sink = sink;
            this.approvalStates = approvalStates;
            this.approvalOffsets = approvalOffsets;
        }

        private static McpToolLogStream create() {
            return new McpToolLogStream(Sinks.many().multicast().onBackpressureBuffer(),
                    new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }

        private static McpToolLogStream disabled() {
            return new McpToolLogStream(null, Map.of(), Map.of());
        }

        private boolean enabled() {
            return sink != null;
        }

        private Flux<String> flux() {
            return enabled() ? sink.asFlux() : Flux.empty();
        }

        private void emit(McpToolCallLoggingProvider.McpToolCallLog logEvent) {
            if (!enabled() || logEvent == null) {
                return;
            }
            sink.tryEmitNext(formatLog(logEvent));
        }

        private void emitApproval(McpApprovalEvent approvalEvent) {
            if (!enabled() || approvalEvent == null || approvalEvent.data() == null) {
                return;
            }
            approvalStates.put(approvalEvent.data().getRequestId(), approvalEvent.data());
            sink.tryEmitNext(APPROVAL_EVENT_PREFIX + JacksonUtil.toJson(approvalEvent));
        }

        private static McpApprovalEvent parseApprovalEvent(String value) {
            if (value == null || !value.startsWith(APPROVAL_EVENT_PREFIX)) {
                return null;
            }
            return JacksonUtil.toObject(value.substring(APPROVAL_EVENT_PREFIX.length()), McpApprovalEvent.class);
        }

        private void recordApprovalPosition(String requestId, int contentOffset) {
            if (enabled() && StringUtils.hasText(requestId)) {
                approvalOffsets.putIfAbsent(requestId, Math.max(contentOffset, 0));
            }
        }

        private List<ChatMessagePart> approvalParts() {
            if (approvalStates.isEmpty()) {
                return List.of();
            }
            return approvalStates.values().stream()
                    .sorted(java.util.Comparator.comparing(McpApprovalVo::getCreateTime,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .map(approval -> toApprovalPart(approval, approvalOffsets.get(approval.getRequestId())))
                    .toList();
        }

        private static ChatMessagePart toApprovalPart(McpApprovalVo approval, Integer contentOffset) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("requestId", approval.getRequestId());
            metadata.put("toolKey", approval.getToolKey());
            metadata.put("toolName", approval.getToolName());
            metadata.put("sourceType", approval.getSourceType());
            metadata.put("serverName", approval.getServerName());
            metadata.put("channel", approval.getChannel());
            metadata.put("policy", approval.getPolicy());
            metadata.put("approvalScope", approval.getApprovalScope());
            metadata.put("argumentsSummary", approval.getArgumentsSummary());
            metadata.put("resultSummary", approval.getResultSummary());
            metadata.put("errorSummary", approval.getErrorSummary());
            metadata.put("riskLevel", approval.getRiskLevel());
            metadata.put("expireTime", approval.getExpireTime());
            if (contentOffset != null) {
                metadata.put("contentOffset", contentOffset);
            }
            return ChatMessagePart.builder()
                    .id(approval.getRequestId())
                    .type("mcp-approval")
                    .title("MCP 工具审批：" + approval.getToolName())
                    .content(approval.getDescription())
                    .status(approval.getStatus() == null ? "pending"
                            : approval.getStatus().name().toLowerCase(java.util.Locale.ROOT))
                    .metadata(metadata)
                    .build();
        }

        private void complete() {
            if (enabled()) {
                sink.tryEmitComplete();
            }
        }

        private static String formatLog(McpToolCallLoggingProvider.McpToolCallLog logEvent) {
            String toolName = inlineCode(logEvent.toolName());
            if ("started".equals(logEvent.status())) {
                return "\n\n**MCP调用开始：** " + toolName
                        + formatPayload("调用参数", logEvent.arguments())
                        + "\n\n";
            }
            if ("succeeded".equals(logEvent.status())) {
                return "\n\n**MCP调用成功：** " + toolName
                        + formatDuration(logEvent.durationMillis())
                        + formatPayload("返回结果", logEvent.result())
                        + "\n\n";
            }
            if ("failed".equals(logEvent.status())) {
                return "\n\n**MCP调用失败：** " + toolName
                        + formatDuration(logEvent.durationMillis())
                        + formatPayload("错误信息", logEvent.error())
                        + "\n\n";
            }
            return "\n\n**MCP调用日志：** " + toolName + "\n\n";
        }

        private static String formatPayload(String title, String payload) {
            if (!StringUtils.hasText(payload)) {
                return "";
            }
            String language = "text";
            String formatted = payload;
            try {
                var json = JacksonConfig.OBJECT_MAPPER.readTree(payload);
                if (json != null) {
                    formatted = JacksonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(json);
                    language = "json";
                }
            } catch (Exception ignored) {
                // Non-JSON tool output is still rendered in a plaintext code card.
            }
            return "\n\n" + title + "：\n\n```" + language + "\n" + formatted + "\n```";
        }

        private static String formatDuration(Long durationMillis) {
            return durationMillis == null ? "" : "，耗时 " + durationMillis + "ms";
        }

        private static String inlineCode(String value) {
            String normalized = StringUtils.hasText(value) ? value : "-";
            return "`" + normalized.replace('`', '\'') + "`";
        }
    }
}
