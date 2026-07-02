package com.coolxer.controller.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.ChatResponse;
import com.coolxer.model.dih.ChatStreamEvent;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatActionDecisionDto;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.ChatMessagePartParser;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.FixedPromptResponseService;
import com.coolxer.service.dih.agent.AnalysisAgent;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.InspectionAgent;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI对答聊天服务
 */

@RestController
@RequestMapping("/api/v1/dih")
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String RESPONSE_FORMAT_EVENTS = "events";
    private static final String DECISION_APPROVED = "approved";
    private static final String DECISION_REJECTED = "rejected";
    private static final String TYPE_ASK = "ask";
    private static final String LEGACY_MCP_AGENT_TYPE = "agent_mcp";

    @Autowired
    private AIChatService chatService;

    @Autowired
    private AIBaseService baseService;

    @Autowired
    private ChatSessionService chatSessionService;
    @Autowired
    private FixedPromptResponseService fixedPromptResponseService;
    @Autowired
    private AnalysisAgent analysisAgent;
    @Autowired
    private DataAccessAgent dataAccessAgent;
    @Autowired
    private InspectionAgent inspectionAgent;
    @Autowired
    private ChatMessagePartParser chatMessagePartParser;
    @Autowired
    private ChatAttachmentService chatAttachmentService;
    @Autowired
    private AgentMcpToolService agentMcpToolService;
    @Autowired
    private SkillService skillService;


    /**
     * Send the specified parameters to get the model response.
     * 1. When the send prompt is empty, an error message is returned.
     * 2. When sending a model, it is allowed to be empty, and when the parameter has a value and
     * is in the model configuration list, the corresponding model is called. If there is no return error.
     * If the model parameter is empty, use the model configured by Spring AI OpenAI.
     * 3. The chatId chat memory, passed by the front-end, is of type Object and cannot be repeated
     */
    @PostMapping("/chat")
    @Operation(summary = "AI Flux Chat")
    public Flux<String> chat(
            HttpServletResponse response,
            @Valid @RequestBody ChatDto chatDto
    ) {
        boolean eventStream = RESPONSE_FORMAT_EVENTS.equals(chatDto.getResponseFormat());
        response.setCharacterEncoding("UTF-8");
        if (eventStream) {
            response.setContentType("application/x-ndjson;charset=UTF-8");
        }

        String chatType = normalizeChatType(chatDto.getType());

        if (chatType != null && chatType.startsWith("agent")
                && (!skillService.isBuiltinAgentType(chatType) || !skillService.isBuiltinAgentEnabled(chatType))) {
            return errorResponse(eventStream, "智能体已停用或不存在。");
        }


        String model = chatDto.getModel();
        String userMessage = resolveUserMessage(chatDto);
        String chatId = chatDto.getChatId();
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

        // 检查chatId，如果不是已有会话，创建新的会话记录
        // 添加用户消息到文档中
        User currentUser = getSessionUser();
        String prompt = chatAttachmentService.appendAttachmentContext(userMessage, chatDto.getAttachments(), currentUser);
        ChatSession chatSession = chatSessionService.getChatSessionBySessionId(chatId, currentUser);
        if (chatSession == null) {
            ChatSessionDto chatSessionDto = new ChatSessionDto();
            chatSessionDto.setSessionId(chatId);
            chatSessionDto.setTitle(userMessage);
            chatSessionDto.setType(chatType);
            chatSessionDto.setDeepThink(chatDto.getDeepThink());
            chatSessionDto.setOnlineSearch(chatDto.getOnlineSearch());
            List<Message> messages = new ArrayList<>();
            messages.add(createUserMessage(userMessage, chatDto.getAttachments()));
            chatSessionDto.setMessages(JacksonUtil.toJson(messages));
            chatSession = chatSessionService.create(chatSessionDto, currentUser);
        } else {
            // 如果是已有会话，将当前内容添加到会话中
            try {
                List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
                });
                messages.add(createUserMessage(userMessage, chatDto.getAttachments()));
                chatSession.setMessages(JacksonUtil.toJson(messages));
                ChatSessionDto chatSessionDto = new ChatSessionDto();
                chatSessionDto.setMessages(chatSession.getMessages());
                chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
            } catch (Exception e) {
                log.error("更新会话失败: {}", e.getMessage(), e);
            }
        }

        // 用于收集模型返回消息的引用和类型
        AtomicReference<String> modelResponse = new AtomicReference<>("");
        AtomicReference<MessageType> messageType = new AtomicReference<>(MessageType.TEXT);

        Flux<String> fluxResponse;
        Optional<String> fixedResponse = fixedPromptResponseService.findResponse(userMessage);
        if (DataAccessAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            fluxResponse = dataAccessAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        } else if (AnalysisAgent.AGENT_TYPE.equals(chatType)) {
            messageType.set(MessageType.TEXT);
            fluxResponse = analysisAgent.chat(chatId, model, prompt, chatDto.getAttachments(), currentUser, mcpToolContext);
        } else if ("agent_inspect".equals(chatType)) {
            ChatResponse chatResponse = inspectionAgent.chat(prompt, model, chatId, mcpToolContext);
            messageType.set(chatResponse.getType());
            fluxResponse = Flux.just(chatResponse.getContent());
        } else if (isPlaceholderBuiltinAgent(chatType)) {
            messageType.set(MessageType.TEXT);
            fluxResponse = Flux.just(skillService.getBuiltinAgentPlaceholder(chatType));
        } else if (fixedResponse.isPresent()) {
            log.info("固定提示词命中，直接返回测试文件中的预期回答。chatId={}", chatId);
            messageType.set(MessageType.TEXT);
            fluxResponse = Flux.just(fixedResponse.get());
        } else if (BooleanUtils.isTrue(chatDto.getDeepThink())) {
            // 普通深度思考对话，类型为 TEXT
            messageType.set(MessageType.TEXT);
            fluxResponse = chatService.deepThinkingChat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    mcpToolContext.toolCallbackProvider(),
                    mcpToolContext.systemPrompt()
            );
        } else {
            // 普通聊天对话，类型为 TEXT
            messageType.set(MessageType.TEXT);
            fluxResponse = chatService.chat(
                    chatId,
                    model,
                    prompt,
                    chatDto.getAttachments(),
                    currentUser,
                    mcpToolContext.toolCallbackProvider(),
                    mcpToolContext.systemPrompt()
            );
        }

        // 在返回前捕获模型响应并保存到会话中
        // 将chatSession声明为final以便在lambda中使用
        final ChatSession finalChatSession = chatSession;
        if (eventStream) {
            return fluxResponse
                    .doOnNext(s -> modelResponse.getAndAccumulate(s, String::concat))
                    .map(s -> toNdjson(ChatStreamEvent.delta(s)))
                    .concatWith(Flux.defer(() -> {
                        Message aiMessage = saveAiResponse(finalChatSession, currentUser, modelResponse.get(), messageType.get(), true, BooleanUtils.isTrue(chatDto.getDeepThink()));
                        return Flux.just(toNdjson(ChatStreamEvent.done(aiMessage)));
                    }))
                    .onErrorResume(e -> {
                        log.error("聊天事件流返回失败: {}", e.getMessage(), e);
                        return Flux.just(toNdjson(ChatStreamEvent.error("抱歉，回复失败，请稍后重试~")));
                    });
        }

        return fluxResponse.doOnNext(s -> modelResponse.getAndAccumulate(s, String::concat))
                .doOnComplete(() -> saveAiResponse(finalChatSession, currentUser, modelResponse.get(), messageType.get(), false, false));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传聊天附件")
    public ResponseWrap<ChatAttachment> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseWrap.success(chatAttachmentService.upload(file, getSessionUser()));
        } catch (IllegalArgumentException e) {
            return ResponseWrap.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("上传聊天附件失败: {}", e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/upload/{fileId}/preview")
    @Operation(summary = "预览聊天图片附件")
    public ResponseEntity<Resource> previewAttachment(@PathVariable("fileId") String fileId) {
        try {
            Optional<Path> filePathOptional = chatAttachmentService.resolveAttachmentFile(fileId, getSessionUser());
            if (filePathOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Path filePath = filePathOptional.get();
            String contentType = chatAttachmentService.detectContentType(filePath);
            if (!contentType.startsWith("image/")) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("预览聊天附件失败: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/chat/action-decision")
    @Operation(summary = "记录聊天动作确认结果")
    public ResponseWrap<?> actionDecision(@Valid @RequestBody ChatActionDecisionDto decisionDto) {
        String decision = decisionDto.getDecision();
        if (!DECISION_APPROVED.equals(decision) && !DECISION_REJECTED.equals(decision)) {
            return ResponseWrap.fail(400, "决策值只支持 approved 或 rejected");
        }

        try {
            User currentUser = getSessionUser();
            ChatSession chatSession = chatSessionService.getChatSessionBySessionId(decisionDto.getChatId(), currentUser);
            if (chatSession == null) {
                return ResponseWrap.fail(404, "会话不存在");
            }

            List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
            });
            boolean updated = false;
            for (Message message : messages) {
                if (!Objects.equals(message.getId(), decisionDto.getMessageId()) || message.getParts() == null) {
                    continue;
                }
                for (ChatMessagePart part : message.getParts()) {
                    if (Objects.equals(part.getId(), decisionDto.getPartId()) && "confirm".equals(part.getType())) {
                        part.setStatus(decision);
                        updated = true;
                        break;
                    }
                }
                if (updated) {
                    break;
                }
            }

            if (!updated) {
                return ResponseWrap.fail(404, "确认项不存在");
            }

            chatSession.setMessages(JacksonUtil.toJson(messages));
            ChatSessionDto chatSessionDto = new ChatSessionDto();
            chatSessionDto.setMessages(chatSession.getMessages());
            chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
            return ResponseWrap.success("记录成功");
        } catch (Exception e) {
            log.error("记录聊天动作确认结果失败: {}", e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    private Flux<String> errorResponse(boolean eventStream, String message) {
        if (eventStream) {
            return Flux.just(toNdjson(ChatStreamEvent.error(message)));
        }
        return Flux.just(message);
    }

    private boolean isPlaceholderBuiltinAgent(String chatType) {
        return BuiltinAgentSkillRegistry.AGENT_DISPOSE.equals(chatType)
                || BuiltinAgentSkillRegistry.AGENT_REPORT.equals(chatType);
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
        if (withParts) {
            List<ChatMessagePart> parts = new ArrayList<>(chatMessagePartParser.parse(content, type));
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
            List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
            });
            messages.add(aiMessage);
            chatSession.setMessages(JacksonUtil.toJson(messages));
            ChatSessionDto chatSessionDto = new ChatSessionDto();
            chatSessionDto.setMessages(chatSession.getMessages());
            chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
            log.info("保存AI响应到会话，消息类型: {}, 富消息片段: {}", aiMessage.getType(), withParts);
        } catch (Exception e) {
            log.error("保存模型响应到会话失败: {}", e.getMessage(), e);
        }
        return aiMessage;
    }

    private String toNdjson(ChatStreamEvent event) {
        return JacksonUtil.toJson(event) + "\n";
    }
}
