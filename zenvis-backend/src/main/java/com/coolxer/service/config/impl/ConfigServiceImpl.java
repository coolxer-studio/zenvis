package com.coolxer.service.config.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.coolxer.commons.constants.ConfigConstants;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.model.config.vo.ConfigVo;
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
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        populateRelativePaths(configVo, Paths.get(path).toAbsolutePath().normalize());
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
            Path filePath = resolveExistingConfigPath(type, fileName);
            return FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("read config file failed !", e);
        }
        return null;
    }

    @Override
    public void modifyConfig(String type, ConfigDto configDto) {
        try {
            Path filePath = resolveExistingConfigPath(type, configDto.getFileName());
            FileUtils.writeStringToFile(filePath.toFile(), configDto.getText(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("modify config file failed!", e);
        }
    }

    @Override
    public boolean addFile(String type, String fileName) {
        // 创建空文件；正文由 config_apply 写入
        try {
            Path filePath = resolveConfigPath(type, fileName, false);
            Files.createFile(filePath);
            return true;
        } catch (IOException e) {
            log.error("create config file failed!", e);
        }
        return false;
    }

    @Override
    public boolean deleteFile(String type, String fileName) {
        return deleteEntry(type, fileName, false).isSuccess();
    }

    @Override
    public boolean renameFile(String type, String originalFile, String newFile) {
        try {
            String sourcePath = normalizeRelativePath(originalFile);
            String targetPath = normalizeRelativePath(newFile);
            String targetName = leafName(targetPath);
            String expectedTarget = siblingPath(sourcePath, targetName);
            if (!expectedTarget.equals(targetPath)) {
                return false;
            }
            return renameEntry(type, sourcePath, targetName).isSuccess();
        } catch (ApiException e) {
            return false;
        }
    }

    @Override
    public ConfigPathOperationResult renameEntry(String type,
                                                 String sourcePath,
                                                 String targetName) {
        String normalizedSource;
        try {
            normalizedSource = normalizeRelativePath(sourcePath);
        } catch (ApiException e) {
            return ConfigPathOperationResult.failure(
                    "rename", sourcePath, "源路径不安全或为空");
        }
        String normalizedTarget = targetName == null ? "" : targetName.trim();
        if (!isSafeLeafName(normalizedTarget)) {
            return ConfigPathOperationResult.failure(
                    "rename", normalizedSource,
                    "新名称必须是当前目录下的单个文件或目录名，不能包含路径分隔符");
        }
        Path source;
        try {
            source = resolveExistingConfigPath(type, normalizedSource);
        } catch (ApiException e) {
            return ConfigPathOperationResult.failure(
                    "rename", normalizedSource, "目标配置不存在或无权访问");
        }
        Path root = configRoot(type);
        if (source.equals(root)) {
            return ConfigPathOperationResult.failure(
                    "rename", normalizedSource, "不能重命名配置类型根目录");
        }
        String targetPath = siblingPath(normalizedSource, normalizedTarget);
        Path target;
        try {
            target = resolveConfigPath(type, targetPath, false);
        } catch (ApiException e) {
            return ConfigPathOperationResult.failure(
                    "rename", normalizedSource, "目标路径不安全或无权访问");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return operationFailure(
                    type, "rename", source, normalizedSource, targetPath,
                    "目标名称已存在，拒绝覆盖");
        }

        ConfigPathOperationResult result = new ConfigPathOperationResult()
                .setOperation("rename")
                .setKind(pathKind(source))
                .setSourcePath(normalizedSource)
                .setTargetPath(targetPath)
                .setAffectedPaths(relativePaths(type, source));
        try {
            Files.move(source, target);
            result.setSuccess(true)
                    .setChanged(true)
                    .setMessage("配置已重命名");
            return applyMetaOperation(type, result);
        } catch (IOException e) {
            log.error("rename config path failed!", e);
            return ConfigPathOperationResult.failure(
                    "rename", normalizedSource, "重命名失败：" + e.getMessage());
        }
    }

    @Override
    public ConfigPathOperationResult deleteEntry(String type,
                                                 String sourcePath,
                                                 boolean recursive) {
        String normalizedSource;
        try {
            normalizedSource = normalizeRelativePath(sourcePath);
        } catch (ApiException e) {
            return ConfigPathOperationResult.failure(
                    "delete", sourcePath, "源路径不安全或为空");
        }
        Path source;
        try {
            source = resolveExistingConfigPath(type, normalizedSource);
        } catch (ApiException e) {
            return ConfigPathOperationResult.failure(
                    "delete", normalizedSource, "目标配置不存在或无权访问");
        }
        Path root = configRoot(type);
        if (source.equals(root)) {
            return ConfigPathOperationResult.failure(
                    "delete", normalizedSource, "不能删除配置类型根目录");
        }

        List<String> affectedPaths = relativePaths(type, source);
        boolean directory = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS);
        if (directory && affectedPaths.size() > 1 && !recursive) {
            return operationFailure(
                    type, "delete", source, normalizedSource, null,
                    "目录非空，必须显式设置 recursive=true 才能递归删除")
                    .setAffectedPaths(affectedPaths);
        }

        ConfigPathOperationResult result = new ConfigPathOperationResult()
                .setOperation("delete")
                .setKind(directory ? "directory" : "file")
                .setSourcePath(normalizedSource)
                .setAffectedPaths(affectedPaths);
        try {
            WalkFileUtil.delete(source);
            result.setSuccess(true)
                    .setChanged(true)
                    .setMessage(directory ? "配置目录已删除" : "配置文件已删除");
            return applyMetaOperation(type, result);
        } catch (IOException e) {
            log.error("delete config path failed!", e);
            return ConfigPathOperationResult.failure(
                    "delete", normalizedSource, "删除失败：" + e.getMessage());
        }
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
    public void applyConfig(String type, ConfigDto configDto) {
        switch (type) {
            case "meta":
                // 重新加载meta
                MetaData metaData = metaDataService.reloadMetaDataOrThrow();
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
        try {
            resolveExistingConfigPath(type, fileName);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private void populateRelativePaths(ConfigVo node, Path root) {
        if (node == null || StringUtils.isBlank(node.getPath())) {
            return;
        }
        Path nodePath = Paths.get(node.getPath()).toAbsolutePath().normalize();
        if (nodePath.startsWith(root)) {
            node.setRelativePath(root.relativize(nodePath).toString().replace(File.separatorChar, '/'));
        }
        if (node.getNodes() != null) {
            node.getNodes().forEach(child -> populateRelativePaths(child, root));
        }
    }

    private Path resolveExistingConfigPath(String type, String fileName) {
        return resolveConfigPath(type, fileName, true);
    }

    private Path configRoot(String type) {
        return Paths.get(configPath(type)).toAbsolutePath().normalize();
    }

    private ConfigPathOperationResult applyMetaOperation(
            String type,
            ConfigPathOperationResult result) {
        if (!result.isSuccess()) {
            return result;
        }
        if (!"meta".equals(type)) {
            return result.setApplied(true);
        }
        try {
            ConfigDto configDto = new ConfigDto();
            configDto.setFileName(result.getSourcePath());
            applyConfig(type, configDto);
            return result.setApplied(true)
                    .setMessage(result.getMessage() + "，Meta 已严格重载并刷新 Schema");
        } catch (RuntimeException e) {
            log.error("reload Meta after config {} failed!", result.getOperation(), e);
            return result.setSuccess(false)
                    .setApplied(false)
                    .setMessage(result.getMessage()
                            + "，但 Meta 严格重载或 Schema 刷新失败："
                            + safeMessage(e));
        }
    }

    private ConfigPathOperationResult operationFailure(String type,
                                                       String operation,
                                                       Path source,
                                                       String sourcePath,
                                                       String targetPath,
                                                       String message) {
        return new ConfigPathOperationResult()
                .setSuccess(false)
                .setOperation(operation)
                .setKind(pathKind(source))
                .setSourcePath(sourcePath)
                .setTargetPath(targetPath)
                .setChanged(false)
                .setApplied(false)
                .setAffectedPaths(relativePaths(type, source))
                .setMessage(message);
    }

    private List<String> relativePaths(String type, Path source) {
        Path root = configRoot(type);
        if (root == null || source == null) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(source)) {
            List<String> result = new ArrayList<>();
            paths.sorted(Comparator.naturalOrder()).forEach(path -> {
                if (path.equals(root)) {
                    return;
                }
                result.add(root.relativize(path).toString()
                        .replace(File.separatorChar, '/'));
            });
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    private String pathKind(Path path) {
        return path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                ? "directory" : "file";
    }

    private String normalizeRelativePath(String path) {
        if (StringUtils.isBlank(path)) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isBlank() || normalized.equals(".")) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }
        }
        return normalized;
    }

    private boolean isSafeLeafName(String name) {
        return StringUtils.isNotBlank(name)
                && !name.contains("/")
                && !name.contains("\\")
                && !".".equals(name)
                && !"..".equals(name);
    }

    private String leafName(String path) {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    private String siblingPath(String sourcePath, String targetName) {
        int index = sourcePath.lastIndexOf('/');
        return index < 0 ? targetName : sourcePath.substring(0, index + 1) + targetName;
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return StringUtils.isBlank(message) ? error.getClass().getSimpleName() : message;
    }

    private Path resolveConfigPath(String type, String fileName, boolean requireExisting) {
        if (StringUtils.isBlank(fileName)) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
        try {
            Path requested = Paths.get(fileName);
            if (requested.isAbsolute()) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }

            Path root = Paths.get(configPath(type)).toAbsolutePath().normalize();
            Path resolved = root.resolve(requested).normalize();
            if (resolved.equals(root) || !resolved.startsWith(root)) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }
            if (!Files.isDirectory(root)) {
                throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
            }

            Path realRoot = root.toRealPath();
            if (requireExisting) {
                if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)
                        || !resolved.toRealPath().startsWith(realRoot)) {
                    throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
                }
            } else {
                Path existingAncestor = resolved.getParent();
                while (existingAncestor != null
                        && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
                    existingAncestor = existingAncestor.getParent();
                }
                if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realRoot)) {
                    throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
                }
            }
            return resolved;
        } catch (InvalidPathException | IOException e) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY);
        }
    }


}
