package com.coolxer.model.dih;

import lombok.Data;

/**
 * AI建议传输实体
 */
@Data
public class SuggestDto {

    /**
     * 上下文
     */
    private String content;

    /**
     * 当前内容
     */
    private String currentLine;

}
