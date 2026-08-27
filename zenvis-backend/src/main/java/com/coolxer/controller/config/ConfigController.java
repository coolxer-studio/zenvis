package com.coolxer.controller.config;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigVo;
import com.coolxer.service.config.ConfigService;
import com.coolxer.utils.JacksonUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置
 */
@Tag(name = "系统配置")
@Slf4j
@RestController
@RequestMapping("/api/v1/config/{type}")
public class ConfigController {

    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private ConfigService configService;

    /**
     * 文件树
     *
     * @return 结果
     */
    @GetMapping(value = "/tree")
    @Operation(summary = "文件树", description = "配置文件树")
    public ResponseWrap<List<ConfigVo>> getConfigFileTree(@PathVariable("type") String type) {
        return ResponseWrap.success(configService.getConfigFileTree(type));
    }

    /**
     * 读文件
     *
     * @param fileName 文件全名，带路径
     * @return 文件内容
     */
    @GetMapping(value = "/schema")
    @Operation(summary = "获取文件schema", description = "获取文件scheme")
    public ResponseWrap<Object> scheme(@PathVariable("type") String type, @RequestParam(value = "file_name") String fileName) {
        String schema = configService.readFileSchema(type, fileName);
        Object jsonObject = JacksonUtil.toObject(schema, Object.class);
        return ResponseWrap.success(jsonObject);
    }

    /**
     * 读文件
     *
     * @param fileName 文件全名，带路径
     * @return 文件内容
     */
    @GetMapping(value = "/read")
    @Operation(summary = "读文件", description = "读文件")
    public ResponseWrap<String> readFile(@PathVariable("type") String type, @RequestParam(value = "file_name") String fileName) {
        if (configService.fileExistsInConfigPath(type, fileName)) {
            return ResponseWrap.success(configService.readFile(type, fileName));
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

    /**
     * 获取配置
     *
     * @param fileName 配置文件
     * @return 配置文件内容，不包含包装数据
     */
    @GetMapping(value = "/get")
    @Operation(summary = "获取配置", description = "获取配置")
    public Object getConfig(@PathVariable("type") String type, @RequestParam(value = "file_name") String fileName) {
        if (configService.fileExistsInConfigPath(type, fileName)) {
            return configService.readFile(type, fileName);
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

    /**
     * 修改文件
     *
     * @param configDto 配置文件实体
     * @return 结果
     */
    @PostMapping(value = "/modify")
    @Operation(summary = "修改文件", description = "修改文件")
    public ResponseWrap<Void> modify(@PathVariable("type") String type, @Valid @RequestBody ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            configService.modifyConfig(type, configDto);
            return ResponseWrap.success();
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

    /**
     * 修改文件
     *
     * @param configDto 配置文件实体
     * @return 结果
     */
    @PostMapping(value = "/apply")
    @Operation(summary = "应用配置", description = "应用配置")
    public ResponseWrap<Void> apply(@PathVariable("type") String type, @RequestBody ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            // 先保存
            configService.modifyConfig(type, configDto);
            // 保存后应用配置
            configService.applyConfig(type, configDto);
            return ResponseWrap.success();
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

    /**
     * 添加文件
     *
     * @param type
     * @param configDto 配置文件实体
     * @return
     */
    @PostMapping(value = "/add")
    @Operation(summary = "添加文件", description = "添加文件")
    public ResponseWrap<String> addFile(@PathVariable("type") String type, @RequestBody ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
        }
        if (configService.addFile(type, configDto.getFileName())) {
            return ResponseWrap.success();
        } else {
            return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    /**
     * 修改文件名
     *
     * @param type
     * @param configDto 配置文件实体
     * @return
     */
    @PostMapping(value = "/rename")
    @Operation(summary = "修改文件名", description = "修改文件名")
    public ResponseWrap<String> renameFile(@PathVariable("type") String type, @RequestBody ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getOriginalFileName())) {
            if (configService.renameFile(type, configDto.getOriginalFileName(), configDto.getFileName())) {
                return ResponseWrap.success();
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

    /**
     * 删除文件
     *
     * @param type
     * @param configDto 配置文件实体
     * @return
     */
    @PostMapping(value = "/delete")
    @Operation(summary = "文件名", description = "文件名")
    public ResponseWrap<String> deleteFile(@PathVariable("type") String type, @RequestBody ConfigDto configDto) {
        if (configService.fileExistsInConfigPath(type, configDto.getFileName())) {
            if (configService.deleteFile(type, configDto.getFileName())) {
                return ResponseWrap.success();
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }
        }
        return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
    }

}
