package com.coolxer.model.vo;

import com.coolxer.model.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 任务视图对象
 * <p>
 * 用于返回给前端的任务信息，包含格式化的时间和状态
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskVo implements Serializable {

    /**
     * 任务id
     */
    private Long id;

    /**
     * 任务名
     */
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 来源
     */
    private String source;

    /**
     * 备注
     */
    private String mark;

    /**
     * 配置
     */
    private String config;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 进程id
     */
    private Integer pid;

    /**
     * 创建时间（格式化）
     */
    private String createTime;

    /**
     * 更新时间（格式化）
     */
    private String updateTime;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 格式化日期
     * @param date 日期
     * @return 格式化后的字符串
     */
    private String formatDate(Date date) {
        return date != null ? DATE_FORMAT.format(date) : null;
    }

    /**
     * 从任务对象构造视图对象
     * @param task 任务对象
     * @param status 任务状态
     */
    public TaskVo(Task task, String status) {
        this.id = task.getId();
        this.name = task.getName();
        this.description = task.getDescription();
        this.source = task.getSource().name();
        this.mark = task.getMark();
        this.config = task.getConfig();
        this.status = status;
        this.pid = task.getPid();
        this.createTime = formatDate(task.getCreateTime());
        this.updateTime = formatDate(task.getUpdateTime());
    }
}