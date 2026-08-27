package com.coolxer.model.dih.dto;

import com.coolxer.model.dih.ChatAttachment;
import lombok.Data;

import java.util.List;

@Data
public class ChatDto {

    /**
     * 会话ID
     */
    private String chatId;

    /**
     * 模型
     */
    private String model;

    /**
     * 会话类型
     */
    private String type;

    /**
     * 会话内容
     */
    private String message;

    /**
     * 本轮消息携带的附件。
     */
    private List<ChatAttachment> attachments;

    /**
     * 是否深度思考
     */
    private Boolean deepThink;

    /**
     * 是否在线检索
     */
    private Boolean onlineSearch;

    /**
     * 响应格式：text 或 events
     */
    private String responseFormat;

    /**
     * 报表生成、全文改写或选区改写协议。
     */
    private ReportActionDto reportAction;

}
