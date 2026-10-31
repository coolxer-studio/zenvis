package com.coolxer.model.vo;

import com.coolxer.model.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务列表摘要，不包含完整配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryVo implements Serializable {

    private Long id;

    private String name;

    private String description;

    private String source;

    private String mark;

    private String format;

    private String status;

    private Integer pid;

    private String createTime;

    private String updateTime;

    private String currentRunId;

    private String runStartedAt;

    public TaskSummaryVo(TaskVo task) {
        this.id = task.getId();
        this.name = task.getName();
        this.description = task.getDescription();
        this.source = task.getSource();
        this.mark = task.getMark();
        this.format = task.getFormat();
        this.status = task.getStatus();
        this.pid = task.getPid();
        this.createTime = task.getCreateTime();
        this.updateTime = task.getUpdateTime();
        this.currentRunId = task.getCurrentRunId();
        this.runStartedAt = task.getRunStartedAt();
    }
}
