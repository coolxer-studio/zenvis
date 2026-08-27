package com.coolxer.model.retrieval.meta;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 *
 */

@Getter
public enum OperatorEnum {

    /**
     * 等于
     */
    EQUAL(1, "等于"),

    NOTEQUAL(2, "不等于"),

    GREATTHAN(3, "大于"),

    GREATEQUALTHAN(4, "大于等于"),

    LESSTHAN(5, "小于"),

    LESSEQUANTHAN(6, "小于等于"),

    BETWEEN(7, "之间"),

    IN(8, "包含"),
    ;

    @JsonValue
    private final int code;

    private final String description;

    OperatorEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
