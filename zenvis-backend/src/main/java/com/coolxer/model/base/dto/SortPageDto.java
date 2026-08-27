package com.coolxer.model.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 排序分页响应
 */
@Data
@Schema(description = "排序分页请求参数。wire 字段为 page/per_page/order_by/order_dir。")
public class SortPageDto extends PageDto {

    /**
     * 排序字段
     */
    @Schema(name = "order_by", description = "排序字段")
    private String orderBy;

    /**
     * 排序方式
     */
    @Schema(name = "order_dir", description = "排序方向，例如 asc 或 desc")
    private String orderDir;
}
