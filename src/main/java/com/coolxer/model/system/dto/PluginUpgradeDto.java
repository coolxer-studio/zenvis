package com.coolxer.model.system.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 插件升级请求。候选插件信息一律由服务端重新解析插件包获得。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PluginUpgradeDto {

    @NotBlank(message = "插件包路径不能为空")
    private String pluginPath;
}
