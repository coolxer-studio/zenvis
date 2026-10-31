package com.coolxer.model.system.dto;

import lombok.Data;

/**
 * 数据推送任务分页查询条件。
 */
@Data
public class PushTaskQueryDto {

    private String sourceMark;

    private String nameContains;

    private String source;

    private String status;

    private Integer offset = 0;

    private Integer limit = 20;
}
