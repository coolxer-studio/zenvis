package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.extend.ExtendJarManager;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.entity.McpServerConfig;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.dao.mysql.repository.McpServerConfigRepository;
import com.coolxer.dao.mysql.repository.PluginRepository;
import com.coolxer.model.dih.dto.McpServerDto;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.system.dto.DashboardDto;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
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
    private static final String HTML_PAGE_PUBLIC_PREFIX = "/html-page/";
    private static final long MAX_PLUGIN_PACKAGE_BYTES = 300L * 1024L * 1024L;
    private static final Pattern SAFE_PACKAGE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,255}$");
    private static final int LOG_QUEUE_CAPACITY = 512;
    private static final long LOG_POLL_TIMEOUT_SECONDS = 2L;

    @Autowired
    private PluginRepository pluginRepository;

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
    public Boolean update(Long id, PluginDto pluginDto) {
        checkCreateOrUpdate(pluginDto);
        try {
            Optional<Plugin> optionalPlugin = pluginRepository.findById(id);
            if (optionalPlugin.isPresent()) {
                Plugin plugin = optionalPlugin.get();
                String oldPackageName = plugin.getPackageName();
                PluginStatusType status = normalizeStatus(plugin.getStatus());
                if (status == PluginStatusType.INSTALLED || status.isInProgress()) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_IS_INSTALLED);
                }
                plugin.updateFromDto(pluginDto);
                if (!Objects.equals(oldPackageName, pluginDto.getPackageName()) && isPackageExist(pluginDto.getPackageName())) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_IS_EXIST);
                } else {
                    pluginRepository.save(plugin);
                }
                return true;
            }
            return false;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            return;
        }
        PluginStatusType status = normalizeStatus(plugin.getStatus());
        if (status == PluginStatusType.INSTALLED || status.isInProgress()) {
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
                Path currentUIPath = requireChildPath(configRoot().resolve(plugin.getPackageName() + "_config"), configRoot());
                pluginPackTool.copyUI(currentUIPath);
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

            writeLog(id, "4 加载API包......");
            compensationStack.add("卸载API包", () -> extendJarManager.unload(packageName));
            loadPluginApiJars(packageName, pluginPackTool);

            writeLog(id, "5 拷贝UI配置......");
            Path uiPath = copyPluginUi(packageName, pluginPackTool);
            if (uiPath != null) {
                compensationStack.add("删除UI配置", () -> deleteIfExists(uiPath));
            }

            writeLog(id, "6 存储数据看板......");
            compensationStack.add("删除数据看板", () -> cleanupPluginDashboards(packageName));
            createPluginDashboards(id, packageName, pluginPackTool);

            writeLog(id, "7 存储MCP服务配置......");
            compensationStack.add("删除MCP服务配置", () -> cleanupPluginMcpServers(packageName));
            createPluginMcpServers(id, packageName, pluginPackTool);

            writeLog(id, "8 文档加载到RAG......");
            try {
                vectorStoreInitializerService.loadDocToRag(packageName.replaceAll("\\.", "_"), pluginPackTool.getDocPath());
            } catch (Exception e) {
                log.error("加载到RAG失败", e);
                warnings.add("RAG加载失败");
                writeLog(id, "加载到RAG失败，跳过");
            }

            writeLog(id, "9 加载插件Skill......");
            compensationStack.add("卸载插件Skill", () -> skillService.uninstallPluginSkills(packageName));
            try {
                skillService.installPluginSkills(packageName, pluginPackTool.getSkillPath());
            } catch (Exception e) {
                log.error("加载插件Skill失败", e);
                warnings.add("Skill加载失败");
                writeLog(id, "加载插件Skill失败，跳过");
            }

            writeLog(id, "10 存储菜单信息......");
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
            writeLog(id, "插件检查......");
            cleanupPluginCoreResources(id, plugin, true);
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
        if (StringUtils.isBlank(path) || path.contains("\\") || path.contains("\0")) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), label + "不合法: " + path);
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
        deleteIfExists(requireChildPath(configRoot().resolve(packageName + "_config"), configRoot()));

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
        for (Path jarPath : pluginPackTool.listApiFiles()) {
            extendJarManager.load(packageName, jarPath.toFile());
        }
    }

    private Path copyPluginUi(String packageName, PluginPackTool pluginPackTool) throws IOException {
        Path pluginUIPath = pluginPackTool.getUiPath();
        if (!Files.exists(pluginUIPath)) {
            return null;
        }
        Path uiPath = requireChildPath(configRoot().resolve(packageName + "_config"), configRoot());
        deleteIfExists(uiPath);
        WalkFileUtil.copy(pluginUIPath, uiPath);
        return uiPath;
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
            dashboardDto.setHtmlPath(HTML_PAGE_PUBLIC_PREFIX + packageName + "/" + toUnixPath(relativeHtmlPath));
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
        String expectedPrefix = HTML_PAGE_PUBLIC_PREFIX + packageName + "/";
        if (!StringUtils.startsWith(htmlPath, expectedPrefix)) {
            throw new ApiException(ResultCodeEnum.PLUGIN_PACKAGE_INVALID.getCode(), "HTML看板路径不属于插件: " + htmlPath);
        }
        return normalizeRelativePath(htmlPath.substring(expectedPrefix.length()), "HTML看板路径");
    }

    private void cleanupPluginDashboards(String packageName) throws IOException {
        List<Dashboard> dashboards = dashboardRepository.findBySource(packageName);
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

        public void copyUI(Path currentUIPath) {
            try {
                WalkFileUtil.copy(currentUIPath, uiPath);
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
            try {
                return Files.readString(pushTaskPath.resolve("config.json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
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
            try {
                try (Stream<Path> paths = Files.walk(apiPath)) {
                    return paths.filter(Files::isRegularFile) // 过滤出文件
                            .filter(path -> path.toString().endsWith(".jar")) // 过滤
                            .toList();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Collections.emptyList();
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
