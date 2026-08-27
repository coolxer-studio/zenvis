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
import com.coolxer.model.dih.dto.ReportActionDto;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.demo.AgentDemoStateStore;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.dih.mcp.McpApprovalEvent;
import com.coolxer.service.dih.mcp.McpApprovalService;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.workflow.AgentWorkflowState;
import com.coolxer.service.dih.workflow.AgentWorkflowStep;
import com.coolxer.service.dih.workflow.WorkflowEvidenceService;
import com.coolxer.service.dih.workflow.WorkflowOrchestrator;
import com.coolxer.service.dih.workflow.WorkflowStateStore;
import com.coolxer.service.dih.workflow.WorkflowMetrics;
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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    private static final String CHAT_CONTEXT_LENGTH_EXCEEDED_MESSAGE =
            "当前对话内容过长，已超过模型可处理的上下文长度。请新建对话，或减少历史消息、附件及输入内容后重试。";
    private static final Set<String> VISUALIZATION_META_ENTITY_TOOLS = Set.of(
            "retrieval_list_display_entity", "retrieval_list_entity");
    private static final Set<String> VISUALIZATION_META_ATTRIBUTE_TOOLS = Set.of(
            "retrieval_list_display_attribute", "retrieval_list_attribute");
    private static final Set<String> VISUALIZATION_DATA_TOOLS = Set.of(
            "entity_overview", "entity_summary", "entity_trend", "entity_distribution",
            "entity_aggregate", "entity_histogram", "entity_scatter",
            "entity_value_statistics", "entity_relations", "entity_relation_timeline",
            "retrieval_search", "entity_list");
    private static final String GENERIC_SKILL_SYSTEM_PROMPT = """
            你是 ZenVis Skill 智能体。请严格遵循下方已加载 Skill 的工作流程、能力边界和输出要求。
            只处理当前 Skill 定义的任务；信息不足时先向用户询问，不得编造系统数据、工具结果或已执行动作。
            通用 Skill 会话不提供 RAG 或 MCP 工具，只能依据用户输入、附件和对话上下文作答。
            """;
    private final AIChatService chatService;
    private final AIBaseService baseService;
    private final ChatSessionService chatSessionService;
    private final DataAccessDemoResponseService dataAccessDemoResponseService;
    private final DataVisualizationDemoResponseService dataVisualizationDemoResponseService;
    private final ReportDemoResponseService reportDemoResponseService;
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

    @Autowired(required = false)
    private WorkflowOrchestrator workflowOrchestrator;

    @Autowired(required = false)
    private WorkflowEvidenceService workflowEvidenceService;

    @Autowired(required = false)
    private WorkflowStateStore workflowStateStore;

    @Autowired(required = false)
    private WorkflowMetrics workflowMetrics;

    @Autowired(required = false)
    private AgentDemoStateStore agentDemoStateStore;

    @Autowired(required = false)
    private ReportDocumentService reportDocumentService;

    public DihChatApplicationService(AIChatService chatService,
                                     AIBaseService baseService,
                                     ChatSessionService chatSessionService,
                                     DataAccessDemoResponseService dataAccessDemoResponseService,
                                     DataVisualizationDemoResponseService dataVisualizationDemoResponseService,
                                     ReportDemoResponseService reportDemoResponseService,
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
        this.reportDemoResponseService = reportDemoResponseService;
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
        Optional<DihChatExecutionPolicy> policyOptional;
        try {
            policyOptional = DihChatExecutionPolicy.resolve(chatType, skillService);
        } catch (IllegalArgumentException e) {
            return errorResponse(eventStream, "智能体能力不可用：" + e.getMessage());
        }
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

        if (executionPolicy.isAgent()
                && !executionPolicy.isDynamicSkill()
                && !skillService.isBuiltinAgentEnabled(chatType)) {
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
        ChatSession existingChatSession = chatSessionService.getChatSessionBySessionId(chatId, currentUser);
        McpToolLogStream builtinDemoToolStream = McpToolLogStream.disabled();
        String builtinDemoTurnId = null;
        String builtinDemoId = resolveBuiltinDemoId(
                chatType, userMessage, existingChatSession);
        Optional<Flux<String>> builtinDemoResponse;
        boolean dataAccessDemo = DataAccessAgent.AGENT_TYPE.equals(chatType)
                && dataAccessDemoResponseService != null;
        boolean dataVisualizationDemo =
                DataVisualizationAgent.AGENT_TYPE.equals(chatType)
                        && dataVisualizationDemoResponseService != null;
        if ((dataAccessDemo || dataVisualizationDemo)
                && StringUtils.hasText(builtinDemoId)) {
            McpToolContext demoToolContext = agentMcpToolService == null
                    ? McpToolContext.empty()
                    : agentMcpToolService.resolve(executionPolicy.agentType(), executionPolicy.skillIds());
            if (demoToolContext.hasTools()) {
                builtinDemoTurnId = UUID.randomUUID().toString();
                builtinDemoToolStream = McpToolLogStream.create(builtinDemoTurnId);
                demoToolContext = demoToolContext.withInvocationContext(new McpInvocationContext(
                        McpInvocationChannel.CHAT_AGENT,
                        currentUser == null ? null : currentUser.getId(),
                        chatId,
                        builtinDemoTurnId,
                        executionPolicy.agentType(),
                        null,
                        dataAccessDemo
                                ? McpInvocationContext.BUILTIN_DATA_ACCESS_DEMO
                                : McpInvocationContext
                                .BUILTIN_DATA_VISUALIZATION_DEMO,
                        builtinDemoToolStream::emitApproval
                ));
                demoToolContext = demoToolContext.withToolCallbackProvider(
                        new McpToolCallLoggingProvider(
                                demoToolContext.toolCallbackProvider(),
                                builtinDemoToolStream::emit)
                );
            }
            if (dataAccessDemo) {
                builtinDemoResponse = dataAccessDemoResponseService.findResponse(
                        existingChatSession,
                        chatId,
                        userMessage,
                        currentUser,
                        demoToolContext
                );
            } else {
                builtinDemoResponse =
                        dataVisualizationDemoResponseService.findResponse(
                                existingChatSession,
                                chatId,
                                userMessage,
                                currentUser,
                                demoToolContext
                        );
            }
        } else if (StringUtils.hasText(builtinDemoId)) {
            builtinDemoResponse = findBuiltinDemoResponse(
                    chatType,
                    chatId,
                    userMessage,
                    currentUser,
                    existingChatSession
            );
        } else {
            builtinDemoResponse = Optional.empty();
        }
        if (builtinDemoResponse.isPresent() && !StringUtils.hasText(builtinDemoId)) {
            builtinDemoId = defaultDemoId(chatType);
        }
        if (builtinDemoResponse.isPresent()) {
            ChatSession chatSession = appendUserMessage(
                    chatDto, chatType, userMessage, currentUser, effectiveDeepThink);
            if (agentDemoStateStore != null && StringUtils.hasText(builtinDemoId)) {
                agentDemoStateStore.activate(
                        chatSession,
                        builtinDemoId,
                        chatType,
                        isBuiltinInitialDemoPrompt(chatType, userMessage)
                                ? "initial" : "continuation");
            }
            Flux<String> response = builtinDemoResponse.get();
            if (builtinDemoToolStream.enabled()) {
                McpToolLogStream finalDemoToolStream = builtinDemoToolStream;
                String finalDemoTurnId = builtinDemoTurnId;
                response = Flux.merge(
                        finalDemoToolStream.flux(),
                        response.doFinally(signalType -> {
                            if (signalType == reactor.core.publisher.SignalType.CANCEL
                                    && mcpApprovalService != null
                                    && finalDemoTurnId != null) {
                                mcpApprovalService.cancelTurn(
                                        finalDemoTurnId,
                                        currentUser == null ? null : currentUser.getId());
                            }
                            finalDemoToolStream.complete();
                        })
                );
            }
            return emitAndSaveTextResponse(
                    response,
                    chatSession,
                    currentUser,
                    eventStream,
                    new AtomicReference<>(MessageType.TEXT),
                    effectiveDeepThink,
                    builtinDemoToolStream,
                    builtinDemoId,
                    chatDto.getReportAction()
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
        McpToolContext mcpToolContext = executionPolicy.toolsAllowed()
                ? agentMcpToolService.resolve(executionPolicy.agentType(), executionPolicy.skillIds())
                : McpToolContext.empty();
        McpToolLogStream mcpToolLogStream = McpToolLogStream.disabled();
        String turnId = UUID.randomUUID().toString();
        if (mcpToolContext.hasTools()) {
            mcpToolLogStream = McpToolLogStream.create(turnId);
            mcpToolContext = mcpToolContext.withInvocationContext(new McpInvocationContext(
                    McpInvocationChannel.CHAT_AGENT,
                    currentUser == null ? null : currentUser.getId(),
                    chatId,
                    turnId,
                    executionPolicy.agentType(),
                    null,
                    null,
                    mcpToolLogStream::emitApproval
            ));
            mcpToolContext = mcpToolContext.withToolCallbackProvider(
                    new McpToolCallLoggingProvider(mcpToolContext.toolCallbackProvider(), mcpToolLogStream::emit)
            );
        }

        String attachmentPrompt = chatAttachmentService.appendAttachmentContext(
                userMessage, chatDto.getAttachments(), currentUser);
        ChatSession chatSession = appendUserMessage(
                chatDto, chatType, userMessage, currentUser, effectiveDeepThink);
        if (reportDocumentService != null
                && chatDto.getReportAction() != null
                && chatDto.getReportAction().getSourceRefs() != null) {
            chatDto.getReportAction().setSourceRefs(
                    reportDocumentService.validateSourceRefs(
                            (long) chatSession.getId(),
                            chatDto.getReportAction().getSourceRefs(),
                            currentUser));
        }
        String prompt = appendReportSourceContext(
                attachmentPrompt, chatDto.getReportAction());

        AtomicReference<MessageType> messageType = new AtomicReference<>(MessageType.TEXT);

        String resolvedModel = model;
        McpToolContext resolvedMcpToolContext = mcpToolContext;
        Flux<String> fluxResponse = Flux.defer(() -> {
            String dispatchPrompt = bootstrapVisualizationWorkflow(
                    executionPolicy, resolvedMcpToolContext, prompt, chatSession);
            if (hasImageAttachment && resolvedMcpToolContext.hasTools()) {
                return chatService.analyzeImageAttachments(
                                chatId,
                                resolvedModel,
                                userMessage,
                                chatDto.getAttachments(),
                                currentUser)
                        .flatMapMany(imageEvidence -> dispatchChat(
                                chatType,
                                chatId,
                                resolvedModel,
                                dispatchPrompt + """

                                        【平台图片取证阶段结果】
                                        以下内容来自同一轮任务的图片识别阶段。它不是最终结论；需要系统数据时继续调用获准的只读 MCP 工具，
                                        并在来源清单中同时保留附件和工具审计。
                                        """ + imageEvidence,
                                chatDto,
                                currentUser,
                                resolvedMcpToolContext,
                                executionPolicy,
                                effectiveDeepThink,
                                messageType
                        ));
            }
            return dispatchChat(
                    chatType,
                    chatId,
                    resolvedModel,
                    dispatchPrompt,
                    chatDto,
                    currentUser,
                    resolvedMcpToolContext,
                    executionPolicy,
                    effectiveDeepThink,
                    messageType
            );
        });
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
                mcpToolLogStream,
                null,
                chatDto.getReportAction()
        );
    }

    /**
     * A visualization turn cannot safely ask the model to invent an entity selector and hope that
     * it voluntarily calls Meta first. Bootstrap the read-only entity Meta tool through the same
     * logged MCP callback that is exposed to the Agent, then give the controlled result to the
     * model. The resulting call is visible in the chat stream and is also retained as validation
     * evidence for the selection/confirmation cards.
     */
    private String bootstrapVisualizationWorkflow(
            DihChatExecutionPolicy executionPolicy,
            McpToolContext mcpToolContext,
            String prompt,
            ChatSession chatSession) {
        if (executionPolicy == null || executionPolicy.isDynamicSkill()) {
            return prompt;
        }
        if (DataAccessAgent.AGENT_TYPE.equals(executionPolicy.chatType())) {
            return bootstrapDataAccessWorkflow(
                    mcpToolContext, prompt, chatSession);
        }
        if (!DataVisualizationAgent.AGENT_TYPE.equals(executionPolicy.chatType())) {
            return prompt;
        }
        if (mcpToolContext == null || !mcpToolContext.hasTools()) {
            return prompt + """

                    【平台工作流阻断】
                    普通数据可视化请求没有可用的 MCP 工具，无法查询真实 Meta 和实体数据。
                    不得输出实体选择卡、查询确认卡或图表。
                    """;
        }
        AgentWorkflowState workflow = workflowStateStore == null
                ? null
                : workflowStateStore.loadActive(
                        chatSession, DataVisualizationAgent.AGENT_TYPE).orElse(null);
        AgentWorkflowStep step = workflow == null ? AgentWorkflowStep.ENTITY_META
                : workflow.getStep();
        if (step == AgentWorkflowStep.DATA_QUERY) {
            return executeApprovedVisualizationQuery(mcpToolContext, prompt, workflow);
        }
        if (step == AgentWorkflowStep.ATTRIBUTE_META) {
            return bootstrapVisualizationAttributeMeta(
                    mcpToolContext, prompt, workflow);
        }
        if (step == AgentWorkflowStep.PERSISTING) {
            return executeApprovedVisualizationPersistence(
                    mcpToolContext, prompt, chatSession, workflow);
        }
        if (!Set.of(
                AgentWorkflowStep.INTENT_CONFIRMATION,
                AgentWorkflowStep.ENTITY_META,
                AgentWorkflowStep.ENTITY_SELECTION).contains(step)) {
            return prompt + "\n\n【平台工作流状态】\n当前阶段：" + step.name()
                    + "。本轮不重复查询实体 Meta，请严格按当前阶段继续。";
        }
        return bootstrapVisualizationEntityMeta(mcpToolContext, prompt);
    }

    private String executeApprovedVisualizationPersistence(
            McpToolContext mcpToolContext,
            String prompt,
            ChatSession chatSession,
            AgentWorkflowState workflow) {
        Map<String, Object> context = workflow.getContext() == null
                ? new LinkedHashMap<>() : workflow.getContext();
        Map<String, Object> candidate = mapValue(
                context.get("persistenceCandidate"));
        Map<String, Object> plan = mapValue(
                context.get("persistencePlan"));
        if (candidate.isEmpty()) {
            try {
                String resourceResult = executeLockedVisualizationResources(
                        mcpToolContext, chatSession, workflow, context, plan);
                if (!StringUtils.hasText(resourceResult)) {
                    throw new IllegalStateException(
                            "已批准可视化方案不包含配置、Dashboard 或 Menu 锁定请求");
                }
                return prompt + "\n\n【平台已完成锁定可视化资源写入与读回】\n"
                        + resourceResult
                        + "\n请仅根据上述真实读回输出对应 dashboard-config-record"
                        + " 或 menu-config-record。";
            } catch (RuntimeException e) {
                markWorkflowBlocked(
                        chatSession,
                        workflow,
                        workflow.getStep(),
                        "dashboard/menu",
                        safeWorkflowError(e));
                return prompt + "\n\n【平台可视化资源写入或读回失败】\n真实错误："
                        + safeWorkflowError(e)
                        + "\n不得输出成功记录；重试时平台会先按锁定业务键读回，"
                        + "不会盲目重复创建。";
            }
        }
        String content = stringValue(candidate, "content", "");
        String configType = firstNonBlank(
                stringValue(plan, "configType", ""),
                stringValue(plan, "configIndex", ""));
        String fileName = stringValue(plan, "fileName", "");
        AgentWorkflowStep retryStep = workflow.getStep();
        if (!StringUtils.hasText(content)
                || !isSafeVisualizationConfigTarget(configType, fileName)) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "已批准可视化方案缺少锁定配置、configType 或安全 fileName");
            return prompt + """

                    【平台可视化配置写入阻断】
                    已批准方案缺少锁定配置、configType 或安全 fileName。
                    不得重新生成内容、替换目标或输出成功记录。
                    """;
        }
        List<String> required = List.of(
                "config_tree",
                "config_ensure_root",
                "config_add",
                "config_apply",
                "config_read");
        if (required.stream().anyMatch(
                name -> findToolCallback(mcpToolContext, name) == null)) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "可视化配置检查、写入或读回 MCP 工具未完整暴露");
            return prompt + "\n\n【平台可视化配置写入阻断】\n"
                    + "config_tree/config_ensure_root/config_add/config_apply/config_read"
                    + " 工具不完整。";
        }
        try {
            String treeResult = callNamedTool(
                    mcpToolContext,
                    "config_tree",
                    Map.of("type", configType));
            boolean exists = configTreeContainsFile(treeResult, fileName);
            if (exists) {
                String current = decodeToolString(callNamedTool(
                        mcpToolContext,
                        "config_read",
                        Map.of("type", configType, "fileName", fileName)));
                if (semanticContentEquals(content, current)) {
                    context.put("persistenceAlreadyApplied", true);
                    context.remove("persistenceOverwriteRequired");
                    workflow.setContext(context);
                    workflowStateStore.upsert(chatSession, workflow);
                    String resourceResult = executeLockedVisualizationResources(
                            mcpToolContext, chatSession, workflow, context, plan);
                    return prompt + "\n\n【平台可视化配置幂等读回通过】\n"
                            + "目标：" + configType + "/" + fileName
                            + "\n现有内容与用户批准的锁定候选语义一致，未重复写入。"
                            + (StringUtils.hasText(resourceResult)
                            ? "\n关联资源读回：" + resourceResult : "")
                            + "\n请输出 visualization-config-record，appliedConfig 必须"
                            + "等于锁定候选；关联 Dashboard/Menu 记录只能使用上述真实读回。";
                }
                if (!Boolean.TRUE.equals(plan.get("overwrite"))) {
                    context.put("persistenceOverwriteRequired", true);
                    workflow.setContext(context);
                    workflow.setStep(AgentWorkflowStep.PERSIST_CONFIRMATION);
                    workflow.setStateRevision(workflow.getStateRevision() + 1);
                    workflowStateStore.upsert(chatSession, workflow);
                    return prompt + "\n\n【平台检测到同名不同内容的可视化配置】\n"
                            + "目标：" + configType + "/" + fileName
                            + "\n尚未调用 config_apply。请输出单独覆盖确认卡，继续使用"
                            + "相同候选和目标，metadata 包含 overwrite=true；"
                            + "不得生成新的配置内容。";
                }
            } else {
                requireSuccessfulBoolean(
                        "config_ensure_root",
                        callNamedTool(
                                mcpToolContext,
                                "config_ensure_root",
                                Map.of("type", configType)));
                requireSuccessfulBoolean(
                        "config_add",
                        callNamedTool(
                                mcpToolContext,
                                "config_add",
                                Map.of(
                                        "type", configType,
                                        "configDto", Map.of(
                                                "fileName", fileName))));
            }
            requireSuccessfulBoolean(
                    "config_apply",
                    callNamedTool(
                            mcpToolContext,
                            "config_apply",
                            Map.of(
                                    "type", configType,
                                    "configDto", Map.of(
                                            "fileName", fileName,
                                            "text", content))));
            String readBack = decodeToolString(callNamedTool(
                    mcpToolContext,
                    "config_read",
                    Map.of("type", configType, "fileName", fileName)));
            if (!semanticContentEquals(content, readBack)) {
                throw new IllegalStateException(
                        "config_read 与用户批准的可视化候选不一致");
            }
            context.remove("persistenceOverwriteRequired");
            workflow.setContext(context);
            workflowStateStore.upsert(chatSession, workflow);
            String resourceResult = executeLockedVisualizationResources(
                    mcpToolContext, chatSession, workflow, context, plan);
            return prompt + "\n\n【平台已完成锁定可视化配置写入与读回】\n"
                    + "目标：" + configType + "/" + fileName
                    + "\n读回与批准候选语义一致。"
                    + (StringUtils.hasText(resourceResult)
                    ? "\n关联资源读回：" + resourceResult : "")
                    + "\n请输出 visualization-config-record，appliedConfig 必须"
                    + "等于锁定候选；关联 Dashboard/Menu 记录只能使用上述真实读回。";
        } catch (RuntimeException e) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "config",
                    safeWorkflowError(e));
            return prompt + "\n\n【平台可视化配置写入或读回失败】\n目标："
                    + configType + "/" + fileName
                    + "\n真实错误：" + safeWorkflowError(e)
                    + "\n不得输出成功记录；保留锁定候选并提供 retry。";
        }
    }

    private String executeLockedVisualizationResources(
            McpToolContext mcpToolContext,
            ChatSession chatSession,
            AgentWorkflowState workflow,
            Map<String, Object> context,
            Map<String, Object> plan) {
        Map<String, Object> dashboardRequest =
                lockedVisualizationResourceRequest(plan, "dashboard");
        Map<String, Object> menuRequest =
                lockedVisualizationResourceRequest(plan, "menu");
        List<Map<String, Object>> readBacks = new ArrayList<>();
        if (!dashboardRequest.isEmpty()) {
            readBacks.add(Map.of(
                    "dashboard",
                    executeLockedDashboard(
                            mcpToolContext, dashboardRequest, context)));
        }
        if (!menuRequest.isEmpty()) {
            readBacks.add(Map.of(
                    "menu",
                    executeLockedMenu(
                            mcpToolContext, menuRequest, context)));
        }
        if (!readBacks.isEmpty()) {
            workflow.setContext(context);
            workflowStateStore.upsert(chatSession, workflow);
        }
        return readBacks.isEmpty() ? "" : JacksonUtil.toJson(readBacks);
    }

    private Map<String, Object> executeLockedDashboard(
            McpToolContext mcpToolContext,
            Map<String, Object> request,
            Map<String, Object> context) {
        requireVisualizationResourceTools(
                mcpToolContext, "dashboard_create", "dashboard_view");
        String name = stringValue(request, "name", "");
        String code = stringValue(request, "code", "");
        if (!StringUtils.hasText(name) || !StringUtils.hasText(code)) {
            throw new IllegalStateException(
                    "Dashboard 锁定请求缺少 name 或 code");
        }
        List<DashboardVo> sameKey = readWithRetries(
                dashboardService::findAll).stream()
                .filter(item -> code.equals(item.getCode())
                        || name.equals(item.getName()))
                .toList();
        DashboardVo dashboard;
        if (!sameKey.isEmpty()) {
            dashboard = sameKey.stream()
                    .filter(item -> dashboardRequestMatches(request, item))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "已存在同名或同 code 但内容不同的 Dashboard，禁止重复创建"));
            context.put("dashboardAlreadyApplied", true);
        } else {
            Map<String, Object> created = toolResultObject(callNamedTool(
                    mcpToolContext,
                    "dashboard_create",
                    Map.of("request", request)));
            long id = longValue(created.get("id"));
            if (id <= 0) {
                throw new IllegalStateException(
                        "dashboard_create 未返回有效 ID");
            }
            dashboard = dashboardFromMap(toolResultObject(callNamedTool(
                    mcpToolContext,
                    "dashboard_view",
                    Map.of("id", id))));
        }
        if (Boolean.TRUE.equals(context.get("dashboardAlreadyApplied"))) {
            dashboard = dashboardFromMap(toolResultObject(callNamedTool(
                    mcpToolContext,
                    "dashboard_view",
                    Map.of("id", dashboard.getId()))));
        }
        if (!dashboardRequestMatches(request, dashboard)) {
            throw new IllegalStateException(
                    "dashboard_view 读回与用户批准的锁定请求不一致："
                            + JacksonUtil.toJson(dashboardReadBack(dashboard)));
        }
        Map<String, Object> readBack = dashboardReadBack(dashboard);
        context.put("dashboardReadBack", readBack);
        return readBack;
    }

    private Map<String, Object> executeLockedMenu(
            McpToolContext mcpToolContext,
            Map<String, Object> request,
            Map<String, Object> context) {
        requireVisualizationResourceTools(
                mcpToolContext, "menu_create", "menu_view");
        String name = stringValue(request, "name", "");
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("Menu 锁定请求缺少 name");
        }
        String source = stringValue(request, "source", "");
        List<MenuVo> sameKey = readWithRetries(menuService::findAll).stream()
                .filter(item -> name.equals(item.getName())
                        || (StringUtils.hasText(source)
                        && source.equals(item.getSource())))
                .toList();
        MenuVo menu;
        if (!sameKey.isEmpty()) {
            menu = sameKey.stream()
                    .filter(item -> menuRequestMatches(request, item))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "已存在同名或同 source 但内容不同的 Menu，禁止重复创建"));
            context.put("menuAlreadyApplied", true);
        } else {
            Map<String, Object> created = toolResultObject(callNamedTool(
                    mcpToolContext,
                    "menu_create",
                    Map.of("request", request)));
            long id = longValue(created.get("id"));
            if (id <= 0) {
                throw new IllegalStateException("menu_create 未返回有效 ID");
            }
            menu = menuFromMap(toolResultObject(callNamedTool(
                    mcpToolContext,
                    "menu_view",
                    Map.of("id", id))));
        }
        if (Boolean.TRUE.equals(context.get("menuAlreadyApplied"))) {
            menu = menuFromMap(toolResultObject(callNamedTool(
                    mcpToolContext,
                    "menu_view",
                    Map.of("id", menu.getId()))));
        }
        if (!menuRequestMatches(request, menu)) {
            throw new IllegalStateException(
                    "menu_view 读回与用户批准的锁定请求不一致："
                            + JacksonUtil.toJson(menuReadBack(menu)));
        }
        Map<String, Object> readBack = menuReadBack(menu);
        context.put("menuReadBack", readBack);
        return readBack;
    }

    private void requireVisualizationResourceTools(
            McpToolContext context,
            String writeTool,
            String readTool) {
        if (findToolCallback(context, writeTool) == null
                || findToolCallback(context, readTool) == null) {
            throw new IllegalStateException(
                    writeTool + "/" + readTool + " MCP 工具未完整暴露");
        }
    }

    private Map<String, Object> lockedVisualizationResourceRequest(
            Map<String, Object> plan,
            String key) {
        Map<String, Object> value = mapValue(plan.get(key));
        Map<String, Object> request = mapValue(value.get("request"));
        return request.isEmpty() ? value : request;
    }

    private Map<String, Object> toolResultObject(String result) {
        Map<String, Object> parsed = parseJsonObject(result);
        Map<String, Object> data = mapValue(parsed.get("data"));
        return data.isEmpty() ? parsed : data;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Objects.toString(value, ""));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private DashboardVo dashboardFromMap(Map<String, Object> value) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(value, DashboardVo.class);
    }

    private MenuVo menuFromMap(Map<String, Object> value) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(value, MenuVo.class);
    }

    private boolean dashboardRequestMatches(
            Map<String, Object> request,
            DashboardVo dashboard) {
        return dashboard != null
                && matchesIfPresent(
                stringValue(request, "name", ""), dashboard.getName())
                && matchesIfPresent(
                stringValue(request, "code", ""), dashboard.getCode())
                && matchesIfPresent(
                stringValue(request, "type", ""),
                dashboard.getType() == null ? "" : dashboard.getType().name())
                && matchesIfPresent(
                stringValue(request, "url", ""), dashboard.getUrl())
                && matchesIfPresent(
                stringValue(request, "configIndex", ""),
                dashboard.getConfigIndex())
                && matchesIfPresent(
                stringValue(request, "htmlPath", ""),
                dashboard.getHtmlPath())
                && matchesIfPresent(
                stringValue(request, "source", ""), dashboard.getSource());
    }

    private boolean menuRequestMatches(
            Map<String, Object> request,
            MenuVo menu) {
        return menu != null
                && matchesIfPresent(
                stringValue(request, "name", ""), menu.getName())
                && matchesIfPresent(
                stringValue(request, "type", ""),
                menu.getType() == null ? "" : menu.getType().name())
                && matchesIfPresent(
                stringValue(request, "route", ""), menu.getRoute())
                && matchesIfPresent(
                stringValue(request, "params", ""), menu.getParams())
                && matchesIfPresent(
                stringValue(request, "source", ""), menu.getSource());
    }

    private Map<String, Object> dashboardReadBack(DashboardVo dashboard) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboardId", dashboard.getId());
        result.put("name", dashboard.getName());
        result.put("code", dashboard.getCode());
        result.put("dashboardType",
                dashboard.getType() == null ? "" : dashboard.getType().name());
        result.put("url", Objects.toString(dashboard.getUrl(), ""));
        result.put("configIndex",
                Objects.toString(dashboard.getConfigIndex(), ""));
        result.put("htmlPath", Objects.toString(dashboard.getHtmlPath(), ""));
        return result;
    }

    private Map<String, Object> menuReadBack(MenuVo menu) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menuId", menu.getId());
        result.put("name", menu.getName());
        result.put("menuType",
                menu.getType() == null ? "" : menu.getType().name());
        result.put("route", Objects.toString(menu.getRoute(), ""));
        result.put("params", Objects.toString(menu.getParams(), ""));
        result.put("businessSource", Objects.toString(menu.getSource(), ""));
        return result;
    }

    private String bootstrapDataAccessWorkflow(
            McpToolContext mcpToolContext,
            String prompt,
            ChatSession chatSession) {
        if (workflowStateStore == null) {
            return prompt;
        }
        AgentWorkflowState workflow = workflowStateStore.loadActive(
                chatSession, DataAccessAgent.AGENT_TYPE).orElse(null);
        if (workflow == null) {
            if (looksLikeDirectDataPushRequest(prompt)) {
                return prompt + """

                        【平台普通数据接入路由】
                        当前请求明确指向数据推送服务，允许跳过 Meta 配置发现。
                        仍须展示并锁定完整 PushTask 配置、sourceMark 和任务参数，
                        经用户确认后才能执行格式检测、冲突检查、创建和日志读回。
                        """;
            }
            return bootstrapDataAccessMetaDiscovery(mcpToolContext, prompt);
        }
        if (mcpToolContext == null || !mcpToolContext.hasTools()) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    workflow.getStep(),
                    "",
                    "普通数据接入工作流没有可用的 MCP 工具");
            return prompt + """

                    【平台工作流阻断】
                    普通数据接入请求没有可用的 MCP 工具，不能生成成功记录。
                    """;
        }
        return switch (workflow.getStep()) {
            case META_DISCOVERY -> bootstrapDataAccessMetaDiscovery(
                    mcpToolContext, prompt);
            case META_PREWRITE_CHECK, META_APPLY -> executeApprovedMetaConfig(
                    mcpToolContext, prompt, chatSession, workflow);
            case PUSH_FORMAT_CHECK, PUSH_EXECUTING -> executeApprovedPushTask(
                    mcpToolContext, prompt, chatSession, workflow);
            default -> prompt + "\n\n【平台工作流状态】\n当前阶段："
                    + workflow.getStep().name()
                    + "。实体配置、任务配置和 MCP 参数均以服务端锁定状态为准；"
                    + "本轮不得绕过确认、重复写入或声明未经读回的成功结果。";
        };
    }

    private boolean looksLikeDirectDataPushRequest(String prompt) {
        String normalized = Objects.toString(prompt, "")
                .toLowerCase(Locale.ROOT);
        boolean pushTarget = normalized.contains("push_task")
                || normalized.contains("pushtask")
                || normalized.contains("vectum")
                || normalized.contains("vector")
                || normalized.contains("数据推送服务")
                || normalized.contains("数据推送任务");
        boolean explicitAction = normalized.contains("创建")
                || normalized.contains("添加")
                || normalized.contains("启动")
                || normalized.contains("修复")
                || normalized.contains("重启")
                || normalized.contains("诊断")
                || normalized.contains("查看");
        boolean metaAlsoRequested = normalized.contains("元数据")
                || normalized.contains("meta")
                || normalized.contains("实体")
                || normalized.contains("字段")
                || normalized.contains("完整接入")
                || normalized.contains("两者");
        boolean explicitlySkipMeta = normalized.contains("直接")
                || normalized.contains("跳过元数据")
                || normalized.contains("不创建元数据")
                || normalized.contains("不需要元数据");
        return pushTarget
                && explicitAction
                && (!metaAlsoRequested || explicitlySkipMeta);
    }

    private String bootstrapDataAccessMetaDiscovery(
            McpToolContext mcpToolContext,
            String prompt) {
        ToolCallback treeTool = findToolCallback(mcpToolContext, "config_tree");
        if (treeTool == null) {
            return prompt + """

                    【平台 Meta 检查阻断】
                    当前未暴露 config_tree，不能生成可确认的 Meta 配置方案。
                    """;
        }
        String arguments = JacksonUtil.toJson(Map.of("type", "meta"));
        try {
            String result = callTool(treeTool, arguments, mcpToolContext);
            return prompt + "\n\n【平台已执行 Meta 配置树 MCP】\n"
                    + "工具：config_tree\n参数：" + arguments
                    + "\n真实返回：" + result
                    + "\n请只基于该结果生成候选配置和确认卡；"
                    + "确认前不得调用 config_add 或 config_apply。";
        } catch (RuntimeException e) {
            return prompt + "\n\n【平台 Meta 配置树 MCP 失败】\n真实错误："
                    + safeWorkflowError(e)
                    + "\n不得输出成功记录，请提供重试入口。";
        }
    }

    private String executeApprovedMetaConfig(
            McpToolContext mcpToolContext,
            String prompt,
            ChatSession chatSession,
            AgentWorkflowState workflow) {
        Map<String, Object> context = workflow.getContext() == null
                ? new LinkedHashMap<>() : workflow.getContext();
        Map<String, Object> candidate = mapValue(context.get("candidate"));
        String content = stringValue(candidate, "content", "");
        String fileName = stringValue(context, "fileName", "");
        AgentWorkflowStep retryStep = workflow.getStep();
        if (!StringUtils.hasText(content) || !isSafeMetaFileName(fileName)) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "已批准方案缺少锁定配置，或目标文件名不安全");
            return prompt + """

                    【平台 Meta 写入阻断】
                    已批准方案缺少锁定配置，或 fileName 不是安全的 JSON 文件名。
                    不得重新生成参数或写入其他文件。
                    """;
        }

        ToolCallback treeTool = findToolCallback(mcpToolContext, "config_tree");
        ToolCallback readTool = findToolCallback(mcpToolContext, "config_read");
        ToolCallback addTool = findToolCallback(mcpToolContext, "config_add");
        ToolCallback applyTool = findToolCallback(mcpToolContext, "config_apply");
        if (treeTool == null || readTool == null || addTool == null
                || applyTool == null) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "Meta 检查、写入或读回 MCP 工具未完整暴露");
            return prompt + "\n\n【平台 Meta 写入阻断】\n"
                    + "config_tree/config_add/config_apply/config_read 工具不完整；"
                    + "不得声明写入成功。";
        }

        try {
            String treeResult = callTool(
                    treeTool,
                    JacksonUtil.toJson(Map.of("type", "meta")),
                    mcpToolContext);
            boolean exists = configTreeContainsFile(treeResult, fileName);
            if (workflow.getStep() == AgentWorkflowStep.META_PREWRITE_CHECK
                    && exists) {
                String readResult = callTool(
                        readTool,
                        JacksonUtil.toJson(Map.of(
                                "type", "meta",
                                "fileName", fileName)),
                        mcpToolContext);
                String current = decodeToolString(readResult);
                if (semanticContentEquals(content, current)) {
                    context.put("candidateAlreadyApplied", true);
                    context.remove("overwriteRequired");
                    workflow.setContext(context);
                    workflowStateStore.upsert(chatSession, workflow);
                    return prompt + "\n\n【平台 Meta 幂等读回通过】\n"
                            + "工具顺序：config_tree → config_read\n目标文件：" + fileName
                            + "\n现有内容与已批准候选 JSON 语义一致，未重复写入。"
                            + "\n请输出 source=workflow、status=applied 的"
                            + " zenvis:meta-config-record；记录中的 config 必须等于锁定候选。"
                            + metaThenPushInstruction(context);
                }
                context.put("overwriteRequired", true);
                workflow.setContext(context);
                workflow.setStep(AgentWorkflowStep.META_OVERWRITE_CONFIRMATION);
                workflow.setStateRevision(workflow.getStateRevision() + 1);
                workflowStateStore.upsert(chatSession, workflow);
                return prompt + "\n\n【平台检测到同名不同内容配置】\n"
                        + "工具顺序：config_tree → config_read\n目标文件：" + fileName
                        + "\n尚未调用 config_apply。请输出单独的覆盖确认卡，"
                        + "metadata 必须包含 configKind=meta、fileName、overwrite=true、"
                        + "planId 和 candidateDigest；不得展示或使用新的候选配置。";
            }

            if (!exists) {
                String addResult = callTool(
                        addTool,
                        JacksonUtil.toJson(Map.of(
                                "type", "meta",
                                "configDto", Map.of("fileName", fileName))),
                        mcpToolContext);
                requireSuccessfulBoolean("config_add", addResult);
            }
            String applyResult = callTool(
                    applyTool,
                    JacksonUtil.toJson(Map.of(
                            "type", "meta",
                            "configDto", Map.of(
                                    "fileName", fileName,
                                    "text", content))),
                    mcpToolContext);
            requireSuccessfulBoolean("config_apply", applyResult);
            String readResult = callTool(
                    readTool,
                    JacksonUtil.toJson(Map.of(
                            "type", "meta",
                            "fileName", fileName)),
                    mcpToolContext);
            String readBack = decodeToolString(readResult);
            if (!semanticContentEquals(content, readBack)) {
                throw new IllegalStateException("config_read 与已批准候选配置语义不一致");
            }
            context.remove("overwriteRequired");
            workflow.setContext(context);
            workflowStateStore.upsert(chatSession, workflow);
            return prompt + "\n\n【平台已完成锁定 Meta 配置写入与读回】\n"
                    + "目标文件：" + fileName
                    + "\n已严格执行 config_tree"
                    + (exists ? "" : " → config_add")
                    + " → config_apply → config_read，读回与候选 JSON 语义一致。"
                    + "\n请输出 source=workflow、status=applied 的"
                    + " zenvis:meta-config-record；config 必须等于锁定候选，"
                    + "不得改写文件名或内容。"
                    + metaThenPushInstruction(context);
        } catch (RuntimeException e) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "config",
                    safeWorkflowError(e));
            return prompt + "\n\n【平台 Meta 写入或读回失败】\n目标文件："
                    + fileName + "\n真实错误：" + safeWorkflowError(e)
                    + "\n不得生成成功记录；保留锁定候选并提供 retry。";
        }
    }

    private String executeApprovedPushTask(
            McpToolContext mcpToolContext,
            String prompt,
            ChatSession chatSession,
            AgentWorkflowState workflow) {
        Map<String, Object> context = workflow.getContext() == null
                ? new LinkedHashMap<>() : workflow.getContext();
        Map<String, Object> candidate = mapValue(context.get("candidate"));
        String config = stringValue(candidate, "content", "");
        String sourceMark = stringValue(context, "sourceMark", "");
        Map<String, Object> lockedRequest =
                new LinkedHashMap<>(mapValue(context.get("request")));
        AgentWorkflowStep retryStep = workflow.getStep();
        if (!StringUtils.hasText(config)
                || !StringUtils.hasText(sourceMark)
                || lockedRequest.isEmpty()
                || !StringUtils.hasText(stringValue(lockedRequest, "name", ""))) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "已批准 PushTask 方案缺少锁定配置、sourceMark 或任务名称");
            return prompt + """

                    【平台 PushTask 执行阻断】
                    已批准方案缺少锁定配置、sourceMark 或任务名称。
                    不得自行编造任务参数或创建任务。
                    """;
        }
        lockedRequest.put("config", config);
        lockedRequest.put("source", "SYSTEM");
        lockedRequest.put("mark", sourceMark);

        List<String> requiredTools = List.of(
                "push_task_detect_format",
                "push_task_list_by_source_mark",
                "push_task_create_and_start",
                "push_task_get_log");
        if (requiredTools.stream().anyMatch(
                name -> findToolCallback(mcpToolContext, name) == null)) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "",
                    "PushTask 格式检测、冲突检查、创建或日志 MCP 工具未完整暴露");
            return prompt + "\n\n【平台 PushTask 执行阻断】\n"
                    + "所需 MCP 工具未完整暴露，不得创建任务或生成成功记录。";
        }

        try {
            String formatResult = callNamedTool(
                    mcpToolContext,
                    "push_task_detect_format",
                    Map.of("content", config));
            String format = decodeToolString(formatResult);
            if (!Set.of("yaml", "toml", "json").contains(
                    format.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("push_task_detect_format 返回不支持的格式：" + format);
            }

            List<PushTaskVo> tasks = readPushTaskList(callNamedTool(
                    mcpToolContext,
                    "push_task_list_by_source_mark",
                    Map.of("sourceMark", sourceMark)));
            if (tasks.size() > 1) {
                throw new IllegalStateException(
                        "sourceMark=" + sourceMark + " 存在多个任务，平台拒绝自动选择或删除");
            }
            boolean created = tasks.isEmpty();
            if (tasks.isEmpty()) {
                String createResult = callNamedTool(
                        mcpToolContext,
                        "push_task_create_and_start",
                        Map.of("request", lockedRequest));
                requireSuccessfulBoolean("push_task_create_and_start", createResult);
            }
            tasks = readPushTaskList(callNamedTool(
                    mcpToolContext,
                    "push_task_list_by_source_mark",
                    Map.of("sourceMark", sourceMark)));
            if (tasks.size() != 1) {
                throw new IllegalStateException("创建或复用后未读回唯一 PushTask");
            }
            PushTaskVo task = tasks.get(0);
            if (task.getId() == null
                    || !"SYSTEM".equals(task.getSource())
                    || !sourceMark.equals(task.getMark())) {
                throw new IllegalStateException("读回任务不属于锁定 sourceMark 或 SYSTEM 来源");
            }
            Map<String, Object> logResult = parseJsonObject(callNamedTool(
                    mcpToolContext,
                    "push_task_get_log",
                    Map.of(
                            "taskId", task.getId(),
                            "sourceMark", sourceMark,
                            "logType", "system")));
            String status = firstNonBlank(
                    stringValue(logResult, "taskStatus", ""),
                    task.getStatus(),
                    "unknown");
            String systemLog = stringValue(logResult, "content", "");
            if (!"running".equalsIgnoreCase(status)
                    || workflowLogContainsCurrentError(systemLog)) {
                throw new IllegalStateException(
                        "任务读回状态为 " + status + "，或最新 system 日志包含错误");
            }
            if (StringUtils.hasText(task.getConfig())
                    && !Objects.equals(task.getConfig().trim(), config.trim())) {
                throw new IllegalStateException("任务读回配置与已批准候选不一致");
            }
            context.put("taskId", task.getId());
            context.put("taskStatus", "running");
            workflow.setContext(context);
            workflowStateStore.upsert(chatSession, workflow);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", task.getId());
            result.put("sourceMark", sourceMark);
            result.put("name", task.getName());
            result.put("description", task.getDescription());
            result.put("status", "running");
            result.put("format", format);
            result.put("config", config);
            return prompt + "\n\n【平台已完成锁定 PushTask 执行与读回】\n"
                    + "工具顺序：push_task_detect_format → "
                    + "push_task_list_by_source_mark"
                    + (created ? " → push_task_create_and_start" : "")
                    + " → push_task_list_by_source_mark → push_task_get_log\n"
                    + "验证结果：" + JacksonUtil.toJson(result)
                    + "\n请输出 source=workflow、status=running 的"
                    + " zenvis:vectum-task-record；taskId、sourceMark 和 config"
                    + "必须严格使用上述读回值。";
        } catch (RuntimeException e) {
            markWorkflowBlocked(
                    chatSession,
                    workflow,
                    retryStep,
                    "push_task",
                    safeWorkflowError(e));
            return prompt + "\n\n【平台 PushTask 执行或读回失败】\nsourceMark："
                    + sourceMark + "\n真实错误：" + safeWorkflowError(e)
                    + "\n不得生成成功记录；写操作不盲目重试，"
                    + "重试时先按 sourceMark 读回现状。";
        }
    }

    private String callNamedTool(
            McpToolContext mcpToolContext,
            String toolName,
            Map<String, Object> arguments) {
        ToolCallback callback = findToolCallback(mcpToolContext, toolName);
        if (callback == null) {
            throw new IllegalStateException("MCP 工具不可用：" + toolName);
        }
        return callTool(callback, JacksonUtil.toJson(arguments), mcpToolContext);
    }

    private void requireSuccessfulBoolean(String toolName, String result) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    JacksonConfig.OBJECT_MAPPER.readTree(result);
            if (node != null && ((node.isBoolean() && node.booleanValue())
                    || (node.isTextual() && Boolean.parseBoolean(node.textValue())))) {
                return;
            }
        } catch (Exception ignored) {
            // Report the bounded raw result below.
        }
        throw new IllegalStateException(toolName + " 未成功："
                + abbreviateWorkflowValue(result, 500));
    }

    private String decodeToolString(String result) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    JacksonConfig.OBJECT_MAPPER.readTree(result);
            return node != null && node.isTextual() ? node.textValue() : result;
        } catch (Exception ignored) {
            return result;
        }
    }

    private boolean configTreeContainsFile(String result, String fileName) {
        try {
            Object value = JacksonConfig.OBJECT_MAPPER.readValue(result, Object.class);
            return containsNamedConfig(value, fileName);
        } catch (Exception e) {
            throw new IllegalStateException("config_tree 返回无法解析", e);
        }
    }

    private boolean containsNamedConfig(Object value, String fileName) {
        if (value instanceof Map<?, ?> map) {
            Object name = map.get("fileName");
            if (fileName.equals(name)) {
                return true;
            }
            return map.values().stream()
                    .anyMatch(item -> containsNamedConfig(item, fileName));
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(
                    item -> containsNamedConfig(item, fileName));
        }
        return false;
    }

    private List<PushTaskVo> readPushTaskList(String result) {
        try {
            Object value = JacksonConfig.OBJECT_MAPPER.readValue(result, Object.class);
            if (value instanceof Map<?, ?> map && map.get("data") != null) {
                value = map.get("data");
            }
            return JacksonConfig.OBJECT_MAPPER.convertValue(
                    value, new TypeReference<List<PushTaskVo>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("PushTask 列表返回无法解析", e);
        }
    }

    private boolean workflowLogContainsCurrentError(String logContent) {
        if (!StringUtils.hasText(logContent)) {
            return false;
        }
        String marker = "=== Vectum run";
        int markerIndex = logContent.lastIndexOf(marker);
        String currentRun = markerIndex >= 0
                ? logContent.substring(markerIndex + marker.length()) : logContent;
        String normalized = currentRun.toLowerCase(Locale.ROOT);
        return normalized.contains("error")
                || normalized.contains("failed")
                || normalized.contains("fatal")
                || normalized.contains("panic");
    }

    private boolean isSafeMetaFileName(String fileName) {
        return StringUtils.hasText(fileName)
                && !fileName.contains("..")
                && fileName.matches("[A-Za-z0-9._-]+\\.json");
    }

    private boolean isSafeVisualizationConfigTarget(
            String configType,
            String fileName) {
        return StringUtils.hasText(configType)
                && configType.matches("[A-Za-z0-9_-]+")
                && StringUtils.hasText(fileName)
                && !fileName.startsWith("/")
                && !fileName.contains("..")
                && fileName.matches("[A-Za-z0-9_./-]+");
    }

    private void markWorkflowBlocked(
            ChatSession chatSession,
            AgentWorkflowState workflow,
            AgentWorkflowStep retryStep,
            String tool,
            String error) {
        if (workflow == null || workflowStateStore == null) {
            return;
        }
        List<Map<String, Object>> failures = workflow.getFailures() == null
                ? new ArrayList<>() : new ArrayList<>(workflow.getFailures());
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("stage", retryStep == null ? "" : retryStep.name());
        failure.put("retryStep", retryStep == null ? "" : retryStep.name());
        failure.put("tool", tool);
        failure.put("requestSummary", "使用工作流中已锁定的候选配置");
        failure.put("error", error);
        failure.put("retryable", true);
        failures.add(failure);
        workflow.setFailures(failures);
        workflow.setStep(AgentWorkflowStep.BLOCKED);
        workflow.setStatus("blocked");
        workflow.setStateRevision(workflow.getStateRevision() + 1);
        workflowStateStore.upsert(chatSession, workflow);
    }

    private String safeWorkflowError(RuntimeException error) {
        return abbreviateWorkflowValue(
                error == null ? "未知错误" : error.getMessage(), 1000);
    }

    private String metaThenPushInstruction(Map<String, Object> context) {
        if (!Boolean.TRUE.equals(context.get("continuePushTask"))) {
            return "";
        }
        return "\n原始意图还要求继续创建数据推送服务。Meta 成功记录之后，"
                + "请进入 PushTask 信息收集/候选生成，展示完整配置并输出"
                + " action=data_access.confirm_push_plan、configKind=push_task、"
                + "稳定 sourceMark 和完整任务参数的确认卡；确认前不得创建任务。";
    }

    private String abbreviateWorkflowValue(String value, int limit) {
        String sanitized = Objects.toString(value, "")
                .replaceAll("(?i)\\bBearer\\s+[^\\s,;]+", "Bearer ***")
                .replaceAll(
                        "(?i)(password|passwd|token|secret|api[_-]?key|access[_-]?key)"
                                + "\\s*[:=]\\s*[^\\s,;]+",
                        "$1=***");
        return sanitized.length() <= limit
                ? sanitized : sanitized.substring(0, limit) + "…";
    }

    private String bootstrapVisualizationEntityMeta(
            McpToolContext mcpToolContext,
            String prompt) {
        ToolCallback entityMetaTool = findToolCallback(
                mcpToolContext, "retrieval_list_display_entity");
        if (entityMetaTool == null) {
            entityMetaTool = findToolCallback(mcpToolContext, "retrieval_list_entity");
        }
        if (entityMetaTool == null) {
            log.warn("数据可视化智能体缺少实体 Meta MCP 工具");
            return prompt + """

                    【平台 Meta 前置校验失败】
                    当前未向数据可视化智能体暴露 retrieval_list_display_entity 或 retrieval_list_entity。
                    不得输出实体选择卡、确认卡或图表；请明确说明 MCP 工具不可用。
                    """;
        }

        String toolName = entityMetaTool.getToolDefinition().name();
        try {
            Map<String, Object> callbackContext = new LinkedHashMap<>();
            if (mcpToolContext.invocationContext() != null) {
                callbackContext.put(
                        McpInvocationContext.TOOL_CONTEXT_KEY,
                        mcpToolContext.invocationContext());
            }
            if (mcpToolContext.toolRuntimeContext() != null) {
                callbackContext.put(
                        com.coolxer.service.dih.mcp.ToolRuntimeContext.TOOL_CONTEXT_KEY,
                        mcpToolContext.toolRuntimeContext());
            }
            String result = callbackContext.isEmpty()
                    ? entityMetaTool.call("{}")
                    : entityMetaTool.call("{}", new ToolContext(callbackContext));
            Map<String, Object> entityPayload = metaToolPayload(result);
            List<Map<String, Object>> entityRows = firstNonEmptyListOfMaps(
                    entityPayload.get("entityList"),
                    entityPayload.get("entity_list"),
                    entityPayload.get("entities"),
                    entityPayload.get("datalist"),
                    mapValue(entityPayload.get("result")).get("rows"));
            List<Map<String, Object>> controlledRows = entityRows.stream()
                    .map(entity -> {
                        Map<String, Object> controlled = new LinkedHashMap<>();
                        controlled.put("name", stringValue(entity, "name", ""));
                        controlled.put("label", stringValue(entity, "label", ""));
                        String description = stringValue(entity, "description", "");
                        if (StringUtils.hasText(description)) {
                            controlled.put("description", description);
                        }
                        return controlled;
                    })
                    .filter(entity -> StringUtils.hasText(stringValue(entity, "name", "")))
                    .limit(50)
                    .toList();
            return prompt + "\n\n【平台已执行实体 Meta MCP】\n"
                    + "工具：" + toolName + "\n"
                    + "调用参数：{}\n"
                    + "真实返回：" + JacksonUtil.toJson(Map.of("entityList", controlledRows)) + "\n"
                    + "实体选择只能使用上述 entityList；value 必须严格等于 name，"
                    + "label 显示 label（name）。不要重复调用实体列表工具，"
                    + "不得输出自由输入框或未出现在返回结果中的实体。";
        } catch (RuntimeException e) {
            log.warn("数据可视化实体 Meta MCP 前置调用失败: tool={}, error={}",
                    toolName, e.getMessage(), e);
            return prompt + "\n\n【平台实体 Meta MCP 调用失败】\n"
                    + "工具：" + toolName + "\n"
                    + "错误：" + e.getMessage() + "\n"
                    + "请重试实体列表 MCP；成功前不得输出实体选择卡、确认卡或图表。";
        }
    }

    private String bootstrapVisualizationAttributeMeta(
            McpToolContext mcpToolContext,
            String prompt,
            AgentWorkflowState workflow) {
        String entity = workflow == null || workflow.getContext() == null
                ? "" : stringValue(workflow.getContext(), "selectedEntity", "");
        if (!StringUtils.hasText(entity)) {
            return prompt + """

                    【平台字段 Meta 前置校验失败】
                    工作流没有经过严格选项校验的实体名称。不得自行补写实体或字段；
                    请返回实体 Meta 阶段重新选择。
                    """;
        }
        ToolCallback attributeTool = findToolCallback(
                mcpToolContext, "retrieval_list_display_attribute");
        if (attributeTool == null) {
            attributeTool = findToolCallback(mcpToolContext, "retrieval_list_attribute");
        }
        if (attributeTool == null) {
            return prompt + "\n\n【平台字段 Meta 前置校验失败】\n"
                    + "当前未暴露字段 Meta MCP；不得生成查询方案或图表。";
        }

        String refreshedPrompt = bootstrapVisualizationEntityMeta(mcpToolContext, prompt);
        String toolName = attributeTool.getToolDefinition().name();
        String arguments = JacksonUtil.toJson(Map.of("entity", entity));
        try {
            String result = callTool(attributeTool, arguments, mcpToolContext);
            Map<String, Object> payload = metaToolPayload(result);
            List<Map<String, Object>> rows = firstNonEmptyListOfMaps(
                    payload.get("attributeList"),
                    payload.get("attribute_list"),
                    payload.get("attributes"),
                    payload.get("datalist"),
                    mapValue(payload.get("result")).get("rows"));
            List<Map<String, Object>> controlledRows = rows.stream()
                    .map(attribute -> {
                        Map<String, Object> controlled = new LinkedHashMap<>();
                        controlled.put("name", stringValue(attribute, "name", ""));
                        controlled.put("label", stringValue(attribute, "label", ""));
                        controlled.put("type", firstNonBlank(
                                stringValue(attribute, "type", ""),
                                stringValue(attribute, "dataType", "")));
                        return controlled;
                    })
                    .filter(attribute -> StringUtils.hasText(
                            stringValue(attribute, "name", "")))
                    .limit(200)
                    .toList();
            return refreshedPrompt + "\n\n【平台已执行字段 Meta MCP】\n"
                    + "工具：" + toolName + "\n"
                    + "调用参数：" + arguments + "\n"
                    + "真实返回：" + JacksonUtil.toJson(Map.of(
                            "entity", entity,
                            "attributeList", controlledRows)) + "\n"
                    + "查询方案中的时间、维度、指标、过滤和排序字段必须逐项使用"
                    + "上述 attributeList 的 name；本轮只能生成确认卡，不能调用数据工具。";
        } catch (RuntimeException e) {
            log.warn("数据可视化字段 Meta MCP 前置调用失败: entity={}, tool={}, error={}",
                    entity, toolName, e.getMessage(), e);
            return refreshedPrompt + "\n\n【平台字段 Meta MCP 调用失败】\n"
                    + "工具：" + toolName + "\n参数：" + arguments
                    + "\n错误：" + e.getMessage()
                    + "\n不得生成查询确认卡或图表，请提供重试入口。";
        }
    }

    private String executeApprovedVisualizationQuery(
            McpToolContext mcpToolContext,
            String prompt,
            AgentWorkflowState workflow) {
        Map<String, Object> context = workflow == null || workflow.getContext() == null
                ? Map.of() : workflow.getContext();
        Map<String, Object> query = mapValue(context.get("query"));
        String toolName = stringValue(query, "tool", "");
        Map<String, Object> request = mapValue(query.get("request"));
        if (!VISUALIZATION_DATA_TOOLS.contains(toolName) || request.isEmpty()) {
            return prompt + """

                    【平台数据查询阻断】
                    已批准方案缺少白名单 query.tool 或 query.request。
                    不得调用其他工具、改写参数或生成图表。
                    """;
        }
        ToolCallback tool = findToolCallback(mcpToolContext, toolName);
        if (tool == null) {
            return prompt + "\n\n【平台数据查询阻断】\n批准工具 " + toolName
                    + " 未向当前智能体暴露；不得改用其他查询工具。";
        }
        String arguments = visualizationToolArguments(toolName, request);
        try {
            String result = callTool(tool, arguments, mcpToolContext);
            return prompt + "\n\n【平台已严格执行批准的数据 MCP】\n"
                    + "planId：" + stringValue(context, "planId", "") + "\n"
                    + "工具：" + toolName + "\n"
                    + "调用参数：" + arguments + "\n"
                    + "真实返回：" + result + "\n"
                    + "必须原样使用返回的 meta、result 和 echarts.option 输出"
                    + " zenvis:visualization-chart-preview；query.tool/query.request 与批准方案"
                    + "必须完全一致，不得重新查询或使用演示数据。";
        } catch (RuntimeException e) {
            log.warn("执行批准的数据可视化查询失败: workflow={}, tool={}, error={}",
                    workflow == null ? "" : workflow.getWorkflowId(),
                    toolName, e.getMessage(), e);
            return prompt + "\n\n【平台数据 MCP 调用失败】\n工具：" + toolName
                    + "\n参数：" + arguments + "\n真实错误：" + e.getMessage()
                    + "\n不得覆盖已有快照；请输出可重试的阻塞状态。";
        }
    }

    private String visualizationToolArguments(
            String toolName,
            Map<String, Object> request) {
        if ("entity_list".equals(toolName)) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("entity", stringValue(request, "entity", ""));
            Map<String, Object> params = mapValue(request.get("params"));
            if (params.isEmpty()) {
                params = new LinkedHashMap<>(request);
                params.remove("entity");
            }
            arguments.put("params", params);
            return JacksonUtil.toJson(arguments);
        }
        return JacksonUtil.toJson(Map.of("request", request));
    }

    private String callTool(
            ToolCallback tool,
            String arguments,
            McpToolContext mcpToolContext) {
        Map<String, Object> callbackContext = new LinkedHashMap<>();
        if (mcpToolContext.invocationContext() != null) {
            callbackContext.put(
                    McpInvocationContext.TOOL_CONTEXT_KEY,
                    mcpToolContext.invocationContext());
        }
        if (mcpToolContext.toolRuntimeContext() != null) {
            callbackContext.put(
                    com.coolxer.service.dih.mcp.ToolRuntimeContext.TOOL_CONTEXT_KEY,
                    mcpToolContext.toolRuntimeContext());
        }
        return callbackContext.isEmpty()
                ? tool.call(arguments)
                : tool.call(arguments, new ToolContext(callbackContext));
    }

    private ToolCallback findToolCallback(McpToolContext mcpToolContext, String toolName) {
        if (mcpToolContext == null
                || mcpToolContext.toolCallbackProvider() == null
                || !StringUtils.hasText(toolName)) {
            return null;
        }
        ToolCallback[] callbacks = mcpToolContext.toolCallbackProvider().getToolCallbacks();
        if (callbacks == null) {
            return null;
        }
        for (ToolCallback callback : callbacks) {
            if (callback != null
                    && callback.getToolDefinition() != null
                    && toolName.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
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
                                      McpToolContext mcpToolContext,
                                      DihChatExecutionPolicy executionPolicy,
                                      boolean effectiveDeepThink,
                                      AtomicReference<MessageType> messageType) {
        String agentType = executionPolicy.agentType();
        if (DataAccessAgent.AGENT_TYPE.equals(agentType)) {
            messageType.set(MessageType.TEXT);
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
        if (ReportAgent.AGENT_TYPE.equals(agentType)) {
            messageType.set(MessageType.TEXT);
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
        if (DataVisualizationAgent.AGENT_TYPE.equals(agentType)) {
            messageType.set(MessageType.TEXT);
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
        if (SkillService.GENERIC_SKILL_AGENT_TYPE.equals(agentType)) {
            messageType.set(MessageType.TEXT);
            try {
                String skillPrompt = skillService.buildAgentSkillPrompt(agentType, executionPolicy.skillIds());
                String systemPrompt = GENERIC_SKILL_SYSTEM_PROMPT + "\n\n【已加载 Skill】\n" + skillPrompt;
                if (mcpToolContext != null && mcpToolContext.hasTools()) {
                    systemPrompt = systemPrompt + "\n\n" + mcpToolContext.systemPrompt();
                }
                return chatService.agentChat(
                        chatId,
                        model,
                        systemPrompt,
                        prompt,
                        chatDto.getAttachments(),
                        currentUser,
                        mcpToolContext
                );
            } catch (IllegalArgumentException e) {
                return Flux.error(new AgentCapabilityUnavailableException(
                        "智能体能力不可用：" + e.getMessage(),
                        e
                ));
            }
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

    private Optional<Flux<String>> findBuiltinDemoResponse(String chatType,
                                                           String chatId,
                                                           String prompt,
                                                           User currentUser,
                                                           ChatSession chatSession) {
        if (DataVisualizationAgent.AGENT_TYPE.equals(chatType)
                && dataVisualizationDemoResponseService != null) {
            return dataVisualizationDemoResponseService.findResponse(
                    chatSession, chatId, prompt, currentUser);
        }
        return findReportDemoResponse(chatType, chatId, prompt, currentUser, chatSession);
    }

    private String resolveBuiltinDemoId(
            String chatType,
            String prompt,
            ChatSession chatSession) {
        if (DataAccessAgent.AGENT_TYPE.equals(chatType)
                && DataAccessDemoResponseService.isUserEventDemoRequirementPrompt(prompt)) {
            return "data-access:user-event";
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(chatType)) {
            String normalized = Objects.toString(prompt, "").trim();
            if (DataVisualizationDemoResponseService.CHART_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "data-visualization:chart";
            }
            if (DataVisualizationDemoResponseService.PAGE_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "data-visualization:single-page";
            }
            if (DataVisualizationDemoResponseService.SIDEBAR_APP_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "data-visualization:sidebar-app";
            }
            if (DataVisualizationDemoResponseService.DASHBOARD_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "data-visualization:dashboard";
            }
            if (DataVisualizationDemoResponseService.MENU_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "data-visualization:menu";
            }
        }
        if (ReportAgent.AGENT_TYPE.equals(chatType)) {
            String normalized = Objects.toString(prompt, "").trim();
            if (ReportDemoResponseService.REPORT_USER_EVENT_ANALYSIS_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "report:user-event-analysis";
            }
            if (ReportDemoResponseService.REPORT_OPERATION_WEEKLY_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "report:operation-weekly";
            }
            if (ReportDemoResponseService.REPORT_INCIDENT_REVIEW_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "report:incident-review";
            }
            if (ReportDemoResponseService.REPORT_VISUALIZATION_ARCHIVE_EXAMPLE_PROMPT
                    .equals(normalized)) {
                return "report:visualization-archive";
            }
        }
        if (agentDemoStateStore != null) {
            Optional<String> active =
                    agentDemoStateStore.activeDemoId(chatSession, chatType);
            if (active.isPresent()) {
                return active.get();
            }
        }
        return "";
    }

    private boolean isBuiltinInitialDemoPrompt(String chatType, String prompt) {
        if (DataAccessAgent.AGENT_TYPE.equals(chatType)) {
            return DataAccessDemoResponseService
                    .isUserEventDemoRequirementPrompt(prompt);
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(chatType)) {
            return DataVisualizationDemoResponseService
                    .isUserEventVisualizationDemoPrompt(prompt);
        }
        return ReportAgent.AGENT_TYPE.equals(chatType)
                && ReportDemoResponseService.isReportDemoPrompt(prompt);
    }

    private String defaultDemoId(String chatType) {
        if (DataAccessAgent.AGENT_TYPE.equals(chatType)) {
            return "data-access:user-event";
        }
        if (DataVisualizationAgent.AGENT_TYPE.equals(chatType)) {
            return "data-visualization:chart";
        }
        if (ReportAgent.AGENT_TYPE.equals(chatType)) {
            return "report:user-event-analysis";
        }
        return "";
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested) {
        return emitAndSaveTextResponse(fluxResponse, chatSession, currentUser, eventStream,
                messageType, deepThinkRequested, McpToolLogStream.disabled(), null, null);
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested,
                                                 McpToolLogStream toolActivityStream) {
        return emitAndSaveTextResponse(
                fluxResponse,
                chatSession,
                currentUser,
                eventStream,
                messageType,
                deepThinkRequested,
                toolActivityStream,
                null,
                null);
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested,
                                                 McpToolLogStream toolActivityStream,
                                                 String demoId) {
        return emitAndSaveTextResponse(
                fluxResponse,
                chatSession,
                currentUser,
                eventStream,
                messageType,
                deepThinkRequested,
                toolActivityStream,
                demoId,
                null);
    }

    private Flux<String> emitAndSaveTextResponse(Flux<String> fluxResponse,
                                                 ChatSession chatSession,
                                                 User currentUser,
                                                 boolean eventStream,
                                                 AtomicReference<MessageType> messageType,
                                                 boolean deepThinkRequested,
                                                 McpToolLogStream toolActivityStream,
                                                 String demoId,
                                                 ReportActionDto reportAction) {
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
                                toolActivityStream.approvalParts(),
                                toolActivityStream,
                                demoId,
                                reportAction
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
                        messageType.get(), false, false, toolActivityStream.approvalParts(),
                        toolActivityStream, demoId, reportAction))
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
        String capabilityErrorMessage = findAgentCapabilityErrorMessage(error);
        if (StringUtils.hasText(capabilityErrorMessage)) {
            return capabilityErrorMessage;
        }
        if (isContextLengthExceeded(error)) {
            return CHAT_CONTEXT_LENGTH_EXCEEDED_MESSAGE;
        }
        return CHAT_ERROR_MESSAGE;
    }

    private String findAgentCapabilityErrorMessage(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 20) {
            if (current instanceof AgentCapabilityUnavailableException
                    && StringUtils.hasText(current.getMessage())) {
                return current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isContextLengthExceeded(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 20) {
            if (containsContextLengthExceededSignal(current.getMessage())) {
                return true;
            }
            if (current instanceof WebClientResponseException responseException
                    && containsContextLengthExceededSignal(responseException.getResponseBodyAsString())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsContextLengthExceededSignal(String details) {
        if (!StringUtils.hasText(details)) {
            return false;
        }
        String normalized = details.toLowerCase(Locale.ROOT);
        if (normalized.contains("maximum context length")
                || normalized.contains("context_length_exceeded")
                || normalized.contains("context length exceeded")
                || normalized.contains("context window exceeded")) {
            return true;
        }
        boolean mentionsInputTokens =
                normalized.contains("input_tokens") || normalized.contains("input tokens");
        boolean mentionsContextLimit =
                normalized.contains("context length")
                        || normalized.contains("context window")
                        || normalized.contains("context limit")
                        || normalized.contains("token limit")
                        || normalized.contains("too many tokens");
        if (mentionsInputTokens && mentionsContextLimit) {
            return true;
        }
        boolean mentionsChineseContext =
                normalized.contains("上下文长度") || normalized.contains("上下文窗口");
        boolean mentionsChineseExceeded =
                normalized.contains("超过") || normalized.contains("超限") || normalized.contains("过长");
        return mentionsChineseContext && mentionsChineseExceeded;
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
        return saveAiResponse(chatSession, currentUser, content, type, withParts,
                deepThinkRequested, List.of(), McpToolLogStream.disabled());
    }

    private Message saveAiResponse(ChatSession chatSession,
                                   User currentUser,
                                   String content,
                                   MessageType type,
                                   boolean withParts,
                                   boolean deepThinkRequested,
                                   List<ChatMessagePart> supplementalParts,
                                   McpToolLogStream toolActivityStream) {
        return saveAiResponse(
                chatSession,
                currentUser,
                content,
                type,
                withParts,
                deepThinkRequested,
                supplementalParts,
                toolActivityStream,
                null,
                null);
    }

    private Message saveAiResponse(ChatSession chatSession,
                                   User currentUser,
                                   String content,
                                   MessageType type,
                                   boolean withParts,
                                   boolean deepThinkRequested,
                                   List<ChatMessagePart> supplementalParts,
                                   McpToolLogStream toolActivityStream,
                                   String demoId) {
        return saveAiResponse(
                chatSession,
                currentUser,
                content,
                type,
                withParts,
                deepThinkRequested,
                supplementalParts,
                toolActivityStream,
                demoId,
                null);
    }

    private Message saveAiResponse(ChatSession chatSession,
                                   User currentUser,
                                   String content,
                                   MessageType type,
                                   boolean withParts,
                                   boolean deepThinkRequested,
                                   List<ChatMessagePart> supplementalParts,
                                   McpToolLogStream toolActivityStream,
                                   String demoId,
                                   ReportActionDto reportAction) {
        Message aiMessage = new Message("ai", content, type);
        List<ChatMessagePart> parts = List.of();
        List<Map<String, Object>> reportEvidenceRefs = List.of();
        if (withParts) {
            String parsableContent = insertSupplementalMarkers(content, supplementalParts);
            parts = new ArrayList<>(chatMessagePartParser.parse(parsableContent, type));
            parts = mergeSupplementalParts(content, parts, supplementalParts);
            reportEvidenceRefs = buildReportEvidenceRefs(
                    chatSession, toolActivityStream, reportAction, aiMessage.getId());
            if (StringUtils.hasText(demoId)) {
                decorateDemoParts(parts, demoId);
            } else {
                ensurePlatformVisualizationChartPreview(
                        chatSession, parts, toolActivityStream);
                validateDataVisualizationParts(chatSession, parts, toolActivityStream);
                if (workflowOrchestrator != null) {
                    workflowOrchestrator.prepareVisualizationParts(
                            chatSession, parts, reportEvidenceRefs, aiMessage.getId());
                }
            }
            parts = applyReportProtocol(
                    parts,
                    content,
                    reportAction,
                    reportEvidenceRefs,
                    aiMessage.getId(),
                    demoId);
            if (deepThinkRequested && parts.stream().noneMatch(part -> "thinking".equals(part.getType()))) {
                parts.add(0, ChatMessagePart.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .type("thinking")
                        .title("思考过程")
                        .content("已完成深度思考，当前模型未返回可展示的思考过程。")
                        .status("completed")
                        .build());
            }
            if (parts.stream().anyMatch(this::isReportDocumentPart)) {
                aiMessage.setContent(stripReportDocumentFence(content));
            }
            aiMessage.setParts(parts);
        }
        if (chatSession == null) {
            return aiMessage;
        }
        try {
            persistGeneratedReport(
                    chatSession,
                    parts,
                    reportAction,
                    reportEvidenceRefs,
                    currentUser);
            ChatSession savedSession = chatSessionService.appendMessage(chatSession, aiMessage, currentUser);
            mergeStructuredExtraData(savedSession, parts, currentUser);
            log.info("保存AI响应到会话，消息类型: {}, 富消息片段: {}", aiMessage.getType(), withParts);
        } catch (Exception e) {
            log.error("保存模型响应到会话失败: {}", e.getMessage(), e);
        }
        return aiMessage;
    }

    private List<Map<String, Object>> buildReportEvidenceRefs(
            ChatSession chatSession,
            McpToolLogStream toolActivityStream,
            ReportActionDto reportAction,
            String aiMessageId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        if (reportAction != null && reportAction.getSourceRefs() != null) {
            refs.addAll(reportAction.getSourceRefs());
        }
        if (toolActivityStream != null) {
            List<Map<String, Object>> toolRefs = workflowEvidenceService == null
                    ? toolActivityStream.evidenceRefs()
                    : workflowEvidenceService.succeededForTurn(
                            toolActivityStream.turnId(),
                            chatSession == null ? null : chatSession.getSessionId(),
                            chatSession == null ? null : chatSession.getCreateBy());
            refs.addAll(toolRefs.isEmpty() ? toolActivityStream.evidenceRefs() : toolRefs);
        }
        if (chatSession != null && StringUtils.hasText(chatSession.getMessages())) {
            try {
                List<Message> messages = JacksonUtil.toList(
                        chatSession.getMessages(),
                        new TypeReference<List<Message>>() {});
                for (int index = messages.size() - 1; index >= 0; index--) {
                    Message message = messages.get(index);
                    if (!"user".equals(message.getSender())) {
                        continue;
                    }
                    Map<String, Object> messageRef = new LinkedHashMap<>();
                    messageRef.put("type", "message");
                    messageRef.put("id", message.getId());
                    messageRef.put("messageId", message.getId());
                    messageRef.put("status", "available");
                    refs.add(messageRef);
                    if (message.getAttachments() != null) {
                        for (ChatAttachment attachment : message.getAttachments()) {
                            Map<String, Object> attachmentRef = new LinkedHashMap<>();
                            attachmentRef.put("type", "attachment");
                            attachmentRef.put("id", attachment.getFileId());
                            attachmentRef.put("attachmentId", attachment.getFileId());
                            attachmentRef.put("name", attachment.getFileName());
                            attachmentRef.put("contentType", attachment.getContentType());
                            attachmentRef.put("parseStatus", attachment.getParseStatus());
                            attachmentRef.put("truncated",
                                    attachment.getMessage() != null
                                            && attachment.getMessage().contains("截断"));
                            attachmentRef.put("status", firstNonBlank(
                                    attachment.getParseStatus(), "uploaded"));
                            refs.add(attachmentRef);
                        }
                    }
                    break;
                }
            } catch (Exception e) {
                log.warn("提取报表附件证据失败：{}", e.getMessage());
            }
        }
        if (StringUtils.hasText(aiMessageId)) {
            Map<String, Object> responseRef = new LinkedHashMap<>();
            responseRef.put("type", "message");
            responseRef.put("id", aiMessageId);
            responseRef.put("messageId", aiMessageId);
            responseRef.put("role", "ai");
            refs.add(responseRef);
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> ref : refs) {
            if (ref == null || ref.isEmpty()) {
                continue;
            }
            String key = firstNonBlank(
                    stringValue(ref, "evidenceId", null),
                    stringValue(ref, "id", null),
                    stringValue(ref, "auditId", null),
                    reportContentHash(JacksonUtil.toJson(ref)));
            unique.putIfAbsent(key, new LinkedHashMap<>(ref));
        }
        return new ArrayList<>(unique.values());
    }

    private List<ChatMessagePart> applyReportProtocol(
            List<ChatMessagePart> sourceParts,
            String rawContent,
            ReportActionDto action,
            List<Map<String, Object>> sourceRefs,
            String messageId,
            String demoId) {
        List<ChatMessagePart> parts = new ArrayList<>(sourceParts == null ? List.of() : sourceParts);
        if (action != null && action.isSelectionRewrite()) {
            String fragmentContent = parts.stream()
                    .filter(this::isReportDocumentPart)
                    .map(ChatMessagePart::getContent)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseGet(() -> parts.stream()
                            .filter(part -> "code".equals(part.getType()))
                            .map(ChatMessagePart::getContent)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(stripSingleOuterFence(rawContent)));
            parts.removeIf(part -> isReportDocumentPart(part)
                    || "markdown".equals(part.getType())
                    || "code".equals(part.getType()));
            if (!StringUtils.hasText(fragmentContent)
                    || fragmentContent.length() > ReportDocumentService.MAX_REPORT_CONTENT_CHARS) {
                parts.add(reportNotice(
                        "选区改写失败",
                        "模型没有返回有效片段，原文未被修改。",
                        "failed"));
                return parts;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("documentId", action.getDocumentId());
            metadata.put("baseRevision", action.getBaseRevision());
            metadata.put("selectionId", action.getSelectionId());
            metadata.put("selectionHash", action.getSelectionHash());
            metadata.put("contentHash", reportContentHash(fragmentContent));
            metadata.put("sourceRefs", sourceRefs);
            metadata.put("messageId", messageId);
            parts.add(ChatMessagePart.builder()
                    .id(UUID.randomUUID().toString())
                    .type("report-fragment")
                    .title("选区改写")
                    .content(fragmentContent)
                    .status("completed")
                    .metadata(metadata)
                    .build());
            return parts;
        }

        List<ChatMessagePart> reportParts = parts.stream()
                .filter(this::isReportDocumentPart)
                .toList();
        boolean expectedFullDocument = action != null && action.isFullDocumentAction();
        if (expectedFullDocument && reportParts.size() != 1) {
            reportParts.forEach(part -> part.setStatus("failed"));
            parts.add(reportNotice(
                    "报表生成失败",
                    reportParts.isEmpty()
                            ? "模型未返回完整的 zenvis:report-document-config 文档。"
                            : "模型返回了重复的完整报表，系统已拒绝覆盖当前文档。",
                    "failed"));
            return parts;
        }
        for (ChatMessagePart part : reportParts) {
            if (!StringUtils.hasText(part.getContent())
                    || part.getContent().length() > ReportDocumentService.MAX_REPORT_CONTENT_CHARS) {
                part.setStatus("failed");
                parts.add(reportNotice(
                        "报表格式无效",
                        "报表正文为空或超过长度限制，当前文档未更新。",
                        "failed"));
                continue;
            }
            Map<String, Object> metadata = part.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(part.getMetadata());
            if (action != null) {
                metadata.put("reportAction", action.getType());
                metadata.put("documentId", action.getDocumentId());
                metadata.put("baseRevision", action.getBaseRevision());
            }
            metadata.put("sourceRefs", sourceRefs);
            metadata.put("sourceAttachments", sourceRefs.stream()
                    .filter(ref -> "attachment".equals(stringValue(ref, "type", "")))
                    .toList());
            metadata.put("messageId", messageId);
            metadata.put("contentHash", reportContentHash(part.getContent()));
            if (StringUtils.hasText(demoId)) {
                metadata.put("demo", true);
                metadata.put("source", "demo");
            }
            part.setStatus("generated");
            part.setMetadata(metadata);
        }
        return parts;
    }

    private void persistGeneratedReport(
            ChatSession chatSession,
            List<ChatMessagePart> parts,
            ReportActionDto action,
            List<Map<String, Object>> sourceRefs,
            User currentUser) {
        if (reportDocumentService == null
                || (action != null && action.isSelectionRewrite())
                || parts == null) {
            return;
        }
        List<ChatMessagePart> documents = parts.stream()
                .filter(this::isReportDocumentPart)
                .filter(part -> !"failed".equals(part.getStatus()))
                .toList();
        if (documents.size() != 1) {
            return;
        }
        ChatMessagePart documentPart = documents.get(0);
        try {
            Map<String, Object> currentDocument = reportDocumentService.saveGenerated(
                    chatSession, documentPart, action, sourceRefs, currentUser)
                    .getCurrentDocument();
            Map<String, Object> metadata = documentPart.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(documentPart.getMetadata());
            metadata.putAll(currentDocument);
            metadata.put("contentStored", true);
            documentPart.setMetadata(metadata);
            // 成功写入独立文档/修订表后，消息 part 只保留引用，避免正文再复制到会话 JSON。
            documentPart.setContent("");
            documentPart.setStatus("saved");
        } catch (ReportRevisionConflictException e) {
            documentPart.setStatus("conflict");
            Map<String, Object> metadata = documentPart.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(documentPart.getMetadata());
            metadata.put("conflict", true);
            metadata.put("currentDocument", e.getCurrentDocument());
            documentPart.setMetadata(metadata);
            parts.add(reportNotice(
                    "报表版本冲突",
                    e.getMessage() + " AI 结果已保留为预览，没有覆盖当前文档。",
                    "warning"));
        } catch (Exception e) {
            documentPart.setStatus("failed");
            parts.add(reportNotice(
                    "报表保存失败",
                    firstNonBlank(e.getMessage(), "生成结果未写入当前文档。"),
                    "failed"));
            log.error("保存生成报表失败：{}", e.getMessage(), e);
        }
    }

    private ChatMessagePart reportNotice(String title, String content, String status) {
        return ChatMessagePart.builder()
                .id(UUID.randomUUID().toString())
                .type("notice")
                .title(title)
                .content(content)
                .level("failed".equals(status) ? "error" : "warning")
                .status(status)
                .metadata(Map.of("source", "platform"))
                .build();
    }

    private String stripSingleOuterFence(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.trim().replaceFirst(
                "(?s)^```[^\\r\\n]*\\R([\\s\\S]*?)\\R?```$",
                "$1").trim();
    }

    private String stripReportDocumentFence(String content) {
        if (!StringUtils.hasText(content)) {
            return "报表已更新至编辑器。";
        }
        String visible = content.replaceAll(
                "(?s)```zenvis:report-document-config\\s*.*?```",
                "").trim();
        return StringUtils.hasText(visible) ? visible : "报表已更新至编辑器。";
    }

    private String reportContentHash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(content));
        }
    }

    private String appendReportSourceContext(String prompt, ReportActionDto action) {
        if (action == null || action.getSourceRefs() == null || action.getSourceRefs().isEmpty()) {
            return prompt;
        }
        String sourceJson = JacksonUtil.toJson(action.getSourceRefs());
        int maxChars = 16_000;
        boolean truncated = sourceJson.length() > maxChars;
        String bounded = truncated ? sourceJson.substring(0, maxChars) : sourceJson;
        return (prompt == null ? "" : prompt) + """

                【用户选定的报表素材】
                以下 JSON 是用户有权访问并主动选择的来源引用、摘要或内容节选。只可据此形成可追溯结论；
                缺失、冲突、失败或截断的来源必须标记“待确认”，不得补造事实。
                """ + bounded + (truncated
                ? "\n【素材清单因长度限制已截断，超出部分不可作为事实依据。】"
                : "");
    }

    private void decorateDemoParts(List<ChatMessagePart> parts, String demoId) {
        if (parts == null || !StringUtils.hasText(demoId)) {
            return;
        }
        for (ChatMessagePart part : parts) {
            if (part == null) {
                continue;
            }
            Map<String, Object> metadata = part.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(part.getMetadata());
            String existingSource = stringValue(metadata, "source", "");
            if (StringUtils.hasText(existingSource)
                    && !"demo".equals(existingSource)
                    && !"workflow".equals(existingSource)) {
                metadata.putIfAbsent("businessSource", existingSource);
            }
            metadata.put("demoId", demoId);
            metadata.put("source", "demo");
            metadata.remove("workflowId");
            metadata.remove("workflowVersion");
            metadata.remove("stateRevision");
            part.setMetadata(metadata);
        }
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

    private void ensurePlatformVisualizationChartPreview(
            ChatSession chatSession,
            List<ChatMessagePart> parts,
            McpToolLogStream evidence) {
        if (chatSession == null
                || workflowStateStore == null
                || parts == null
                || parts.stream().anyMatch(part ->
                "visualization-chart-preview".equals(part.getType()))) {
            return;
        }
        AgentWorkflowState workflow = workflowStateStore.loadActive(
                chatSession, DataVisualizationAgent.AGENT_TYPE).orElse(null);
        if (workflow == null
                || workflow.getStep() != AgentWorkflowStep.DATA_QUERY
                || workflow.getContext() == null) {
            return;
        }
        Map<String, Object> query = mapValue(workflow.getContext().get("query"));
        String toolName = stringValue(query, "tool", "");
        McpToolLogStream.SuccessfulToolCall call =
                evidence == null ? null : evidence.successfulCall(toolName);
        if (!VISUALIZATION_DATA_TOOLS.contains(toolName)) {
            return;
        }
        if (call == null) {
            McpToolLogStream.FailedToolCall failure =
                    evidence == null ? null : evidence.failedCall(toolName);
            if (failure != null) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("action", "data_visualization.execute_approved_query");
                metadata.put("validationStatus", "blocked");
                metadata.put("validationMessage", failure.error());
                metadata.put("query", query);
                metadata.put("planId",
                        stringValue(workflow.getContext(), "planId", ""));
                parts.add(ChatMessagePart.builder()
                        .id("query-failure:" + workflow.getWorkflowId())
                        .type("confirm")
                        .title("真实数据查询失败")
                        .content("工具 " + toolName + " 调用失败，已保留原有产物快照。")
                        .status("blocked")
                        .metadata(metadata)
                        .build());
            }
            return;
        }
        Map<String, Object> toolResult = parseJsonObject(call.result());
        Map<String, Object> wrapped = mapValue(toolResult.get("data"));
        if (wrapped.containsKey("echarts")) {
            toolResult = wrapped;
        }
        Map<String, Object> echarts = mapValue(toolResult.get("echarts"));
        if (!(echarts.get("option") instanceof Map<?, ?>)) {
            return;
        }
        String planId = stringValue(workflow.getContext(), "planId", "");
        if (!StringUtils.hasText(planId)) {
            return;
        }
        String artifactId = "artifact:" + workflow.getWorkflowId() + ":" + planId;
        Map<String, Object> request = mapValue(query.get("request"));
        List<String> entities = visualizationRequestEntities(request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("artifactId", artifactId);
        metadata.put("planId", planId);
        metadata.put("action", "data_visualization.add_chart_library");
        metadata.put("query", query);
        metadata.put("entities", entities);
        metadata.put("fields", workflow.getContext().getOrDefault("fields", List.of()));
        metadata.put("chartType", firstNonBlank(
                stringValue(echarts, "chart_type", ""),
                stringValue(echarts, "chartType", ""),
                "chart"));
        metadata.put("validationStatus", "pending");
        ChatMessagePart preview = ChatMessagePart.builder()
                .id(artifactId)
                .type("visualization-chart-preview")
                .title(entities.isEmpty()
                        ? "真实数据图表" : entities.get(0) + " 数据图表")
                .content("平台已按批准方案执行 " + toolName
                        + "，以下预览直接使用该 MCP 返回的 ECharts 配置。")
                .status("completed")
                .metadata(metadata)
                .build();
        int persistConfirmationIndex = -1;
        for (int index = 0; index < parts.size(); index++) {
            ChatMessagePart existing = parts.get(index);
            if ("confirm".equals(existing.getType())
                    && existing.getMetadata() != null
                    && "data_visualization.apply_config".equals(
                    stringValue(existing.getMetadata(), "action", ""))) {
                persistConfirmationIndex = index;
                break;
            }
        }
        if (persistConfirmationIndex >= 0) {
            parts.add(persistConfirmationIndex, preview);
        } else {
            parts.add(preview);
        }
    }

    private void validateDataVisualizationParts(ChatSession chatSession,
                                                List<ChatMessagePart> parts,
                                                McpToolLogStream toolActivityStream) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        McpToolLogStream evidence = toolActivityStream == null
                ? McpToolLogStream.disabled() : toolActivityStream;
        boolean metaReady = VISUALIZATION_META_ENTITY_TOOLS.stream()
                .anyMatch(evidence::hasSuccessfulTool)
                && VISUALIZATION_META_ATTRIBUTE_TOOLS.stream()
                .anyMatch(evidence::hasSuccessfulTool);
        boolean visualizationSession = chatSession == null
                || !StringUtils.hasText(chatSession.getType())
                || DataVisualizationAgent.AGENT_TYPE.equals(chatSession.getType());
        for (ChatMessagePart part : parts) {
            Map<String, Object> raw = part.getMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(part.getMetadata());
            part.setMetadata(raw);
            String action = stringValue(raw, "action", "");
            if ("info-steps".equals(part.getType())
                    && visualizationSession
                    && isVisualizationEntitySelection(part, raw)) {
                validateVisualizationEntitySelection(part, raw, evidence);
                continue;
            }
            if ("confirm".equals(part.getType())
                    && "data_visualization.confirm_query_plan".equals(action)) {
                String metaValidationError = metaReady
                        ? normalizeVisualizationPlanMetadata(part, raw, evidence)
                        : "尚未成功调用实体与字段 Meta MCP，查询方案不能确认";
                boolean complete = metaReady
                        && !StringUtils.hasText(metaValidationError)
                        && StringUtils.hasText(stringValue(raw, "planId", ""))
                        && StringUtils.hasText(stringValue(raw, "entity", ""))
                        && hasSafeVisualizationQuery(raw);
                if (complete) {
                    raw.put("validationStatus", "success");
                    raw.put("metaVerified", true);
                } else {
                    blockVisualizationPart(part, raw,
                            StringUtils.hasText(metaValidationError)
                                    ? metaValidationError
                                    : "确认卡缺少planId或安全查询参数，查询方案不能确认");
                }
                continue;
            }
            if ("visualization-chart-record".equals(part.getType())) {
                blockVisualizationPart(
                        part,
                        raw,
                        "普通图表库记录只能由 add_to_library 工作流动作复制当前已验证预览，"
                                + "不能由 Agent 重新生成");
                continue;
            }
            if (Set.of(
                    "visualization-config-record",
                    "dashboard-config-record",
                    "menu-config-record").contains(part.getType())) {
                validateVisualizationPersistencePart(
                        chatSession, part, raw, evidence);
                continue;
            }
            if (!Set.of("visualization-chart-preview", "visualization-chart-record")
                    .contains(part.getType())) {
                continue;
            }
            if (!visualizationSession
                    && "visualization-chart-preview".equals(part.getType())) {
                validateSkillVisualizationChartArtifact(part, raw, evidence);
                continue;
            }
            validateVisualizationChartArtifact(chatSession, part, raw, evidence);
        }
    }

    private void validateSkillVisualizationChartArtifact(
            ChatMessagePart part,
            Map<String, Object> raw,
            McpToolLogStream evidence) {
        String source = stringValue(raw, "source", "");
        int separator = source.indexOf('.');
        String tool = separator > 0 ? source.substring(0, separator) : "";
        McpToolLogStream.SuccessfulToolCall call = evidence.successfulCall(tool);
        if (!StringUtils.hasText(tool) || call == null) {
            blockVisualizationPart(part, raw,
                    "图表 source 未指向本轮成功的 MCP 工具调用，图表不能验证");
            return;
        }
        if (StringUtils.hasText(stringValue(raw, "action", ""))) {
            blockVisualizationPart(part, raw,
                    "Skill 原生图表只允许临时预览，不能声明图表库写入动作");
            return;
        }

        Object echartsOption = raw.get("echartsOption");
        Map<String, Object> amisConfig = mapValue(raw.get("amisConfig"));
        Object amisOption = amisConfig.get("config");
        Map<String, Object> option = echartsOption instanceof Map<?, ?>
                ? new LinkedHashMap<>(mapValue(echartsOption))
                : amisOption instanceof Map<?, ?>
                ? new LinkedHashMap<>(mapValue(amisOption))
                : new LinkedHashMap<>();
        if (!(echartsOption instanceof Map<?, ?>) && !option.isEmpty()) {
            for (String key : List.of(
                    "title", "legend", "grid", "tooltip", "axisPointer",
                    "dataZoom", "visualMap", "dataset", "xAxis", "yAxis",
                    "polar", "radiusAxis", "angleAxis", "radar", "geo",
                    "calendar", "parallel", "parallelAxis", "singleAxis",
                    "series", "color", "animation")) {
                if (!option.containsKey(key) && amisConfig.containsKey(key)) {
                    option.put(key, amisConfig.get(key));
                }
            }
        }
        if (option.isEmpty()) {
            blockVisualizationPart(part, raw,
                    "Skill 原生图表缺少合法的 echartsOption");
            return;
        }
        if (echartsOption instanceof Map<?, ?>
                && amisOption instanceof Map<?, ?>
                && !echartsOption.equals(amisOption)) {
            blockVisualizationPart(part, raw,
                    "echartsOption 与 amisConfig.config 不一致，图表不能验证");
            return;
        }
        if (!(option.get("series") instanceof List<?> series) || series.isEmpty()) {
            blockVisualizationPart(part, raw,
                    "Skill 原生图表的 echartsOption.series 必须是非空数组");
            return;
        }

        raw.put("echartsOption", option);
        raw.put("amisConfig", Map.of("type", "chart", "config", option));
        raw.put("queriedAt", call.time() == null ? "" : call.time().toString());
        raw.put("validationStatus", "success");
        raw.put("validated", true);
        raw.remove("api");
        raw.remove("url");
        raw.remove("echarts");
        raw.remove("option");
    }

    private void validateVisualizationPersistencePart(
            ChatSession chatSession,
            ChatMessagePart part,
            Map<String, Object> raw,
            McpToolLogStream evidence) {
        AgentWorkflowState workflow = chatSession == null
                || workflowStateStore == null
                ? null
                : workflowStateStore.loadActive(
                chatSession, DataVisualizationAgent.AGENT_TYPE).orElse(null);
        if (workflow == null
                || workflow.getStep() != AgentWorkflowStep.PERSISTING) {
            blockVisualizationPart(
                    part,
                    raw,
                    "可视化写入记录没有处于已批准的 PERSISTING 工作流阶段");
            return;
        }
        Map<String, Object> workflowContext = workflow.getContext() == null
                ? Map.of() : workflow.getContext();
        Map<String, Object> persistenceCandidate = mapValue(
                workflowContext.get("persistenceCandidate"));
        Map<String, Object> persistencePlan = mapValue(
                workflowContext.get("persistencePlan"));
        if ("visualization-config-record".equals(part.getType())
                && !persistenceCandidate.isEmpty()) {
            Object applied = firstNonNull(
                    raw.get("appliedConfig"),
                    raw.get("expectedConfig"),
                    raw.get("config"));
            String lockedContent = stringValue(
                    persistenceCandidate, "content", "");
            if (applied == null
                    || !semanticContentEquals(applied, lockedContent)
                    || !matchesLockedPersistenceField(
                    persistencePlan, raw, "configType")
                    || !matchesLockedPersistenceField(
                    persistencePlan, raw, "configIndex")
                    || !matchesLockedPersistenceField(
                    persistencePlan, raw, "fileName")) {
                blockVisualizationPart(
                        part,
                        raw,
                        "可视化配置记录与用户批准后锁定的内容或目标文件不一致");
                return;
            }
        }

        boolean toolEvidenceReady;
        boolean readBackReady;
        switch (part.getType()) {
            case "visualization-config-record" -> {
                toolEvidenceReady = evidence.hasSuccessfulTool("config_apply")
                        || Boolean.TRUE.equals(
                        workflowContext.get("persistenceAlreadyApplied"));
                readBackReady = evidence.hasSuccessfulTool("config_read");
            }
            case "dashboard-config-record" -> {
                toolEvidenceReady = evidence.hasSuccessfulTool("dashboard_create")
                        || Boolean.TRUE.equals(
                        workflowContext.get("dashboardAlreadyApplied"));
                readBackReady = evidence.hasSuccessfulTool("dashboard_view");
                Map<String, Object> readBack = mapValue(
                        workflowContext.get("dashboardReadBack"));
                if (readBack.isEmpty()) {
                    blockVisualizationPart(
                            part, raw, "缺少平台锁定 Dashboard 的真实读回快照");
                    return;
                }
                raw.putAll(readBack);
            }
            case "menu-config-record" -> {
                toolEvidenceReady = evidence.hasSuccessfulTool("menu_create")
                        || Boolean.TRUE.equals(
                        workflowContext.get("menuAlreadyApplied"));
                readBackReady = evidence.hasSuccessfulTool("menu_view");
                Map<String, Object> readBack = mapValue(
                        workflowContext.get("menuReadBack"));
                if (readBack.isEmpty()) {
                    blockVisualizationPart(
                            part, raw, "缺少平台锁定 Menu 的真实读回快照");
                    return;
                }
                raw.putAll(readBack);
            }
            default -> {
                return;
            }
        }
        if (!toolEvidenceReady || !readBackReady) {
            blockVisualizationPart(
                    part,
                    raw,
                    "缺少当前轮次真实写入与读回 MCP 成功证据");
            return;
        }

        raw.put("workflowId", workflow.getWorkflowId());
        raw.put("source", "workflow");
        boolean present = switch (part.getType()) {
            case "visualization-config-record" ->
                    isVisualizationConfigRecordPresent(
                            buildVisualizationConfigRecord(part));
            case "dashboard-config-record" ->
                    isDashboardConfigRecordPresent(
                            buildDashboardConfigRecord(part));
            case "menu-config-record" ->
                    isMenuConfigRecordPresent(buildMenuConfigRecord(part));
            default -> false;
        };
        if (!present) {
            blockVisualizationPart(
                    part,
                    raw,
                    "写入后的服务端读回不存在或关键字段不一致");
            return;
        }
        raw.put("validationStatus", "success");
        raw.put("validated", true);
    }

    private boolean matchesLockedPersistenceField(
            Map<String, Object> persistencePlan,
            Map<String, Object> record,
            String field) {
        String locked = stringValue(persistencePlan, field, "");
        return !StringUtils.hasText(locked)
                || Objects.equals(locked, stringValue(record, field, ""));
    }

    private boolean isVisualizationEntitySelection(
            ChatMessagePart part,
            Map<String, Object> raw) {
        if ("data_visualization.select_entity_from_meta".equals(
                stringValue(raw, "action", ""))) {
            return true;
        }
        String title = StringUtils.hasText(part.getTitle()) ? part.getTitle() : "";
        if (title.contains("选择") && title.contains("实体")) {
            return true;
        }
        return listOfMaps(raw.get("steps")).stream().anyMatch(step -> {
            String id = stringValue(step, "id", "").toLowerCase(Locale.ROOT);
            String stepTitle = stringValue(step, "title", "");
            return id.contains("entity")
                    || (stepTitle.contains("选择") && stepTitle.contains("实体"))
                    || stepTitle.contains("分析实体");
        });
    }

    private void validateVisualizationEntitySelection(
            ChatMessagePart part,
            Map<String, Object> raw,
            McpToolLogStream evidence) {
        McpToolLogStream.SuccessfulToolCall entityCall =
                latestSuccessfulCall(evidence, VISUALIZATION_META_ENTITY_TOOLS);
        if (entityCall == null) {
            blockVisualizationEntitySelection(part, raw,
                    "尚未成功调用实体 Meta MCP，不能提供实体候选项");
            return;
        }
        Map<String, Object> payload = metaToolPayload(entityCall.result());
        List<Map<String, Object>> entityRows = firstNonEmptyListOfMaps(
                payload.get("entityList"),
                payload.get("entity_list"),
                payload.get("entities"),
                payload.get("datalist"),
                mapValue(payload.get("result")).get("rows"));
        Map<String, Map<String, Object>> entitiesByName = new LinkedHashMap<>();
        Map<String, String> namesByLabel = new LinkedHashMap<>();
        for (Map<String, Object> entity : entityRows) {
            String name = stringValue(entity, "name", "");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            entitiesByName.put(name, entity);
            String label = stringValue(entity, "label", "");
            if (StringUtils.hasText(label)) {
                namesByLabel.putIfAbsent(label, name);
            }
        }
        if (entitiesByName.isEmpty()) {
            blockVisualizationEntitySelection(part, raw,
                    "实体 Meta MCP 未返回可选择的逻辑实体");
            return;
        }

        List<Map<String, Object>> steps = listOfMaps(raw.get("steps"));
        Map<String, Object> entityStep = steps.stream()
                .filter(step -> {
                    String id = stringValue(step, "id", "").toLowerCase(Locale.ROOT);
                    return id.contains("entity")
                            || stringValue(step, "title", "").contains("实体");
                })
                .findFirst()
                .orElseGet(LinkedHashMap::new);
        LinkedHashSet<String> requestedNames = new LinkedHashSet<>();
        for (Object suggestion : listValue(entityStep.get("suggestions"))) {
            String value;
            if (suggestion instanceof String text) {
                value = text;
            } else {
                Map<String, Object> option = mapValue(suggestion);
                value = firstNonBlank(
                        stringValue(option, "value", ""),
                        stringValue(option, "name", ""),
                        stringValue(option, "label", ""));
            }
            if (entitiesByName.containsKey(value)) {
                requestedNames.add(value);
            } else if (namesByLabel.containsKey(value)) {
                requestedNames.add(namesByLabel.get(value));
            }
        }
        List<String> selectedNames = requestedNames.isEmpty()
                ? new ArrayList<>(entitiesByName.keySet())
                : new ArrayList<>(requestedNames);
        List<Map<String, Object>> verifiedSuggestions = selectedNames.stream()
                .limit(50)
                .map(name -> {
                    Map<String, Object> entity = entitiesByName.get(name);
                    String label = stringValue(entity, "label", name);
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("label", label + "（" + name + "）");
                    option.put("value", name);
                    String description = stringValue(entity, "description", "");
                    if (StringUtils.hasText(description)) {
                        option.put("description", description);
                    }
                    return option;
                })
                .toList();
        entityStep.put("id", StringUtils.hasText(stringValue(entityStep, "id", ""))
                ? stringValue(entityStep, "id", "") : "analysis_entity");
        entityStep.put("title", "选择实体");
        String metaToolDescription = "retrieval_list_display_entity".equals(entityCall.toolName())
                ? "获取展示用实体 Meta 列表"
                : "获取实体 Meta 列表";
        entityStep.put("description", "来源接口：" + entityCall.toolName()
                + "（" + metaToolDescription + "，调用成功）。"
                + "提交值为括号内的准确逻辑名称。");
        entityStep.put("required", true);
        entityStep.put("strictOptions", true);
        entityStep.put("suggestions", verifiedSuggestions);
        if (steps.isEmpty()) {
            steps = new ArrayList<>();
            steps.add(entityStep);
        } else {
            boolean replaced = false;
            List<Map<String, Object>> normalizedSteps = new ArrayList<>();
            for (Map<String, Object> step : steps) {
                String id = stringValue(step, "id", "").toLowerCase(Locale.ROOT);
                if (!replaced && (id.contains("entity")
                        || stringValue(step, "title", "").contains("实体"))) {
                    normalizedSteps.add(entityStep);
                    replaced = true;
                } else {
                    normalizedSteps.add(step);
                }
            }
            if (!replaced) {
                normalizedSteps.add(0, entityStep);
            }
            steps = normalizedSteps;
        }

        raw.put("action", "data_visualization.select_entity_from_meta");
        raw.put("steps", steps);
        raw.put("validationStatus", "success");
        raw.put("metaVerified", true);
        raw.put("metaTool", entityCall.toolName());
        raw.put("metaToolDescription", metaToolDescription);
        raw.put("metaEntities", verifiedSuggestions);
        part.setStatus("pending");
        part.setContent("数据来源：MCP 接口 " + entityCall.toolName()
                + "（" + metaToolDescription + "，调用成功）。"
                + "请选择该接口实际返回的逻辑实体；选项中的中文为标签，"
                + "括号内为后续查询使用的准确实体名称。");
    }

    private void blockVisualizationEntitySelection(
            ChatMessagePart part,
            Map<String, Object> raw,
            String message) {
        String action = stringValue(raw, "action", "");
        if (StringUtils.hasText(action)) {
            raw.put("blockedAction", action);
        }
        raw.remove("action");
        raw.put("steps", List.of());
        raw.put("validationStatus", "blocked");
        raw.put("validationMessage", message);
        part.setStatus("blocked");
        part.setContent(message + "。请重新执行实体 Meta 查询后再选择。");
    }

    private String normalizeVisualizationPlanMetadata(
            ChatMessagePart part,
            Map<String, Object> raw,
            McpToolLogStream evidence) {
        McpToolLogStream.SuccessfulToolCall entityCall =
                latestSuccessfulCall(evidence, VISUALIZATION_META_ENTITY_TOOLS);
        McpToolLogStream.SuccessfulToolCall attributeCall =
                latestSuccessfulCall(evidence, VISUALIZATION_META_ATTRIBUTE_TOOLS);
        if (entityCall == null || attributeCall == null) {
            return "尚未成功调用实体与字段 Meta MCP，查询方案不能确认";
        }
        Map<String, Object> entityPayload = metaToolPayload(entityCall.result());
        List<Map<String, Object>> entityRows = firstNonEmptyListOfMaps(
                entityPayload.get("entityList"),
                entityPayload.get("entity_list"),
                entityPayload.get("entities"),
                entityPayload.get("datalist"),
                mapValue(entityPayload.get("result")).get("rows"));
        Map<String, Map<String, Object>> entitiesByName = new LinkedHashMap<>();
        for (Map<String, Object> entity : entityRows) {
            String name = stringValue(entity, "name", "");
            if (StringUtils.hasText(name)) {
                entitiesByName.put(name, entity);
            }
        }
        if (entitiesByName.isEmpty()) {
            return "实体 Meta MCP 未返回可用的逻辑实体名称";
        }

        Map<String, Object> query = mapValue(raw.get("query"));
        Map<String, Object> request = mapValue(query.get("request"));
        List<String> requestedEntities = visualizationRequestEntities(request);
        if (requestedEntities.isEmpty()) {
            String fallbackEntity = stringValue(raw, "entity", "");
            if (StringUtils.hasText(fallbackEntity)) {
                requestedEntities = List.of(fallbackEntity);
            }
        }
        if (requestedEntities.isEmpty()) {
            return "查询方案未提供 Meta 逻辑实体名称";
        }
        for (String entity : requestedEntities) {
            if (!entitiesByName.containsKey(entity)) {
                return "实体 Meta MCP 返回结果中不存在逻辑实体：" + entity;
            }
        }
        String entityName = requestedEntities.get(0);
        Map<String, Object> entityMeta = entitiesByName.get(entityName);

        Map<String, Object> attributePayload = metaToolPayload(attributeCall.result());
        Map<String, Object> attributeArguments = parseJsonObject(attributeCall.arguments());
        String attributeEntity = firstNonBlank(
                stringValue(attributePayload, "entity", ""),
                stringValue(attributeArguments, "entity", ""));
        if (!entityName.equals(attributeEntity)) {
            return "字段 Meta MCP 查询的实体为"
                    + (StringUtils.hasText(attributeEntity) ? attributeEntity : "空")
                    + "，与查询方案实体" + entityName + "不一致";
        }
        List<Map<String, Object>> attributeRows = firstNonEmptyListOfMaps(
                attributePayload.get("attributeList"),
                attributePayload.get("attribute_list"),
                attributePayload.get("attributes"),
                attributePayload.get("datalist"),
                mapValue(attributePayload.get("result")).get("rows"));
        List<Map<String, Object>> selectedAttributeRows = firstNonEmptyListOfMaps(
                attributePayload.get("selectAttributeList"),
                attributePayload.get("select_attribute_list"));
        Map<String, Map<String, Object>> attributesByName = new LinkedHashMap<>();
        java.util.stream.Stream.concat(
                        attributeRows.stream(), selectedAttributeRows.stream())
                .forEach(attribute -> {
                    String name = stringValue(attribute, "name", "");
                    if (StringUtils.hasText(name)) {
                        attributesByName.putIfAbsent(name, attribute);
                    }
                });
        if (attributesByName.isEmpty()) {
            return "字段 Meta MCP 未返回实体" + entityName + "的逻辑字段名称";
        }

        List<Map<String, Object>> requestedFields = inferVisualizationPlanFields(request);
        List<Map<String, Object>> verifiedFields = new ArrayList<>();
        for (Map<String, Object> requestedField : requestedFields) {
            String fieldName = stringValue(requestedField, "field", "");
            Map<String, Object> attribute = attributesByName.get(fieldName);
            if (attribute == null) {
                return "字段 Meta MCP 返回结果中不存在实体"
                        + entityName + "的逻辑字段：" + fieldName;
            }
            Map<String, Object> verified = new LinkedHashMap<>();
            verified.put("field", fieldName);
            verified.put("label", stringValue(attribute, "label", fieldName));
            verified.put("role", stringValue(requestedField, "role", "field"));
            String description = stringValue(attribute, "description", "");
            if (StringUtils.hasText(description)) {
                verified.put("description", description);
            }
            verifiedFields.add(verified);
        }

        String entityLabel = stringValue(entityMeta, "label", entityName);
        raw.put("entity", entityName);
        raw.put("entities", requestedEntities);
        raw.put("entityLabel", entityLabel);
        raw.put("fields", verifiedFields);
        raw.put("metaSelection", Map.of(
                "entity", Map.of("name", entityName, "label", entityLabel),
                "fields", verifiedFields,
                "entityTool", entityCall.toolName(),
                "attributeTool", attributeCall.toolName()));
        query.put("request", request);
        raw.put("query", query);
        part.setContent(visualizationPlanContent(
                entityName, entityLabel, verifiedFields,
                stringValue(query, "tool", "")));
        return null;
    }

    private McpToolLogStream.SuccessfulToolCall latestSuccessfulCall(
            McpToolLogStream evidence,
            Set<String> toolNames) {
        return toolNames.stream()
                .map(evidence::successfulCall)
                .filter(Objects::nonNull)
                .max(java.util.Comparator.comparing(
                        McpToolLogStream.SuccessfulToolCall::time))
                .orElse(null);
    }

    private Map<String, Object> metaToolPayload(String result) {
        Map<String, Object> payload = parseJsonObject(result);
        Map<String, Object> data = mapValue(payload.get("data"));
        if (!data.isEmpty()) {
            return data;
        }
        return payload;
    }

    private List<String> visualizationRequestEntities(Map<String, Object> request) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        addVisualizationEntity(entities, stringValue(request, "entity", ""));
        for (Object value : listValue(request.get("entities"))) {
            if (value instanceof String entity) {
                addVisualizationEntity(entities, entity);
            }
        }
        for (String collection : List.of("mappings", "series", "display_list")) {
            for (Object value : listValue(request.get(collection))) {
                addVisualizationEntity(
                        entities, stringValue(mapValue(value), "entity", ""));
            }
        }
        return List.copyOf(entities);
    }

    private void addVisualizationEntity(Set<String> entities, String entity) {
        if (StringUtils.hasText(entity)) {
            entities.add(entity);
        }
    }

    private String visualizationPlanContent(
            String entityName,
            String entityLabel,
            List<Map<String, Object>> fields,
            String tool) {
        String fieldText = fields.isEmpty()
                ? "无显式字段（仅统计记录数）"
                : fields.stream()
                .map(field -> stringValue(field, "label",
                        stringValue(field, "field", ""))
                        + "（" + stringValue(field, "field", "")
                        + "，" + stringValue(field, "role", "field") + "）")
                .collect(java.util.stream.Collectors.joining("；"));
        return "实体：" + entityLabel + "（" + entityName + "）；字段："
                + fieldText + "；目标工具：" + tool
                + "。以上实体和字段名称均来自本轮 Meta MCP 返回结果，请确认后执行真实数据查询。";
    }

    private List<Map<String, Object>> inferVisualizationPlanFields(
            Map<String, Object> request) {
        List<Map<String, Object>> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addVisualizationPlanField(fields, seen,
                stringValue(request, "time_field", ""), "time");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "dimension", ""), "dimension");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "field", ""), "metric");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "x_field", ""), "x");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "y_field", ""), "y");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "size_field", ""), "size");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "category_field", ""), "category");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "label_field", ""), "label");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "sort_by", ""), "sort");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "source_field", ""), "source");
        addVisualizationPlanField(fields, seen,
                stringValue(request, "target_field", ""), "target");
        for (Object value : listValue(request.get("dimensions"))) {
            Map<String, Object> item = mapValue(value);
            String role = "TIME".equalsIgnoreCase(stringValue(item, "kind", ""))
                    ? "time" : "dimension";
            addVisualizationPlanField(fields, seen,
                    stringValue(item, "field", ""), role);
        }
        for (Object value : listValue(request.get("metrics"))) {
            addVisualizationPlanField(fields, seen,
                    stringValue(mapValue(value), "field", ""), "metric");
        }
        for (Object value : listValue(request.get("criteria_list"))) {
            Map<String, Object> criterion = mapValue(value);
            addVisualizationPlanField(fields, seen,
                    firstNonBlank(
                            stringValue(criterion, "attribute", ""),
                            stringValue(criterion, "field", ""),
                            stringValue(criterion, "name", ""),
                            stringValue(criterion, "key", "")),
                    "filter");
        }
        for (String collection : List.of("mappings", "series")) {
            for (Object value : listValue(request.get(collection))) {
                collectVisualizationNestedFields(
                        mapValue(value), fields, seen);
            }
        }
        for (Object value : listValue(request.get("display_list"))) {
            Map<String, Object> display = mapValue(value);
            for (Object attribute : listValue(firstNonNull(
                    display.get("attribute_list"),
                    display.get("attributeList")))) {
                if (attribute instanceof String name) {
                    addVisualizationPlanField(fields, seen, name, "detail");
                }
            }
        }
        return fields;
    }

    private void collectVisualizationNestedFields(
            Map<String, Object> item,
            List<Map<String, Object>> fields,
            Set<String> seen) {
        for (Map.Entry<String, String> field : Map.ofEntries(
                Map.entry("time_field", "time"),
                Map.entry("dimension", "dimension"),
                Map.entry("source_field", "source"),
                Map.entry("target_field", "target"),
                Map.entry("category_field", "category")).entrySet()) {
            addVisualizationPlanField(fields, seen,
                    stringValue(item, field.getKey(), ""), field.getValue());
        }
        for (Object matchField : listValue(item.get("match_fields"))) {
            if (matchField instanceof String name) {
                addVisualizationPlanField(fields, seen, name, "match");
            }
        }
        Map<String, Object> metric = mapValue(item.get("metric"));
        addVisualizationPlanField(fields, seen,
                stringValue(metric, "field", ""), "metric");
        for (Object criterionValue : listValue(item.get("criteria_list"))) {
            Map<String, Object> criterion = mapValue(criterionValue);
            addVisualizationPlanField(fields, seen,
                    stringValue(criterion, "attribute", ""), "filter");
        }
    }

    private void addVisualizationPlanField(List<Map<String, Object>> fields,
                                           Set<String> seen,
                                           String field,
                                           String role) {
        if (!StringUtils.hasText(field) || !seen.add(field)) {
            return;
        }
        fields.add(Map.of("field", field, "role", role));
    }

    private String firstString(Object value) {
        return listValue(value).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String firstMappingEntity(Object value) {
        return listValue(value).stream()
                .map(this::mapValue)
                .map(item -> stringValue(item, "entity", ""))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private void validateVisualizationChartArtifact(ChatSession chatSession,
                                                    ChatMessagePart part,
                                                    Map<String, Object> raw,
                                                    McpToolLogStream evidence) {
        String planId = stringValue(raw, "planId", "");
        Map<String, Object> query = mapValue(raw.get("query"));
        String tool = stringValue(query, "tool", "");
        Map<String, Object> approvedPlan = approvedVisualizationPlan(chatSession, planId);
        Map<String, Object> approvedQuery = mapValue(approvedPlan.get("query"));
        String approvedTool = stringValue(approvedQuery, "tool", "");
        Map<String, Object> approvedRequest = mapValue(approvedQuery.get("request"));
        McpToolLogStream.SuccessfulToolCall call = evidence.successfulCall(approvedTool);
        if (!StringUtils.hasText(planId)
                || approvedPlan.isEmpty()
                || !tool.equals(approvedTool)
                || !VISUALIZATION_DATA_TOOLS.contains(approvedTool)
                || approvedRequest.isEmpty()
                || call == null
                || !hasApprovedVisualizationPlan(chatSession, planId)) {
            blockVisualizationPart(part, raw,
                    "查询方案未批准或本轮数据 MCP 未成功，图表不能验证或入库");
            return;
        }

        Map<String, Object> arguments = parseJsonObject(call.arguments());
        Object request = arguments.containsKey("request")
                ? arguments.get("request") : arguments;
        if (!(request instanceof Map<?, ?>)
                || !approvedRequest.equals(mapValue(request))) {
            blockVisualizationPart(part, raw,
                    "实际数据 MCP 查询参数与已批准方案不一致，图表不能验证或入库");
            return;
        }

        Map<String, Object> toolResult = parseJsonObject(call.result());
        Map<String, Object> wrapped = mapValue(toolResult.get("data"));
        if (wrapped.containsKey("echarts")) {
            toolResult = wrapped;
        }
        Map<String, Object> echarts = mapValue(toolResult.get("echarts"));
        Object option = echarts.get("option");
        if (!(option instanceof Map<?, ?>)) {
            blockVisualizationPart(part, raw,
                    "数据 MCP 未返回可渲染的 echarts.option");
            return;
        }

        Map<String, Object> verifiedQuery = new LinkedHashMap<>();
        verifiedQuery.put("tool", approvedTool);
        verifiedQuery.put("request", request);
        raw.put("query", verifiedQuery);
        raw.put("queryMeta", mapValue(toolResult.get("meta")));
        raw.put("echartsOption", option);
        raw.put("amisConfig", Map.of("type", "chart", "config", option));
        raw.put("queriedAt", call.time().toString());
        raw.put("validationStatus", "success");
        raw.put("validated", true);
        raw.remove("api");
        raw.remove("url");
        raw.remove("echarts");
        raw.remove("option");
    }

    private boolean hasSafeVisualizationQuery(Map<String, Object> raw) {
        Map<String, Object> query = mapValue(raw.get("query"));
        String tool = stringValue(query, "tool", "");
        Map<String, Object> request = mapValue(query.get("request"));
        return VISUALIZATION_DATA_TOOLS.contains(tool)
                && !request.isEmpty();
    }

    private boolean hasApprovedVisualizationPlan(ChatSession chatSession, String planId) {
        return !approvedVisualizationPlan(chatSession, planId).isEmpty();
    }

    private Map<String, Object> approvedVisualizationPlan(ChatSession chatSession,
                                                           String planId) {
        if (chatSession == null || !StringUtils.hasText(chatSession.getMessages())) {
            return Map.of();
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    chatSession.getMessages(), new TypeReference<List<Message>>() {
                    });
            if (messages == null) {
                return Map.of();
            }
            return messages.stream()
                    .filter(Objects::nonNull)
                    .flatMap(message -> message.getParts() == null
                            ? java.util.stream.Stream.empty()
                            : message.getParts().stream())
                    .filter(part -> "confirm".equals(part.getType())
                            && "approved".equals(part.getStatus())
                            && part.getMetadata() != null
                            && "data_visualization.confirm_query_plan".equals(
                            stringValue(part.getMetadata(), "action", ""))
                            && planId.equals(stringValue(part.getMetadata(), "planId", "")))
                    .map(ChatMessagePart::getMetadata)
                    .findFirst()
                    .<Map<String, Object>>map(LinkedHashMap::new)
                    .orElseGet(Map::of);
        } catch (RuntimeException e) {
            log.warn("读取数据可视化查询方案状态失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private void blockVisualizationPart(ChatMessagePart part,
                                        Map<String, Object> raw,
                                        String message) {
        raw.put("validationStatus", "blocked");
        raw.put("validationMessage", message);
        String action = stringValue(raw, "action", "");
        if (StringUtils.hasText(action)) {
            raw.put("blockedAction", action);
        }
        raw.remove("action");
        part.setStatus("blocked");
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
        mergeSectionRecords(extraData, patch, "dataAnalysis", List.of(
                "records",
                "datasetRecords",
                "serviceResults",
                "reportTimeline"
        ));
        mergeSectionRecords(extraData, patch, "configuration", List.of("records"));
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
        // 仅供未装配独立报表存储的兼容性/测试环境使用；生产运行由
        // ReportDocumentService 写入独立文档、修订和归档表。
        if (reportDocumentService == null) {
            Map<String, Object> reportPatch = buildReportExtraDataPatch(parts);
            if (reportPatch != null && !reportPatch.isEmpty()) {
                patch.putAll(reportPatch);
            }
        }
        Map<String, Object> dataAnalysisPatch = buildDataAnalysisExtraDataPatch(parts);
        if (dataAnalysisPatch != null && !dataAnalysisPatch.isEmpty()) {
            patch.putAll(dataAnalysisPatch);
        }
        Map<String, Object> configurationPatch = buildConfigurationExtraDataPatch(parts);
        if (configurationPatch != null && !configurationPatch.isEmpty()) {
            patch.putAll(configurationPatch);
        }
        return patch.isEmpty() ? null : patch;
    }

    private Map<String, Object> buildConfigurationExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (ChatMessagePart part : parts) {
            if (!"config-record".equals(part.getType())) {
                continue;
            }
            Map<String, Object> record = buildConfigurationRecord(part);
            if (!record.isEmpty()) {
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            return null;
        }

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("records", records);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("configuration", configuration);
        return metadata;
    }

    private Map<String, Object> buildConfigurationRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        String id = stringValue(raw, "recordId", null);
        String changeDescription = stringValue(raw, "changeDescription", null);
        String changeMode = stringValue(raw, "changeMode", null);
        String configType = stringValue(raw, "configType", null);
        String fileName = stringValue(raw, "fileName", null);
        String format = stringValue(raw, "format", null);
        String updatedAt = stringValue(raw, "updatedAt", null);
        if (!StringUtils.hasText(id)
                || !StringUtils.hasText(changeDescription)
                || !Set.of("add", "modify").contains(changeMode)
                || !StringUtils.hasText(configType)
                || !StringUtils.hasText(fileName)
                || !StringUtils.hasText(format)
                || !raw.containsKey("oldConfig")
                || !raw.containsKey("newConfig")
                || !StringUtils.hasText(updatedAt)) {
            log.warn("忽略字段不完整的配置记录：{}", raw);
            return Map.of();
        }

        String validationStatus = stringValue(raw, "validationStatus", "unverified");
        if (!Set.of("unverified", "success", "failed", "blocked").contains(validationStatus)) {
            validationStatus = "unverified";
        }
        Object validationResult = raw.getOrDefault("validationResult", Map.of());
        Object applyResult = raw.getOrDefault("applyResult", Map.of());
        boolean requestedEffective = "yes".equals(stringValue(raw, "effectiveStatus", "no"));
        String effectiveStatus = requestedEffective
                && "success".equals(validationStatus)
                && hasVerifiedApplyResult(applyResult)
                ? "yes"
                : "no";

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordId", id);
        record.put("changeDescription", changeDescription);
        record.put("changeMode", changeMode);
        record.put("configType", configType);
        record.put("fileName", fileName);
        record.put("format", format.toLowerCase(Locale.ROOT));
        record.put("oldConfig", raw.get("oldConfig"));
        record.put("newConfig", raw.get("newConfig"));
        record.put("validationStatus", validationStatus);
        record.put("effectiveStatus", effectiveStatus);
        record.put("validationResult", validationResult);
        record.put("applyResult", applyResult);
        record.put("updatedAt", updatedAt);
        return record;
    }

    private boolean hasVerifiedApplyResult(Object applyResult) {
        Map<String, Object> result = mapValue(applyResult);
        String approvalStatus = stringValue(result, "approvalStatus", "");
        boolean approvalSucceeded = "approved".equals(approvalStatus);
        boolean writeSucceeded = Boolean.TRUE.equals(result.get("writeSucceeded"));
        boolean readBackMatched = Boolean.TRUE.equals(result.get("readBackMatched"));
        return approvalSucceeded && writeSucceeded && readBackMatched;
    }

    private Map<String, Object> buildDataAnalysisExtraDataPatch(List<ChatMessagePart> parts) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<Map<String, Object>> datasetRecords = new ArrayList<>();
        List<Map<String, Object>> serviceResults = new ArrayList<>();
        List<Map<String, Object>> reportTimeline = new ArrayList<>();
        for (ChatMessagePart part : parts) {
            if (!"data-analysis-record".equals(part.getType())) {
                continue;
            }
            Map<String, Object> record = buildDataAnalysisRecord(part);
            if (record.isEmpty()) {
                continue;
            }
            records.add(record);
            String stage = stringValue(record, "stage", "");
            Map<String, Object> raw = mapValue(record.get("raw"));
            if ("dataset_preparation".equals(stage)) {
                datasetRecords.addAll(extractDatasetRecords(raw));
            } else if ("service_analysis".equals(stage)) {
                serviceResults.add(buildServiceAnalysisResult(record, raw));
            } else if ("report_output".equals(stage)) {
                reportTimeline.addAll(extractReportTimeline(record, raw));
            }
        }
        if (records.isEmpty()) {
            return null;
        }

        Map<String, Object> dataAnalysis = new LinkedHashMap<>();
        dataAnalysis.put("records", records);
        if (!datasetRecords.isEmpty()) {
            dataAnalysis.put("datasetRecords", datasetRecords);
        }
        if (!serviceResults.isEmpty()) {
            dataAnalysis.put("serviceResults", serviceResults);
        }
        if (!reportTimeline.isEmpty()) {
            dataAnalysis.put("reportTimeline", reportTimeline);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("dataAnalysis", dataAnalysis);
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
                Map<String, Object> record = buildVisualizationChartRecord(part);
                if (isValidatedVisualizationChartRecord(record)) {
                    chartLibrary.add(record);
                } else {
                    log.warn("忽略未验证或缺少安全查询信息的图表库记录：{}",
                            part.getMetadata());
                }
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
        Map<String, Object> currentDocument = null;

        for (ChatMessagePart part : parts) {
            if (!isReportDocumentPart(part)) {
                continue;
            }
            Map<String, Object> document = buildReportDocumentRecord(part);
            currentDocument = document;
            Map<String, Object> summary = new LinkedHashMap<>(document);
            summary.remove("content");
            summary.remove("raw");
            documents.add(summary);
        }

        if (documents.isEmpty() && currentDocument == null) {
            return null;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        if (currentDocument != null) {
            report.put("currentDocument", currentDocument);
        }
        if (!documents.isEmpty()) {
            report.put("documents", documents);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("report", report);
        return metadata;
    }

    private Map<String, Object> buildDataAnalysisRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        String stage = stringValue(raw, "stage", null);
        String id = stringValue(raw, "recordId", null);
        if (!StringUtils.hasText(id)
                || !Set.of("dataset_preparation", "service_analysis", "report_output").contains(stage)
                || !hasRequiredDataAnalysisFields(stage, raw)) {
            log.warn("忽略字段不完整的数据分析记录：{}", raw);
            return Map.of();
        }
        String title = firstNonBlank(
                stringValue(raw, "title", null),
                part.getTitle(),
                defaultDataAnalysisStageTitle(stage)
        );

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordId", id);
        record.put("stage", stage);
        record.put("status", stringValue(raw, "status", "completed"));
        record.put("title", title);
        record.put("content", firstNonBlank(
                stringValue(raw, "content", null), part.getContent()
        ));
        record.put("startedAt", stringValue(raw, "startedAt", null));
        record.put("completedAt", stringValue(raw, "completedAt", null));
        record.put("analysisTarget", raw.get("analysisTarget"));
        record.put("datasetSummary", raw.get("datasetSummary"));
        record.put("datasetRecords", raw.get("datasetRecords"));
        record.put("serviceTaskId", raw.get("serviceTaskId"));
        record.put("analysisResult", raw.get("analysisResult"));
        record.put("timeline", raw.get("timeline"));
        record.put("toolNames", raw.getOrDefault("toolNames", List.of()));
        record.put("raw", raw);
        return record;
    }

    private boolean hasRequiredDataAnalysisFields(String stage, Map<String, Object> raw) {
        return switch (stage) {
            case "dataset_preparation" -> StringUtils.hasText(stringValue(raw, "analysisTarget", null))
                    && StringUtils.hasText(stringValue(raw, "datasetSummary", null))
                    && raw.get("datasetRecords") instanceof List<?> records
                    && !records.isEmpty();
            case "service_analysis" -> StringUtils.hasText(stringValue(raw, "serviceTaskId", null))
                    && hasCompleteAnalysisResult(raw.get("analysisResult"));
            case "report_output" -> hasCompleteReportTimeline(raw.get("timeline"));
            default -> false;
        };
    }

    private boolean hasCompleteAnalysisResult(Object value) {
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        return value != null;
    }

    private boolean hasCompleteReportTimeline(Object value) {
        List<Map<String, Object>> timeline = listOfMaps(value);
        if (timeline.size() != 3) {
            return false;
        }
        Set<String> titles = timeline.stream()
                .map(item -> stringValue(item, "title", ""))
                .collect(java.util.stream.Collectors.toSet());
        return titles.equals(Set.of("分析目标", "分析过程", "分析结论"))
                && timeline.stream().allMatch(item -> StringUtils.hasText(stringValue(item, "content", null)));
    }

    private String defaultDataAnalysisStageTitle(String stage) {
        return switch (StringUtils.hasText(stage) ? stage : "") {
            case "dataset_preparation" -> "数据集准备";
            case "service_analysis" -> "分析服务";
            case "report_output" -> "分析报告";
            default -> "数据分析记录";
        };
    }

    private List<Map<String, Object>> extractDatasetRecords(Map<String, Object> raw) {
        return listOfMaps(raw.get("datasetRecords"));
    }

    private Map<String, Object> buildServiceAnalysisResult(Map<String, Object> record, Map<String, Object> raw) {
        Object result = raw.get("analysisResult");
        Map<String, Object> serviceRecord = new LinkedHashMap<>();
        serviceRecord.put("serviceTaskId", stringValue(raw, "serviceTaskId", null));
        serviceRecord.put("status", stringValue(record, "status", "completed"));
        serviceRecord.put("title", firstNonBlank(stringValue(record, "title", null), "分析服务结果"));
        serviceRecord.put("completedAt", stringValue(record, "completedAt", ""));
        serviceRecord.put("analysisResult", result);
        return serviceRecord;
    }

    private List<Map<String, Object>> extractReportTimeline(Map<String, Object> record, Map<String, Object> raw) {
        List<Map<String, Object>> timeline = firstNonEmptyListOfMaps(raw.get("timeline"));
        return timeline;
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
        long parsedRevision = longValue(raw.get("revision"));
        long revision = parsedRevision > 0 ? parsedRevision : 1L;
        String version = firstNonBlank(stringValue(raw, "version", null), "v" + revision);
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
        record.put("revision", revision);
        record.put("version", version);
        record.put("status", stringValue(raw, "status", "generated"));
        record.put("source", "agent_report");
        record.put("updatedAt", updatedAt);
        record.put("content", part.getContent());
        record.put("outline", raw.getOrDefault("outline", List.of()));
        record.put("sourceAttachments", raw.getOrDefault("sourceAttachments", List.of()));
        record.put("sourceRefs", raw.getOrDefault("sourceRefs", List.of()));
        record.put("contentHash", stringValue(raw, "contentHash", reportContentHash(part.getContent())));
        return record;
    }

    private Map<String, Object> buildVisualizationChartRecord(ChatMessagePart part) {
        Map<String, Object> raw = part.getMetadata() == null ? Map.of() : part.getMetadata();
        Map<String, Object> record = baseVisualizationRecord(raw, part, "临时可视化图表");
        record.put("chartType", firstNonBlank(stringValue(raw, "chartType", null), stringValue(raw, "chart_type", null)));
        Object entities = raw.get("entities");
        String legacyEntity = firstNonBlank(stringValue(raw, "entity", null),
                stringValue(raw, "entityName", null));
        record.put("entities", entities instanceof List<?> ? entities
                : StringUtils.hasText(legacyEntity) ? List.of(legacyEntity) : List.of());
        record.put("entity", firstVisualizationEntity(record.get("entities")));
        record.put("fields", raw.getOrDefault("fields", List.of()));
        record.put("planId", stringValue(raw, "planId", null));
        Map<String, Object> query = mapValue(raw.get("query"));
        record.put("query", query);
        record.put("queryMeta", mapValue(raw.get("queryMeta")));
        Object echartsOption = firstNonNull(
                raw.get("echartsOption"),
                mapValue(raw.get("echarts")).get("option"),
                raw.get("option"));
        record.put("echartsOption", echartsOption);
        Object amisConfig = firstNonNull(raw.get("amisConfig"), raw.get("config"));
        record.put("amisConfig", amisConfig);
        record.put("api", visualizationRestPath(stringValue(query, "tool", "")));
        record.put("queriedAt", stringValue(raw, "queriedAt", null));
        record.put("validationStatus", stringValue(raw, "validationStatus", "unverified"));
        record.put("status", stringValue(raw, "status", "temporary"));
        record.put("source", stringValue(raw, "source", "session"));
        record.put("config", amisConfig == null ? Map.of() : amisConfig);
        record.put("raw", raw);
        return record;
    }

    private boolean isValidatedVisualizationChartRecord(Map<String, Object> record) {
        if ("demo".equals(stringValue(record, "source", ""))) {
            return StringUtils.hasText(stringValue(record, "id", ""))
                    && !mapValue(record.get("amisConfig")).isEmpty();
        }
        if (!"success".equals(stringValue(record, "validationStatus", ""))
                || !StringUtils.hasText(stringValue(record, "planId", ""))) {
            return false;
        }
        Map<String, Object> query = mapValue(record.get("query"));
        Map<String, Object> request = mapValue(query.get("request"));
        Map<String, Object> option = mapValue(record.get("echartsOption"));
        return VISUALIZATION_DATA_TOOLS.contains(stringValue(query, "tool", ""))
                && !request.isEmpty()
                && !option.isEmpty();
    }

    private String firstVisualizationEntity(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty() || list.get(0) == null) {
            return null;
        }
        return list.get(0).toString();
    }

    private String visualizationRestPath(String tool) {
        return switch (tool) {
            case "entity_overview" -> "/api/v1/entity/overview/query";
            case "entity_summary" -> "/api/v1/entity/summary/query";
            case "entity_trend" -> "/api/v1/entity/trend/query";
            case "entity_distribution" -> "/api/v1/entity/distribution/query";
            case "entity_aggregate" -> "/api/v1/entity/aggregate/query";
            case "entity_histogram" -> "/api/v1/entity/histogram/query";
            case "entity_scatter" -> "/api/v1/entity/scatter/query";
            case "entity_value_statistics" -> "/api/v1/entity/value-statistics/query";
            case "entity_relations" -> "/api/v1/entity/relations/query";
            case "entity_relation_timeline" -> "/api/v1/entity/relation-timeline/query";
            default -> null;
        };
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
        record.put("source", firstNonBlank(
                stringValue(raw, "source", null), "open_config"));
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
        record.put("source", firstNonBlank(
                stringValue(raw, "source", null), "dashboard"));
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
        record.put("source", firstNonBlank(
                stringValue(raw, "source", null), "menu"));
        record.put("sourceKey", firstNonBlank(
                stringValue(raw, "businessSource", null),
                stringValue(raw, "sourceKey", null),
                Set.of("demo", "workflow").contains(
                        stringValue(raw, "source", ""))
                        ? null : stringValue(raw, "source", null)));
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
            boolean exists = readWithRetries(
                    () -> configService.fileExistsInConfigPath(configType, fileName));
            if (!exists) {
                log.warn("忽略未验证的可视化配置记录：{}_config 中不存在文件 {}", configType, fileName);
                recordWorkflowReadBack("config", "missing");
                return false;
            }
            Map<String, Object> raw = mapValue(record.get("raw"));
            boolean workflowRecord = StringUtils.hasText(
                    stringValue(raw, "workflowId", ""));
            Object expected = firstNonNull(
                    raw.get("appliedConfig"),
                    raw.get("expectedConfig"),
                    raw.get("config"));
            if (workflowRecord && expected == null) {
                log.warn("忽略新版工作流可视化配置记录：缺少 appliedConfig 读回基准，record={}", record);
                recordWorkflowReadBack("config", "mismatch");
                return false;
            }
            if (expected != null) {
                String actual = readWithRetries(
                        () -> configService.readFile(configType, fileName));
                if (!semanticContentEquals(expected, actual)) {
                    log.warn("忽略读回不一致的可视化配置记录：{}/{}", configType, fileName);
                    recordWorkflowReadBack("config", "mismatch");
                    return false;
                }
            }
            recordWorkflowReadBack("config", "success");
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的可视化配置记录：校验 {}/{} 失败: {}", configType, fileName, e.getMessage(), e);
            recordWorkflowReadBack("config", "failed");
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
                    DashboardVo dashboard = readWithRetries(
                            () -> dashboardService.info(Long.parseLong(dashboardId)));
                    if (dashboardMatches(record, dashboard)) {
                        recordWorkflowReadBack("dashboard", "success");
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("数据看板记录 dashboardId 不是数字，将继续按 code/name 校验：{}", dashboardId);
                }
            }
            List<DashboardVo> dashboards = readWithRetries(dashboardService::findAll);
            boolean matched = dashboards != null && dashboards.stream().anyMatch(item ->
                    dashboardMatches(record, item)
                            && ((StringUtils.hasText(code) && code.equals(item.getCode()))
                            || (StringUtils.hasText(name) && name.equals(item.getName()))));
            if (!matched) {
                log.warn("忽略未验证的数据看板记录：未找到 dashboardId/code/name 对应看板，record={}", record);
            }
            recordWorkflowReadBack("dashboard", matched ? "success" : "mismatch");
            return matched;
        } catch (Exception e) {
            log.warn("忽略未验证的数据看板记录：校验失败: {}", e.getMessage(), e);
            recordWorkflowReadBack("dashboard", "failed");
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
                    MenuVo menu = readWithRetries(
                            () -> menuService.info(Long.parseLong(menuId)));
                    if (menuMatches(record, menu)) {
                        recordWorkflowReadBack("menu", "success");
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("菜单配置记录 menuId 不是数字，将继续按 name/source 校验：{}", menuId);
                }
            }
            List<MenuVo> menus = readWithRetries(menuService::findAll);
            boolean matched = menus != null && menus.stream().anyMatch(item ->
                    menuMatches(record, item)
                            && ((StringUtils.hasText(name) && name.equals(item.getName()))
                            || (StringUtils.hasText(source) && source.equals(item.getSource()))));
            if (!matched) {
                log.warn("忽略未验证的菜单配置记录：未找到 menuId/name/source 对应菜单，record={}", record);
            }
            recordWorkflowReadBack("menu", matched ? "success" : "mismatch");
            return matched;
        } catch (Exception e) {
            log.warn("忽略未验证的菜单配置记录：校验失败: {}", e.getMessage(), e);
            recordWorkflowReadBack("menu", "failed");
            return false;
        }
    }

    private void recordWorkflowReadBack(String objectType, String status) {
        if (workflowMetrics != null) {
            workflowMetrics.readBack(objectType, status);
        }
    }

    private boolean dashboardMatches(
            Map<String, Object> record,
            DashboardVo dashboard) {
        if (dashboard == null) {
            return false;
        }
        return matchesIfPresent(stringValue(record, "name", ""), dashboard.getName())
                && matchesIfPresent(stringValue(record, "code", ""), dashboard.getCode())
                && matchesIfPresent(stringValue(record, "dashboardType", ""),
                dashboard.getType() == null ? "" : dashboard.getType().name())
                && matchesIfPresent(stringValue(record, "configIndex", ""),
                dashboard.getConfigIndex())
                && matchesIfPresent(stringValue(record, "url", ""), dashboard.getUrl())
                && matchesIfPresent(stringValue(record, "htmlPath", ""),
                dashboard.getHtmlPath());
    }

    private boolean menuMatches(
            Map<String, Object> record,
            MenuVo menu) {
        if (menu == null) {
            return false;
        }
        return matchesIfPresent(stringValue(record, "name", ""), menu.getName())
                && matchesIfPresent(stringValue(record, "menuType", ""),
                menu.getType() == null ? "" : menu.getType().name())
                && matchesIfPresent(stringValue(record, "route", ""), menu.getRoute())
                && matchesIfPresent(stringValue(record, "params", ""), menu.getParams())
                && matchesIfPresent(stringValue(record, "sourceKey", ""), menu.getSource());
    }

    private boolean matchesIfPresent(String expected, String actual) {
        return !StringUtils.hasText(expected) || Objects.equals(expected, actual);
    }

    private boolean semanticContentEquals(Object expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode expectedNode =
                    expected instanceof String text
                            ? JacksonConfig.OBJECT_MAPPER.readTree(text)
                            : JacksonConfig.OBJECT_MAPPER.valueToTree(expected);
            com.fasterxml.jackson.databind.JsonNode actualNode =
                    JacksonConfig.OBJECT_MAPPER.readTree(actual);
            return expectedNode != null && expectedNode.equals(actualNode);
        } catch (Exception ignored) {
            String expectedText = expected instanceof String text
                    ? text : JacksonUtil.toJson(expected);
            return expectedText != null
                    && expectedText.trim().equals(actual.trim());
        }
    }

    private <T> T readWithRetries(java.util.function.Supplier<T> readOperation) {
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return readOperation.get();
            } catch (RuntimeException e) {
                lastError = e;
            }
        }
        throw lastError == null
                ? new IllegalStateException("读回校验失败") : lastError;
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
                stringValue(record, "recordId", null),
                stringValue(record, "serviceTaskId", null),
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
                    stringValue(item, "recordId", null),
                    stringValue(item, "serviceTaskId", null),
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
        record.put("source", firstNonBlank(
                stringValue(metadata, "source", null), "message"));
        record.put("validationStatus",
                stringValue(metadata, "validationStatus", ""));
        record.put("content", part.getContent());
        if (!config.isEmpty()) {
            record.put("config", config);
        }
        record.put("raw", metadata);
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
            if ("workflow".equals(stringValue(record, "source", ""))
                    && !"success".equals(
                    stringValue(record, "validationStatus", ""))) {
                log.warn("忽略普通工作流元数据配置记录：缺少成功校验状态，fileName={}", fileName);
                return false;
            }
            if ("workflow".equals(stringValue(record, "source", ""))) {
                Object expected = record.get("config");
                if (expected == null || !semanticContentEquals(expected, content)) {
                    log.warn("忽略普通工作流元数据配置记录：读回内容不一致，fileName={}", fileName);
                    recordWorkflowReadBack("meta_config", "mismatch");
                    return false;
                }
            }
            recordWorkflowReadBack("meta_config", "success");
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的元数据配置记录：校验文件 {} 失败: {}", fileName, e.getMessage(), e);
            recordWorkflowReadBack("meta_config", "failed");
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
        record.put("source", firstNonBlank(
                stringValue(raw, "source", null), "vectum"));
        record.put("validationStatus",
                stringValue(raw, "validationStatus", ""));
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
            if ("workflow".equals(stringValue(record, "source", ""))) {
                if (!"success".equals(
                        stringValue(record, "validationStatus", ""))
                        || !"running".equalsIgnoreCase(
                        stringValue(record, "status", ""))) {
                    log.warn("忽略普通工作流数据推送服务记录：缺少成功校验状态或 running 状态");
                    return false;
                }
                String expectedConfig = stringValue(record, "config", "");
                boolean matched = tasks.stream().anyMatch(task ->
                        task != null
                                && "SYSTEM".equals(task.getSource())
                                && sourceMark.equals(task.getMark())
                                && "running".equalsIgnoreCase(task.getStatus())
                                && (!StringUtils.hasText(expectedConfig)
                                || Objects.equals(expectedConfig.trim(),
                                Objects.toString(task.getConfig(), "").trim())));
                if (!matched) {
                    log.warn("忽略普通工作流数据推送服务记录：任务读回状态或配置不一致");
                    recordWorkflowReadBack("push_task", "mismatch");
                    return false;
                }
            }
            recordWorkflowReadBack("push_task", "success");
            return true;
        } catch (Exception e) {
            log.warn("忽略未验证的数据推送服务记录：校验 sourceMark={} 失败: {}", sourceMark, e.getMessage(), e);
            recordWorkflowReadBack("push_task", "failed");
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

        private final Map<String, String> pendingArguments;

        private final Map<String, SuccessfulToolCall> successfulCalls;

        private final Map<String, FailedToolCall> failedCalls;

        private final String turnId;

        private McpToolLogStream(Sinks.Many<String> sink,
                                 Map<String, McpApprovalVo> approvalStates,
                                 Map<String, Integer> approvalOffsets,
                                 Map<String, String> pendingArguments,
                                 Map<String, SuccessfulToolCall> successfulCalls,
                                 Map<String, FailedToolCall> failedCalls,
                                 String turnId) {
            this.sink = sink;
            this.approvalStates = approvalStates;
            this.approvalOffsets = approvalOffsets;
            this.pendingArguments = pendingArguments;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.turnId = turnId;
        }

        private static McpToolLogStream create() {
            return create(UUID.randomUUID().toString());
        }

        private static McpToolLogStream create(String turnId) {
            return new McpToolLogStream(Sinks.many().multicast().onBackpressureBuffer(),
                    new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>(), turnId);
        }

        private static McpToolLogStream disabled() {
            return new McpToolLogStream(
                    null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null);
        }

        private String turnId() {
            return turnId;
        }

        private List<Map<String, Object>> evidenceRefs() {
            if (successfulCalls.isEmpty()) {
                return List.of();
            }
            return successfulCalls.values().stream()
                    .sorted(java.util.Comparator.comparing(
                            SuccessfulToolCall::time,
                            java.util.Comparator.nullsLast(
                                    java.util.Comparator.naturalOrder())))
                    .map(call -> {
                        Map<String, Object> evidence = new LinkedHashMap<>();
                        evidence.put("evidenceId", firstNonBlankStatic(
                                turnId, "turn") + ":" + call.toolName());
                        evidence.put("tool", call.toolName());
                        evidence.put("status", "succeeded");
                        evidence.put("argumentsDigest",
                                Integer.toHexString(Objects.hashCode(call.arguments())));
                        evidence.put("argumentsSummary", summarizeStatic(call.arguments()));
                        evidence.put("resultDigest",
                                Integer.toHexString(Objects.hashCode(call.result())));
                        evidence.put("resultSummary", summarizeStatic(call.result()));
                        evidence.put("turnId", turnId);
                        evidence.put("recordedAt",
                                call.time() == null ? null : call.time().toString());
                        return evidence;
                    })
                    .toList();
        }

        private static String firstNonBlankStatic(String value, String fallback) {
            return StringUtils.hasText(value) ? value : fallback;
        }

        private static String summarizeStatic(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            String normalized = value.replaceAll("\\s+", " ").trim();
            return normalized.length() <= 1000
                    ? normalized : normalized.substring(0, 1000) + "…";
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
            if ("started".equals(logEvent.status())) {
                pendingArguments.put(logEvent.toolName(),
                        StringUtils.hasText(logEvent.rawArguments())
                                ? logEvent.rawArguments() : logEvent.arguments());
            } else if ("succeeded".equals(logEvent.status())) {
                failedCalls.remove(logEvent.toolName());
                successfulCalls.put(logEvent.toolName(), new SuccessfulToolCall(
                        logEvent.toolName(),
                        pendingArguments.remove(logEvent.toolName()),
                        StringUtils.hasText(logEvent.rawResult())
                                ? logEvent.rawResult() : logEvent.result(),
                        logEvent.time()));
            } else if ("failed".equals(logEvent.status())) {
                failedCalls.put(logEvent.toolName(), new FailedToolCall(
                        logEvent.toolName(),
                        pendingArguments.remove(logEvent.toolName()),
                        logEvent.error(),
                        logEvent.time()));
            }
            sink.tryEmitNext(formatLog(logEvent));
        }

        private boolean hasSuccessfulTool(String toolName) {
            return successfulCalls.containsKey(toolName);
        }

        private SuccessfulToolCall successfulCall(String toolName) {
            return successfulCalls.get(toolName);
        }

        private FailedToolCall failedCall(String toolName) {
            return failedCalls.get(toolName);
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
            metadata.put(
                    "sessionApprovalAllowed",
                    approval.getSessionApprovalAllowed());
            metadata.put("arguments", approval.getArguments());
            metadata.put("result", approval.getResult());
            metadata.put("resultLength", approval.getResultLength());
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

        private record SuccessfulToolCall(String toolName,
                                          String arguments,
                                          String result,
                                          java.time.Instant time) {
        }

        private record FailedToolCall(String toolName,
                                      String arguments,
                                      String error,
                                      java.time.Instant time) {
        }
    }
}
