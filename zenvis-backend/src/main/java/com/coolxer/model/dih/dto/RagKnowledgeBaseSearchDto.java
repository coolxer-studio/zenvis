package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG知识库搜索传输对象
 */
@Data
@NoArgsConstructor
public class RagKnowledgeBaseSearchDto extends SortPageDto {

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
    private String tag;
}
