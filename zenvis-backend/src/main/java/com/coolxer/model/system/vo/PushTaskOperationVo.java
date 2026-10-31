package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据推送任务写操作结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskOperationVo {

    private String action;

    private Integer taskId;

    private String previousStatus;

    private String status;

    private boolean changed;

    private Integer pid;

    private String currentRunId;

    private String runStartedAt;
}
