package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 系统信息表
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = MysqlFinalTableName.T_SYS_INFO)
public class SystemInfo extends BaseEntity {

    /**
     * 系统标题
     */
    @Column(name = "system_title")
    private String systemTitle;

    /**
     * 系统英文副标题
     */
    @Column(name = "system_subtitle")
    private String systemSubtitle;

    /**
     * 系统Icon
     */
    @Column(name = "system_icon")
    private String systemIcon;

    /**
     * 系统Logo
     */
    @Column(name = "system_logo")
    private String systemLogo;

    /**
     * 系统Banner
     */
    @Column(name = "system_banner")
    private String systemBanner;

    /**
     * 产品名称
     */
    @Column(name = "product_name")
    private String productName;

    /**
     * 产品版本
     */
    @Column(name = "product_version")
    private String productVersion;

    /**
     * 产品介绍
     */
    @Column(name = "product_introduction")
    private String productIntroduction;

    /**
     * 服务电话
     */
    @Column(name = "service_phone")
    private String servicePhone;

    /**
     * 服务邮箱
     */
    @Column(name = "service_email")
    private String serviceEmail;

    /**
     * 技术支持邮箱
     */
    @Column(name = "technical_email")
    private String technicalEmail;

    /**
     * 集成链接
     */
    @Column(name = "integrate_link")
    private String integrateLink;

    /**
     * 版权信息
     */
    @Column(name = "copyright")
    private String copyright;
}
