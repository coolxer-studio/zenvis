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
        this.name = taskDto.getName();
        this.description = taskDto.getDescription();
        this.config = taskDto.getConfig();
        if (taskDto.getSource() == null){
            this.source = TaskSourceEnum.API;
        }else{
            this.source = TaskSourceEnum.fromString(taskDto.getSource());
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
