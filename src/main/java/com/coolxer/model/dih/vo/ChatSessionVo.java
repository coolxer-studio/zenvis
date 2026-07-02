package com.coolxer.model.dih.vo;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.model.dih.Message;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionVo implements Serializable {

    /**
     * id
     */
    private Integer id;

    /**
     * 会话ID（UUID形式）
     */
    private String sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 会话类型
     */
    private String type;

    /**
     * 会话内容
     */
    private List<Message> messageList;

    /**
     * 附加数据（json）
     */
    private String extraData;

    /**
     * 是否深度思考
     */
    private Boolean deepThink;

    /**
     * 是否在线检索
     */
    private Boolean onlineSearch;

    /**
     * 是否置顶
     */
    private Boolean pin;

    /**
     * 更新时间
     */
    private Date updateTime;

    public ChatSessionVo(ChatSession chatSession) {
        this.id = chatSession.getId();
        this.sessionId = chatSession.getSessionId();
        this.title = chatSession.getTitle();
        this.type = chatSession.getType();
        this.messageList = parseMessages(chatSession.getMessages());
        this.extraData = chatSession.getExtraData();
        this.deepThink = chatSession.getDeepThink();
        this.onlineSearch = chatSession.getOnlineSearch();
        this.pin = chatSession.getPin();
        this.updateTime = chatSession.getUpdateTime();
    }

    private static List<Message> parseMessages(String messages) {
        if (StringUtils.isBlank(messages)) {
            return new ArrayList<>();
        }
        try {
            List<Message> parsed = JacksonUtil.toList(messages, new TypeReference<List<Message>>() {
            });
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            log.warn("会话消息JSON解析失败，返回空消息列表。", e);
            return new ArrayList<>();
        }
    }

}
