package com.coolxer.plugin.lab.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LabNote(
        long id,
        String title,
        String content,
        String status,
        String owner,
        @JsonSerialize(using = LocalDateTimeTextSerializer.class) LocalDateTime createTime,
        @JsonSerialize(using = LocalDateTimeTextSerializer.class) LocalDateTime updateTime) {
}
