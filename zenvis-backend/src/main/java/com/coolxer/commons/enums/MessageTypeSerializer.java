package com.coolxer.commons.enums;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * MessageType 自定义序列化器
 * 将枚举序列化为小写字符串
 */
public class MessageTypeSerializer extends JsonSerializer<MessageType> {

    @Override
    public void serialize(MessageType value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeString("text"); // 默认值为 text
        } else {
            gen.writeString(value.getCode()); // 使用小写 code
        }
    }
}
