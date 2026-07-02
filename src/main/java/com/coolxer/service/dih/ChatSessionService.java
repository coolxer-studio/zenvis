package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.dih.dto.ChatSessionDto;
import com.coolxer.model.dih.dto.ChatSessionSearchDto;
import com.coolxer.model.dih.vo.ChatSessionVo;
import com.coolxer.model.dih.Message;

import java.util.List;

/**
 * 会话接口
 */
public interface ChatSessionService {

    /**
     * 查询全部列表
     *
     * @return 结果
     */
    List<ChatSessionVo> findAll();

    /**
     * 创建会话
     *
     * @param chatSessionDto 传输实体
     */
    ChatSession create(ChatSessionDto chatSessionDto, User currentUser);

    /**
     * 修改会话
     *
     * @param id             会话id
     * @param chatSessionDto 用户传输实体
     */
    Boolean update(Long id, ChatSessionDto chatSessionDto, User currentUser);

    /**
     * 删除会话
     *
     * @param id 会话id
     */
    void delete(Long id, User currentUser);

    /**
     * 批量删除
     */
    void deleteByIds(List<Long> ids, User currentUser);

    /**
     * 会话详情
     *
     * @param id 会话id
     * @return 结果
     */
    ChatSessionVo info(Long id, User currentUser);

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    List<ChatSessionVo> getPinList(User currentUser);

    /**
     * 获取会话列表
     *
     * @param chatSessionSearchDto 搜索参数
     * @return 会话列表
     */
    PageRowsVo<ChatSessionVo> getPageList(ChatSessionSearchDto chatSessionSearchDto, User currentUser);

    /**
     * 获取会话列表
     *
     * @param chatId 搜索参数
     * @return 会话列表
     */
    ChatSession getChatSessionBySessionId(String chatId, User currentUser);

    /**
     * 将消息追加到会话；会话不存在时按默认信息创建。
     */
    ChatSession appendMessage(String chatId, ChatSessionDto createDefaults, Message message, User currentUser);

    /**
     * 将消息追加到已有会话。
     */
    ChatSession appendMessage(ChatSession chatSession, Message message, User currentUser);

}
