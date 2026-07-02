package com.coolxer.service.dih.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.ChatSessionRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.Message;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.service.dih.ChatSessionService;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Override
    public List<ChatSessionVo> findAll() {
        return chatSessionRepository.findAll().stream().map(ChatSessionVo::new).toList();
    }

    @Override
    public ChatSession create(ChatSessionDto chatSessionDto, User currentUser) {
        if (currentUser == null) {
            // 不支持删除
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        checkCreateOrUpdate(chatSessionDto);
        ChatSession chatSession = new ChatSession();
        chatSession.updateFromDto(chatSessionDto);
        chatSession.setCreateBy(currentUser.getId());
        return chatSessionRepository.save(chatSession);
    }

    @Override
    @Transactional
    public Boolean update(Long id, ChatSessionDto chatSessionDto, User currentUser) {
        try {
            if (currentUser == null) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }
            Optional<ChatSession> optionalChatSession = chatSessionRepository.findById(id);
            if (optionalChatSession.isPresent() && Objects.equals(optionalChatSession.get().getCreateBy(), currentUser.getId())) {
                ChatSession chatSession = optionalChatSession.get();
                chatSession.updateFromDto(chatSessionDto);
                chatSessionRepository.save(chatSession);
                return true;
            } else {
                // 不支持删除
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id, User currentUser) {
        ChatSession chatSession = chatSessionRepository.findById(id).orElse(null);
        if (chatSession != null) {
            if (currentUser == null || !Objects.equals(chatSession.getCreateBy(), currentUser.getId())) {
                // 不支持删除
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            } else {
                chatSessionRepository.deleteById(id);
            }
        }
    }

    @Override
    public void deleteByIds(List<Long> ids, User currentUser) {
        for (Long id : ids) {
            delete(id, currentUser);
        }
    }

    @Override
    public ChatSessionVo info(Long id, User currentUser) {
        try {
            Optional<ChatSession> optionalChatSession = chatSessionRepository.findById(id);
            if (optionalChatSession.isPresent()
                    && (currentUser == null || !Objects.equals(optionalChatSession.get().getCreateBy(), currentUser.getId()))) {
                return null;
            }
            return optionalChatSession.map(ChatSessionVo::new).orElse(null);
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    @Override
    public List<ChatSessionVo> getPinList(User currentUser) {
        try {
            List<ChatSession> chatSessionList = chatSessionRepository.findPinChatSessionByUser(currentUser.getId());
            return chatSessionList.stream().map(ChatSessionVo::new).toList();
        } catch (Exception e) {
            log.error("查询失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public PageRowsVo<ChatSessionVo> getPageList(ChatSessionSearchDto chatSessionSearchDto, User currentUser) {
        try {
            Pageable pageable = PageRequest.of(chatSessionSearchDto.getPage() - 1, chatSessionSearchDto.getPerPage());
            Page<ChatSession> byPage;
            byPage = chatSessionRepository.findByPage(pageable, chatSessionSearchDto.getTitle(),
                    StringUtils.isBlank(chatSessionSearchDto.getType()) ? null : chatSessionSearchDto.getType(),
                    currentUser.getId());
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(ChatSessionVo::new).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public ChatSession getChatSessionBySessionId(String chatId, User currentUser) {
        if (StringUtils.isBlank(chatId) || currentUser == null) {
            return null;
        }
        return chatSessionRepository.findBySessionIdAndCreateBy(chatId, currentUser.getId()).orElse(null);
    }

    @Override
    @Transactional
    public ChatSession appendMessage(String chatId, ChatSessionDto createDefaults, Message message, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        if (StringUtils.isBlank(chatId) || message == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        ChatSession chatSession = getChatSessionBySessionId(chatId, currentUser);
        if (chatSession == null) {
            ChatSessionDto defaults = createDefaults == null ? new ChatSessionDto() : createDefaults;
            defaults.setSessionId(chatId);
            defaults.setMessages(JacksonUtil.toJson(List.of(message)));
            if (StringUtils.isBlank(defaults.getTitle())) {
                defaults.setTitle(StringUtils.defaultIfBlank(message.getContent(), "新建会话"));
            }
            return create(defaults, currentUser);
        }
        return appendMessage(chatSession, message, currentUser);
    }

    @Override
    @Transactional
    public ChatSession appendMessage(ChatSession chatSession, Message message, User currentUser) {
        if (chatSession == null || message == null) {
            return chatSession;
        }
        if (currentUser == null || !Objects.equals(chatSession.getCreateBy(), currentUser.getId())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        List<Message> messages = parseMessages(chatSession.getMessages());
        messages.add(message);
        chatSession.setMessages(JacksonUtil.toJson(messages));
        return chatSessionRepository.save(chatSession);
    }

    private static void checkCreateOrUpdate(ChatSessionDto chatSessionDto) {
        if (StringUtils.isEmpty(chatSessionDto.getTitle()) || StringUtils.isEmpty(chatSessionDto.getMessages())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
    }

    private List<Message> parseMessages(String rawMessages) {
        if (StringUtils.isBlank(rawMessages)) {
            return new ArrayList<>();
        }
        try {
            List<Message> messages = JacksonUtil.toList(rawMessages, new TypeReference<List<Message>>() {
            });
            return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        } catch (Exception e) {
            log.warn("会话消息JSON解析失败，将使用空消息列表继续追加。", e);
            return new ArrayList<>();
        }
    }

}
