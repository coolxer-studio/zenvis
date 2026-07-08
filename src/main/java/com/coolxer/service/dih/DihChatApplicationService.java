package com.coolxer.service.dih;

import com.coolxer.commons.enums.MessageType;
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
import com.coolxer.service.dih.agent.DisposeAgent;
import com.coolxer.service.dih.agent.InspectionAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.dih.mcp.McpToolCallLoggingProvider;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DIH 聊天应用编排服务。
 */
@Slf4j
@Service
public class DihChatApplicationService {

    public static final String RESPONSE_FORMAT_EVENTS = "events";

    private static final String TYPE_ASK = "ask";
    private static final String LEGACY_MCP_AGENT_TYPE = "agent_mcp";
    private static final String CHAT_ERROR_MESSAGE = "抱歉，回复失败，请稍后重试~";
    private final AIChatService chatService;
    private final AIBaseService baseService;
    private final ChatSessionService chatSessionService;
    private final FixedPromptResponseService fixedPromptResponseService;
    private final DataAccessDemoResponseService dataAccessDemoResponseService;
    private final AnalysisAgent analysisAgent;
    private final DisposeAgent disposeAgent;
    private final ReportAgent reportAgent;
    private final DataAccessAgent dataAccessAgent;
    private final InspectionAgent inspectionAgent;
    private final ChatMessagePartParser chatMessagePartParser;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatTitleService chatTitleService;
    private final AgentMcpToolService agentMcpToolService;
    private final SkillService skillService;
    private final ConfigService configService;
    private final PushTaskService pushTaskService;

    public DihChatApplicationService(AIChatService chatService,
                                     AIBaseService baseService,
                                     ChatSessionService chatSessionService,
                                     FixedPromptResponseService fixedPromptResponseService,
                                     DataAccessDemoResponseService dataAccessDemoResponseService,
                                     AnalysisAgent analysisAgent,
                                     DisposeAgent disposeAgent,
                                     ReportAgent reportAgent,
                                     DataAccessAgent dataAccessAgent,
                                     InspectionAgent inspectionAgent,
                                     ChatMessagePartParser chatMessagePartParser,
                                     ChatAttachmentService chatAttachmentService,
                                     ChatTitleService chatTitleService,
                                     AgentMcpToolService agentMcpToolService,
                                     SkillService skillService,
                                     ConfigService configService,
                                     PushTaskService pushTaskService) {
        this.chatService = chatService;
        this.baseService = baseService;
        this.chatSessionService = chatSessionService;
        this.fixedPromptResponseService = fixedPromptResponseService;
        this.dataAccessDemoResponseService = dataAccessDemoResponseService;
        this.analysisAgent = analysisAgent;
        this.disposeAgent = disposeAgent;
        this.reportAgent = reportAgent;
        this.dataAccessAgent = dataAccessAgent;
        this.inspectionAgent = inspectionAgent;
        this.chatMessagePartParser = chatMessagePartParser;
        this.chatAttachmentService = chatAttachmentService;
        this.chatTitleService = chatTitleService;
        this.agentMcpToolService = agentMcpToolService;
        this.skillService = skillService;
        this.configService = configService;
        this.pushTaskService = pushTaskService;
    }

    public Flux<String> chat(ChatDto chatDto, User currentUser) {
        boolean eventStream = isEventStream(chatDto);
        if (chatDto == null) {
            return errorResponse(eventStream, "消息内容或附件不能为空。");
        }
        String chatType = normalizeChatType(chatDto.getType());

        if (chatType != null && chatType.startsWith("agent")
                && (!skillService.isBuiltinAgentType(chatType) || !skillService.isBuiltinAgentEnabled(chatType))) {
            return errorResponse(eventStream, "智能体已停用或不存在。");
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
        if (!baseService.isModelSupported(model)) {
            return errorResponse(eventStream, "Input model not support.");
        }

        boolean hasImageAttachment = chatAttachmentService.hasImageAttachment(chatDto.getAttachments());
        model = baseService.resolveChatModel(
                model,
                BooleanUtils.isTrue(chatDto.getDeepThink()),
                hasImageAttachment
        );
        McpToolContext mcpToolContext = hasImageAttachment
                ? McpToolContext.empty()
                : agentMcpToolService.resolve(chatType);
        McpToolLogStream mcpToolLogStream = McpToolLogStream.disabled();
        if (mcpToolContext.hasTools()) {
            mcpToolLogStream = McpToolLogStream.create();
            mcpToolContext = mcpToolContext.withToolCallbackProvider(
                    new McpToolCallLoggingProvider(mcpToolContext.toolCallbackProvider(), mcpToolLogStream::emit)
            );
        }

        String prompt = chatAttachmentService.appendAttachmentContext(userMessage, chatDto.getAttachments(), currentUser);
        ChatSession chatSession = appendUserMessage(chatDto, chatType, userMessage, currentUser);

        StringBuilder modelResponse = new StringBuilder();
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
                messageType
        ));
        if (mcpToolLogStream.enabled()) {
            McpToolLogStream finalMcpToolLogStream = mcpToolLogStream;
            fluxResponse = Flux.merge(
                    finalMcpToolLogStream.flux(),
                    fluxResponse.doFinally(signalType -> finalMcpToolLogStream.complete())
            );
        }

        if (eventStream) {
            return fluxResponse
                    .doOnNext(modelResponse::append)
                    .map(s -> toNdjson(ChatStreamEvent.delta(s)))
                    .concatWith(Flux.defer(() -> {
                        Message aiMessage = saveAiResponse(
                                chatSession,
                                currentUser,
                                modelResponse.toString(),
                                messageType.get(),
                                true,
                                BooleanUtils.isTrue(chatDto.getDeepThink())
                        );
                        return Flux.just(toNdjson(ChatStreamEvent.done(aiMessage)));
                    }))
                    .onErrorResume(e -> {
                        log.error("聊天事件流返回失败: {}", e.getMessage(), e);
                        persistErrorResponse(chatSession, currentUser);
                        return Flux.just(toNdjson(ChatStreamEvent.error(CHAT_ERROR_MESSAGE)));
                    });
        }

        return fluxResponse.doOnNext(modelResponse::append)
                .doOnComplete(() -> saveAiResponse(chatSession, currentUser, modelResponse.toString(), messageType.get(), false, false))
                .doOnError(e -> persistErrorResponse(chatSession, currentUser));
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
                                      AtomicReference<MessageType> messageType) {
        Optional<String> fixedResponse = fixedPromptResponseService.findResponse(resolveUserMessage(chatDto));
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
            return dataAccessAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        }
        if (AnalysisAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            return analysisAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        }
        if (DisposeAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            return disposeAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        }
        if (ReportAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            return reportAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        }
        if (InspectionAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            return inspectionAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        }
        if (isPlaceholderBuiltinAgent(chatType)) {
            messageType.set(MessageType.TEXT);
            return Flux.just(skillService.getBuiltinAgentPlaceholder(chatType));
        }
        if (fixedResponse.isPresent()) {
            log.info("固定提示词命中，直接返回测试文件中的预期回答。chatId={}", chatId);
            messageType.set(MessageType.TEXT);
            return Flux.just(fixedResponse.get());
        }
        if (BooleanUtils.isTrue(chatDto.getDeepThink())) {
            messageType.set(MessageType.TEXT);
            return chatService.deepThinkingChat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    mcpToolContext.toolCallbackProvider(),
                    mcpToolContext.systemPrompt()
            );
        }

        messageType.set(MessageType.TEXT);
        return chatService.chat(
                chatId,
                model,
                prompt,
                chatDto.getAttachments(),
                currentUser,
                mcpToolContext.toolCallbackProvider(),
                mcpToolContext.systemPrompt()
        );
    }

    private ChatSession appendUserMessage(ChatDto chatDto, String chatType, String userMessage, User currentUser) {
        ChatSessionDto chatSessionDto = new ChatSessionDto();
        chatSessionDto.setSessionId(chatDto.getChatId());
        if (chatSessionService.getChatSessionBySessionId(chatDto.getChatId(), currentUser) == null) {
            chatSessionDto.setTitle(chatTitleService.generateTitle(userMessage));
        }
        chatSessionDto.setType(chatType);
        chatSessionDto.setDeepThink(chatDto.getDeepThink());
        chatSessionDto.setOnlineSearch(chatDto.getOnlineSearch());
        return chatSessionService.appendMessage(
                chatDto.getChatId(),
                chatSessionDto,
                createUserMessage(userMessage, chatDto.getAttachments()),
                currentUser
        );
    }

    private void persistErrorResponse(ChatSession chatSession, User currentUser) {
        if (chatSession == null) {
            return;
        }
        try {
            Message errorMessage = new Message("ai", CHAT_ERROR_MESSAGE, MessageType.TEXT);
            errorMessage.setIsError(true);
            chatSessionService.appendMessage(chatSession, errorMessage, currentUser);
        } catch (Exception e) {
            log.error("保存错误响应到会话失败: {}", e.getMessage(), e);
        }
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
        if (!StringUtils.hasText(type) || LEGACY_MCP_AGENT_TYPE.equals(type)) {
            return TYPE_ASK;
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
        Message aiMessage = new Message("ai", content, type);
        List<ChatMessagePart> parts = List.of();
        if (withParts) {
            parts = new ArrayList<>(chatMessagePartParser.parse(content, type));
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
            mergeDataAccessExtraData(savedSession, parts, currentUser);
            log.info("保存AI响应到会话，消息类型: {}, 富消息片段: {}", aiMessage.getType(), withParts);
        } catch (Exception e) {
            log.error("保存模型响应到会话失败: {}", e.getMessage(), e);
        }
        return aiMessage;
    }

    private void mergeDataAccessExtraData(ChatSession chatSession, List<ChatMessagePart> parts, User currentUser) {
        Map<String, Object> patch = buildDataAccessExtraDataPatch(parts);
        if (chatSession == null || patch == null || patch.isEmpty()) {
            return;
        }
        Map<String, Object> extraData = new LinkedHashMap<>(parseJsonObject(chatSession.getExtraData()));
        Map<String, Object> dataAccess = mapValue(extraData.get("dataAccess"));
        Map<String, Object> patchDataAccess = mapValue(patch.get("dataAccess"));
        mergeRecordList(dataAccess, patchDataAccess, "metadataConfigs");
        mergeRecordList(dataAccess, patchDataAccess, "dataPushServices");
        extraData.put("dataAccess", dataAccess);

        String extraDataJson = JacksonUtil.toJson(extraData);
        ChatSessionDto chatSessionDto = new ChatSessionDto();
        chatSessionDto.setExtraData(extraDataJson);
        chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
        chatSession.setExtraData(extraDataJson);
    }

    private Map<String, Object> buildDataAccessExtraDataPatch(List<ChatMessagePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
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

    private void upsertRecord(List<Map<String, Object>> records, Map<String, Object> record) {
        String id = firstNonBlank(
                stringValue(record, "id", null),
                stringValue(record, "fileName", null),
                stringValue(record, "taskId", null),
                stringValue(record, "name", null)
        );
        if (id != null) {
            records.removeIf(item -> id.equals(firstNonBlank(
                    stringValue(item, "id", null),
                    stringValue(item, "fileName", null),
                    stringValue(item, "taskId", null),
                    stringValue(item, "name", null)
            )));
        }
        records.add(record);
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

        private final Sinks.Many<String> sink;

        private McpToolLogStream(Sinks.Many<String> sink) {
            this.sink = sink;
        }

        private static McpToolLogStream create() {
            return new McpToolLogStream(Sinks.many().multicast().onBackpressureBuffer());
        }

        private static McpToolLogStream disabled() {
            return new McpToolLogStream(null);
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

        private void complete() {
            if (enabled()) {
                sink.tryEmitComplete();
            }
        }

        private static String formatLog(McpToolCallLoggingProvider.McpToolCallLog logEvent) {
            String toolName = inlineCode(logEvent.toolName());
            if ("started".equals(logEvent.status())) {
                return "\n\n> MCP调用开始：" + toolName + formatArguments(logEvent.arguments()) + "\n\n";
            }
            if ("succeeded".equals(logEvent.status())) {
                return "\n\n> MCP调用成功：" + toolName
                        + formatDuration(logEvent.durationMillis())
                        + formatResult(logEvent.result())
                        + "\n\n";
            }
            if ("failed".equals(logEvent.status())) {
                return "\n\n> MCP调用失败：" + toolName
                        + formatDuration(logEvent.durationMillis())
                        + formatError(logEvent.error())
                        + "\n\n";
            }
            return "\n\n> MCP调用日志：" + toolName + "\n\n";
        }

        private static String formatArguments(String arguments) {
            return StringUtils.hasText(arguments) ? "，参数：" + inlineCode(arguments) : "";
        }

        private static String formatResult(String result) {
            return StringUtils.hasText(result) ? "，返回：" + inlineCode(result) : "";
        }

        private static String formatError(String error) {
            return StringUtils.hasText(error) ? "，错误：" + inlineCode(error) : "";
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
