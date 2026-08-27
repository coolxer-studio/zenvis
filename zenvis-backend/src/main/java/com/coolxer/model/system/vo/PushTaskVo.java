package com.coolxer.model.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 推送任务传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushTaskVo implements Serializable {

    /**
     * 任务id
     */
    private Integer id;

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
     * 更新时间
     */
    private Date updateTime;

}
