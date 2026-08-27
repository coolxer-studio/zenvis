package com.coolxer.model.base.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 基础查询条件
 */
@Data
public class BaseQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = -5681997717526425897L;

    /**
     * 页码，从1开始
     */
    int page = 1;

    /**
     * 页大小
     */
    int size = 10;

    /**
     * 时间条件的起始值
     */
    private Date startTime;

    /**
     * 时间条件的终止值
     */
    private Date endTime;

    /**
     * 排序的字段
     */
    private String sort;

    /**
     * 排序方式，如正序：asc；倒序：desc
     */
    private String order;

    /**
     * 模糊查询条件
     */
    private String search;
}
