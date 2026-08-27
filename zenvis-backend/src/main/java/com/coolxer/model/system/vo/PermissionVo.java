package com.coolxer.model.system.vo;

import lombok.Data;

/**
 * 权限
 */
@Data
public class PermissionVo {

    /**
     * 权限id
     */
    private int id;

    /**
     * 权限编码
     */
    private String code;

    /**
     * 权限名称
     */
    private String name;

}
