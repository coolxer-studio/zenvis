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
     * 完整配置文本。
     */
    private String config;

    /**
     * 锁定配置格式：yaml、toml 或 json。
     */
    private String format;

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

    /**
     * 候选配置摘要，用于执行前防篡改校验。
     */
    private String candidateDigest;

    /**
     * 已发布 Meta 内容摘要。
     */
    private String metaDigest;

    /**
     * 平台校验摘要。
     */
    private String validationDigest;

    /**
     * 当前修复轮次。
     */
    private Integer repairRound;

    /**
     * 已发布 Meta 文件名。
     */
    private String metaFileName;

    /**
     * 已发布 Meta 原文，仅供服务端执行前复验。
     */
    private String metaContent;


}
