package com.coolxer.controller.config;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.config.MetaConfigValidationService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.utils.JacksonUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP 工具服务：读取、创建、写入并应用系统配置。
 */
@Service
public class ConfigMcpTool {

    private final ConfigService configService;

    @Autowired(required = false)
    private MetaConfigValidationService metaConfigValidationService;

    public ConfigMcpTool(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 获取配置文件树
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "config_tree", description = "获取指定配置类型的配置文件树结构")
    public List<ConfigVo> getConfigFileTree(@ToolParam(description = "配置类型，例如 web、agent、meta、device_id 或低代码配置索引") String type) {
        return configService.getConfigFileTree(type);
    }

    /**
     * 获取文件schema
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "config_schema", description = "获取指定配置文件对应的JSON Schema定义")
    public Object scheme(@ToolParam(description = "配置类型，例如 web、agent、meta、device_id 或低代码配置索引") String type,
                         @ToolParam(description = "文件全名，带路径或文件名，例如 system_info.json") String fileName) {
        String schema = configService.readFileSchema(type, fileName);
        return JacksonUtil.toObject(schema, Object.class);
    }

    /**
     * 读取文件内容
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "config_read", description = "读取指定配置文件内容，返回文件文本")
    public String readFile(@ToolParam(description = "配置类型，例如 web、agent、meta、device_id 或低代码配置索引") String type,
                           @ToolParam(description = "文件全名，带路径或文件名") String fileName) {
        checkFileExists(type, fileName);
        return configService.readFile(type, fileName);
    }

    /**
     * 应用配置
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "config_apply", description = "保存指定配置文件内容并执行该配置类型对应的应用动作")
    public Boolean apply(@ToolParam(description = "配置类型，例如 meta；meta 会重新加载元数据并刷新 ClickHouse Schema") String type,
                         @ToolParam(description = "配置文件实体，fileName为文件全名，text为新文件内容") ConfigDto configDto,
                         @ToolParam(description = "是否允许写入未通过校验的 Meta；仅限用户显式强制确认时使用",
                                 required = false) Boolean force) {
        checkFileExists(type, configDto.getFileName());
        boolean forced = Boolean.TRUE.equals(force);
        validateMetaConfiguration(type, configDto, forced);
        configService.modifyConfig(type, configDto);
        configService.applyConfig(type, configDto);
        return true;
    }

    /**
     * 添加文件
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "config_add", description = "在指定配置类型目录下创建空配置文件；正文由 config_apply 写入")
    public Boolean addFile(@ToolParam(description = "配置类型，例如 web、agent、meta、device_id 或低代码配置索引") String type,
                           @ToolParam(description = "配置文件实体，fileName为要新增的文件全名；text 不会在 add 阶段写入") ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        if (!configService.addFile(type, configDto.getFileName())) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    /**
     * 重命名配置文件或目录；仅允许同一父目录内改名。
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "config_rename", description = "重命名指定配置文件或目录；仅允许同一父目录内改名，目标已存在时拒绝覆盖")
    public ConfigPathOperationResult rename(
            @ToolParam(description = "配置类型，例如 meta") String type,
            @ToolParam(description = "配置根下的相对路径，例如 student_info.json 或 archive") String fileName,
            @ToolParam(description = "同一目录下的新文件或目录名，不包含路径分隔符") String newFileName) {
        return configService.renameEntry(type, fileName, newFileName);
    }

    /**
     * 删除配置文件或目录。
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "config_delete", description = "删除指定配置文件或目录；非空目录必须显式设置 recursive=true")
    public ConfigPathOperationResult delete(
            @ToolParam(description = "配置类型，例如 meta") String type,
            @ToolParam(description = "配置根下的相对路径，例如 student_info.json 或 archive") String fileName,
            @ToolParam(description = "是否递归删除非空目录；默认 false",
                    required = false) Boolean recursive) {
        return configService.deleteEntry(type, fileName, Boolean.TRUE.equals(recursive));
    }

    /**
     * 幂等创建配置类型根目录
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "config_ensure_root", description = "幂等创建指定配置类型根目录，例如 visual-report 会创建 visual-report_config 目录")
    public Boolean ensureRoot(@ToolParam(description = "配置类型，例如低代码配置索引 inspection-dashboard") String type) {
        if (!configService.ensureRootPath(type)) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
        return true;
    }

    private void checkFileExists(String type, String fileName) {
        if (!configService.fileExistsInConfigPath(type, fileName)) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }

    private void validateMetaConfiguration(String type,
                                           ConfigDto configDto,
                                           boolean forced) {
        if (!"meta".equals(type)
                || metaConfigValidationService == null
                || !StringUtils.hasText(configDto.getText())) {
            return;
        }
        MetaConfigValidationService.ValidationResult validation =
                metaConfigValidationService.validate(
                        configDto.getFileName(), configDto.getText());
        if (!validation.passed() && !forced) {
            throw new IllegalArgumentException(
                    "Meta 校验未通过，拒绝写入：" + String.join("；", validation.errors()));
        }
        if (StringUtils.hasText(validation.normalizedText())) {
            configDto.setText(validation.normalizedText());
        }
    }
}
