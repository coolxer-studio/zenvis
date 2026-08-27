package com.coolxer.model.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜单排序后传输对象
 */
@Data
public class MenuOrderRowDto {

    /**
     * 排序id
     */
    private String ids;

    /**
     * 数据集合
     */
    private List<MenuDto> rows;

}
