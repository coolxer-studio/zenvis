package com.coolxer.configuration;

import com.coolxer.commons.enums.MessageType;
import com.coolxer.commons.enums.MessageTypeDeserializer;
import com.coolxer.commons.enums.MessageTypeSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * jackson 配置类
 */

@Configuration
public class JacksonConfig {

    private static final String TIME_ZONE = "GMT+8";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setTimeZone(TimeZone.getTimeZone(TIME_ZONE))
            .setDateFormat(new SimpleDateFormat(DATE_FORMAT))
            .registerModule(new SimpleModule()
                    .addSerializer(MessageType.class, new MessageTypeSerializer())
                    .addDeserializer(MessageType.class, new MessageTypeDeserializer())
            );

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }


}
