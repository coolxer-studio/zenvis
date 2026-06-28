package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * AI分析任务队列状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskQueueVo implements Serializable {

    /**
     * 当前执行任务
     */
    private AnalysisTaskVo runningTask;

    /**
     * 下一个等待任务
     */
    private AnalysisTaskVo nextTask;

    /**
     * 等待任务数量
     */
    private long pendingCount;

    /**
     * 到期可执行任务数量
     */
    private long readyCount;

    /**
     * 执行中任务数量
     */
    private long runningCount;

    /**
     * 队列检查时间
     */
    private Date checkedAt;
}
