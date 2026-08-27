package com.coolxer.model.dih;

import com.coolxer.commons.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应对象
 * 包含响应内容和对应的类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    /**
     * 响应内容
     */
    private String content;
    
    /**
     * 消息类型
     */
    private MessageType type;
}
