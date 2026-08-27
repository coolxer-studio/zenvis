package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 插件搜索传输对象
 */
@Data
@NoArgsConstructor
public class PluginSearchDto extends SortPageDto {

    /**
     * 插件名
     */
    private String name;

    /**
     * 包名
     */
    private String packageName;

}