package com.coolxer.model.system.dto;

import lombok.Data;

import java.util.Date;

/**
 * 推送任务传输对象
 */
@Data
public class PushTaskDto {

    /**
     * 任务名
     */
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 配置
     */
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
