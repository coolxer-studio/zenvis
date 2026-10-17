package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG数据集搜索传输对象
 */
@Data
@NoArgsConstructor
public class RagDatasetSearchDto extends SortPageDto {

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
    private String tag;
}
