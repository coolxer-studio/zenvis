package com.coolxer.model.dih.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG数据集传输对象
 */
@Data
@NoArgsConstructor
public class RagDatasetDto {

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 来源
     */
    private String source;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 描述
     */
    private String description;
}
