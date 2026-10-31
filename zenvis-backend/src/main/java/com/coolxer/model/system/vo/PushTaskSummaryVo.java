package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据推送任务列表摘要，不包含完整配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushTaskSummaryVo implements Serializable {

    private Integer id;

    private String name;

    private String description;

    private String source;

    private String mark;

    private String format;

    private String status;

    private Integer pid;

    private String updateTime;

    private String currentRunId;

    private String runStartedAt;
}
