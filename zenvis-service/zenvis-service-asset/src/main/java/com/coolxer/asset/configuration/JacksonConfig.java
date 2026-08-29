package com.coolxer.asset.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * jackson 配置类
 *
 * @author yaoqi.li
 * @date 2023/7/4 10:38
 */

public class JacksonConfig {

  private static final String TIME_ZONE = "GMT+8";
  private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  /**
   * 反序列化用驼峰命名
   */
  public static final ObjectMapper DESERIALIZATION_OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
      .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE)
      .setTimeZone(TimeZone.getTimeZone(TIME_ZONE))
      .setDateFormat(new SimpleDateFormat(DATE_FORMAT));

  /**
   * 序列化时候用蛇形命名
   */
  public static final ObjectMapper SERIALIZATION_OBJECT_MAPPER = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE)
      .setSerializationInclusion(JsonInclude.Include.ALWAYS)
      .setTimeZone(TimeZone.getTimeZone(TIME_ZONE))
      .setDateFormat(new SimpleDateFormat(DATE_FORMAT));

}
