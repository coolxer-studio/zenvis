package com.coolxer.model.dih;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 聊天消息的结构化片段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePart {
    private String id;
    private String type;
    private String content;
    private String language;
    private String title;
    private String level;
    private String status;
    private Map<String, Object> metadata;
}
