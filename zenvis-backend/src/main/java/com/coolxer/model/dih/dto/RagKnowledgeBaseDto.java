package com.coolxer.model.dih.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG知识库传输对象
 */
@Data
@NoArgsConstructor
public class RagKnowledgeBaseDto {

    /**
     * 知识库名称
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

    /**
     * 关联的数据集ID列表
     */
    private List<Integer> datasetIds;
}
