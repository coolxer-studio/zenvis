package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单搜索传输对象
 */
@Data
@NoArgsConstructor
public class MenuSearchDto extends SortPageDto {

    /**
     * 菜单名
     */
    private String name;

    /**
     * 路由
     */
    private String route;

}