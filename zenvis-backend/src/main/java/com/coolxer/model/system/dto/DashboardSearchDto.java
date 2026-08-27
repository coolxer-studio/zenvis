package com.coolxer.model.system.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板搜索传输对象
 */
@Data
@NoArgsConstructor
public class DashboardSearchDto extends SortPageDto {

    /**
     * 看板名称
     */
    private String name;

    /**
     * URL地址
     */
    private String url;

    /**
     * 低代码配置索引
     */
    private String configIndex;

    /**
     * HTML文件路径
     */
    private String htmlPath;

}