package com.coolxer.model.dih;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.utils.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String id;
    private String sender;
    private String content;
    private String time;
    private Boolean isError;
    private Boolean effective;
    /**
     * 消息类型: text, chart, code, table, image, etc.
     * 序列化和反序列化已在 JacksonConfig 中全局配置
     */
    private MessageType type;
    /**
     * 结构化消息片段；历史消息没有该字段时前端继续按 content 渲染。
     */
    private List<ChatMessagePart> parts;
    /**
     * 用户消息携带的附件；历史消息没有该字段时前端忽略。
     */
    private List<ChatAttachment> attachments;
    public Message(String sender, String content) {
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.content = content;
        this.time = DateUtil.getCurrentDateTime();
        this.type = MessageType.TEXT;
    }

    public Message(String sender, String content, String type) {
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.content = content;
        this.time = DateUtil.getCurrentDateTime();
        this.type = MessageType.fromCode(type);
    }

    public Message(String sender, String content, MessageType type) {
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.content = content;
        this.time = DateUtil.getCurrentDateTime();
        this.type = type;
    }
}
