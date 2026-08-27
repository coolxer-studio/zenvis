package com.coolxer.model.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 配置文件传输实体
 */
@Data
public class ConfigDto {

    /**
     * ID（备用）
     */
    private Integer id;

    /**
     * 原文件名（更新操作才用）
     */
    private String originalFileName;

    /**
     * 文件名
     */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /**
     * 文件内容
     */
    private String text;

    /**
     * 提交信息
     */
    private String commitMsg;
}
