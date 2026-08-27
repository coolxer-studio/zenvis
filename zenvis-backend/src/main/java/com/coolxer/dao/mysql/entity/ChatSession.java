package com.coolxer.dao.mysql.entity;


import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.model.dih.dto.ChatSessionDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

/**
 * AI聊天会话
 */
@Data
@Entity
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_AI_CHAT_SESSION)
public class ChatSession extends BaseEntity {

    public static final int MAX_TITLE_LENGTH = 60;

    /**
     * 会话ID（UUID形式）
     */
    @Column
    private String sessionId;

    /**
     * 会话标题
     */
    @Column
    private String title;

    /**
     * 会话类型
     */
    @Column
    private String type;

    /**
     * 会话内容（json格式）
     */
    @Column(columnDefinition = "LONGTEXT")
    private String messages;

    /**
     * 附加数据（json格式）
     */
    @Column(name = "extra_data", columnDefinition = "LONGTEXT")
    private String extraData;

    /**
     * 是否深度思考
     */
    @Column(name = "deep_think")
    private Boolean deepThink;

    /**
     * 是否在线检索
     */
    @Column(name = "online_search")
    private Boolean onlineSearch;

    /**
     * 是否置顶
     */
    @Column()
    private Boolean pin;

    public void updateFromDto(ChatSessionDto chatSessionDto) {
        if (StringUtils.isNotEmpty(chatSessionDto.getSessionId())) {
            this.sessionId = chatSessionDto.getSessionId();
        }
        if (StringUtils.isNotEmpty(chatSessionDto.getTitle())) {
            this.title = StringUtils.left(chatSessionDto.getTitle(), MAX_TITLE_LENGTH);
        }
        if (StringUtils.isNotEmpty(chatSessionDto.getType())) {
            this.type = chatSessionDto.getType();
        }
        if (StringUtils.isNotEmpty(chatSessionDto.getMessages())) {
            this.messages = chatSessionDto.getMessages();
        }
        if (StringUtils.isNotEmpty(chatSessionDto.getExtraData())) {
            this.extraData = chatSessionDto.getExtraData();
        }
        if (chatSessionDto.getDeepThink() != null) {
            this.deepThink = chatSessionDto.getDeepThink();
        }
        if (chatSessionDto.getOnlineSearch() != null) {
            this.onlineSearch = chatSessionDto.getOnlineSearch();
        }
        if (chatSessionDto.getPin() != null) {
            this.pin = chatSessionDto.getPin();
        }
    }
}
