package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.configuration.extend.ExtendJarManager;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.dao.mysql.entity.RolePermission;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.dao.mysql.repository.MenuRepository;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.dao.mysql.repository.PluginRepository;
import com.coolxer.dao.mysql.repository.RolePermissionRepository;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
import com.coolxer.model.system.dto.PluginUpgradeDto;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.model.system.vo.PluginVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.McpClientService;
import com.coolxer.service.dih.rag.VectorStoreInitializerService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PluginMigrationService;
import com.coolxer.service.system.PluginService;
import com.coolxer.service.system.PushTaskService;
import com.coolxer.utils.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 插件接口实现
 */
@Slf4j
@Service
public class PluginServiceImpl implements PluginService {

    private static final String PLUGIN_DASHBOARD_DIR_NAME = "05_dashboard";
    private static final String PLUGIN_MCP_DIR_NAME = "06_mcp";
    private static final String PLUGIN_SKILL_DIR_NAME = "07_skill";
    private static final String PLUGIN_MENU_DIR_NAME = "08_menu";
    private static final String DASHBOARD_LOW_CODE_DIR_NAME = "low-code";
    private static final String DASHBOARD_HTML_PAGE_DIR_NAME = "html-page";
    private static final String PLUGIN_UPGRADE_DIR_NAME = "upgrade";
    private static final String UPGRADE_SNAPSHOT_FILE_NAME = "snapshot.json";
    private static final long MAX_PLUGIN_PACKAGE_BYTES = 300L * 1024L * 1024L;
    private static final Pattern SAFE_PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,255}$");
    private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final int LOG_QUEUE_CAPACITY = 512;
    private static final long LOG_POLL_TIMEOUT_SECONDS = 2L;

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private McpServerConfigRepository mcpServerConfigRepository;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private MenuService menuService;

    @Autowired
    private ClickhouseSchemeService clickhouseSchemeService;

    @Autowired
    private MetaDataService metaDataService;

    @Autowired
    private PushTaskService pushTaskService;

    @Autowired
    private ExtendJarManager extendJarManager;

    @Autowired
    private PluginMigrationService pluginMigrationService;

    @Autowired
    private VectorStoreInitializerService vectorStoreInitializerService;

    @Autowired
    private SkillService skillService;

    @Autowired
    private McpClientService mcpClientService;

    @Autowired
    private PluginOperationExecutor pluginOperationExecutor;

    private static final ConcurrentHashMap<Long, PluginLogBuffer> LOG_CACHE = new ConcurrentHashMap<>();

    @Override
    public List<PluginVo> findAll() {
        return pluginRepository.findAll().stream().map(PluginVo::new).toList();
    }

    @Override
    public PageRowsVo<PluginVo> getPageList(PluginSearchDto pluginSearchDto) {
        try {
            Pageable pageable = PageRequest.of(pluginSearchDto.getPage() - 1, pluginSearchDto.getPerPage());
            Page<Plugin> byPage;
            byPage = pluginRepository.findByPage(pageable, pluginSearchDto.getName(), pluginSearchDto.getPackageName());
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(PluginVo::new).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public Plugin create(PluginDto pluginDto) {
        checkCreateOrUpdate(pluginDto);
        Plugin plugin = new Plugin();
        plugin.updateFromDto(pluginDto);
        plugin.setStatus(PluginStatusType.UN_INSTALL);
        plugin.setOperationMessage("插件已创建");
        plugin.setOperationError(null);
        if (StringUtils.isEmpty(plugin.getIcon())) {
            // 设置默认图标
            plugin.setIcon("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAAACXBIWXMAAAsTAAALEwEAmpwYAAADrklEQVR4nO2dOWgVURiFPyVBtAha+lwQlwi2ro0xnUU0plDxRhG10xiLWGovagQrFRVcSkshuASNgmgTt0pLLRQRF1xAweXJwH0gwRff8s+8O3fOgVMOPM5355//zr3vDkiSJEmSJEmSJEmSJElSbZoBdALdQA+wBXCBeRcwBBwBbgDvgHIT/go8B64D+4F5rQh+OrAS2BZAwK5O9wMngbdNgqj4F3AFWJBV+HMDHemuTu8Gxo0gJP4M9KYd/tKcjno3yd1wzRBCcjccSHPkxxS++wvCuDGE3jQetjGUHTdJOfpgXI5KlgBWBxCSS9lnDQEkPmc5+mMsPW6CtwPvDQH89GW7aXUGEI7LyDeM74IBCwDdAQTjMvJRYwAjFgA2BBCMy8hDxgCSGXPTirn7cf/ohiwBfLEA4ArmsrEFAAHIlcsCgAAIAALgBEAAygKAALQ6EFd0AEV4E+q8d4QIoCeAYFxGPhgigGUBBOMy8tUQAUwBuqr84K3AWmARMBNo8+4AZgPLgU0ZBjgIXASeAq+B794vgVvAMLCzyrXDfhElOAAVCAs8iPXAGmC+D7uWaxNAfSkGPwCM+QXx/wXyDXgAnAYOA8eBe8DvFMI3A2Ch9knuJNeET/hQ0wgvKgCVu2G5YfiXUhy5UQL43zPF1TnyQw8/SACVctTXZM0PuewEDwD/YHYN+k4AweYewJQGW9TBGrudUBy0VjT44G11qNEAKDUA4EkAoUYDoKMBAG8CCDUaAG0NAMhL95MLAO0CkL8S9DqAUKMBUNJDOH9t6IUAQi30RGy/JmI2WtxA+BWPBRBsrgHoZVwEr6OP5qQUBSUtyES4JDkceDkyG7kLgXUTFuXba7x2ccqL8vuA23Usyt8HTgGHfCm7m2I5a1pTffDVtqV0+YBnTdiWUvJ9fpbbUgb8POEx8MqHnfgFMOp3QFTblnIs1G0p2phFawHob6q0FoA259JaAK5gLgsAAiAACIATAAEoCwAC0OpAnAAUy2UBQACKemDTHuPwP1kAKNK7oIPGAJ5ZANChfbT20L4iHVs5agxgrwWAohzcusP44NYfVge3JloVQEAuZZ83Hv1nMP5gw+YAQnIpdj8fjbuf5KQAU82O+Pj6h4bhJ4v7G0lJnZFB6PffgrEMP/kTYaqaE0k52gM8Mi47yZwpE03zO9y25XTUnzKs+cmov5xGza+1RV0S+GesdvsZbjLJumnQan7xM9wRvyHMrNWUJEmSJEmSJEmSJEmSiFp/AAmQ4TkXK5gLAAAAAElFTkSuQmCC");
        }
        return pluginRepository.save(plugin);
    }

    @Override
    public void delete(Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return;
        }
        PluginStatusType status = normalizeStatus(plugin.getStatus());
        if (status == PluginStatusType.INSTALLED || status == PluginStatusType.UPGRADE_FAILED || status.isInProgress()) {
            // 已经加载的不支持删除
            throw new ApiException(ResultCodeEnum.PLUGIN_IS_INSTALLED);
        }
        try {
            if (StringUtils.isNotBlank(plugin.getPluginPath())) {
                Path pluginPath = safePluginPath(plugin.getPluginPath());
                if (Files.exists(pluginPath) && Files.isRegularFile(pluginPath)) {
                    WalkFileUtil.delete(pluginPath);
                }
            }
        } catch (IOException e) {
            log.warn("删除插件包文件失败: id={}", id, e);
        }
        pluginRepository.deleteById(id);
    }


    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public PluginVo info(Long id) {
        try {
            Optional<Plugin> optionalPlugin = pluginRepository.findById(id);
            return optionalPlugin.map(PluginVo::new).orElse(null);
        } catch (Exception e) {
            log.error("获取对象失败, id: {}", id, e);
            return null;
        }
    }

    @Override
    public PluginVo uploadFile(MultipartFile file) {
        validateUploadFile(file);
        try {
            Path directory = pluginRoot();
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            Path tempDir = directory.resolve("temp").resolve(DateUtil.getCurrentDateTime().replace(" ", "/"));
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }
            String originalFilename = Objects.requireNonNull(file.getOriginalFilename(), "插件包文件名不能为空");
            String fileName = Paths.get(originalFilename).getFileName().toString();
            Path path = requireChildPath(tempDir.resolve(fileName), directory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            }
            TarGzUtil.validateTarGz(path);
            // 解析插件包，提取信息
            String pluginJsonString = TarGzUtil.readRootFile(path, "index.json");
            if (StringUtils.isBlank(pluginJsonString)) {
                throw invalidPluginPackage("插件包缺少 index.json");
            }
            PluginVo pluginVo = JacksonUtil.toObject(pluginJsonString, PluginVo.class);
            if (pluginVo == null || StringUtils.isBlank(pluginVo.getName()) || StringUtils.isBlank(pluginVo.getPackageName())) {
                throw invalidPluginPackage("插件包 index.json 缺少插件名称或包名");
            }
            validatePackageName(pluginVo.getPackageName());
            // icon转化
            if (StringUtils.isNotBlank(pluginVo.getIcon()) && !ImageDataUriUtil.isDataUrl(pluginVo.getIcon())) {
                String iconBase64 = TarGzUtil.readBase64File(path, pluginVo.getIcon());
                if (iconBase64 == null) {
                    throw invalidPluginPackage("插件图标文件不存在");
                }
                pluginVo.setIcon(ImageDataUriUtil.toDataUri(pluginVo.getIcon(), iconBase64));
            }
            pluginVo.setPluginPath(path.toString());
            return pluginVo;
        } catch (IOException e) {
            throw invalidPluginPackage(e.getMessage());
        }
    }

    @Override
    public String base64Icon(MultipartFile file) {
        // 获取文件的字节内容
        try {
            // 使用 Base64 编码器将字节内容编码为 Base64 字符串
            byte[] fileContent = file.getBytes();
            String encodedFile = Base64.getEncoder().encodeToString(fileContent);
            return ImageDataUriUtil.toDataUri(file.getOriginalFilename(), encodedFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void export(Long id, jakarta.servlet.http.HttpServletResponse response) {
        // 1.查询插件详情
        Plugin plugin = getPluginOrThrow(id);
        validatePackageName(plugin.getPackageName());
        Path pluginTarGzPath = null;
        if (normalizeStatus(plugin.getStatus()) != PluginStatusType.INSTALLED && StringUtils.isNotEmpty(plugin.getPluginPath())) {
            // 未安装状态，存在默认插件包，直接输出
            pluginTarGzPath = safePluginPackagePath(plugin.getPluginPath());
        } else {
            // 已安装状态，或者没有默认包，以加载内容输出插件包.构建打包属性包装类
            Path installedRoot = installedPluginRoot(plugin);
            PluginPackTool pluginPackTool = new PluginPackTool().buildPacker(pluginRoot().toString(), plugin.getPackageName())
                    .init().mkdir();
            // 2.构建插件包
            try {
                // 2-1 构建index.json,存放icon,生成readme
                PluginVo pluginVo = new PluginVo();
                pluginVo.setName(plugin.getName());
                pluginVo.setPackageName(plugin.getPackageName());
                pluginVo.setVersion(plugin.getVersion());
                pluginVo.setDescription(plugin.getDescription());
                pluginVo.setAuthor(plugin.getAuthor());
                pluginVo.setIcon(pluginPackTool.saveImageFile(plugin.getIcon()));
                pluginPackTool.writeIndexJson(JacksonUtil.toJson(pluginVo));
                // 拷贝 DOC 文档
                Path docPath = requireChildPath(installedRoot.resolve("00_doc"), installedRoot);
                pluginPackTool.copyDoc(docPath);
                // 拷贝 Skill 配置
                Path installedSkillPath = skillService.getInstalledPluginSkillPath(plugin.getPackageName());
                Path currentSkillPath = hasDirectoryContent(installedSkillPath)
                        ? installedSkillPath
                        : resolvePluginSkillPath(installedRoot);
                pluginPackTool.copySkill(currentSkillPath);
                // 2-2 构建meta文件
                try (Stream<Path> paths = Files.walk(Paths.get(customWebConfig.getRetrievalMetaFilePath()))) {
                    paths.filter(Files::isRegularFile) // 过滤出文件
                            .filter(path -> path.getFileName().toString().startsWith(plugin.getPackageName())) // 过滤
                            .forEach(path -> {
                                pluginPackTool.copyMeta(path, plugin.getPackageName());
                            });
                }
                // 2-3 构建push-task文件
                List<PushTaskVo> pushTaskVoList = pushTaskService.findBySourceMark(plugin.getPackageName()).stream().map(pushTaskVo -> {
                    String configFile = pushTaskVo.getId() + "." + pushTaskService.detectFormat(pushTaskVo.getConfig());
                    // 只需要name,description,config
                    PushTaskVo newPushTaskVo = new PushTaskVo();
                    newPushTaskVo.setConfig(configFile);
                    pluginPackTool.writePushTaskToml(configFile, pushTaskVo.getConfig());
                    newPushTaskVo.setName(pushTaskVo.getName());
                    newPushTaskVo.setDescription(pushTaskVo.getDescription());
                    return newPushTaskVo;
                }).toList();
                pluginPackTool.writePushTaskConfig(JacksonUtil.toJson(pushTaskVoList));
                // 2-4 查看并导出api服务jar包
                Path currentApiJarPath = requireChildPath(installedRoot.resolve("03_api"), installedRoot);
                pluginPackTool.copyApiJar(currentApiJarPath);
                // 2-5 构建UI配置
                Path installedUIPath = requireChildPath(installedRoot.resolve("04_ui"), installedRoot);
                exportPluginUi(plugin.getPackageName(), installedUIPath, pluginPackTool.getUiPath());
                // 2-6 构建看板配置
                exportPluginDashboards(plugin.getPackageName(), pluginPackTool);
                // 2-7 构建MCP服务配置
                exportPluginMcpServers(plugin.getPackageName(), pluginPackTool);
                // 2-8 构建菜单配置
                List<Menu> menuList = menuService.findBySource(plugin.getPackageName());
                List<MenuVo> menuVoList = menuList.stream().map(pushTask -> {
                    MenuVo menuVo = new MenuVo();
                    menuVo.setName(pushTask.getName());
                    menuVo.setParams(pushTask.getParams());
                    menuVo.setType(pushTask.getType());
                    return menuVo;
                }).toList();
                pluginPackTool.writeMenuConfig(JacksonUtil.toJson(menuVoList));
                // 2-9 压缩目录
                pluginTarGzPath = pluginPackTool.compressDirToTarGz();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // 3.下载插件
        responseDownload(response, pluginTarGzPath);
    }

    @Override
    public synchronized PluginVo uninstall(Long id) {
        Plugin plugin = getPluginOrThrow(id);
        PluginStatusType status = normalizeStatus(plugin.getStatus());
        if (status.isInProgress()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_OPERATION_RUNNING);
        }
        if (!status.canUninstall()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_IS_UNINSTALL);
        }
        validatePackageName(plugin.getPackageName());
        resetLogs(id);
        updateOperationState(plugin, PluginStatusType.UNINSTALLING, "卸载已开始", null, true);
        Plugin saved = pluginRepository.save(plugin);
        try {
            pluginOperationExecutor.submit(() -> executeUninstall(id));
        } catch (RuntimeException e) {
            String error = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            finishOperation(id, PluginStatusType.UNINSTALL_FAILED, "卸载任务提交失败", error);
            writeLog(id, "失败......" + error);
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "插件卸载任务提交失败");
        }
        return new PluginVo(saved);
    }

    @Override
    public synchronized PluginVo install(Long id) {
        Plugin plugin = getPluginOrThrow(id);
        PluginStatusType status = normalizeStatus(plugin.getStatus());
        if (status.isInProgress()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_OPERATION_RUNNING);
        }
        if (!status.canInstall()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_IS_INSTALLED);
        }
        validatePackageName(plugin.getPackageName());
        safePluginPackagePath(plugin.getPluginPath());
        resetLogs(id);
        updateOperationState(plugin, PluginStatusType.INSTALLING, "安装已开始", null, true);
        Plugin saved = pluginRepository.save(plugin);
        try {
            pluginOperationExecutor.submit(() -> executeInstall(id));
        } catch (RuntimeException e) {
            String error = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            finishOperation(id, PluginStatusType.INSTALL_FAILED, "安装任务提交失败", error);
            writeLog(id, "失败......" + error);
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "插件安装任务提交失败");
        }
        return new PluginVo(saved);
    }

    @Override
    public synchronized PluginVo upgrade(Long id, PluginUpgradeDto upgradeDto) {
        Plugin plugin = getPluginOrThrow(id);
        PluginStatusType status = normalizeStatus(plugin.getStatus());
        if (status.isInProgress()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_OPERATION_RUNNING);
        }
        if (!status.canUpgrade()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_IS_UNINSTALL.getCode(), "只有已安装插件可以升级");
        }
        if (upgradeDto == null || StringUtils.isBlank(upgradeDto.getPluginPath())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }

        validatePackageName(plugin.getPackageName());
        Path candidateArchive = safePluginPackagePath(upgradeDto.getPluginPath());
        String operationId = UUID.randomUUID().toString();
        Path operationRoot = upgradeOperationRoot(id, operationId);
        try {
            Files.createDirectories(operationRoot);
            Path candidateRoot = requireChildPath(operationRoot.resolve("candidate"), operationRoot);
            TarGzUtil.validateTarGz(candidateArchive);
            TarGzUtil.decompressTarGz(candidateArchive, candidateRoot);
            UpgradeCandidate candidate = preflightUpgrade(plugin, candidateRoot);
            createUpgradeSnapshot(plugin, operationId);

            resetLogs(id);
            plugin.setPendingUpgradePath(candidateArchive.toString());
            plugin.setPendingUpgradeVersion(candidate.descriptor().getVersion());
            plugin.setUpgradeOperationId(operationId);
            updateOperationState(plugin, PluginStatusType.UPGRADING,
                    "升级预检完成，等待进入维护窗口", null, true);
            Plugin saved = pluginRepository.save(plugin);
            try {
                pluginOperationExecutor.submit(() -> executeUpgrade(id));
            } catch (RuntimeException submitError) {
                plugin.setPendingUpgradePath(null);
                plugin.setPendingUpgradeVersion(null);
                plugin.setUpgradeOperationId(null);
                updateOperationState(plugin, PluginStatusType.INSTALLED,
                        "升级任务提交失败", submitError.getMessage(), false);
                pluginRepository.save(plugin);
                deleteIfExists(operationRoot);
                throw submitError;
            }
            return new PluginVo(saved);
        } catch (ApiException e) {
            cleanupFailedPreparation(operationRoot);
            throw e;
        } catch (Exception e) {
            cleanupFailedPreparation(operationRoot);
            throw invalidPluginPackage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    @Override
    public synchronized PluginVo recoverUpgrade(Long id) {
        Plugin plugin = getPluginOrThrow(id);
        if (normalizeStatus(plugin.getStatus()) != PluginStatusType.UPGRADE_FAILED) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "只有升级失败的插件可以恢复旧版本");
        }
        requireUpgradeSnapshot(plugin);
        resetLogs(id);
        updateOperationState(plugin, PluginStatusType.UPGRADING, "开始恢复旧版本", plugin.getOperationError(), true);
        Plugin saved = pluginRepository.save(plugin);
        try {
            pluginOperationExecutor.submit(() -> executeUpgradeRecovery(id, "手动恢复旧版本"));
        } catch (RuntimeException e) {
            finishOperation(id, PluginStatusType.UPGRADE_FAILED, "恢复任务提交失败", e.getMessage());
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "插件恢复任务提交失败");
        }
        return new PluginVo(saved);
    }

    @Override
    public void recoverInterruptedUpgrades() {
        pluginRepository.findAll().stream()
                .filter(plugin -> normalizeStatus(plugin.getStatus()) == PluginStatusType.UPGRADING)
                .forEach(plugin -> {
                    log.warn("检测到未完成的插件升级，开始恢复: id={}, package={}, operation={}",
                            plugin.getId(), plugin.getPackageName(), plugin.getUpgradeOperationId());
                    resetLogs(plugin.getId().longValue());
                    executeUpgradeRecovery(plugin.getId().longValue(), "应用重启时自动恢复未完成升级");
                });
    }

    private void cleanupFailedPreparation(Path operationRoot) {
        try {
            deleteIfExists(operationRoot);
        } catch (IOException cleanupError) {
            log.warn("清理升级预检目录失败: {}", operationRoot, cleanupError);
        }
    }

    private UpgradeCandidate preflightUpgrade(Plugin plugin, Path candidateRoot) throws IOException {
        PluginPackTool candidatePack = new PluginPackTool().buildFromDirectory(candidateRoot).init();
        if (!Files.isRegularFile(candidatePack.getIndexJsonPath())) {
            throw invalidPluginPackage("候选插件缺少根目录 index.json");
        }
        validateAllJsonFiles(candidateRoot);
        PluginVo descriptor = JacksonUtil.toObject(Files.readString(candidatePack.getIndexJsonPath()), PluginVo.class);
        if (descriptor == null || StringUtils.isAnyBlank(descriptor.getName(), descriptor.getPackageName(), descriptor.getVersion())) {
            throw invalidPluginPackage("候选插件 index.json 缺少名称、包名或版本");
        }
        validatePackageName(descriptor.getPackageName());
        validateUpgradeIdentity(plugin, descriptor);
        if (StringUtils.isNotBlank(descriptor.getIcon()) && !ImageDataUriUtil.isDataUrl(descriptor.getIcon())) {
            Path iconPath = requireChildPath(candidateRoot.resolve(normalizeRelativePath(descriptor.getIcon(), "插件图标路径")), candidateRoot);
            if (!Files.isRegularFile(iconPath)) {
                throw invalidPluginPackage("候选插件图标文件不存在");
            }
            descriptor.setIcon(ImageDataUriUtil.toDataUri(
                    descriptor.getIcon(), Base64.getEncoder().encodeToString(Files.readAllBytes(iconPath))));
        }

        List<Path> apiJars = candidatePack.listApiFiles();
        if (apiJars.size() > 1) {
            throw invalidPluginPackage("每个插件的 03_api 目录只能包含一个 Jar");
        }
        validateApiJar(apiJars);
        inspectPluginUi(plugin.getPackageName(), candidatePack.getUiPath(), true);
        validatePushTaskCandidate(candidatePack);
        validateDashboardCandidate(plugin.getPackageName(), candidatePack);
        validateMcpCandidate(plugin.getPackageName(), candidatePack);
        validateMenuCandidate(plugin.getPackageName(), candidatePack);

        List<Path> candidateMetaFiles = candidatePack.listMetaFiles();
        Set<String> metaFileNames = new HashSet<>();
        for (Path metaFile : candidateMetaFiles) {
            if (!metaFileNames.add(metaFile.getFileName().toString())) {
                throw invalidPluginPackage("01_meta 中存在同名 Meta 文件: " + metaFile.getFileName());
            }
        }
        List<Path> currentMetaFiles = listInstalledMetaFiles(plugin.getPackageName());
        MetaData currentPluginMeta = readRawMetaData(currentMetaFiles);
        MetaData candidatePluginMeta = readRawMetaData(candidateMetaFiles);
        validateMetaSqlSafety(candidatePluginMeta);
        validateAdditiveMetaChange(currentPluginMeta, candidatePluginMeta);
        validateCandidateMetaAgainstGlobal(plugin.getPackageName(), candidateMetaFiles, candidatePluginMeta);
        pluginMigrationService.validateMysql(plugin.getPackageName(), candidatePack.listMysqlMigrationFiles());
        return new UpgradeCandidate(descriptor, candidatePack, candidatePluginMeta);
    }

    private void validateUpgradeIdentity(Plugin plugin, PluginVo descriptor) {
        if (!Objects.equals(plugin.getPackageName(), descriptor.getPackageName())) {
            throw invalidPluginPackage("升级包名必须与当前插件一致: " + plugin.getPackageName());
        }
        SemanticVersion currentVersion;
        SemanticVersion candidateVersion;
        try {
            currentVersion = SemanticVersion.parse(plugin.getVersion());
            candidateVersion = SemanticVersion.parse(descriptor.getVersion());
        } catch (IllegalArgumentException e) {
            throw invalidPluginPackage(e.getMessage());
        }
        if (candidateVersion.compareTo(currentVersion) <= 0) {
            throw invalidPluginPackage("升级版本必须严格高于当前版本 " + plugin.getVersion());
        }
    }

    private void validateApiJar(List<Path> apiJars) {
        if (apiJars.isEmpty()) {
            return;
        }
        try (JarFile jarFile = new JarFile(apiJars.get(0).toFile())) {
            boolean containsClass = jarFile.stream()
                    .anyMatch(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"));
            if (!containsClass) {
                throw invalidPluginPackage("03_api Jar 中未发现 class 文件");
            }
        } catch (IOException e) {
            throw invalidPluginPackage("03_api Jar 无法读取: " + e.getMessage());
        }
    }

    private void validateAllJsonFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .toList()) {
                try {
                    JacksonConfig.OBJECT_MAPPER.readTree(path.toFile());
                } catch (Exception e) {
                    throw invalidPluginPackage("JSON 配置解析失败: " + root.relativize(path));
                }
            }
        }
    }

    private List<PushTaskDto> readPushTaskDefinitions(PluginPackTool pluginPackTool) {
        List<PushTaskDto> definitions = JacksonUtil.toList(pluginPackTool.readPushTaskConfigFile(),
                new TypeReference<List<PushTaskDto>>() { });
        for (PushTaskDto definition : definitions) {
            if (definition == null || StringUtils.isBlank(definition.getName())) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
            }
            String configRef = StringUtils.defaultString(definition.getConfig());
            if (StringUtils.endsWithAny(configRef, ".toml", ".yaml", ".yml", ".json")) {
                Path relative = normalizeRelativePath(configRef, "推送任务配置路径");
                Path configFile = requireChildPath(pluginPackTool.getPushTaskPath().resolve(relative),
                        pluginPackTool.getPushTaskPath());
                if (!Files.isRegularFile(configFile)) {
                    throw invalidPluginPackage("推送任务配置文件不存在: " + configRef);
                }
                try {
                    definition.setConfig(Files.readString(configFile));
                } catch (IOException e) {
                    throw invalidPluginPackage("读取推送任务配置失败: " + configRef);
                }
            }
        }
        return definitions;
    }

    private void validatePushTaskCandidate(PluginPackTool pluginPackTool) {
        Set<String> names = new HashSet<>();
        for (PushTaskDto definition : readPushTaskDefinitions(pluginPackTool)) {
            if (!names.add(definition.getName())) {
                throw invalidPluginPackage("推送任务名称重复: " + definition.getName());
            }
        }
    }

    private List<DashboardDto> readDashboardDefinitions(PluginPackTool pluginPackTool) {
        return JacksonUtil.toList(pluginPackTool.readDashboardConfigFile(),
                new TypeReference<List<DashboardDto>>() { });
    }

    private void validateDashboardCandidate(String packageName, PluginPackTool pluginPackTool) {
        Set<String> codes = new HashSet<>();
        Set<String> lowCodeIndexes = new HashSet<>();
        List<DashboardDto> definitions = readDashboardDefinitions(pluginPackTool);
        for (DashboardDto definition : definitions) {
            if (definition == null || StringUtils.isAnyBlank(definition.getName(), definition.getCode())
                    || definition.getType() == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
            }
            validateResourceName(definition.getCode(), "看板编码");
            if (!codes.add(definition.getCode())) {
                throw invalidPluginPackage("看板编码重复: " + definition.getCode());
            }
            Dashboard existing = dashboardRepository.findByCode(definition.getCode()).orElse(null);
            if (existing != null && !Objects.equals(packageName, existing.getSource())) {
                throw invalidPluginPackage("看板编码已被其他来源占用: " + definition.getCode());
            }
            if (definition.getType() == DashboardType.LOW_CODE_PAGE) {
                validateResourceName(definition.getConfigIndex(), "看板配置索引");
                if (!lowCodeIndexes.add(definition.getConfigIndex())) {
                    throw invalidPluginPackage("看板低代码配置索引重复: " + definition.getConfigIndex());
                }
                Path source = requireChildPath(pluginPackTool.getDashboardLowCodePath()
                        .resolve(definition.getConfigIndex() + "_config"), pluginPackTool.getDashboardLowCodePath());
                if (!Files.isDirectory(source)) {
                    throw invalidPluginPackage("看板低代码配置不存在: " + definition.getConfigIndex());
                }
            } else if (definition.getType() == DashboardType.HTML_PAGE) {
                Path relative = normalizeRelativePath(definition.getHtmlPath(), "HTML看板路径");
                Path source = requireChildPath(pluginPackTool.getDashboardHtmlPath().resolve(relative),
                        pluginPackTool.getDashboardHtmlPath());
                if (!Files.isRegularFile(source)) {
                    throw invalidPluginPackage("HTML看板文件不存在: " + definition.getHtmlPath());
                }
            } else if (definition.getType() == DashboardType.LINK && StringUtils.isBlank(definition.getUrl())) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
            }
        }
        for (Dashboard dashboard : dashboardRepository.findBySource(packageName)) {
            if (Boolean.TRUE.equals(dashboard.getIsDefault()) && !codes.contains(dashboard.getCode())) {
                throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED.getCode(),
                        "升级包不能删除当前默认看板: " + dashboard.getCode());
            }
        }
    }

    private List<McpServerDto> readMcpDefinitions(PluginPackTool pluginPackTool) {
        return JacksonUtil.toList(pluginPackTool.readMcpConfigFile(),
                new TypeReference<List<McpServerDto>>() { });
    }

    private void validateMcpCandidate(String packageName, PluginPackTool pluginPackTool) {
        Set<String> codes = new HashSet<>();
        for (McpServerDto definition : readMcpDefinitions(pluginPackTool)) {
            if (definition == null || StringUtils.isAnyBlank(definition.getCode(), definition.getName(), definition.getBaseUrl())) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
            }
            definition.setCode(normalizeMcpCode(definition.getCode()));
            if (!codes.add(definition.getCode())) {
                throw invalidPluginPackage("MCP服务标识重复: " + definition.getCode());
            }
            McpServerConfig existing = mcpServerConfigRepository.findByCode(definition.getCode()).orElse(null);
            if (existing != null && !Objects.equals(packageName, existing.getSource())) {
                throw invalidPluginPackage("MCP服务标识已被其他来源占用: " + definition.getCode());
            }
        }
    }

    private List<MenuDto> readMenuDefinitions(PluginPackTool pluginPackTool) {
        return JacksonUtil.toList(pluginPackTool.readMenuConfigFile(),
                new TypeReference<List<MenuDto>>() { });
    }

    private void validateMenuCandidate(String packageName, PluginPackTool pluginPackTool) {
        Map<String, Long> liveKeys = new HashMap<>();
        for (Menu menu : menuService.findBySource(packageName)) {
            String key = menuMatchKey(menu.getType(), menu.getParams(), menu.getRoute(), menu.getName());
            if (liveKeys.putIfAbsent(key, menu.getId().longValue()) != null) {
                throw invalidPluginPackage("现有菜单无法唯一匹配: " + key);
            }
        }
        Set<String> candidateKeys = new HashSet<>();
        for (MenuDto definition : readMenuDefinitions(pluginPackTool)) {
            if (definition == null || StringUtils.isBlank(definition.getName()) || definition.getType() == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
            }
            String route = definition.getType() == com.coolxer.commons.enums.MenuType.BUILT_APP
                    ? definition.getRoute() : definition.getType().getRoute();
            String key = menuMatchKey(definition.getType(), definition.getParams(), route, definition.getName());
            if (!candidateKeys.add(key)) {
                throw invalidPluginPackage("升级包菜单无法唯一匹配: " + key);
            }
        }
    }

    private String menuMatchKey(Object type, String params, String route, String name) {
        if (StringUtils.isNotBlank(params)) {
            return String.valueOf(type) + "|params|" + params;
        }
        return String.valueOf(type) + "|route|" + StringUtils.defaultString(route)
                + "|name|" + StringUtils.defaultString(name);
    }

    private List<Path> listInstalledMetaFiles(String packageName) throws IOException {
        Path metaRoot = requireChildPath(configRoot().resolve("meta_config"), configRoot());
        if (!Files.isDirectory(metaRoot)) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(metaRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(packageName + "_"))
                    .sorted()
                    .toList();
        }
    }

    private MetaData readRawMetaData(List<Path> files) throws IOException {
        MetaData merged = new MetaData();
        for (Path file : files) {
            MetaData part = JacksonUtil.toObject(Files.readString(file), MetaData.class);
            if (part == null) {
                throw invalidPluginPackage("Meta 文件为空: " + file.getFileName());
            }
            merged.merge(part);
        }
        return merged;
    }

    private void validateCandidateMetaAgainstGlobal(String packageName,
                                                    List<Path> candidateMetaFiles,
                                                    MetaData candidateMeta) throws IOException {
        Path metaRoot = requireChildPath(configRoot().resolve("meta_config"), configRoot());
        List<Path> globalFiles = new ArrayList<>();
        if (Files.isDirectory(metaRoot)) {
            try (Stream<Path> paths = Files.walk(metaRoot)) {
                globalFiles.addAll(paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().startsWith(packageName + "_"))
                        .toList());
            }
        }
        MetaData otherMeta = readRawMetaData(globalFiles);
        Set<String> otherTables = otherMeta.getEntity().stream()
                .map(item -> item == null ? null : item.getTableName())
                .filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> candidateTables = new HashSet<>();
        for (com.coolxer.model.retrieval.meta.DataEntity entity : candidateMeta.getEntity()) {
            if (!candidateTables.add(entity.getTableName())) {
                throw invalidPluginPackage("候选 Meta 重复使用 ClickHouse表: " + entity.getTableName());
            }
            if (otherTables.contains(entity.getTableName())) {
                throw invalidPluginPackage("ClickHouse表已被其他 Meta 占用: " + entity.getTableName());
            }
        }
        globalFiles.addAll(candidateMetaFiles);
        metaDataService.validateMetaDataFiles(globalFiles);
    }

    private void validateMetaSqlSafety(MetaData metaData) {
        for (com.coolxer.model.retrieval.meta.DataEntity entity : metaData.getEntity()) {
            rejectUnsafeSqlFragment(entity.getTableName(), "表名");
            if (entity.getAutoCreate() == null) {
                continue;
            }
            if (StringUtils.isBlank(entity.getAutoCreate().getEngine())
                    || entity.getAutoCreate().getOrderBy() == null
                    || entity.getAutoCreate().getOrderBy().isEmpty()) {
                throw invalidPluginPackage("auto_create 缺少引擎或排序键: " + entity.getName());
            }
            rejectUnsafeSqlFragment(entity.getAutoCreate().getEngine(), "引擎");
            if (StringUtils.isNotBlank(entity.getAutoCreate().getPartitionBy())) {
                rejectUnsafeSqlFragment(entity.getAutoCreate().getPartitionBy(), "分区键");
            }
            entity.getAutoCreate().getOrderBy().forEach(value -> rejectUnsafeSqlFragment(value, "排序键"));
        }
        for (com.coolxer.model.retrieval.meta.DataAttribute attribute : metaData.getAttribute()) {
            rejectUnsafeSqlFragment(attribute.getColumnName(), "列名");
            rejectUnsafeSqlFragment(attribute.getColumnType(), "字段类型");
        }
    }

    private void rejectUnsafeSqlFragment(String value, String label) {
        if (StringUtils.isBlank(value) || value.indexOf(';') >= 0 || value.contains("--")
                || value.contains("/*") || value.contains("*/") || value.indexOf('\0') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw invalidPluginPackage("Meta " + label + "不合法: " + value);
        }
    }

    private void validateAdditiveMetaChange(MetaData current, MetaData candidate) {
        Map<String, com.coolxer.model.retrieval.meta.DataEntity> nextEntities = candidate.getEntity().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.coolxer.model.retrieval.meta.DataEntity::getName, item -> item,
                        (left, right) -> { throw invalidPluginPackage("候选 Meta 实体重复: " + left.getName()); },
                        LinkedHashMap::new));
        for (com.coolxer.model.retrieval.meta.DataEntity oldEntity : current.getEntity()) {
            com.coolxer.model.retrieval.meta.DataEntity nextEntity = nextEntities.get(oldEntity.getName());
            if (nextEntity == null) {
                throw invalidPluginPackage("升级不允许删除或重命名实体: " + oldEntity.getName());
            }
            requireUnchanged("实体ID", oldEntity.getId(), nextEntity.getId(), oldEntity.getName());
            requireUnchanged("表名", oldEntity.getTableName(), nextEntity.getTableName(), oldEntity.getName());
            requireUnchanged("数据源", oldEntity.getDataSource(), nextEntity.getDataSource(), oldEntity.getName());
            requireUnchanged("引擎", autoCreateValue(oldEntity, "engine"), autoCreateValue(nextEntity, "engine"), oldEntity.getName());
            requireUnchanged("排序键", autoCreateValue(oldEntity, "order"), autoCreateValue(nextEntity, "order"), oldEntity.getName());
            requireUnchanged("分区键", autoCreateValue(oldEntity, "partition"), autoCreateValue(nextEntity, "partition"), oldEntity.getName());
        }

        Map<String, com.coolxer.model.retrieval.meta.DataAttribute> nextAttributes = candidate.getAttribute().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getEntity() + "." + item.getName(), item -> item,
                        (left, right) -> { throw invalidPluginPackage("候选 Meta 字段重复: " + left.getEntity() + "." + left.getName()); },
                        LinkedHashMap::new));
        for (com.coolxer.model.retrieval.meta.DataAttribute oldAttribute : current.getAttribute()) {
            String key = oldAttribute.getEntity() + "." + oldAttribute.getName();
            com.coolxer.model.retrieval.meta.DataAttribute nextAttribute = nextAttributes.get(key);
            if (nextAttribute == null) {
                throw invalidPluginPackage("升级不允许删除或重命名字段: " + key);
            }
            requireUnchanged("字段ID", oldAttribute.getId(), nextAttribute.getId(), key);
            requireUnchanged("列名", oldAttribute.getColumnName(), nextAttribute.getColumnName(), key);
            requireUnchanged("字段类型", oldAttribute.getColumnType(), nextAttribute.getColumnType(), key);
        }
        Map<String, Set<String>> physicalColumns = new HashMap<>();
        for (com.coolxer.model.retrieval.meta.DataAttribute attribute : candidate.getAttribute()) {
            if (!physicalColumns.computeIfAbsent(attribute.getEntity(), ignored -> new HashSet<>())
                    .add(attribute.getColumnName())) {
                throw invalidPluginPackage("实体存在重复物理列: " + attribute.getEntity() + "." + attribute.getColumnName());
            }
        }
    }

    private Object autoCreateValue(com.coolxer.model.retrieval.meta.DataEntity entity, String field) {
        if (entity.getAutoCreate() == null) {
            return null;
        }
        return switch (field) {
            case "engine" -> entity.getAutoCreate().getEngine();
            case "order" -> entity.getAutoCreate().getOrderBy();
            case "partition" -> entity.getAutoCreate().getPartitionBy();
            default -> null;
        };
    }

    private void requireUnchanged(String field, Object oldValue, Object newValue, String resource) {
        if (!Objects.equals(oldValue, newValue)) {
            throw invalidPluginPackage("升级不允许修改" + field + ": " + resource);
        }
    }

    private Path upgradeOperationRoot(Long pluginId, String operationId) {
        validateResourceName(operationId, "升级操作标识");
        Path upgradeRoot = requireChildPath(pluginRoot().resolve(PLUGIN_UPGRADE_DIR_NAME), pluginRoot());
        return requireChildPath(upgradeRoot.resolve(String.valueOf(pluginId)).resolve(operationId), upgradeRoot);
    }

    private Path requireUpgradeSnapshot(Plugin plugin) {
        if (StringUtils.isBlank(plugin.getUpgradeOperationId())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "插件没有可恢复的升级快照");
        }
        Path snapshotRoot = upgradeOperationRoot(plugin.getId().longValue(), plugin.getUpgradeOperationId())
                .resolve("snapshot");
        if (!Files.isRegularFile(snapshotRoot.resolve(UPGRADE_SNAPSHOT_FILE_NAME))
                || !Files.isDirectory(snapshotRoot.resolve("installed"))) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "插件升级快照不完整");
        }
        return snapshotRoot;
    }

    private void createUpgradeSnapshot(Plugin plugin, String operationId) throws IOException {
        Path operationRoot = upgradeOperationRoot(plugin.getId().longValue(), operationId);
        Path snapshotRoot = requireChildPath(operationRoot.resolve("snapshot"), operationRoot);
        deleteIfExists(snapshotRoot);
        Files.createDirectories(snapshotRoot);

        Path installedRoot = installedPluginRoot(plugin);
        if (!Files.isDirectory(installedRoot)) {
            throw new IOException("当前插件安装目录不存在: " + installedRoot);
        }
        WalkFileUtil.copy(installedRoot, snapshotRoot.resolve("installed"));

        Path snapshotMeta = snapshotRoot.resolve("meta");
        Files.createDirectories(snapshotMeta);
        for (Path metaFile : listInstalledMetaFiles(plugin.getPackageName())) {
            WalkFileUtil.copy(metaFile, snapshotMeta.resolve(metaFile.getFileName()));
        }

        Set<Path> configPaths = new LinkedHashSet<>();
        PluginUiLayout oldUi = inspectPluginUi(plugin.getPackageName(), installedRoot.resolve("04_ui"), false);
        if (!oldUi.legacyFiles().isEmpty()) {
            configPaths.add(uiConfigPath(plugin.getPackageName()));
        }
        oldUi.bundles().forEach(bundle -> configPaths.add(uiConfigPath(bundle.configIndex())));
        for (Dashboard dashboard : dashboardRepository.findBySource(plugin.getPackageName())) {
            if (dashboard.getType() == DashboardType.LOW_CODE_PAGE && StringUtils.isNotBlank(dashboard.getConfigIndex())) {
                configPaths.add(uiConfigPath(dashboard.getConfigIndex()));
            }
        }
        Path snapshotConfig = snapshotRoot.resolve("config");
        Files.createDirectories(snapshotConfig);
        for (Path configPath : configPaths) {
            if (Files.exists(configPath)) {
                WalkFileUtil.copy(configPath, snapshotConfig.resolve(configPath.getFileName()));
            }
        }

        Path currentHtml = requireChildPath(htmlPageRoot().resolve(plugin.getPackageName()), htmlPageRoot());
        if (Files.exists(currentHtml)) {
            WalkFileUtil.copy(currentHtml, snapshotRoot.resolve("html"));
        }
        Path installedSkill = skillService.getInstalledPluginSkillPath(plugin.getPackageName());
        if (Files.exists(installedSkill)) {
            WalkFileUtil.copy(installedSkill, snapshotRoot.resolve("skill"));
        }

        List<Menu> menus = menuService.findBySource(plugin.getPackageName());
        List<RolePermission> permissions = menus.stream()
                .flatMap(menu -> rolePermissionRepository.findByPermissionId(menu.getId()).stream())
                .toList();
        UpgradeSnapshot snapshot = new UpgradeSnapshot(
                operationId,
                new PluginState(plugin.getName(), plugin.getIcon(), plugin.getPackageName(), plugin.getVersion(),
                        plugin.getDescription(), plugin.getAuthor(), plugin.getPluginPath()),
                new ArrayList<>(menus),
                new ArrayList<>(permissions),
                new ArrayList<>(dashboardRepository.findBySource(plugin.getPackageName())),
                new ArrayList<>(mcpServerConfigRepository.findBySource(plugin.getPackageName())),
                new ArrayList<>(pushTaskService.findBySourceMark(plugin.getPackageName()))
        );
        JacksonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(snapshotRoot.resolve(UPGRADE_SNAPSHOT_FILE_NAME).toFile(), snapshot);
    }

    private UpgradeSnapshot readUpgradeSnapshot(Plugin plugin) {
        Path snapshotRoot = requireUpgradeSnapshot(plugin);
        try {
            return JacksonConfig.OBJECT_MAPPER.readValue(
                    snapshotRoot.resolve(UPGRADE_SNAPSHOT_FILE_NAME).toFile(), UpgradeSnapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取插件升级快照失败", e);
        }
    }

    private void executeUpgrade(Long id) {
        String upgradeError = null;
        try {
            Plugin plugin = getPluginOrThrow(id);
            Path operationRoot = upgradeOperationRoot(id, plugin.getUpgradeOperationId());
            Path candidateRoot = requireChildPath(operationRoot.resolve("candidate"), operationRoot);
            UpgradeCandidate candidate = preflightUpgrade(plugin, candidateRoot);
            UpgradeSnapshot snapshot = readUpgradeSnapshot(plugin);
            List<String> warnings = new ArrayList<>();

            writeLog(id, "1 暂停插件推送任务并卸载旧API......");
            pauseRunningPushTasks(snapshot.pushTasks());
            extendJarManager.unload(plugin.getPackageName());

            writeLog(id, "2 执行尚未执行的MySQL迁移......");
            pluginMigrationService.migrateMysql(plugin.getPackageName(),
                    candidate.pluginPackTool().listMysqlMigrationFiles());

            writeLog(id, "3 应用ClickHouse新增表和字段......");
            clickhouseSchemeService.applyAdditiveScheme(candidate.pluginMeta());

            writeLog(id, "4 原子切换插件目录和Meta......");
            switchInstalledPluginDirectory(plugin, candidateRoot, operationRoot);
            PluginPackTool activePack = new PluginPackTool().buildFromDirectory(installedPluginRoot(plugin)).init();
            replacePluginMeta(plugin.getPackageName(), activePack.listMetaFiles(), candidate.pluginMeta());

            writeLog(id, "5 加载新API和UI......");
            loadPluginApiJars(plugin.getPackageName(), activePack);
            replacePluginUi(plugin.getPackageName(), snapshot, activePack);

            writeLog(id, "6 更新数据看板......");
            reconcilePluginDashboards(id, plugin.getPackageName(), activePack);

            writeLog(id, "7 更新MCP服务配置......");
            reconcilePluginMcpServers(plugin.getPackageName(), snapshot, activePack);

            writeLog(id, "8 更新插件Skill和RAG......");
            updatePluginSkillAndRag(id, plugin.getPackageName(), snapshot, activePack, warnings);

            writeLog(id, "9 原位更新菜单并保留角色授权......");
            reconcilePluginMenus(plugin.getPackageName(), activePack);

            writeLog(id, "10 更新推送任务并恢复启停状态......");
            Set<Integer> obsoleteTaskIds = reconcilePluginPushTasks(plugin.getPackageName(), snapshot, activePack);

            String oldPluginPath = snapshot.plugin().pluginPath();
            PluginVo descriptor = candidate.descriptor();
            plugin = getPluginOrThrow(id);
            plugin.setName(descriptor.getName());
            plugin.setIcon(descriptor.getIcon());
            plugin.setVersion(descriptor.getVersion());
            plugin.setDescription(descriptor.getDescription());
            plugin.setAuthor(descriptor.getAuthor());
            plugin.setPluginPath(plugin.getPendingUpgradePath());
            clearUpgradePending(plugin);
            String message = warnings.isEmpty() ? "升级完成" : "升级完成（" + String.join("，", warnings) + "）";
            updateOperationState(plugin, PluginStatusType.INSTALLED, message, null, false);
            pluginRepository.save(plugin);
            List<String> cleanupWarnings = new ArrayList<>();
            for (Integer obsoleteTaskId : obsoleteTaskIds) {
                try {
                    if (!pushTaskService.delete(obsoleteTaskId)) {
                        cleanupWarnings.add("旧推送任务清理失败(ID=" + obsoleteTaskId + ")");
                    }
                } catch (Exception cleanupError) {
                    log.warn("清理旧推送任务失败: pluginId={}, taskId={}", id, obsoleteTaskId, cleanupError);
                    cleanupWarnings.add("旧推送任务清理失败(ID=" + obsoleteTaskId + ")");
                }
            }
            if (!cleanupWarnings.isEmpty()) {
                plugin.setOperationMessage(message + "（" + String.join("，", cleanupWarnings) + "）");
                try {
                    pluginRepository.save(plugin);
                } catch (Exception messageError) {
                    log.warn("保存插件升级清理警告失败: id={}", id, messageError);
                }
            }
            cleanupSuccessfulUpgrade(operationRoot, oldPluginPath, plugin.getPluginPath());
            writeLog(id, "完成......");
        } catch (Exception e) {
            upgradeError = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            log.error("插件升级失败: id={}", id, e);
            writeLog(id, "升级失败，开始恢复旧版本......");
            try {
                Plugin plugin = getPluginOrThrow(id);
                UpgradeSnapshot snapshot = readUpgradeSnapshot(plugin);
                restoreUpgradeSnapshot(plugin, snapshot);
                plugin = getPluginOrThrow(id);
                applyPluginState(plugin, snapshot.plugin());
                String pendingPath = plugin.getPendingUpgradePath();
                clearUpgradePending(plugin);
                updateOperationState(plugin, PluginStatusType.INSTALLED,
                        "升级失败，已恢复旧版本", upgradeError, false);
                pluginRepository.save(plugin);
                cleanupRecoveredUpgrade(id, snapshot.operationId(), pendingPath, snapshot.plugin().pluginPath());
                writeLog(id, "失败......" + upgradeError + "（已恢复旧版本）");
            } catch (Exception recoveryError) {
                log.error("插件升级自动恢复失败: id={}", id, recoveryError);
                String recoveryMessage = StringUtils.defaultIfBlank(recoveryError.getMessage(),
                        recoveryError.getClass().getSimpleName());
                finishOperation(id, PluginStatusType.UPGRADE_FAILED,
                        "升级失败且自动恢复未完成", upgradeError + "；恢复失败：" + recoveryMessage);
                writeLog(id, "失败......" + upgradeError + "；恢复失败：" + recoveryMessage);
            }
        }
    }

    private void executeUpgradeRecovery(Long id, String reason) {
        try {
            Plugin plugin = getPluginOrThrow(id);
            UpgradeSnapshot snapshot = readUpgradeSnapshot(plugin);
            String pendingPath = plugin.getPendingUpgradePath();
            restoreUpgradeSnapshot(plugin, snapshot);
            plugin = getPluginOrThrow(id);
            applyPluginState(plugin, snapshot.plugin());
            clearUpgradePending(plugin);
            updateOperationState(plugin, PluginStatusType.INSTALLED,
                    reason + "完成", null, false);
            pluginRepository.save(plugin);
            cleanupRecoveredUpgrade(id, snapshot.operationId(), pendingPath, snapshot.plugin().pluginPath());
            writeLog(id, "完成......");
        } catch (Exception e) {
            String error = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            log.error("恢复插件旧版本失败: id={}", id, e);
            finishOperation(id, PluginStatusType.UPGRADE_FAILED, "恢复旧版本失败", error);
            writeLog(id, "失败......" + error);
        }
    }

    private void pauseRunningPushTasks(List<PushTaskVo> tasks) {
        for (PushTaskVo task : tasks) {
            if (isRunningPushTask(task) && !pushTaskService.toggle(task.getId())) {
                throw new IllegalStateException("暂停推送任务失败: " + task.getName());
            }
        }
    }

    private boolean isRunningPushTask(PushTaskVo task) {
        return task != null && StringUtils.startsWithIgnoreCase(StringUtils.trimToEmpty(task.getStatus()), "running");
    }

    private void switchInstalledPluginDirectory(Plugin plugin, Path candidateRoot, Path operationRoot) throws IOException {
        Path installedRoot = installedPluginRoot(plugin);
        Path retiredRoot = requireChildPath(operationRoot.resolve("retired"), operationRoot);
        deleteIfExists(retiredRoot);
        moveAtomically(installedRoot, retiredRoot);
        try {
            moveAtomically(candidateRoot, installedRoot);
        } catch (IOException e) {
            moveAtomically(retiredRoot, installedRoot);
            throw e;
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void replacePluginMeta(String packageName, List<Path> newMetaFiles, MetaData expectedMeta) throws IOException {
        deletePluginMetaFilesOnly(packageName);
        copyPluginMetaFiles(packageName, newMetaFiles);
        MetaData loaded = metaDataService.loadMetaData();
        if (loaded == null) {
            throw new IllegalStateException("切换插件 Meta 后加载失败");
        }
        for (com.coolxer.model.retrieval.meta.DataEntity entity : expectedMeta.getEntity()) {
            com.coolxer.model.retrieval.meta.DataEntity active = metaDataService.getDataEntityByName(entity.getName());
            if (active == null || !Objects.equals(entity.getTableName(), active.getTableName())) {
                throw new IllegalStateException("插件 Meta 未生效: " + entity.getName());
            }
        }
    }

    private void deletePluginMetaFilesOnly(String packageName) throws IOException {
        for (Path path : listInstalledMetaFiles(packageName)) {
            deleteIfExists(path);
        }
    }

    private void cleanupSuccessfulUpgrade(Path operationRoot, String oldPluginPath, String newPluginPath) {
        try {
            if (StringUtils.isNotBlank(oldPluginPath) && !Objects.equals(oldPluginPath, newPluginPath)) {
                Path oldArchive = safePluginPath(oldPluginPath);
                if (Files.isRegularFile(oldArchive)) {
                    deleteIfExists(oldArchive);
                }
            }
        } catch (Exception e) {
            log.warn("清理插件旧归档失败: {}", oldPluginPath, e);
        }
        try {
            deleteIfExists(operationRoot);
        } catch (Exception e) {
            log.warn("清理插件升级临时文件失败: {}", operationRoot, e);
        }
    }

    private void cleanupRecoveredUpgrade(Long pluginId,
                                         String operationId,
                                         String pendingPath,
                                         String restoredPluginPath) {
        try {
            if (StringUtils.isNotBlank(pendingPath) && !Objects.equals(pendingPath, restoredPluginPath)) {
                Path archive = safePluginPath(pendingPath);
                if (Files.isRegularFile(archive)) {
                    deleteIfExists(archive);
                }
            }
        } catch (Exception e) {
            log.warn("清理已恢复的升级候选包失败: pluginId={}", pluginId, e);
        }
        try {
            deleteIfExists(upgradeOperationRoot(pluginId, operationId));
        } catch (Exception e) {
            log.warn("清理已恢复的插件升级快照失败: pluginId={}", pluginId, e);
        }
    }

    private void clearUpgradePending(Plugin plugin) {
        plugin.setPendingUpgradePath(null);
        plugin.setPendingUpgradeVersion(null);
        plugin.setUpgradeOperationId(null);
    }

    private void applyPluginState(Plugin plugin, PluginState state) {
        plugin.setName(state.name());
        plugin.setIcon(state.icon());
        plugin.setPackageName(state.packageName());
        plugin.setVersion(state.version());
        plugin.setDescription(state.description());
        plugin.setAuthor(state.author());
        plugin.setPluginPath(state.pluginPath());
    }

    private Path snapshotRoot(String packageName, UpgradeSnapshot snapshot) {
        Plugin plugin = pluginRepository.findByPackageName(packageName)
                .orElseThrow(() -> new IllegalStateException("插件不存在: " + packageName));
        return upgradeOperationRoot(plugin.getId().longValue(), snapshot.operationId()).resolve("snapshot");
    }

    private void replacePluginUi(String packageName,
                                 UpgradeSnapshot snapshot,
                                 PluginPackTool activePack) throws IOException {
        Path oldInstalledUi = snapshotRoot(packageName, snapshot).resolve("installed/04_ui");
        cleanupPluginUi(packageName, oldInstalledUi);
        installPluginUi(packageName, activePack.getUiPath());
    }

    private void reconcilePluginDashboards(Long id,
                                           String packageName,
                                           PluginPackTool pluginPackTool) throws IOException {
        List<DashboardDto> definitions = readDashboardDefinitions(pluginPackTool);
        Set<String> nextCodes = definitions.stream().map(DashboardDto::getCode)
                .collect(java.util.stream.Collectors.toSet());
        List<Dashboard> current = new ArrayList<>(dashboardRepository.findBySource(packageName));
        for (Dashboard dashboard : current) {
            if (dashboard.getType() == DashboardType.LOW_CODE_PAGE
                    && StringUtils.isNotBlank(dashboard.getConfigIndex())) {
                deleteIfExists(uiConfigPath(dashboard.getConfigIndex()));
            }
        }
        deleteIfExists(requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot()));

        List<Path> copiedPaths = new ArrayList<>();
        for (DashboardDto definition : definitions) {
            Dashboard existing = dashboardRepository.findByCode(definition.getCode()).orElse(null);
            DashboardDto normalized = normalizePluginDashboard(
                    packageName, definition, pluginPackTool, copiedPaths, existing);
            Dashboard dashboard = existing == null ? new Dashboard() : existing;
            dashboard.updateFromDto(normalized);
            dashboard.setSource(packageName);
            dashboardRepository.save(dashboard);
        }
        for (Dashboard dashboard : current) {
            if (!nextCodes.contains(dashboard.getCode())) {
                dashboardRepository.deleteById(dashboard.getId());
            }
        }
        if (definitions.isEmpty()) {
            writeLog(id, "未发现数据看板配置，已移除旧版非默认看板");
        }
    }

    private void reconcilePluginMcpServers(String packageName,
                                           UpgradeSnapshot snapshot,
                                           PluginPackTool newPack) {
        PluginPackTool oldPack = new PluginPackTool()
                .buildFromDirectory(snapshotRoot(packageName, snapshot).resolve("installed")).init();
        Map<String, McpServerDto> oldDefaults = readMcpDefinitions(oldPack).stream()
                .peek(item -> item.setCode(normalizeMcpCode(item.getCode())))
                .collect(java.util.stream.Collectors.toMap(McpServerDto::getCode, item -> item));
        List<McpServerDto> newDefinitions = readMcpDefinitions(newPack);
        Set<String> nextCodes = new HashSet<>();
        for (McpServerDto next : newDefinitions) {
            next.setCode(normalizeMcpCode(next.getCode()));
            next.setSource(packageName);
            nextCodes.add(next.getCode());
            McpServerConfig live = mcpServerConfigRepository.findByCode(next.getCode()).orElse(null);
            if (live == null) {
                mcpClientService.create(next);
                continue;
            }
            McpServerDto oldDefault = oldDefaults.get(next.getCode());
            next.setEnabled(preserveRuntimeValue(oldDefault == null ? null : oldDefault.getEnabled(),
                    live.getEnabled(), next.getEnabled(), oldDefault == null));
            next.setBaseUrl(preserveRuntimeValue(oldDefault == null ? null : oldDefault.getBaseUrl(),
                    live.getBaseUrl(), next.getBaseUrl(), oldDefault == null));
            next.setSseEndpoint(preserveRuntimeValue(oldDefault == null ? null : oldDefault.getSseEndpoint(),
                    live.getSseEndpoint(), next.getSseEndpoint(), oldDefault == null));
            next.setHeaders(preserveRuntimeValue(oldDefault == null ? null : oldDefault.getHeaders(),
                    live.getHeaders(), next.getHeaders(), oldDefault == null));
            next.setRequestTimeoutSeconds(preserveRuntimeValue(
                    oldDefault == null ? null : oldDefault.getRequestTimeoutSeconds(),
                    live.getRequestTimeoutSeconds(), next.getRequestTimeoutSeconds(), oldDefault == null));
            next.setConnectTimeoutSeconds(preserveRuntimeValue(
                    oldDefault == null ? null : oldDefault.getConnectTimeoutSeconds(),
                    live.getConnectTimeoutSeconds(), next.getConnectTimeoutSeconds(), oldDefault == null));
            if (!mcpClientService.update(live.getId(), next)) {
                throw new IllegalStateException("更新MCP服务失败: " + next.getCode());
            }
        }
        for (McpServerConfig existing : new ArrayList<>(mcpServerConfigRepository.findBySource(packageName))) {
            if (!nextCodes.contains(existing.getCode())) {
                mcpClientService.delete(existing.getId());
            }
        }
    }

    private <T> T preserveRuntimeValue(T oldDefault, T liveValue, T newDefault, boolean unknownOldDefault) {
        if (unknownOldDefault || !Objects.equals(oldDefault, liveValue)) {
            return liveValue;
        }
        return newDefault;
    }

    private void updatePluginSkillAndRag(Long id,
                                         String packageName,
                                         UpgradeSnapshot snapshot,
                                         PluginPackTool activePack,
                                         List<String> warnings) {
        Path oldInstalled = snapshotRoot(packageName, snapshot).resolve("installed");
        try {
            vectorStoreInitializerService.unloadDocFromRag(packageName.replaceAll("\\.", "_"));
            vectorStoreInitializerService.loadDocToRag(
                    packageName.replaceAll("\\.", "_"), activePack.getDocPath());
        } catch (Exception e) {
            log.warn("升级插件RAG失败，恢复旧文档: package={}", packageName, e);
            warnings.add("RAG更新失败，已尝试恢复旧文档");
            try {
                vectorStoreInitializerService.unloadDocFromRag(packageName.replaceAll("\\.", "_"));
                vectorStoreInitializerService.loadDocToRag(
                        packageName.replaceAll("\\.", "_"), oldInstalled.resolve("00_doc"));
            } catch (Exception restoreError) {
                log.warn("恢复旧RAG文档失败: package={}", packageName, restoreError);
                writeLog(id, "恢复旧RAG文档失败，请人工检查");
            }
        }

        try {
            skillService.installPluginSkills(packageName, activePack.getSkillPath());
        } catch (Exception e) {
            log.warn("升级插件Skill失败，恢复旧Skill: package={}", packageName, e);
            warnings.add("Skill更新失败，已恢复旧版本");
            Path snapshotSkill = snapshotRoot(packageName, snapshot).resolve("skill");
            Path oldSkill = Files.isDirectory(snapshotSkill) ? snapshotSkill : oldInstalled.resolve(PLUGIN_SKILL_DIR_NAME);
            skillService.installPluginSkills(packageName, oldSkill);
        }
    }

    private void reconcilePluginMenus(String packageName, PluginPackTool pluginPackTool) {
        Map<String, Menu> currentByKey = menuService.findBySource(packageName).stream()
                .collect(java.util.stream.Collectors.toMap(
                        menu -> menuMatchKey(menu.getType(), menu.getParams(), menu.getRoute(), menu.getName()),
                        menu -> menu));
        Set<Integer> retainedIds = new HashSet<>();
        for (MenuDto definition : readMenuDefinitions(pluginPackTool)) {
            definition.setLevel(MenuLevel.LEVEL_1);
            definition.setParentId(0);
            definition.setSource(packageName);
            String route = definition.getType() == com.coolxer.commons.enums.MenuType.BUILT_APP
                    ? definition.getRoute() : definition.getType().getRoute();
            String key = menuMatchKey(definition.getType(), definition.getParams(), route, definition.getName());
            Menu existing = currentByKey.get(key);
            if (existing == null) {
                Menu created = menuService.create(definition);
                retainedIds.add(created.getId());
            } else {
                definition.setRoute(route);
                existing.updateFromDto(definition);
                menuRepository.save(existing);
                retainedIds.add(existing.getId());
            }
        }
        for (Menu menu : new ArrayList<>(menuService.findBySource(packageName))) {
            if (!retainedIds.contains(menu.getId())) {
                rolePermissionRepository.deleteAll(rolePermissionRepository.findByPermissionId(menu.getId()));
                menuRepository.deleteById(menu.getId());
            }
        }
    }

    private Set<Integer> reconcilePluginPushTasks(String packageName,
                                                  UpgradeSnapshot snapshot,
                                                  PluginPackTool pluginPackTool) {
        Map<String, PushTaskVo> oldByName = snapshot.pushTasks().stream()
                .collect(java.util.stream.Collectors.toMap(PushTaskVo::getName, item -> item));
        Map<String, PushTaskVo> liveByName = pushTaskService.findBySourceMark(packageName).stream()
                .collect(java.util.stream.Collectors.toMap(PushTaskVo::getName, item -> item));
        Set<String> nextNames = new HashSet<>();
        for (PushTaskDto definition : readPushTaskDefinitions(pluginPackTool)) {
            definition.setSource("SYSTEM");
            definition.setMark(packageName);
            nextNames.add(definition.getName());
            PushTaskVo existing = liveByName.get(definition.getName());
            if (existing == null) {
                if (!pushTaskService.createAndStart(definition)) {
                    throw new IllegalStateException("创建推送任务失败: " + definition.getName());
                }
                continue;
            }
            if (!pushTaskService.update(existing.getId(), definition)) {
                throw new IllegalStateException("更新推送任务失败: " + definition.getName());
            }
            PushTaskVo old = oldByName.get(definition.getName());
            if (old != null && isRunningPushTask(old) && !pushTaskService.toggle(existing.getId())) {
                throw new IllegalStateException("恢复推送任务运行状态失败: " + definition.getName());
            }
        }
        return snapshot.pushTasks().stream()
                .filter(task -> !nextNames.contains(task.getName()))
                .map(PushTaskVo::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void restoreUpgradeSnapshot(Plugin plugin, UpgradeSnapshot snapshot) throws Exception {
        String packageName = plugin.getPackageName();
        Path snapshotRoot = snapshotRoot(packageName, snapshot);
        Path installedRoot = installedPluginRoot(plugin);

        writeLog(plugin.getId().longValue(), "恢复旧API、目录和Meta......");
        extendJarManager.unload(packageName);
        removeCurrentPluginConfigFiles(packageName, installedRoot);

        Path restoreTemp = requireChildPath(installedRoot.getParent()
                .resolve(packageName + ".restore-" + snapshot.operationId()), pluginRoot());
        deleteIfExists(restoreTemp);
        WalkFileUtil.copy(snapshotRoot.resolve("installed"), restoreTemp);
        deleteIfExists(installedRoot);
        moveAtomically(restoreTemp, installedRoot);

        deletePluginMetaFilesOnly(packageName);
        Path liveMetaRoot = requireChildPath(configRoot().resolve("meta_config"), configRoot());
        Files.createDirectories(liveMetaRoot);
        Path snapshotMeta = snapshotRoot.resolve("meta");
        if (Files.isDirectory(snapshotMeta)) {
            try (Stream<Path> paths = Files.list(snapshotMeta)) {
                for (Path source : paths.filter(Files::isRegularFile).toList()) {
                    WalkFileUtil.copy(source, liveMetaRoot.resolve(source.getFileName()));
                }
            }
        }
        if (metaDataService.loadMetaData() == null) {
            throw new IllegalStateException("恢复旧Meta失败");
        }

        restoreConfigDirectories(snapshotRoot);
        restoreDashboards(packageName, snapshot.dashboards());
        restoreMenus(packageName, snapshot.menus(), snapshot.rolePermissions());
        restoreMcpServers(packageName, snapshot.mcpServers());
        restorePushTasks(packageName, snapshot.pushTasks());

        writeLog(plugin.getId().longValue(), "恢复旧Skill和RAG......");
        Path snapshotSkill = snapshotRoot.resolve("skill");
        Path oldSkill = Files.isDirectory(snapshotSkill) ? snapshotSkill : installedRoot.resolve(PLUGIN_SKILL_DIR_NAME);
        try {
            skillService.installPluginSkills(packageName, oldSkill);
        } catch (Exception skillError) {
            log.warn("恢复旧Skill失败: package={}", packageName, skillError);
            writeLog(plugin.getId().longValue(), "恢复旧Skill失败，请人工检查");
        }
        try {
            vectorStoreInitializerService.unloadDocFromRag(packageName.replaceAll("\\.", "_"));
            vectorStoreInitializerService.loadDocToRag(
                    packageName.replaceAll("\\.", "_"), installedRoot.resolve("00_doc"));
        } catch (Exception ragError) {
            log.warn("恢复旧RAG文档失败: package={}", packageName, ragError);
            writeLog(plugin.getId().longValue(), "恢复旧RAG文档失败，请人工检查");
        }

        loadPluginApiJars(packageName, new PluginPackTool().buildFromDirectory(installedRoot).init());
    }

    private void removeCurrentPluginConfigFiles(String packageName, Path installedRoot) throws IOException {
        if (Files.isDirectory(installedRoot.resolve("04_ui"))) {
            cleanupPluginUi(packageName, installedRoot.resolve("04_ui"));
        }
        for (Dashboard dashboard : dashboardRepository.findBySource(packageName)) {
            if (dashboard.getType() == DashboardType.LOW_CODE_PAGE
                    && StringUtils.isNotBlank(dashboard.getConfigIndex())) {
                deleteIfExists(uiConfigPath(dashboard.getConfigIndex()));
            }
        }
        deleteIfExists(requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot()));
    }

    private void restoreConfigDirectories(Path snapshotRoot) throws IOException {
        Path snapshotConfig = snapshotRoot.resolve("config");
        if (Files.isDirectory(snapshotConfig)) {
            try (Stream<Path> paths = Files.list(snapshotConfig)) {
                for (Path source : paths.filter(Files::isDirectory).toList()) {
                    Path target = requireChildPath(configRoot().resolve(source.getFileName()), configRoot());
                    deleteIfExists(target);
                    WalkFileUtil.copy(source, target);
                }
            }
        }
        Path snapshotHtml = snapshotRoot.resolve("html");
        if (Files.exists(snapshotHtml)) {
            String packageName = readPackageNameFromSnapshotRoot(snapshotRoot);
            Path target = requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot());
            deleteIfExists(target);
            WalkFileUtil.copy(snapshotHtml, target);
        }
    }

    private String readPackageNameFromSnapshotRoot(Path snapshotRoot) throws IOException {
        UpgradeSnapshot snapshot = JacksonConfig.OBJECT_MAPPER.readValue(
                snapshotRoot.resolve(UPGRADE_SNAPSHOT_FILE_NAME).toFile(), UpgradeSnapshot.class);
        return snapshot.plugin().packageName();
    }

    private void restoreDashboards(String packageName, List<Dashboard> dashboards) {
        for (Dashboard current : new ArrayList<>(dashboardRepository.findBySource(packageName))) {
            dashboardRepository.deleteById(current.getId());
        }
        dashboardRepository.saveAll(dashboards);
    }

    private void restoreMenus(String packageName,
                              List<Menu> menus,
                              List<RolePermission> permissions) {
        List<Menu> current = new ArrayList<>(menuService.findBySource(packageName));
        for (Menu menu : current) {
            rolePermissionRepository.deleteAll(rolePermissionRepository.findByPermissionId(menu.getId()));
            menuRepository.deleteById(menu.getId());
        }
        menuRepository.saveAll(menus);
        for (RolePermission permission : permissions) {
            RolePermission existing = rolePermissionRepository.findByRoleIdAndPermissionId(
                    permission.getRoleId(), permission.getPermissionId());
            if (existing == null) {
                rolePermissionRepository.save(permission);
            }
        }
    }

    private void restoreMcpServers(String packageName, List<McpServerConfig> servers) {
        for (McpServerConfig current : new ArrayList<>(mcpServerConfigRepository.findBySource(packageName))) {
            mcpClientService.delete(current.getId());
        }
        mcpServerConfigRepository.saveAll(servers);
        mcpClientService.refreshAll();
    }

    private void restorePushTasks(String packageName, List<PushTaskVo> snapshotTasks) {
        Map<String, PushTaskVo> expected = snapshotTasks.stream()
                .collect(java.util.stream.Collectors.toMap(PushTaskVo::getName, item -> item));
        Map<String, PushTaskVo> current = pushTaskService.findBySourceMark(packageName).stream()
                .collect(java.util.stream.Collectors.toMap(PushTaskVo::getName, item -> item));
        for (PushTaskVo oldTask : snapshotTasks) {
            PushTaskVo live = current.get(oldTask.getName());
            PushTaskDto dto = new PushTaskDto();
            dto.setName(oldTask.getName());
            dto.setDescription(oldTask.getDescription());
            dto.setConfig(oldTask.getConfig());
            dto.setSource("SYSTEM");
            dto.setMark(packageName);
            if (live == null) {
                if (!pushTaskService.createAndStart(dto)) {
                    throw new IllegalStateException("恢复推送任务失败: " + oldTask.getName());
                }
                PushTaskVo recreated = pushTaskService.findBySourceMark(packageName).stream()
                        .filter(item -> Objects.equals(oldTask.getName(), item.getName()))
                        .findFirst().orElseThrow();
                if (!isRunningPushTask(oldTask) && !pushTaskService.toggle(recreated.getId())) {
                    throw new IllegalStateException("恢复推送任务停止状态失败: " + oldTask.getName());
                }
                continue;
            }
            if (isRunningPushTask(live) && !pushTaskService.toggle(live.getId())) {
                throw new IllegalStateException("暂停待恢复推送任务失败: " + oldTask.getName());
            }
            if (!pushTaskService.update(live.getId(), dto)) {
                throw new IllegalStateException("恢复推送任务定义失败: " + oldTask.getName());
            }
            if (isRunningPushTask(oldTask) && !pushTaskService.toggle(live.getId())) {
                throw new IllegalStateException("恢复推送任务运行状态失败: " + oldTask.getName());
            }
        }
        for (PushTaskVo live : current.values()) {
            if (!expected.containsKey(live.getName()) && !pushTaskService.delete(live.getId())) {
                throw new IllegalStateException("删除升级新增推送任务失败: " + live.getName());
            }
        }
    }

    private void executeInstall(Long id) {
        CompensationStack compensationStack = new CompensationStack(id);
        List<String> warnings = new ArrayList<>();
        try {
            Plugin plugin = getPluginOrThrow(id);
            String packageName = plugin.getPackageName();
            Path pluginTarGzPath = safePluginPackagePath(plugin.getPluginPath());
            PluginPackTool pluginPackTool = new PluginPackTool()
                    .buildInstaller(pluginRoot().toString(), packageName, pluginTarGzPath.toString())
                    .init();

            writeLog(id, "插件检查......");
            cleanupPluginCoreResources(id, plugin, false);

            writeLog(id, "1 解压插件包......");
            Path pluginDir = pluginPackTool.getPluginFilePath();
            deleteIfExists(pluginDir);
            TarGzUtil.decompressTarGz(pluginTarGzPath, pluginDir);
            compensationStack.add("删除解压目录", () -> deleteIfExists(pluginDir));

            writeLog(id, "2 拷贝meta并加载......");
            List<String> pluginTableNames = readMetaTableNames(pluginPackTool.listMetaFiles());
            List<Path> copiedMetaPaths = copyPluginMetaFiles(packageName, pluginPackTool.listMetaFiles());
            compensationStack.add("删除meta和库表", () -> {
                deletePluginTables(pluginTableNames);
                for (Path metaPath : copiedMetaPaths) {
                    deleteIfExists(metaPath);
                }
                metaDataService.loadMetaData();
            });
            MetaData metaData = metaDataService.loadMetaData();
            clickhouseSchemeService.loadSchemeFromMetaData(metaData);

            writeLog(id, "3 存储push-task任务......");
            compensationStack.add("删除push-task任务", () -> pushTaskService.deleteBySourceMark(packageName));
            createPluginPushTasks(id, packageName, pluginPackTool);

            writeLog(id, "4 执行插件MySQL迁移......");
            pluginMigrationService.migrateMysql(packageName, pluginPackTool.listMysqlMigrationFiles());

            writeLog(id, "5 加载API包......");
            compensationStack.add("卸载API包", () -> extendJarManager.unload(packageName));
            loadPluginApiJars(packageName, pluginPackTool);

            writeLog(id, "6 拷贝UI配置......");
            List<Path> uiPaths = copyPluginUi(packageName, pluginPackTool);
            if (!uiPaths.isEmpty()) {
                compensationStack.add("删除UI配置", () -> deletePluginUiPaths(uiPaths));
            }

            writeLog(id, "7 存储数据看板......");
            compensationStack.add("删除数据看板", () -> cleanupPluginDashboards(packageName));
            createPluginDashboards(id, packageName, pluginPackTool);

            writeLog(id, "8 存储MCP服务配置......");
            compensationStack.add("删除MCP服务配置", () -> cleanupPluginMcpServers(packageName));
            createPluginMcpServers(id, packageName, pluginPackTool);

            writeLog(id, "9 文档加载到RAG......");
            try {
                vectorStoreInitializerService.loadDocToRag(packageName.replaceAll("\\.", "_"), pluginPackTool.getDocPath());
            } catch (Exception e) {
                log.error("加载到RAG失败", e);
                warnings.add("RAG加载失败");
                writeLog(id, "加载到RAG失败，跳过");
            }

            writeLog(id, "10 加载插件Skill......");
            compensationStack.add("卸载插件Skill", () -> skillService.uninstallPluginSkills(packageName));
            try {
                skillService.installPluginSkills(packageName, pluginPackTool.getSkillPath());
            } catch (Exception e) {
                log.error("加载插件Skill失败", e);
                warnings.add("Skill加载失败");
                writeLog(id, "加载插件Skill失败，跳过");
            }

            writeLog(id, "11 存储菜单信息......");
            compensationStack.add("删除菜单按钮", () -> deletePluginMenus(packageName));
            createPluginMenus(packageName, pluginPackTool);

            String message = warnings.isEmpty() ? "安装完成" : "安装完成（" + String.join("，", warnings) + "）";
            finishOperation(id, PluginStatusType.INSTALLED, message, null);
            writeLog(id, "完成......");
        } catch (Exception e) {
            log.error("插件安装失败: id={}", id, e);
            writeLog(id, "安装失败，开始回滚......");
            compensationStack.rollback();
            String error = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            finishOperation(id, PluginStatusType.INSTALL_FAILED, "安装失败", error);
            writeLog(id, "失败......" + error);
        }
    }

    private void executeUninstall(Long id) {
        try {
            Plugin plugin = getPluginOrThrow(id);
            String upgradeOperationId = plugin.getUpgradeOperationId();
            String pendingUpgradePath = plugin.getPendingUpgradePath();
            writeLog(id, "插件检查......");
            cleanupPluginCoreResources(id, plugin, true);
            if (StringUtils.isNotBlank(pendingUpgradePath)) {
                try {
                    Path pendingArchive = safePluginPath(pendingUpgradePath);
                    if (Files.isRegularFile(pendingArchive)) {
                        deleteIfExists(pendingArchive);
                    }
                } catch (Exception cleanupError) {
                    log.warn("卸载时清理升级候选包失败: id={}", id, cleanupError);
                }
            }
            if (StringUtils.isNotBlank(upgradeOperationId)) {
                try {
                    deleteIfExists(upgradeOperationRoot(id, upgradeOperationId));
                } catch (Exception cleanupError) {
                    log.warn("卸载时清理升级快照失败: id={}", id, cleanupError);
                }
            }
            plugin = getPluginOrThrow(id);
            clearUpgradePending(plugin);
            pluginRepository.save(plugin);
            finishOperation(id, PluginStatusType.UN_INSTALL, "卸载完成", null);
            writeLog(id, "完成......");
        } catch (Exception e) {
            log.error("插件卸载失败: id={}", id, e);
            String error = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
            finishOperation(id, PluginStatusType.UNINSTALL_FAILED, "卸载失败", error);
            writeLog(id, "失败......" + error);
        }
    }


    @Override
    public String readme(Long id) {
        Plugin plugin = getPluginOrThrow(id);
        try {
            if (StringUtils.isNotBlank(plugin.getPluginPath())) {
                return TarGzUtil.readRootFile(safePluginPackagePath(plugin.getPluginPath()), "README.md");
            }
            return "# " + plugin.getName() + "\n\n" + "暂无";
        } catch (IOException e) {
            log.warn("读取插件 README 失败: id={}", id, e);
        }
        return null;
    }

    @Override
    public boolean isPackageExist(String packageName) {
        return pluginRepository.findByPackageName(packageName).isPresent();
    }

    @Override
    public List<FileTreeNodeVo> docTree(Long id) {
        try {
            Plugin plugin = getPluginOrThrow(id);
            Path root = installedPluginDocRoot(plugin);
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                return Collections.emptyList();
            }
            FileTreeNodeVo tree = buildTree(root, root);
            return tree.getChildren();
        } catch (IOException e) {
            log.warn("读取插件文档树失败: id={}", id, e);
        }
        return Collections.emptyList();
    }

    private FileTreeNodeVo buildTree(Path current, Path root) throws IOException {
        String relative = root.relativize(current).toString().replace("\\", "/");
        if (relative.isEmpty()) relative = current.getFileName().toString();
        FileTreeNodeVo node = new FileTreeNodeVo(current.getFileName().toString(), relative);
        if (Files.isDirectory(current)) {
            node.setChildren(new ArrayList<>());
            // 使用 DirectoryStream.Filter 过滤隐藏文件，并收集到列表中进行排序
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(current, entry -> {
                // 过滤掉隐藏文件（以 . 开头的文件）
                return !entry.getFileName().toString().startsWith(".");
            })) {
                // 将目录流转换为列表并按文件名排序
                List<Path> sortedEntries = new ArrayList<>();
                ds.forEach(sortedEntries::add);
                sortedEntries.sort(Comparator.comparing(path -> path.getFileName().toString()));

                // 递归构建子节点
                for (Path child : sortedEntries) {
                    node.getChildren().add(buildTree(child, root));
                }
            }
        }
        return node;
    }

    @Override
    public String readDocFile(Long id, String file) {
        Plugin plugin = getPluginOrThrow(id);
        try {
            Path docPath = installedPluginDocRoot(plugin);
            Path docFile = requireChildPath(docPath.resolve(file), docPath);
            if (!Files.exists(docFile) || !Files.isRegularFile(docFile) || docFile.getFileName().toString().startsWith(".")) {
                return null;
            }
            return Files.readString(docFile);
        } catch (IOException e) {
            log.warn("读取插件文档失败: id={}, file={}", id, file, e);
        }
        return null;
    }

    @Override
    public String getLogs(Long id) {
        return readLog(id);
    }

    private void resetLogs(Long id) {
        LOG_CACHE.put(id, new PluginLogBuffer());
    }

    private void writeLog(Long id, String logLine) {
        LOG_CACHE.computeIfAbsent(id, key -> new PluginLogBuffer()).write(logLine);
    }

    private String readLog(Long id) {
        PluginLogBuffer buffer = LOG_CACHE.computeIfAbsent(id, key -> new PluginLogBuffer());
        try {
            return buffer.poll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidPluginPackage("上传文件为空");
        }
        if (file.getSize() > MAX_PLUGIN_PACKAGE_BYTES) {
            throw invalidPluginPackage("插件包不能超过300MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw invalidPluginPackage("插件包文件名不能为空");
        }
        String fileName = Paths.get(originalFilename).getFileName().toString();
        if (!StringUtils.endsWithIgnoreCase(fileName, ".tar.gz")) {
            throw invalidPluginPackage("插件包仅支持 .tar.gz 格式");
        }
    }

    private static ApiException invalidPluginPackage(String message) {
        return new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), message);
    }

    private Plugin getPluginOrThrow(Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "插件不存在");
        }
        return plugin;
    }

    private static PluginStatusType normalizeStatus(PluginStatusType status) {
        return status == null ? PluginStatusType.UN_INSTALL : status;
    }

    private static void updateOperationState(Plugin plugin,
                                             PluginStatusType status,
                                             String message,
                                             String error,
                                             boolean started) {
        Date now = new Date();
        plugin.setStatus(status);
        plugin.setOperationMessage(message);
        plugin.setOperationError(error);
        if (started) {
            plugin.setOperationStartedAt(now);
            plugin.setOperationEndedAt(null);
        } else {
            plugin.setOperationEndedAt(now);
        }
    }

    private void finishOperation(Long id, PluginStatusType status, String message, String error) {
        Plugin plugin = getPluginOrThrow(id);
        updateOperationState(plugin, status, message, error, false);
        pluginRepository.save(plugin);
    }

    private void validatePackageName(String packageName) {
        if (StringUtils.isBlank(packageName) || !SAFE_PACKAGE_PATTERN.matcher(packageName).matches()
                || packageName.contains("..") || packageName.contains("/") || packageName.contains("\\")) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "非法插件包名: " + packageName);
        }
    }

    private Path pluginRoot() {
        return Paths.get(customWebConfig.getPluginPath()).toAbsolutePath().normalize();
    }

    private Path configRoot() {
        return Paths.get(customWebConfig.getConfigPath()).toAbsolutePath().normalize();
    }

    private Path htmlPageRoot() {
        return Paths.get(customWebConfig.getHtmlPagePath()).toAbsolutePath().normalize();
    }

    private Path requireChildPath(Path candidate, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new ApiException(ResultCodeEnum.NO_AUTHORITY.getCode(), "非法文件路径: " + candidate);
        }
        return normalizedCandidate;
    }

    private void validateResourceName(String resourceName, String label) {
        if (StringUtils.isBlank(resourceName) || !SAFE_PACKAGE_PATTERN.matcher(resourceName).matches()
                || resourceName.contains("..") || resourceName.contains("/") || resourceName.contains("\\")) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + resourceName);
        }
    }

    private Path normalizeRelativePath(String path, String label) {
        if (StringUtils.isBlank(path) || path.startsWith("/") || path.contains("\\") || path.contains("\0")
                || path.contains("?") || path.contains("#") || URI_SCHEME_PATTERN.matcher(path).find()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + path);
        }
        for (String part : path.split("/", -1)) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + path);
            }
        }
        Path normalizedPath = Paths.get(path).normalize();
        if (normalizedPath.isAbsolute() || normalizedPath.startsWith("..") || normalizedPath.toString().isBlank()) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + path);
        }
        for (Path part : normalizedPath) {
            String name = part.toString();
            if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
                throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + path);
            }
        }
        return normalizedPath;
    }

    private static String normalizeMcpCode(String code) {
        String normalized = StringUtils.trimToEmpty(code).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (StringUtils.isBlank(normalized)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (normalized.length() > 64) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "MCP服务标识不能超过64个字符");
        }
        return normalized;
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private Path safePluginPath(String pluginPath) {
        if (StringUtils.isBlank(pluginPath)) {
            throw invalidPluginPackage("插件包路径不能为空");
        }
        return requireChildPath(Paths.get(pluginPath), pluginRoot());
    }

    private Path safePluginPackagePath(String pluginPath) {
        Path path = safePluginPath(pluginPath);
        if (!StringUtils.endsWithIgnoreCase(path.getFileName().toString(), ".tar.gz")
                || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw invalidPluginPackage("插件包文件不存在或格式不正确");
        }
        return path;
    }

    private Path installedPluginRoot(Plugin plugin) {
        validatePackageName(plugin.getPackageName());
        return requireChildPath(pluginRoot().resolve(plugin.getPackageName()), pluginRoot());
    }

    private Path installedPluginDocRoot(Plugin plugin) {
        return requireChildPath(installedPluginRoot(plugin).resolve("00_doc"), installedPluginRoot(plugin));
    }

    private void cleanupPluginCoreResources(Long id, Plugin plugin, boolean includeRagSkill) throws Exception {
        String packageName = plugin.getPackageName();
        validatePackageName(packageName);

        writeLog(id, "清理菜单按钮......");
        deletePluginMenus(packageName);

        writeLog(id, "清理数据看板......");
        cleanupPluginDashboards(packageName);

        writeLog(id, "清理MCP服务配置......");
        cleanupPluginMcpServers(packageName);

        writeLog(id, "清理UI配置......");
        Path installedUiPath = requireChildPath(installedPluginRoot(plugin).resolve("04_ui"), installedPluginRoot(plugin));
        cleanupPluginUi(packageName, installedUiPath);

        writeLog(id, "卸载API包......");
        extendJarManager.unload(packageName);

        writeLog(id, "清理push-task任务......");
        pushTaskService.deleteBySourceMark(packageName);

        writeLog(id, "清理meta和库表......");
        deletePluginMetaAndTables(packageName);

        writeLog(id, "清理解压目录......");
        deleteIfExists(installedPluginRoot(plugin));

        if (includeRagSkill) {
            writeLog(id, "卸载RAG中的文档......");
            try {
                vectorStoreInitializerService.unloadDocFromRag(packageName.replaceAll("\\.", "_"));
            } catch (Exception e) {
                log.error("卸载RAG中的文档失败", e);
                writeLog(id, "卸载RAG中的文档失败，跳过");
            }

            writeLog(id, "卸载插件Skill......");
            try {
                skillService.uninstallPluginSkills(packageName);
            } catch (Exception e) {
                log.error("卸载插件Skill失败", e);
                writeLog(id, "卸载插件Skill失败，跳过");
            }
        }
    }

    private List<Path> copyPluginMetaFiles(String packageName, List<Path> metaFiles) throws IOException {
        Path metaRoot = requireChildPath(configRoot().resolve("meta_config"), configRoot());
        Files.createDirectories(metaRoot);
        List<Path> copiedPaths = new ArrayList<>();
        for (Path source : metaFiles) {
            Path target = requireChildPath(metaRoot.resolve(packageName + "_" + source.getFileName()), metaRoot);
            WalkFileUtil.copy(source, target);
            copiedPaths.add(target);
        }
        return copiedPaths;
    }

    private List<String> readMetaTableNames(List<Path> metaFiles) throws IOException {
        List<String> tableNames = new ArrayList<>();
        for (Path path : metaFiles) {
            tableNames.addAll(readMetaTableNames(path));
        }
        return tableNames;
    }

    private List<String> readMetaTableNames(Path path) throws IOException {
        MetaData metaData = JacksonUtil.toObject(Files.readString(path), MetaData.class);
        if (metaData == null || metaData.getEntity() == null) {
            return Collections.emptyList();
        }
        List<String> tableNames = new ArrayList<>();
        metaData.getEntity().forEach(entity -> {
            if (StringUtils.isNotBlank(entity.getTableName())) {
                tableNames.add(entity.getTableName());
            }
        });
        return tableNames;
    }

    private void deletePluginMetaAndTables(String packageName) throws IOException {
        Path metaRoot = requireChildPath(configRoot().resolve("meta_config"), configRoot());
        if (!Files.exists(metaRoot)) {
            return;
        }
        List<Path> pluginMetaFiles;
        try (Stream<Path> paths = Files.walk(metaRoot)) {
            pluginMetaFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(packageName + "_"))
                    .toList();
        }
        for (Path path : pluginMetaFiles) {
            deletePluginTables(readMetaTableNames(path));
            deleteIfExists(path);
        }
        metaDataService.loadMetaData();
    }

    private void deletePluginTables(List<String> tableNames) {
        for (String tableName : tableNames) {
            clickhouseSchemeService.deleteTable(tableName);
        }
    }

    private void createPluginPushTasks(Long id, String packageName, PluginPackTool pluginPackTool) {
        String pushTaskConfig = pluginPackTool.readPushTaskConfigFile();
        List<PushTaskDto> pushTaskDtoList = JacksonUtil.toList(pushTaskConfig, new TypeReference<List<PushTaskDto>>() {
        });
        pushTaskDtoList.forEach(pushTaskDto -> {
            String configRef = StringUtils.defaultString(pushTaskDto.getConfig());
            if (configRef.endsWith(".toml") || configRef.endsWith(".yaml") || configRef.endsWith(".json")) {
                pushTaskDto.setConfig(pluginPackTool.readPushTaskConfigFile(configRef));
            }
            pushTaskDto.setSource("SYSTEM");
            pushTaskDto.setMark(packageName);
            if (!pushTaskService.createAndStart(pushTaskDto)) {
                writeLog(id, "push-task 创建失败: " + pushTaskDto.getName());
            }
        });
    }

    private void loadPluginApiJars(String packageName, PluginPackTool pluginPackTool) throws Exception {
        List<Path> apiFiles = pluginPackTool.listApiFiles();
        if (apiFiles.size() > 1) {
            throw new IllegalArgumentException("每个插件的 03_api 目录只能包含一个 Jar");
        }
        if (!apiFiles.isEmpty()) {
            extendJarManager.load(packageName, apiFiles.get(0).toFile());
        }
    }

    private List<Path> copyPluginUi(String packageName, PluginPackTool pluginPackTool) throws IOException {
        Path pluginUIPath = pluginPackTool.getUiPath();
        return installPluginUi(packageName, pluginUIPath);
    }

    private List<Path> installPluginUi(String packageName, Path pluginUiPath) throws IOException {
        PluginUiLayout layout = inspectPluginUi(packageName, pluginUiPath, true);
        if (layout.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> copiedPaths = new ArrayList<>();
        try {
            if (!layout.legacyFiles().isEmpty()) {
                Path legacyTarget = uiConfigPath(packageName);
                deleteIfExists(legacyTarget);
                copiedPaths.add(legacyTarget);
                Files.createDirectories(legacyTarget);
                for (Path legacyFile : layout.legacyFiles()) {
                    Path target = requireChildPath(legacyTarget.resolve(legacyFile.getFileName()), legacyTarget);
                    WalkFileUtil.copy(legacyFile, target);
                }
            }
            for (PluginUiBundle bundle : layout.bundles()) {
                Path target = uiConfigPath(bundle.configIndex());
                deleteIfExists(target);
                copiedPaths.add(target);
                WalkFileUtil.copy(bundle.sourcePath(), target);
            }
            return copiedPaths;
        } catch (IOException | RuntimeException e) {
            try {
                deletePluginUiPaths(copiedPaths);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private void exportPluginUi(String packageName, Path installedUiPath, Path exportUiPath) throws IOException {
        PluginUiLayout layout = inspectPluginUi(packageName, installedUiPath, false);
        if (layout.isEmpty()) {
            return;
        }

        if (!layout.legacyFiles().isEmpty()) {
            Path legacySource = requireUiConfigDirectory(packageName);
            WalkFileUtil.copy(legacySource, exportUiPath);
        }
        for (PluginUiBundle bundle : layout.bundles()) {
            Path source = requireUiConfigDirectory(bundle.configIndex());
            Path target = requireChildPath(exportUiPath.resolve(bundle.directoryName()), exportUiPath);
            WalkFileUtil.copy(source, target);
        }
    }

    private void cleanupPluginUi(String packageName, Path installedUiPath) throws IOException {
        PluginUiLayout layout = inspectPluginUi(packageName, installedUiPath, false);
        List<Path> uiPaths = new ArrayList<>();
        uiPaths.add(uiConfigPath(packageName));
        layout.bundles().stream()
                .map(bundle -> uiConfigPath(bundle.configIndex()))
                .forEach(uiPaths::add);
        deletePluginUiPaths(uiPaths);
    }

    private PluginUiLayout inspectPluginUi(String packageName,
                                           Path pluginUiPath,
                                           boolean validateEntryFile) throws IOException {
        validatePackageName(packageName);
        if (!Files.exists(pluginUiPath)) {
            return PluginUiLayout.empty();
        }
        if (!Files.isDirectory(pluginUiPath)) {
            throw invalidPluginPackage("04_ui 必须是目录");
        }

        List<Path> legacyFiles = new ArrayList<>();
        List<PluginUiBundle> bundles = new ArrayList<>();
        try (Stream<Path> entries = Files.list(pluginUiPath)) {
            for (Path entry : entries.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (Files.isRegularFile(entry)) {
                    legacyFiles.add(entry);
                    continue;
                }
                if (!Files.isDirectory(entry)) {
                    throw invalidPluginPackage("04_ui 包含不支持的文件类型: " + name);
                }
                if (name.endsWith("_config")) {
                    throw invalidPluginPackage("04_ui 子目录名不能包含 _config 后缀: " + name);
                }
                validateResourceName(name, "04_ui 子目录名");
                String configIndex = packageName + "." + name;
                validateResourceName(configIndex, "UI配置索引");
                if (validateEntryFile && !hasLowCodeEntryFile(entry)) {
                    throw invalidPluginPackage("04_ui 子目录缺少 site.json 或 index.json: " + name);
                }
                bundles.add(new PluginUiBundle(name, configIndex, entry));
            }
        }
        return new PluginUiLayout(List.copyOf(legacyFiles), List.copyOf(bundles));
    }

    private boolean hasLowCodeEntryFile(Path bundlePath) {
        return Files.isRegularFile(bundlePath.resolve("site.json"))
                || Files.isRegularFile(bundlePath.resolve("index.json"));
    }

    private Path uiConfigPath(String configIndex) {
        validateResourceName(configIndex, "UI配置索引");
        return requireChildPath(configRoot().resolve(configIndex + "_config"), configRoot());
    }

    private Path requireUiConfigDirectory(String configIndex) {
        Path path = uiConfigPath(configIndex);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw invalidPluginPackage("UI配置目录不存在: " + configIndex + "_config");
        }
        return path;
    }

    private void deletePluginUiPaths(Collection<Path> paths) throws IOException {
        IOException failure = null;
        List<Path> reversedPaths = new ArrayList<>(new LinkedHashSet<>(paths));
        Collections.reverse(reversedPaths);
        for (Path path : reversedPaths) {
            try {
                deleteIfExists(path);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void createPluginMenus(String packageName, PluginPackTool pluginPackTool) {
        String menuConfig = pluginPackTool.readMenuConfigFile();
        List<MenuDto> menuDtoList = JacksonUtil.toList(menuConfig, new TypeReference<List<MenuDto>>() {
        });
        menuDtoList.forEach(menuDto -> {
            menuDto.setLevel(MenuLevel.LEVEL_1);
            menuDto.setParentId(0);
            menuDto.setOrderNumber(0);
            menuDto.setSource(packageName);
            menuService.create(menuDto);
        });
    }

    private void createPluginDashboards(Long id, String packageName, PluginPackTool pluginPackTool) throws IOException {
        String dashboardConfig = pluginPackTool.readDashboardConfigFile();
        List<DashboardDto> dashboardDtoList = JacksonUtil.toList(dashboardConfig, new TypeReference<List<DashboardDto>>() {
        });
        if (dashboardDtoList.isEmpty()) {
            writeLog(id, "未发现数据看板配置，跳过");
            return;
        }

        List<Path> copiedPaths = new ArrayList<>();
        try {
            for (DashboardDto dashboardDto : dashboardDtoList) {
                if (dashboardDto == null || StringUtils.isBlank(dashboardDto.getCode())) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
                }
                validateResourceName(dashboardDto.getCode(), "看板编码");
                Optional<Dashboard> existing = dashboardRepository.findByCode(dashboardDto.getCode());
                Dashboard existingDashboard = existing.orElse(null);
                if (existingDashboard != null && !Objects.equals(packageName, existingDashboard.getSource())) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "看板编码已被其他来源占用: " + dashboardDto.getCode());
                }
                DashboardDto normalizedDto = normalizePluginDashboard(packageName, dashboardDto, pluginPackTool, copiedPaths, existingDashboard);
                Dashboard dashboard = existing.orElseGet(Dashboard::new);
                dashboard.updateFromDto(normalizedDto);
                dashboard.setSource(packageName);
                dashboardRepository.save(dashboard);
            }
        } catch (Exception e) {
            cleanupPluginDashboards(packageName);
            for (Path copiedPath : copiedPaths) {
                deleteIfExists(copiedPath);
            }
            throw e;
        }
    }

    private DashboardDto normalizePluginDashboard(String packageName,
                                                  DashboardDto dashboardDto,
                                                  PluginPackTool pluginPackTool,
                                                  List<Path> copiedPaths,
                                                  Dashboard existingDashboard) throws IOException {
        if (dashboardDto == null || StringUtils.isBlank(dashboardDto.getName())
                || StringUtils.isBlank(dashboardDto.getCode()) || dashboardDto.getType() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        validateResourceName(dashboardDto.getCode(), "看板编码");
        dashboardDto.setSource(packageName);
        dashboardDto.setIsDefault(existingDashboard == null ? Boolean.FALSE : null);
        DashboardType type = dashboardDto.getType();
        if (type == DashboardType.LOW_CODE_PAGE) {
            String configIndex = dashboardDto.getConfigIndex();
            validateResourceName(configIndex, "看板配置索引");
            Path source = requireChildPath(pluginPackTool.getDashboardLowCodePath().resolve(configIndex + "_config"), pluginPackTool.getDashboardLowCodePath());
            if (!Files.exists(source) || !Files.isDirectory(source)) {
                throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "看板低代码配置不存在: " + configIndex);
            }
            Path target = requireChildPath(configRoot().resolve(configIndex + "_config"), configRoot());
            if (existingDashboard != null && Objects.equals(packageName, existingDashboard.getSource())
                    && StringUtils.isNotBlank(existingDashboard.getConfigIndex())
                    && !Objects.equals(configIndex, existingDashboard.getConfigIndex())) {
                validateResourceName(existingDashboard.getConfigIndex(), "看板配置索引");
                deleteIfExists(requireChildPath(configRoot().resolve(existingDashboard.getConfigIndex() + "_config"), configRoot()));
            }
            if (Files.exists(target)) {
                if (existingDashboard == null || !Objects.equals(packageName, existingDashboard.getSource())) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "看板低代码配置目录已存在: " + configIndex);
                }
                deleteIfExists(target);
            }
            WalkFileUtil.copy(source, target);
            copiedPaths.add(target);
        } else if (type == DashboardType.HTML_PAGE) {
            Path relativeHtmlPath = normalizeRelativePath(dashboardDto.getHtmlPath(), "HTML看板路径");
            Path source = requireChildPath(pluginPackTool.getDashboardHtmlPath().resolve(relativeHtmlPath), pluginPackTool.getDashboardHtmlPath());
            if (!Files.exists(source) || !Files.isRegularFile(source)) {
                throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "HTML看板文件不存在: " + dashboardDto.getHtmlPath());
            }
            Path targetRoot = requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot());
            Path target = requireChildPath(targetRoot.resolve(relativeHtmlPath), targetRoot);
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            copiedPaths.add(targetRoot);
            dashboardDto.setHtmlPath(packageName + "/" + toUnixPath(relativeHtmlPath));
        } else if (type == DashboardType.LINK && StringUtils.isBlank(dashboardDto.getUrl())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        return dashboardDto;
    }

    private void exportPluginDashboards(String packageName, PluginPackTool pluginPackTool) throws IOException {
        List<DashboardDto> dashboardDtoList = new ArrayList<>();
        for (Dashboard dashboard : dashboardRepository.findBySource(packageName)) {
            DashboardDto dashboardDto = new DashboardDto();
            dashboardDto.setName(dashboard.getName());
            dashboardDto.setCode(dashboard.getCode());
            dashboardDto.setType(dashboard.getType());
            dashboardDto.setUrl(dashboard.getUrl());
            dashboardDto.setConfigIndex(dashboard.getConfigIndex());
            dashboardDto.setHtmlPath(dashboard.getHtmlPath());
            if (dashboard.getType() == DashboardType.LOW_CODE_PAGE && StringUtils.isNotBlank(dashboard.getConfigIndex())) {
                Path source = requireChildPath(configRoot().resolve(dashboard.getConfigIndex() + "_config"), configRoot());
                if (Files.exists(source)) {
                    Path target = requireChildPath(pluginPackTool.getDashboardLowCodePath().resolve(dashboard.getConfigIndex() + "_config"), pluginPackTool.getDashboardLowCodePath());
                    WalkFileUtil.copy(source, target);
                }
            } else if (dashboard.getType() == DashboardType.HTML_PAGE && StringUtils.isNotBlank(dashboard.getHtmlPath())) {
                Path relativePath = exportHtmlPagePath(packageName, dashboard.getHtmlPath());
                Path source = requireChildPath(htmlPageRoot().resolve(packageName).resolve(relativePath), requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot()));
                if (Files.exists(source) && Files.isRegularFile(source)) {
                    Path target = requireChildPath(pluginPackTool.getDashboardHtmlPath().resolve(relativePath), pluginPackTool.getDashboardHtmlPath());
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    dashboardDto.setHtmlPath(toUnixPath(relativePath));
                }
            }
            dashboardDtoList.add(dashboardDto);
        }
        pluginPackTool.writeDashboardConfig(JacksonUtil.toJson(dashboardDtoList));
    }

    private Path exportHtmlPagePath(String packageName, String htmlPath) {
        String expectedPrefix = packageName + "/";
        if (!StringUtils.startsWith(htmlPath, expectedPrefix)) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "HTML看板路径不属于插件: " + htmlPath);
        }
        return normalizeRelativePath(htmlPath.substring(expectedPrefix.length()), "HTML看板路径");
    }

    private void cleanupPluginDashboards(String packageName) throws IOException {
        List<Dashboard> dashboards = dashboardRepository.findBySource(packageName);
        if (dashboards.stream().anyMatch(item -> Boolean.TRUE.equals(item.getIsDefault()))) {
            throw new ApiException(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED);
        }
        for (Dashboard dashboard : dashboards) {
            if (dashboard.getType() == DashboardType.LOW_CODE_PAGE && StringUtils.isNotBlank(dashboard.getConfigIndex())) {
                validateResourceName(dashboard.getConfigIndex(), "看板配置索引");
                deleteIfExists(requireChildPath(configRoot().resolve(dashboard.getConfigIndex() + "_config"), configRoot()));
            }
        }
        for (Dashboard dashboard : dashboards) {
            dashboardRepository.deleteById(dashboard.getId());
        }
        deleteIfExists(requireChildPath(htmlPageRoot().resolve(packageName), htmlPageRoot()));
    }

    private void createPluginMcpServers(Long id, String packageName, PluginPackTool pluginPackTool) {
        String mcpConfig = pluginPackTool.readMcpConfigFile();
        List<McpServerDto> mcpServerDtoList = JacksonUtil.toList(mcpConfig, new TypeReference<List<McpServerDto>>() {
        });
        if (mcpServerDtoList.isEmpty()) {
            writeLog(id, "未发现MCP服务配置，跳过");
            return;
        }
        try {
            for (McpServerDto mcpServerDto : mcpServerDtoList) {
                if (mcpServerDto == null) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
                }
                String code = normalizeMcpCode(mcpServerDto.getCode());
                mcpServerDto.setCode(code);
                mcpServerDto.setSource(packageName);
                Optional<McpServerConfig> existing = mcpServerConfigRepository.findByCode(code);
                if (existing.isPresent() && !Objects.equals(packageName, existing.get().getSource())) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "MCP服务标识已被其他来源占用: " + code);
                }
                if (existing.isPresent()) {
                    mcpClientService.update(existing.get().getId(), mcpServerDto);
                } else {
                    mcpClientService.create(mcpServerDto);
                }
            }
        } catch (Exception e) {
            cleanupPluginMcpServers(packageName);
            throw e;
        }
    }

    private void exportPluginMcpServers(String packageName, PluginPackTool pluginPackTool) {
        List<McpServerDto> mcpServerDtoList = new ArrayList<>();
        for (McpServerConfig config : mcpServerConfigRepository.findBySource(packageName)) {
            McpServerDto dto = new McpServerDto();
            dto.setCode(config.getCode());
            dto.setName(config.getName());
            dto.setDescription(config.getDescription());
            dto.setBaseUrl(config.getBaseUrl());
            dto.setSseEndpoint(config.getSseEndpoint());
            dto.setHeaders(config.getHeaders());
            dto.setEnabled(config.getEnabled());
            dto.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());
            dto.setConnectTimeoutSeconds(config.getConnectTimeoutSeconds());
            mcpServerDtoList.add(dto);
        }
        pluginPackTool.writeMcpConfig(JacksonUtil.toJson(mcpServerDtoList));
    }

    private void cleanupPluginMcpServers(String packageName) {
        for (McpServerConfig config : mcpServerConfigRepository.findBySource(packageName)) {
            mcpClientService.delete(config.getId());
        }
    }

    private void deletePluginMenus(String packageName) {
        List<Menu> menuList = menuService.findBySource(packageName);
        menuList.forEach(menu -> menuService.delete(menu.getId().longValue()));
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            WalkFileUtil.delete(path);
        }
    }

    private static void checkCreateOrUpdate(PluginDto pluginDto) {
        if (StringUtils.isEmpty(pluginDto.getName()) || StringUtils.isEmpty(pluginDto.getPackageName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
        if (StringUtils.isNotBlank(pluginDto.getPackageName())
                && (!SAFE_PACKAGE_PATTERN.matcher(pluginDto.getPackageName()).matches()
                || pluginDto.getPackageName().contains("..")
                || pluginDto.getPackageName().contains("/")
                || pluginDto.getPackageName().contains("\\"))) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "非法插件包名: " + pluginDto.getPackageName());
        }
    }

    private static Path resolvePluginSkillPath(Path pluginDir) {
        return pluginDir.resolve(PLUGIN_SKILL_DIR_NAME);
    }

    private static boolean hasDirectoryContent(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(path)) {
            return paths.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private static void responseDownload(HttpServletResponse response, Path inputFilePath) {
        InputStream inputStream = null;// 文件的存放路径
        try {
            inputStream = new FileInputStream(inputFilePath.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        response.reset();
        response.setContentType("application/octet-stream");
        try {
            response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(inputFilePath.getFileName().toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] b = new byte[1024];
        int len;
        //从输入流中读取一定数量的字节，并将其存储在缓冲区字节数组中，读到末尾返回-1
        while (true) {
            try {
                if (!((len = inputStream.read(b)) > 0)) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                outputStream.write(b, 0, len);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class PluginLogBuffer {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(LOG_QUEUE_CAPACITY);
        private final Deque<String> recentLogs = new ArrayDeque<>();

        synchronized void write(String logLine) {
            recentLogs.addLast(logLine);
            while (recentLogs.size() > LOG_QUEUE_CAPACITY) {
                recentLogs.removeFirst();
            }
            if (!queue.offer(logLine)) {
                queue.poll();
                queue.offer(logLine);
            }
        }

        String poll() throws InterruptedException {
            return queue.poll(LOG_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private class CompensationStack {
        private final Long pluginId;
        private final Deque<CompensationStep> steps = new ArrayDeque<>();

        CompensationStack(Long pluginId) {
            this.pluginId = pluginId;
        }

        void add(String name, CompensationAction action) {
            steps.push(new CompensationStep(name, action));
        }

        void rollback() {
            while (!steps.isEmpty()) {
                CompensationStep step = steps.pop();
                try {
                    writeLog(pluginId, "回滚：" + step.name);
                    step.action.run();
                } catch (Exception e) {
                    log.warn("插件安装回滚失败: pluginId={}, step={}", pluginId, step.name, e);
                    writeLog(pluginId, "回滚失败：" + step.name);
                }
            }
        }
    }

    private record CompensationStep(String name, CompensationAction action) {
    }

    private record PluginUiBundle(String directoryName, String configIndex, Path sourcePath) {
    }

    private record PluginUiLayout(List<Path> legacyFiles, List<PluginUiBundle> bundles) {
        static PluginUiLayout empty() {
            return new PluginUiLayout(Collections.emptyList(), Collections.emptyList());
        }

        boolean isEmpty() {
            return legacyFiles.isEmpty() && bundles.isEmpty();
        }
    }

    private record UpgradeCandidate(PluginVo descriptor,
                                    PluginPackTool pluginPackTool,
                                    MetaData pluginMeta) {
    }

    private record PluginState(String name,
                               String icon,
                               String packageName,
                               String version,
                               String description,
                               String author,
                               String pluginPath) {
    }

    private record UpgradeSnapshot(String operationId,
                                   PluginState plugin,
                                   List<Menu> menus,
                                   List<RolePermission> rolePermissions,
                                   List<Dashboard> dashboards,
                                   List<McpServerConfig> mcpServers,
                                   List<PushTaskVo> pushTasks) {
    }

    @FunctionalInterface
    private interface CompensationAction {
        void run() throws Exception;
    }

    @Getter
    private static class PluginPackTool {
        private Path pluginFilePath;
        private Path pluginTarGzPath;
        private Path indexJsonPath;
        private Path readmePath;
        private Path docPath;
        private Path metaPath;
        private Path pushTaskPath;
        private Path apiPath;
        private Path uiPath;
        private Path menuPath;
        private Path skillPath;
        private Path dashboardPath;
        private Path dashboardLowCodePath;
        private Path dashboardHtmlPath;
        private Path mcpPath;

        public PluginPackTool buildPacker(String workspaceDir, String packageName) {
            this.pluginFilePath = Paths.get(workspaceDir).resolve("temp/" + DateUtil.getCurrentDateTime().replace(" ", "/") + "/" + packageName);
            this.pluginTarGzPath = pluginFilePath.getParent().resolve(packageName + ".tar.gz");
            return this;
        }

        public PluginPackTool buildInstaller(String workspaceDir, String packageName, String pluginTarGzFile) {
            this.pluginFilePath = Paths.get(workspaceDir, packageName);
            this.pluginTarGzPath = Paths.get(pluginTarGzFile);
            return this;
        }

        public PluginPackTool buildFromDirectory(Path directory) {
            this.pluginFilePath = directory.toAbsolutePath().normalize();
            return this;
        }

        public PluginPackTool init() {
            this.indexJsonPath = pluginFilePath.resolve("index.json");
            this.readmePath = pluginFilePath.resolve("README.md");
            this.docPath = pluginFilePath.resolve("00_doc");
            this.metaPath = pluginFilePath.resolve("01_meta");
            this.pushTaskPath = pluginFilePath.resolve("02_push-task");
            this.apiPath = pluginFilePath.resolve("03_api");
            this.uiPath = pluginFilePath.resolve("04_ui");
            this.menuPath = pluginFilePath.resolve(PLUGIN_MENU_DIR_NAME);
            this.skillPath = pluginFilePath.resolve(PLUGIN_SKILL_DIR_NAME);
            this.dashboardPath = pluginFilePath.resolve(PLUGIN_DASHBOARD_DIR_NAME);
            this.dashboardLowCodePath = dashboardPath.resolve(DASHBOARD_LOW_CODE_DIR_NAME);
            this.dashboardHtmlPath = dashboardPath.resolve(DASHBOARD_HTML_PAGE_DIR_NAME);
            this.mcpPath = pluginFilePath.resolve(PLUGIN_MCP_DIR_NAME);
            return this;
        }

        public PluginPackTool mkdir() {
            // 构建目录
            try {
                WalkFileUtil.mkdir(pluginFilePath);
                WalkFileUtil.mkdir(docPath);
                WalkFileUtil.mkdir(metaPath);
                WalkFileUtil.mkdir(pushTaskPath);
                WalkFileUtil.mkdir(apiPath);
                WalkFileUtil.mkdir(uiPath);
                WalkFileUtil.mkdir(dashboardPath);
                WalkFileUtil.mkdir(dashboardLowCodePath);
                WalkFileUtil.mkdir(dashboardHtmlPath);
                WalkFileUtil.mkdir(mcpPath);
                WalkFileUtil.mkdir(skillPath);
                WalkFileUtil.mkdir(menuPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return this;
        }

        public PluginPackTool decompress() {
            try {
                TarGzUtil.decompressTarGz(pluginTarGzPath, pluginFilePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return this;
        }

        public void writeIndexJson(String context) {
            try {
                Files.write(indexJsonPath, context.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void copyDoc(Path srcPath) {
            try {
                WalkFileUtil.copy(srcPath, docPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void copyMeta(Path srcPath, String packageName) {
            try {
                // +1是包含_
                Path destPath = metaPath.resolve(srcPath.getFileName().toString().substring(packageName.length() + 1));
                WalkFileUtil.copy(srcPath, destPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writePushTaskToml(String configFile, String configContext) {
            try {
                Files.write(pushTaskPath.resolve(configFile), configContext.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writePushTaskConfig(String configContext) {
            try {
                Files.write(pushTaskPath.resolve("config.json"), configContext.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void copyApiJar(Path currentApiJarPath) {
            try {
                WalkFileUtil.copy(currentApiJarPath, apiPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void copySkill(Path currentSkillPath) {
            try {
                if (currentSkillPath != null && Files.exists(currentSkillPath)) {
                    WalkFileUtil.copy(currentSkillPath, skillPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeMenuConfig(String configContext) {
            try {
                Files.write(menuPath.resolve("config.json"), configContext.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeDashboardConfig(String configContext) {
            try {
                Files.createDirectories(dashboardPath);
                Files.write(dashboardPath.resolve("config.json"), configContext.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeMcpConfig(String configContext) {
            try {
                Files.createDirectories(mcpPath);
                Files.write(mcpPath.resolve("config.json"), configContext.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        public String saveImageFile(String iconBase64String) {
            try {
                return ImageDataUriUtil.dataUriToFile(iconBase64String, pluginFilePath, "icon");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void writeReadme(String context) {
            try {
                Files.write(readmePath, context.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Path compressDirToTarGz() {
            try {
                TarGzUtil.compressDirToTarGz(pluginFilePath, pluginTarGzPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return pluginTarGzPath;
        }

        public List<Path> listMetaFiles() {
            if (!Files.isDirectory(metaPath)) {
                return Collections.emptyList();
            }
            try {
                try (Stream<Path> paths = Files.walk(metaPath)) {
                    return paths.filter(Files::isRegularFile) // 过滤出文件
                            .filter(path -> path.toString().endsWith(".json")) // 过滤出 .json 文件
                            .toList();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Collections.emptyList();
        }

        public String readPushTaskConfigFile() {
            return readOptionalConfigFile(pushTaskPath.resolve("config.json"));
        }

        public String readPushTaskConfigFile(String fileName) {
            try {
                return Files.readString(pushTaskPath.resolve(fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public List<Path> listApiFiles() {
            if (!Files.isDirectory(apiPath)) {
                return Collections.emptyList();
            }
            try {
                try (Stream<Path> paths = Files.walk(apiPath)) {
                    return paths.filter(Files::isRegularFile) // 过滤出文件
                            .filter(path -> path.toString().endsWith(".jar")) // 过滤
                            .sorted()
                            .toList();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Collections.emptyList();
        }

        public List<Path> listMysqlMigrationFiles() {
            Path migrationPath = apiPath.resolve("migrations/mysql");
            if (!Files.isDirectory(migrationPath)) {
                return Collections.emptyList();
            }
            try (Stream<Path> paths = Files.walk(migrationPath)) {
                return paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".sql"))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException("读取插件 MySQL 迁移目录失败", e);
            }
        }

        public String readMenuConfigFile() {
            return readOptionalConfigFile(menuPath.resolve("config.json"));
        }

        public String readDashboardConfigFile() {
            return readOptionalConfigFile(dashboardPath.resolve("config.json"));
        }

        public String readMcpConfigFile() {
            return readOptionalConfigFile(mcpPath.resolve("config.json"));
        }

        private String readOptionalConfigFile(Path path) {
            try {
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    return Files.readString(path);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public Path getSkillPath() {
            return skillPath;
        }

        public Path getDashboardLowCodePath() {
            return dashboardLowCodePath;
        }

        public Path getDashboardHtmlPath() {
            return dashboardHtmlPath;
        }
    }

}
