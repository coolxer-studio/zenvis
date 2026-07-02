package com.coolxer.controller.dih;

import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.ChatAttachment;
import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatActionDecisionDto;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.service.dih.ChatAttachmentService;
import com.coolxer.service.dih.DihChatApplicationService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Path;
import java.time.Duration;

/**
 * AI对答聊天服务
 */

@RestController
@RequestMapping("/api/v1/dih")
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String DECISION_APPROVED = "approved";
    private static final String DECISION_REJECTED = "rejected";

    @Autowired
    private DihChatApplicationService dihChatApplicationService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatAttachmentService chatAttachmentService;


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

    private boolean isPlaceholderBuiltinAgent(String chatType) {
        return false;
    }
}
