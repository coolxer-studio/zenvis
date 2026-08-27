package com.coolxer.model.retrieval.meta;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 *
 */

@Getter
public enum AttributeTypeEnum {

    /**
     *
     */
    STRING(1, "字符"),

    /**
     *
     */
    NUMBER(2, "数字"),

    /**
     *
     */
    DATE(3, "日期"),
    ;

    @JsonValue
    private final int code;

    private final String description;

    AttributeTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
