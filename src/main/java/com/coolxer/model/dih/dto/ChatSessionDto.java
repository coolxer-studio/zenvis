package com.coolxer.model.dih.dto;

import lombok.Data;

@Data
public class ChatSessionDto {

    /**
     * id
     */
    private int id;

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
     * 会话内容（json）
     */
    private String messages;

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


}
