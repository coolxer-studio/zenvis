package com.coolxer.model;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.model.dto.TaskDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;

/**
 * 推送任务实体类
 * <p>
 * 表示一个向量推送任务，包含任务的基本信息、配置和状态
 * </p>
 */
@Data
public class Task {

    private long id;
    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 配置信息
     */
    private String config;

    /**
     * 配置格式：yaml、toml 或 json。
     */
    private String format;

    /**
     * 进程id
     */
    private int pid;

    private ArrayList<LuaFile> luaFiles;

    /**
     * 任务来源
     */
    private TaskSourceEnum source = TaskSourceEnum.SYSTEM;

    /**
     * 备注
     */
    private String mark;

    /**
     * 最近一次启动的轮次标识。
     */
    private String currentRunId;

    /**
     * 最近一次启动时间。
     */
    private String runStartedAt;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 从DTO更新任务信息
     * @param taskDto 任务数据传输对象
     */
    public void updateFromDto(TaskDto taskDto) {
        boolean creating = this.id == 0;
        if (taskDto.getName() != null || creating) {
            this.name = taskDto.getName();
        }
        if (taskDto.getDescription() != null || creating) {
            this.description = taskDto.getDescription();
        }
        if (taskDto.getConfig() != null || creating) {
            this.config = taskDto.getConfig();
        }
        if (taskDto.getFormat() != null || creating) {
            this.format = taskDto.getFormat();
        }
        if (taskDto.getSource() != null) {
            this.source = TaskSourceEnum.fromString(taskDto.getSource());
        } else if (creating) {
            this.source = TaskSourceEnum.API;
        }
        // 更新请求未传备注时保留原值，传空字符串时允许显式清空。
        if (taskDto.getMark() != null) {
            this.mark = taskDto.getMark();
        }
        this.updateTime = java.sql.Timestamp.valueOf(LocalDateTime.now());
        if(createTime == null){
            createTime =  this.updateTime;
        }
    }

}
