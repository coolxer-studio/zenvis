package com.coolxer.model.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统信息DTO
 */
@Data
public class SystemInfoDto implements Serializable {

    /**
     * 系统标题
     */
    private String systemTitle;

    /**
     * 系统英文副标题
     */
    private String systemSubtitle;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 产品版本
     */
    private String productVersion;

    /**
     * 产品介绍
     */
    private String productIntroduction;

    /**
     * 服务电话
     */
    private String servicePhone;

    /**
     * 服务邮箱
     */
    private String serviceEmail;

    /**
     * 技术支持邮箱
     */
    private String technicalEmail;

    /**
     * 集成链接
     */
    private String integrateLink;

    /**
     * 版权信息
     */
    private String copyright;
}
