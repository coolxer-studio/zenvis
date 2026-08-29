package com.coolxer.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * jackson 配置类
 *
 * @author hunter
 */

@Configuration
public class JacksonConfig {

    private static final String TIME_ZONE = "GMT+8";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setTimeZone(TimeZone.getTimeZone(TIME_ZONE))
            .setDateFormat(new SimpleDateFormat(DATE_FORMAT));

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }


}
