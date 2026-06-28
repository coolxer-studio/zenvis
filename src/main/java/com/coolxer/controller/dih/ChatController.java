package com.coolxer.controller.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.controller.BaseController;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.ChatResponse;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.dto.ChatDto;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AIChatService;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.service.dih.FixedPromptResponseService;
import com.coolxer.service.dih.agent.InspectionAgent;
import com.coolxer.utils.JacksonUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI对答聊天服务
 */

@RestController
@RequestMapping("/api/v1/dih")
public class ChatController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AIChatService chatService;

    @Autowired
    private AIBaseService baseService;

    @Autowired
    private ChatSessionService chatSessionService;
    @Autowired
    private FixedPromptResponseService fixedPromptResponseService;
    @Autowired
    private InspectionAgent inspectionAgent;


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
        // TODO 临时限制ask之外的不允许使用
        if (chatDto.getType() != null && chatDto.getType().startsWith("agent") && !"agent_inspect".equals(chatDto.getType())) {
            return Flux.just("对不起，当前智能体没有开通权限，请联系管理员！");
        }


        List<Map<String, String>> models = baseService.getModels();
        List<String> modelName = models.stream()
                .map(map -> map.get("model"))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        String model = chatDto.getModel();
        String prompt = chatDto.getMessage();
        String chatId = chatDto.getChatId();
        if (StringUtils.hasText(model)) {
            if (!modelName.contains(model)) {
                return Flux.just("Input model not support.");
            } else if ("auto".equals(model)) {
                // 使用配置中的默认模型
                model = null;
            } else if ("x-sage-v1".equals(model)) {
                // TODO 以后再添加自己的模型
                model = null;
            }
        } else {
            model = null;
        }

        // 检查chatId，如果不是已有会话，创建新的会话记录
        // 添加用户消息到文档中
        User currentUser = getSessionUser();
        ChatSession chatSession = chatSessionService.getChatSessionBySessionId(chatId, currentUser);
        if (chatSession == null) {
            ChatSessionDto chatSessionDto = new ChatSessionDto();
            chatSessionDto.setSessionId(chatId);
            chatSessionDto.setTitle(chatDto.getMessage());
            chatSessionDto.setType(chatDto.getType());
            chatSessionDto.setDeepThink(chatDto.getDeepThink());
            chatSessionDto.setOnlineSearch(chatDto.getOnlineSearch());
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", chatDto.getMessage()));
            chatSessionDto.setMessages(JacksonUtil.toJson(messages));
            chatSession = chatSessionService.create(chatSessionDto, currentUser);
        } else {
            // 如果是已有会话，将当前内容添加到会话中
            try {
                List<Message> messages = JacksonUtil.toList(chatSession.getMessages(), new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {
                });
                messages.add(new Message("user", chatDto.getMessage()));
                chatSession.setMessages(JacksonUtil.toJson(messages));
                ChatSessionDto chatSessionDto = new ChatSessionDto();
                chatSessionDto.setMessages(chatSession.getMessages());
                chatSessionService.update((long) chatSession.getId(), chatSessionDto, currentUser);
            } catch (Exception e) {
                log.error("更新会话失败: {}", e.getMessage(), e);
            }
        }

        response.setCharacterEncoding("UTF-8");

        // 用于收集模型返回消息的引用和类型
        AtomicReference<String> modelResponse = new AtomicReference<>("");
        AtomicReference<MessageType> messageType = new AtomicReference<>(MessageType.TEXT);

        Flux<String> fluxResponse;
        Optional<String> fixedResponse = fixedPromptResponseService.findResponse(prompt);
        if (fixedResponse.isPresent()) {
            log.info("固定提示词命中，直接返回测试文件中的预期回答。chatId={}", chatId);
            messageType.set(MessageType.TEXT);
            fluxResponse = Flux.just(fixedResponse.get());
        } else if ("agent_inspect".equals(chatDto.getType())) {
            ChatResponse chatResponse = inspectionAgent.chat(chatDto.getMessage(), model, chatId);
            messageType.set(chatResponse.getType());
            fluxResponse = Flux.just(chatResponse.getContent());
        } else if (BooleanUtils.isTrue(chatDto.getDeepThink())) {
            // 普通深度思考对话，类型为 TEXT
            messageType.set(MessageType.TEXT);
            fluxResponse = chatService.deepThinkingChat(chatId, model, prompt);
        } else {
            // 普通聊天对话，类型为 TEXT
            messageType.set(MessageType.TEXT);
            fluxResponse = chatService.chat(chatId, model, prompt);
        }

        // 在返回前捕获模型响应并保存到会话中
        // 将chatSession声明为final以便在lambda中使用
        final ChatSession finalChatSession = chatSession;
        return fluxResponse.doOnNext(s -> modelResponse.getAndAccumulate(s, String::concat))
                .doOnComplete(() -> {
                    // 当流完成时，将模型响应保存到会话中
                    if (finalChatSession == null) {
                        return;
                    }
                    try {
                        List<Message> messages = JacksonUtil.toList(finalChatSession.getMessages(), new com.fasterxml.jackson.core.type.TypeReference<List<Message>>() {
                        });
                        // 根据agent返回的类型或普通文本创建消息
                        Message aiMessage = new Message("ai", modelResponse.get(), messageType.get());
                        messages.add(aiMessage);
                        finalChatSession.setMessages(JacksonUtil.toJson(messages));
                        ChatSessionDto chatSessionDto = new ChatSessionDto();
                        chatSessionDto.setMessages(finalChatSession.getMessages());
                        chatSessionService.update((long) finalChatSession.getId(), chatSessionDto, currentUser);
                        log.info("保存AI响应到会话，消息类型: {}", aiMessage.getType());
                    } catch (Exception e) {
                        log.error("保存模型响应到会话失败: {}", e.getMessage(), e);
                    }
                });
    }
}
