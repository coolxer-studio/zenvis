package com.coolxer.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPageVo {

    @Builder.Default
    private List<TaskSummaryVo> items = new ArrayList<>();

    private long total;

    private int offset;

    private int limit;

    private boolean hasMore;
}
