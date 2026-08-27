package com.coolxer.model.system.dto;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.MenuType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 菜单传输对象
 */
@Data
public class MenuDto {

    /**
     * id
     */
    private Integer id;

    /**
     * 菜单名
     */
    @NotBlank(message = "菜单名称不能为空")
    private String name;

    /**
     * 类型
     */
    private MenuType type;

    /**
     * 路由
     */
    private String route;

    /**
     * 参数，不同类型参数值不同
     */
    private String params;

    /**
     * 是否创建根目录（策略配置类型菜单有效）
     */
    private Boolean createRootPath;

    /**
     * 父级id
     */
    private Integer parentId;

    /**
     * 目录顺序
     */
    private Integer orderNumber;

    /**
     * 目录级别
     */
    private MenuLevel level;

    /**
     * 角标文字
     */
    private String superscript;

    /**
     * 子级菜单
     */
    private List<MenuDto> children;

    /**
     * 来源
     */
    private String source;

}
