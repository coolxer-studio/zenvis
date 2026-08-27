package com.coolxer.model.base.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页响应
 */
@Data
@Schema(description = "分页请求参数。JSON/query wire 字段按全局 Jackson 策略输出为 page/per_page。")
public class PageDto {

    /**
     * 每页显示条数，默认 10
     */
    @JsonAlias({"per_page", "perPage"})
    @Schema(name = "per_page", description = "每页显示条数", example = "10")
    private int perPage = 10;

    /**
     * 当前页
     */
    @Schema(description = "当前页，从 1 开始", example = "1")
    private int page = 1;

    /**
     * 兼容 GET query/form 传参中的 per_page。Spring MVC 的 Bean 属性绑定不会走 Jackson 的 snake_case 策略。
     */
    @JsonIgnore
    public void setPer_page(int perPage) {
        this.perPage = perPage;
    }
}
