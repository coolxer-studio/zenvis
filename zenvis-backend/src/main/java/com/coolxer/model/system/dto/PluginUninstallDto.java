package com.coolxer.model.system.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 插件卸载请求。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PluginUninstallDto {

    /**
     * 是否保留插件数据。默认 false，即卸载时删除 ClickHouse、MySQL 和 RAG 数据。
     */
    private boolean keepData;
}
