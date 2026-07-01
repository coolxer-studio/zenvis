package com.coolxer.controller.policy;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.policy.dto.ConfigDto;
import com.coolxer.model.policy.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.utils.JacksonUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP工具服务 - 暴露策略配置相关接口为MCP工具
 */
@Service
public class ConfigMcpTool {

    private final ConfigService configService;

    public ConfigMcpTool(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 获取配置文件树
     */
    @Tool(name = "policy_config_tree", description = "获取指定配置类型的配置文件树结构")
    public List<ConfigVo> getConfigFileTree(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type) {
        return configService.getConfigFileTree(type);
    }

    /**
     * 获取文件schema
     */
    @Tool(name = "policy_config_schema", description = "获取指定配置文件对应的JSON Schema定义")
    public Object scheme(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                         @ToolParam(description = "文件全名，带路径或文件名，例如system_info.json") String fileName) {
        String schema = configService.readFileSchema(type, fileName);
        return JacksonUtil.toObject(schema, Object.class);
    }

    /**
     * 读取文件内容
     */
    @Tool(name = "policy_config_read", description = "读取指定配置文件内容，返回文件文本")
    public String readFile(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                           @ToolParam(description = "文件全名，带路径或文件名") String fileName) {
        checkFileExists(type, fileName);
        return configService.readFile(type, fileName);
    }

    /**
     * 获取配置
     */
    @Tool(name = "policy_config_get", description = "获取指定配置文件内容，返回原始文件文本")
    public String getConfig(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                            @ToolParam(description = "文件全名，带路径或文件名") String fileName) {
        checkFileExists(type, fileName);
        return configService.readFile(type, fileName);
    }

    /**
     * 修改文件
     */
    @Tool(name = "policy_config_modify", description = "修改指定配置文件内容")
    public Boolean modify(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                          @ToolParam(description = "配置文件实体，fileName为文件全名，text为新文件内容") ConfigDto configDto) {
        checkFileExists(type, configDto.getFileName());
        configService.modifyConfig(type, configDto);
        return true;
    }

    /**
     * 应用配置
     */
    @Tool(name = "policy_config_apply", description = "保存指定配置文件内容并执行对应策略应用动作")
    public Boolean apply(@ToolParam(description = "配置类型，例如meta等；meta会重新加载元数据并刷新ClickHouse Schema") String type,
                         @ToolParam(description = "配置文件实体，fileName为文件全名，text为新文件内容") ConfigDto configDto) {
        checkFileExists(type, configDto.getFileName());
        configService.modifyConfig(type, configDto);
        configService.applyPolicy(type, configDto);
        return true;
    }

    /**
     * 添加文件
     */
    @Tool(name = "policy_config_add", description = "在指定配置类型目录下添加新配置文件")
    public Boolean addFile(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                           @ToolParam(description = "配置文件实体，fileName为要新增的文件全名") ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        if (!configService.addFile(type, configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    /**
     * 幂等创建配置类型根目录
     */
    @Tool(name = "policy_config_ensure_root", description = "幂等创建指定配置类型根目录，例如 visual-report 会创建 visual-report_config 目录")
    public Boolean ensureRoot(@ToolParam(description = "配置类型，例如低代码配置索引 inspection-dashboard") String type) {
        if (!configService.ensureRootPath(type)) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    /**
     * 修改文件名
     */
    @Tool(name = "policy_config_rename", description = "重命名指定配置文件")
    public Boolean renameFile(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                              @ToolParam(description = "配置文件实体，originalFileName为原文件全名，fileName为新文件全名") ConfigDto configDto) {
        checkFileExists(type, configDto.getOriginalFileName());
        if (!configService.renameFile(type, configDto.getOriginalFileName(), configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    /**
     * 删除文件
     */
    @Tool(name = "policy_config_delete", description = "删除指定配置文件")
    public Boolean deleteFile(@ToolParam(description = "配置类型，例如web、rating、checker、punish、agent、meta、device_id等") String type,
                              @ToolParam(description = "配置文件实体，fileName为要删除的文件全名") ConfigDto configDto) {
        checkFileExists(type, configDto.getFileName());
        if (!configService.deleteFile(type, configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    private void checkFileExists(String type, String fileName) {
        if (!configService.fileExistsInConfigPath(type, fileName)) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }
}
