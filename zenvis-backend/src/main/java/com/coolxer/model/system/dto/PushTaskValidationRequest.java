package com.coolxer.model.system.dto;

import lombok.Data;

/**
 * 数据推送配置校验请求。
 */
@Data
public class PushTaskValidationRequest {

    /**
     * 完整 Vector 配置文本。
     */
    private String config;

    /**
     * 可选配置格式：yaml、toml 或 json。
     */
    private String format;

    /**
     * 已发布 Meta 文件名。
     */
    private String metaFileName;

    /**
     * config_read 返回的已发布 Meta 原文。
     */
    private String metaContent;
}
