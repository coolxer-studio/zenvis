package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.SuggestDto;
import com.coolxer.model.dih.dto.ChatActionDecisionDto;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.WorkflowActionDto;
import com.coolxer.model.dih.dto.WorkflowTelemetryDto;
import com.coolxer.model.dih.vo.WorkflowActionVo;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AIGeneralCompleteService;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihChatApplicationService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.workflow.WorkflowActionService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * AI对答聊天服务
 */

@RestController
@RequestMapping("/api/v1/dih")
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String DECISION_APPROVED = "approved";
    private static final String DECISION_REJECTED = "rejected";
    private static final String DECISION_APPLY_CONFIG = "apply_config";
    private static final String DECISION_ABANDON = "abandon";
    private static final String DECISION_REVISE = "revise";
    private static final String DECISION_SUBMITTED = "submitted";

    @Autowired
    private DihChatApplicationService dihChatApplicationService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatAttachmentService chatAttachmentService;

    @Autowired
    private AIBaseService baseService;

    @Autowired
    private AIGeneralCompleteService completeService;

    @Autowired
    private WorkflowActionService workflowActionService;


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
        boolean eventStream = dihChatApplicationService.isEventStream(chatDto);
        response.setCharacterEncoding("UTF-8");
        if (eventStream) {
            response.setContentType("application/x-ndjson;charset=UTF-8");
        }
        return dihChatApplicationService.chat(chatDto, getSessionUser());
    }

    /**
     * 获取建议
     */
    @PostMapping(value = "/suggest")
    @Operation(summary = "补全建议", description = "补全建议")
    public ResponseWrap<String> suggest(@RequestBody SuggestDto suggestDto) {
        try {
            if (suggestDto == null || !StringUtils.hasText(suggestDto.getCurrentLine()) || suggestDto.getCurrentLine().length() < 2) {
                return ResponseWrap.success("AI暂无可用建议");
            }
            String currentLine = suggestDto.getCurrentLine().substring(0, suggestDto.getCurrentLine().length() - 2);
            String context = StringUtils.hasText(suggestDto.getContent())
                    ? suggestDto.getContent().replace(suggestDto.getCurrentLine(), currentLine)
                    : currentLine;
            String prompt = "上下文：%s\n当前行：%s\n".formatted(context, currentLine);
            String suggest = completeService.complete(prompt);
            return ResponseWrap.success(suggest);
        } catch (Exception e) {
            log.warn("生成补全建议失败: {}", e.getMessage(), e);
        }
        return ResponseWrap.success("AI暂无可用建议");
    }

    @GetMapping("/model/list")
    public ResponseWrap<List<Map<String, String>>> modelList() {
        List<Map<String, String>> models = baseService.getModels();
        if (models.isEmpty()) {
            return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
        }
        return ResponseWrap.success(models);
    }

    @GetMapping("/health")
    public ResponseWrap<String> health() {
        return ResponseWrap.success("is running......");
    }

    @PostMapping("/upload")
    @Operation(summary = "上传聊天附件")
    public ResponseWrap<ChatAttachment> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chatId", required = false) String chatId) {
        try {
            return ResponseWrap.success(chatAttachmentService.upload(file, getSessionUser(), chatId));
        } catch (IllegalArgumentException e) {
            return ResponseWrap.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("上传聊天附件失败: {}", e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/upload/{fileId}/status")
    @Operation(summary = "查询聊天附件解析状态")
    public ResponseWrap<ChatAttachment> attachmentStatus(@PathVariable("fileId") String fileId) {
        try {
            Optional<ChatAttachment> status = chatAttachmentService.status(fileId, getSessionUser());
            return status.map(ResponseWrap::success)
                    .orElseGet(() -> ResponseWrap.fail(404, "附件不存在或无权访问"));
        } catch (Exception e) {
            log.error("查询附件状态失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/upload/{fileId}/reparse")
    @Operation(summary = "重新解析聊天附件")
    public ResponseWrap<ChatAttachment> reparseAttachment(@PathVariable("fileId") String fileId) {
        try {
            Optional<ChatAttachment> status = chatAttachmentService.reparse(fileId, getSessionUser());
            return status.map(ResponseWrap::success)
                    .orElseGet(() -> ResponseWrap.fail(404, "附件不存在或无权访问"));
        } catch (Exception e) {
            log.error("重新解析附件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @DeleteMapping("/upload/{fileId}")
    @Operation(summary = "删除聊天附件")
    public ResponseWrap<String> deleteAttachment(@PathVariable("fileId") String fileId) {
        try {
            return chatAttachmentService.deleteAttachment(fileId, getSessionUser())
                    ? ResponseWrap.success("删除成功")
                    : ResponseWrap.fail(404, "附件不存在或无权访问");
        } catch (Exception e) {
            log.error("删除附件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/upload/{fileId}/download")
    @Operation(summary = "鉴权下载聊天附件")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable("fileId") String fileId) {
        try {
            Optional<ChatAttachmentService.AttachmentStream> attachment =
                    chatAttachmentService.openAttachment(fileId, getSessionUser());
            if (attachment.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            ChatAttachmentService.AttachmentStream stream = attachment.get();
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (StringUtils.hasText(stream.contentType())) {
                try {
                    mediaType = MediaType.parseMediaType(stream.contentType());
                } catch (IllegalArgumentException ignored) {
                }
            }
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(stream.fileName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(stream.size())
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new InputStreamResource(stream.inputStream()));
        } catch (Exception e) {
            log.error("下载聊天附件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/upload/{fileId}/preview")
    @Operation(summary = "预览聊天图片附件")
    public ResponseEntity<Resource> previewAttachment(@PathVariable("fileId") String fileId) {
        try {
            Optional<ChatAttachmentService.AttachmentStream> attachment =
                    chatAttachmentService.openAttachment(fileId, getSessionUser());
            if (attachment.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            ChatAttachmentService.AttachmentStream stream = attachment.get();
            String contentType = stream.contentType();
            if (!ChatAttachmentService.isPreviewableImageContentType(contentType)) {
                stream.close();
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(stream.size())
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + stream.fileName() + "\"")
                    .body(new InputStreamResource(stream.inputStream()));
        } catch (Exception e) {
            log.error("预览聊天附件失败: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/chat/action-decision")
    @Operation(summary = "记录聊天动作确认结果")
    public ResponseWrap<?> actionDecision(@Valid @RequestBody ChatActionDecisionDto decisionDto) {
        String decision = decisionDto.getDecision();
        if (!isSupportedDecision(decision)) {
            return ResponseWrap.fail(400, "决策值只支持 approved、rejected、dispose、ignore、continue、apply_config、abandon、revise 或 submitted");
        }

        try {
            User currentUser = getSessionUser();
            ChatSession chatSession = chatSessionService.getChatSessionBySessionId(decisionDto.getChatId(), currentUser);
            if (chatSession == null) {
                return ResponseWrap.fail(404, "会话不存在");
            }

            List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new TypeReference<List<Message>>() {
            });
            boolean updated = updateDecisionPart(messages, decisionDto);

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

    @PostMapping("/chat/workflow/action")
    @Operation(summary = "执行精确匹配的聊天工作流动作")
    public ResponseWrap<WorkflowActionVo> workflowAction(
            @Valid @RequestBody WorkflowActionDto workflowActionDto) {
        try {
            return ResponseWrap.success(
                    workflowActionService.handle(workflowActionDto, getSessionUser()));
        } catch (ApiException e) {
            return ResponseWrap.fail(e);
        } catch (Exception e) {
            log.error("执行聊天工作流动作失败: {}", e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/chat/workflow/telemetry")
    @Operation(summary = "记录聊天工作流客户端遥测")
    public ResponseWrap<String> workflowTelemetry(
            @Valid @RequestBody WorkflowTelemetryDto telemetryDto) {
        try {
            workflowActionService.recordTelemetry(telemetryDto, getSessionUser());
            return ResponseWrap.success("记录成功");
        } catch (ApiException e) {
            return ResponseWrap.fail(e);
        } catch (Exception e) {
            log.error("记录聊天工作流客户端遥测失败: {}", e.getMessage(), e);
            return ResponseWrap.fail(e);
        }
    }

    private boolean isPlaceholderBuiltinAgent(String chatType) {
        return false;
    }

    private boolean isSupportedDecision(String decision) {
        return DECISION_APPROVED.equals(decision)
                || DECISION_REJECTED.equals(decision)
                || DECISION_APPLY_CONFIG.equals(decision)
                || DECISION_ABANDON.equals(decision)
                || DECISION_REVISE.equals(decision)
                || DECISION_SUBMITTED.equals(decision);
    }

    private boolean isDecisionPart(ChatMessagePart part) {
        return "confirm".equals(part.getType())
                || "info-steps".equals(part.getType())
                || "data-access-decision".equals(part.getType());
    }

    private boolean updateDecisionPart(List<Message> messages, ChatActionDecisionDto decisionDto) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        String expectedType = expectedDecisionPartType(decisionDto.getDecision());
        ChatMessagePart matchedPart = findDecisionPart(messages, decisionDto, expectedType, true, true);
        if (matchedPart == null) {
            matchedPart = findDecisionPart(messages, decisionDto, expectedType, false, true);
        }
        if (matchedPart == null) {
            matchedPart = findDecisionPart(messages, decisionDto, expectedType, false, false);
        }
        if (matchedPart == null) {
            log.warn("确认项不存在: chatId={}, messageId={}, partId={}, decision={}",
                    decisionDto.getChatId(),
                    decisionDto.getMessageId(),
                    decisionDto.getPartId(),
                    decisionDto.getDecision());
            return false;
        }
        if (matchedPart.getMetadata() != null
                && StringUtils.hasText(String.valueOf(
                matchedPart.getMetadata().getOrDefault("workflowId", "")))) {
            log.warn("拒绝使用旧动作接口修改共享工作流卡片: chatId={}, messageId={}, partId={}",
                    decisionDto.getChatId(),
                    decisionDto.getMessageId(),
                    decisionDto.getPartId());
            return false;
        }
        matchedPart.setStatus(decisionDto.getDecision());
        return true;
    }

    private ChatMessagePart findDecisionPart(List<Message> messages,
                                             ChatActionDecisionDto decisionDto,
                                             String expectedType,
                                             boolean requireMessageId,
                                             boolean requirePartId) {
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            Message message = messages.get(messageIndex);
            if (requireMessageId && !Objects.equals(message.getId(), decisionDto.getMessageId())) {
                continue;
            }
            if (message.getParts() == null || message.getParts().isEmpty()) {
                continue;
            }
            for (int partIndex = message.getParts().size() - 1; partIndex >= 0; partIndex--) {
                ChatMessagePart part = message.getParts().get(partIndex);
                if (!isDecisionPart(part)) {
                    continue;
                }
                if (expectedType != null && !expectedType.equals(part.getType())) {
                    continue;
                }
                if (requirePartId && !Objects.equals(part.getId(), decisionDto.getPartId())) {
                    continue;
                }
                if (!requirePartId && part.getStatus() != null && !"pending".equals(part.getStatus())) {
                    continue;
                }
                return part;
            }
        }
        return null;
    }

    private String expectedDecisionPartType(String decision) {
        if (DECISION_APPLY_CONFIG.equals(decision)
                || DECISION_ABANDON.equals(decision)
                || DECISION_REVISE.equals(decision)) {
            return "data-access-decision";
        }
        if (DECISION_APPROVED.equals(decision) || DECISION_REJECTED.equals(decision)) {
            return "confirm";
        }
        if (DECISION_SUBMITTED.equals(decision)) {
            return "info-steps";
        }
        return null;
    }
}
