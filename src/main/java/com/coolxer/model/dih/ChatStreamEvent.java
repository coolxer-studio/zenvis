package com.coolxer.model.dih;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NDJSON 流式事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {
    private String event;
    private String content;
    private Object message;
    private Object data;

    public static ChatStreamEvent delta(String content) {
        return ChatStreamEvent.builder()
                .event("delta")
                .content(content)
                .build();
    }

    public static ChatStreamEvent done(Message message) {
        return ChatStreamEvent.builder()
                .event("done")
                .message(message)
                .build();
    }

    public static ChatStreamEvent error(String message) {
        return ChatStreamEvent.builder()
                .event("error")
                .message(message)
                .build();
    }

    public static ChatStreamEvent approval(String event, Object data) {
        return ChatStreamEvent.builder()
                .event(event)
                .data(data)
                .build();
    }
}
