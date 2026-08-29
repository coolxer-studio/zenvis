package com.coolxer.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

import java.util.Date;

/**
 * 任务数据传输对象
 * <p>
 * 用于接收前端传递的任务数据
 * </p>
 */
@Data
public class TaskDto {

    /**
     * ID
     */
    private Long id;

    /**
     * 任务名
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 配置
     */
    @NotBlank(message = "任务配置不能为空")
    private String config;

    /**
     * 来源
     */
    private String source;

    /**
     * 备注
     */
    private String mark;

    /**
     * 更新时间
     */
    private Date updateTime;
}