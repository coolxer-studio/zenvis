package com.coolxer.model.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "标准分页列表响应。新接口优先使用 rows/total，并在需要时补充 page/per_page。")
public class PageRowsVo<T> {
    @Schema(description = "当前页数据")
    private List<T> rows;

    @Schema(description = "总记录数", example = "100")
    private long total;

}
