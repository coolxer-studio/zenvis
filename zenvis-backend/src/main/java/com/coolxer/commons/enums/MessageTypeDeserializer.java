package com.coolxer.commons.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * MessageType 自定义反序列化器
 * 支持从小写字符串反序列化为枚举类型
 */
public class MessageTypeDeserializer extends JsonDeserializer<MessageType> {

    @Override
    public MessageType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) {
            return MessageType.TEXT;
        }
        
        // 尝试直接通过枚举值匹配（已转为枚举的情况）
        try {
            return MessageType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 如果不匹配，尝试通过 code 匹配（旧数据的小写字符串）
            try {
                return MessageType.fromCode(value);
            } catch (IllegalArgumentException ignored) {
                return MessageType.TEXT;
            }
        }
    }
}
