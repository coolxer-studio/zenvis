package com.coolxer.dao.clickhouse.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        return "[" + attribute.stream()
                .map(item -> "'" + item + "'")
                .collect(Collectors.joining(",")) + "]";
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return Collections.emptyList();
        }
        // 去掉首尾的 []
        String content = dbData.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        if (content.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(content.split(","));
    }
} 