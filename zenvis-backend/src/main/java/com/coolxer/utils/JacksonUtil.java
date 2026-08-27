package com.coolxer.utils;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.JacksonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * jackson 工具类
 */
@Slf4j
public class JacksonUtil {

    private JacksonUtil() {
    }

    public static <T> T toObject(String content, Class<T> type) {
        if (!StringUtils.hasLength(content)) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(content, type);
        } catch (JsonProcessingException e) {
            log.error("jackson toObject error", e);
            throw new ApiException(ResultCodeEnum.JACKSON_PROCESSING_EXCEPTION);
        }
    }

    public static <T> List<T> toList(String content, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasLength(content)) {
            return Collections.emptyList();
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(content, typeReference);
        } catch (JsonProcessingException e) {
            log.error("jackson toList error ", e);
            throw new ApiException(ResultCodeEnum.JACKSON_PROCESSING_EXCEPTION);
        }
    }

    public static <K, V> Map<K, V> toMap(String content, TypeReference<Map<K, V>> typeReference) {
        if (!StringUtils.hasLength(content)) {
            return Collections.emptyMap();
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(content, typeReference);
        } catch (JsonProcessingException e) {
            log.error("jackson toMap error", e);
            throw new ApiException(ResultCodeEnum.JACKSON_PROCESSING_EXCEPTION);
        }
    }

    public static String toJson(Object object) {
        if (Objects.isNull(object)) {
            return null;
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("jackson toJson error", e);
            throw new ApiException(ResultCodeEnum.JACKSON_PROCESSING_EXCEPTION);
        }
    }


    public static Map<String, Object> toMap(Object object) {
        if (Objects.isNull(object)) {
            return Collections.emptyMap();
        }
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(toJson(object), Map.class);
        } catch (JsonProcessingException e) {
            log.error("jackson toMap error", e);
            throw new ApiException(ResultCodeEnum.JACKSON_PROCESSING_EXCEPTION);
        }
    }

    public static String readJsonFile(String filePath) {

        try {
            return JacksonConfig.OBJECT_MAPPER.readTree(new File(filePath)).toString();

        } catch (IOException e) {
            log.error("读取json文件失败", e);
        }
        return null;
    }

    /**
     * 检查字符串是否为有效的JSON格式
     *
     * @param content 要检查的字符串
     * @return 如果是有效的JSON则返回true，否则返回false
     */
    public static boolean isJson(String content) {
        if (!StringUtils.hasLength(content)) {
            return false;
        }
        try {
            JacksonConfig.OBJECT_MAPPER.readTree(content);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
