package com.coolxer.risk.utils;

import com.coolxer.risk.configuration.JacksonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * jackson 工具类
 *
 * @author yaoqi.li
 * @date 2023/7/3 10:45
 */
@Slf4j
public class JacksonUtil {

  private JacksonUtil() {

  }

  public static <T> T toObject(String content, Class<T> type) throws JsonProcessingException {
    if (!StringUtils.hasLength(content)) {
      return null;
    }
    return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, type);
  }

  public static <T> List<T> toList(String content, TypeReference<List<T>> typeReference) throws JsonProcessingException {
    if (!StringUtils.hasLength(content)) {
      return Collections.emptyList();
    }
    return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, typeReference);
  }

  public static List<Object> toList(String content) throws JsonProcessingException {
    return toList(content, new TypeReference<List<Object>>() {
    });
  }

  public static List<String> toStringList(String content) throws JsonProcessingException {
    return toList(content, new TypeReference<List<String>>() {
    });
  }

  public static <K, V> Map<K, V> toMap(String content, TypeReference<Map<K, V>> typeReference) throws JsonProcessingException {
    if (!StringUtils.hasLength(content)) {
      return Collections.emptyMap();
    }
    return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, typeReference);
  }

  public static Map<String, Object> toMap(String context) throws JsonProcessingException {
    return toMap(context, new TypeReference<Map<String, Object>>() {
    });
  }

  public static String toJson(Object object) throws JsonProcessingException {
    if (Objects.isNull(object)) {
      return null;
    }
    return JacksonConfig.SERIALIZATION_OBJECT_MAPPER.writeValueAsString(object);
  }


}
