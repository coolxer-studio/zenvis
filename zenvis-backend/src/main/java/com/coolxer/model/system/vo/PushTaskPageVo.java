package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据推送任务分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskPageVo {

    @Builder.Default
    private List<PushTaskSummaryVo> items = new ArrayList<>();

    private long total;

    private int offset;

    private int limit;

    private boolean hasMore;
}
