package com.coolxer.model.base.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应
 */
@Data
public class PageView<T extends java.io.Serializable> implements Serializable {

    private static final long serialVersionUID = -6492719223823093585L;

    /**
     * 查询数据列表 前端要求字段名称取datalist
     */
    @JsonProperty(value = "datalist")
    private List<T> items = Collections.emptyList();

    /**
     * 总数
     */
    private long total = 0;

    /**
     * 每页显示条数，默认 10
     */
    private long size = 10;

    /**
     * 当前页
     */
    private long page = 1;
}
