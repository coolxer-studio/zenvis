package com.coolxer.model.dto;

import lombok.Data;

/**
 * 任务分页查询条件。
 */
@Data
public class TaskQueryDto {

    private String source;

    private String mark;

    private String nameContains;

    private String status;

    private Integer offset = 0;

    private Integer limit = 20;
}
