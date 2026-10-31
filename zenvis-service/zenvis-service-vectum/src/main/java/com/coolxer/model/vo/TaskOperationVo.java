package com.coolxer.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务生命周期操作结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskOperationVo {

    private String action;

    private Long taskId;

    private String previousStatus;

    private String status;

    private boolean changed;

    private Integer pid;

    private String currentRunId;

    private String runStartedAt;
}
