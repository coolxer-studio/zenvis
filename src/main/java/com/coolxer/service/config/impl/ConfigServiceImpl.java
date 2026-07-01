package com.coolxer.service.config.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.coolxer.commons.constants.ConfigConstants;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.policy.dto.ConfigDto;
import com.coolxer.model.policy.vo.ConfigVo;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.WalkFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 配置文件接口
 */
@Slf4j
@Service
public class ConfigServiceImpl implements ConfigService {

    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private MetaDataService metaDataService;

    @Autowired
    private ClickhouseSchemeService clickhouseSchemeService;

    /**
     * 默认文件夹深度
     */
    private static final int DEFAULT_DEPTH = 1;

    /**
     * 默认id
     */
    private static final long DEFAULT_ID = 1L;

    @Override
    public List<ConfigVo> getConfigFileTree(String type) {

        String path = configPath(type);
        if (StringUtils.isEmpty(path)) {
            path = ConfigConstants.CONFIG_PATH;
        }

        ConfigVo configVo = new ConfigVo();
        WalkFileUtil.walkFilesWithDir(
                FileUtil.file(path), configVo, DEFAULT_DEPTH, DEFAULT_ID,
                configVoConsumer -> {
                    if (Objects.isNull(configVoConsumer)) {
                        return;
                    }
                    String extName = FileNameUtil.extName(configVoConsumer.getFileName());
                    if (ConfigConstants.MODIFIABLE_FILE_SUFFIX_NAME.contains(extName)) {
                        configVoConsumer.setModifiable(true);
                    }
                    if (configVoConsumer.getSize() != null
                            && configVoConsumer.getSize() > ConfigConstants.MODIFIABLE_LIMIT_SIZE) {
                        configVoConsumer.setModifiable(false);
                    }
                }
        );
        return Collections.singletonList(configVo);
    }

    @Override
    public String readFileSchema(String type, String fileName) {
        try {
            String schemaFile = null;
            switch (type) {
                case "web":
                    if (fileName.equals("system_info.json")) {
                        schemaFile = "schema/system_info_schema.json";
                    }
                    break;
                case "rating":
                    if (fileName.equals("rating_rule.json")) {
                        schemaFile = "schema/risk_rating_schema.json";
                    }
                    break;
                case "checker":
                    if (fileName.equals("android.json")) {
                        schemaFile = "schema/checker_android_schema.json";
                    } else if (fileName.equals("ios.json")) {
                        schemaFile = "schema/checker_ios_schema.json";
                    } else if (fileName.equals("h5.json")) {
                        schemaFile = "schema/checker_h5_schema.json";
                    } else if (fileName.equals("wechat.json")) {
                        schemaFile = "schema/checker_wechat_schema.json";
                    } else if (fileName.equals("host.json")) {
                        schemaFile = "schema/checker_host_schema.json";
                    }
                    break;
                case "punish":
                    schemaFile = "schema/punish_schema.json";
                    break;
                case "agent":
                    schemaFile = "schema/agent_info_schema.json";
                    break;
                case "meta":
                    schemaFile = "schema/meta_schema.json";
                    break;
                case "device_id":
                    schemaFile = "schema/device_id_schema.json";
                    break;
                case "identify":
                    break;
                default:
                    schemaFile = "schema/amis_schema.json";
                    break;
            }
            if (schemaFile != null) {
                // 读取schema文件内容
                Path filePath = Paths.get(customWebConfig.getSystemConfigPath(), schemaFile);
                return FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
                // （暂时废弃）直接通过ClassLoader获取资源流，避免URI到Path再到File的转换
                /**
                 try (java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(schemaFile)) {
                 if (inputStream == null) {
                 log.error("Schema file not found: {}", schemaFile);
                 return null;
                 }
                 return org.apache.commons.io.IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                 }
                 */
            }
        } catch (Exception e) {
            log.error("read config file schema failed !", e);
        }
        return null;
    }

    @Override
    public String readFile(String type, String fileName) {
        try {
            Path filePath = Paths.get(configPath(type), fileName);
            return FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("read config file failed !", e);
        }
        return null;
    }

    @Override
    public void modifyConfig(String type, ConfigDto configDto) {
        try {
            Path filePath = Paths.get(configPath(type), configDto.getFileName());
            FileUtils.writeStringToFile(filePath.toFile(), configDto.getText(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("modify config file failed!", e);
        }
    }

    @Override
    public boolean addFile(String type, String fileName) {
        // 创建文件
        try {
            Path filePath = Paths.get(configPath(type), fileName);
            Files.createFile(filePath);
            return true;
        } catch (IOException e) {
            log.error("create config file failed!", e);
        }
        return false;
    }

    @Override
    public boolean deleteFile(String type, String fileName) {
        try {
            Path filePath = Paths.get(configPath(type), fileName);
            Files.deleteIfExists(filePath);
            return true;
        } catch (IOException e) {
            log.error("delete config file failed!", e);
        }
        return false;
    }

    @Override
    public boolean renameFile(String type, String originalFile, String newFile) {
        try {
            Path originalFilePath = Paths.get(configPath(type), originalFile);
            Path newFilePath = Paths.get(configPath(type), newFile);
            Files.move(originalFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("delete config file failed!", e);
        }
        return false;
    }

    /**
     * 设置配置文件路径
     */
    @Override
    public String configPath(String type) {
        // plugin-build|ef62f8fc-f5f8-4f67-b164-a045df47000b
        if (type.startsWith("plugin-build|")) {
            String[] typeArr = type.split("\\|");
            String sessionId = typeArr[1];
            // TODO 假设根据sessionId查询出对应的插件目录
            return String.format("%s/%s/%s/%s/", customWebConfig.getSessionWorkspacePath(), sessionId, "plugin", typeArr[2]);
        }
        // TODO 权限检查，仅允许访问指定目录下的文件
        return String.format("%s/%s_%s/", customWebConfig.getConfigPath(), type, "config");
    }

    @Override
    public void applyPolicy(String type, ConfigDto configDto) {
        switch (type) {
            case "meta":
                // 重新加载meta
                MetaData metaData = metaDataService.loadMetaData();
                // 初始化Clickhouse表
                clickhouseSchemeService.loadSchemeFromMetaData(metaData);
                log.info("刷新Schema");
                break;
            default:
                break;
        }
    }

    @Override
    public boolean addRootPath(String type) {
        // 创建目录
        File rootDir = new File(configPath(type));
        if (rootDir.mkdirs()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean ensureRootPath(String type) {
        try {
            Path rootPath = Paths.get(configPath(type));
            if (Files.exists(rootPath)) {
                return Files.isDirectory(rootPath);
            }
            Files.createDirectories(rootPath);
            return true;
        } catch (IOException e) {
            log.error("ensure config root path failed!", e);
        }
        return false;
    }

    /**
     * 判断指定配置目录下是否存在名为 fileName 的文件。
     *
     * @param type     配置类型
     * @param fileName 要查找的文件名
     * @return 如果文件存在返回 true，否则返回 false
     */
    @Override
    public boolean fileExistsInConfigPath(String type, String fileName) {
        String configPath = configPath(type);
        // 创建一个表示目录的File对象
        File directory = new File(configPath);
        // 检查目录是否存在并且是一个目录
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("指定的路径不是有效的目录: " + configPath);
            return false;
        }
        // 获取目录中的所有文件和文件夹
        File[] files = directory.listFiles();
        // 如果目录为空，返回false
        if (files == null) {
            return false;
        }
        // 遍历目录中的所有文件和文件夹
        for (File file : files) {
            // 检查文件名是否匹配
            if (file.getName().equals(fileName)) {
                // 如果匹配，返回true
                return true;
            }
        }
        // 如果遍历完所有文件都没有匹配，返回false
        return false;
    }


}
