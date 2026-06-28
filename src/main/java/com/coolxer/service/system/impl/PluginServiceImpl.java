package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.MenuLevel;
import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.extend.ExtendJarManager;
import com.coolxer.dao.mysql.entity.Menu;
import com.coolxer.dao.mysql.entity.Plugin;
import com.coolxer.dao.mysql.repository.PluginRepository;
import com.coolxer.model.base.vo.FileTreeNodeVo;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.system.dto.MenuDto;
import com.coolxer.model.system.dto.PluginDto;
import com.coolxer.model.system.dto.PluginSearchDto;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.model.system.vo.PluginVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.dih.agent.skill.SkillService;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

/**
 * 插件接口实现
 */
@Slf4j
@Service
public class PluginServiceImpl implements PluginService {

    private static final String PLUGIN_SKILL_DIR_NAME = "06_skill";
    private static final String LEGACY_PLUGIN_SKILL_DIR_NAME = "skill";

    @Autowired
    private PluginRepository pluginRepository;

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

<<<<<<< HEAD
    @Autowired
    private VectorStoreInitializerService vectorStoreInitializerService;

    @Autowired
    private SkillService skillService;

=======
>>>>>>> upstream/main
    private static ConcurrentSkipListMap<Long, String> LOG_CACHE = new ConcurrentSkipListMap<>();

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
                plugin.updateFromDto(pluginDto);
                if (pluginDto.getPackageName() != plugin.getPackageName() && isPackageExist(pluginDto.getPackageName())) {
                    throw new ApiException(ResultCodeEnum.PLUGIN_IS_EXIST);
                } else if (plugin.getStatus() == PluginStatusType.INSTALLED) {
                    // 已经加载的不支持更新插件包
                    throw new ApiException(ResultCodeEnum.PLUGIN_IS_INSTALLED);
                } else {
                    pluginRepository.save(plugin);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("更新对象失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin.getStatus() == PluginStatusType.INSTALLED) {
            // 已经加载的不支持删除
            throw new ApiException(ResultCodeEnum.PLUGIN_IS_INSTALLED);
        }
        if (plugin != null) {
            try {
                Path pluginPath = Paths.get(plugin.getPluginPath());
                if (Files.exists(pluginPath) && Files.isRegularFile(pluginPath)) {
                    WalkFileUtil.delete(pluginPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }
        try {
            Path directory = Paths.get(customWebConfig.getPluginPath());
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            Path tempDir = directory.resolve("temp/" + DateUtil.getCurrentDateTime().replace(" ", "/"));
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }
            Path path = tempDir.resolve(file.getOriginalFilename());
            Files.write(path, file.getBytes());
            // 解析插件包，提取信息
            String pluginJsonString = TarGzUtil.readRootFile(path, "index.json");
            PluginVo pluginVo = JacksonUtil.toObject(pluginJsonString, PluginVo.class);
            // icon转化
            if (!ImageDataUriUtil.isDataUrl(pluginVo.getIcon())) {
                pluginVo.setIcon(ImageDataUriUtil.toDataUri(pluginVo.getIcon(), TarGzUtil.readBase64File(path, pluginVo.getIcon())));
            }
            pluginVo.setPluginPath(path.toString());
            return pluginVo;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            throw new RuntimeException("插件不存在");
        }
        Path pluginTarGzPath = null;
        if (plugin.getStatus() != PluginStatusType.INSTALLED && StringUtils.isNotEmpty(plugin.getPluginPath())) {
            // 未安装状态，存在默认插件包，直接输出
            pluginTarGzPath = Paths.get(plugin.getPluginPath());
        } else {
            // 已安装状态，或者没有默认包，以加载内容输出插件包.构建打包属性包装类
            PluginPackTool pluginPackTool = new PluginPackTool().buildPacker(customWebConfig.getPluginPath(), plugin.getPackageName())
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
                Path docPath = Paths.get(customWebConfig.getPluginPath(), plugin.getPackageName(), "00_doc");
                pluginPackTool.copyDoc(docPath);
                // 拷贝 Skill 配置
                Path installedSkillPath = skillService.getInstalledPluginSkillPath(plugin.getPackageName());
                Path currentSkillPath = hasDirectoryContent(installedSkillPath)
                        ? installedSkillPath
                        : resolvePluginSkillPath(Paths.get(customWebConfig.getPluginPath(), plugin.getPackageName()));
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
                Path currentApiJarPath = Paths.get(customWebConfig.getPluginPath()).resolve(plugin.getPackageName()).resolve("03_api");
                pluginPackTool.copyApiJar(currentApiJarPath);
                // 2-5 构建UI配置
                Path currentUIPath = Paths.get(customWebConfig.getConfigPath()).resolve(plugin.getPackageName() + "_config");
                pluginPackTool.copyUI(currentUIPath);
                // 2-6 构建菜单配置
                List<Menu> menuList = menuService.findBySource(plugin.getPackageName());
                List<MenuVo> menuVoList = menuList.stream().map(pushTask -> {
                    MenuVo menuVo = new MenuVo();
                    menuVo.setName(pushTask.getName());
                    menuVo.setParams(pushTask.getParams());
                    menuVo.setType(pushTask.getType());
                    return menuVo;
                }).toList();
                pluginPackTool.writeMenuConfig(JacksonUtil.toJson(menuVoList));
                // 2-7 压缩目录
                pluginTarGzPath = pluginPackTool.compressDirToTarGz();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // 3.下载插件
        responseDownload(response, pluginTarGzPath);
    }

    @Override
    public boolean uninstall(Long id) {
        writeLog(id, "插件检查......");
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            writeLog(id, "插件检查异常！！！");
            writeLog(id, "完成......");
            return false;
        }
        try {
            writeLog(id, "1 删除菜单按钮......");
            List<Menu> menuList = menuService.findBySource(plugin.getPackageName());
            menuList.forEach(menu -> {
                menuService.delete(menu.getId().longValue());
            });
            // 2-ui
            writeLog(id, "2 删除UI配置......");
            Path uiPath = Paths.get(customWebConfig.getConfigPath()).resolve(plugin.getPackageName() + "_config");
            if (Files.exists(uiPath)) {
                WalkFileUtil.delete(uiPath);
            }
            writeLog(id, "3 删除API包......");
            extendJarManager.unload(plugin.getPackageName());
            writeLog(id, "4 删除push-task任务和文件......");
            pushTaskService.deleteBySourceMark(plugin.getPackageName());
            writeLog(id, "5 删除meta，删除库表......");
            try (Stream<Path> paths = Files.walk(Paths.get(customWebConfig.getConfigPath()).resolve("meta_config"))) {
                paths.filter(Files::isRegularFile) // 过滤出文件
                        .filter(path -> path.getFileName().toString().startsWith(plugin.getPackageName())) // 过滤
                        .forEach(path -> {
                            try {
                                // 删除库表
                                MetaData metaData = JacksonUtil.toObject(Files.readString(path), MetaData.class);
                                metaData.getEntity().forEach(entity -> {
                                    clickhouseSchemeService.deleteTable(entity.getTableName());
                                });
                                // 删除文件
                                if (Files.exists(path)) {
                                    WalkFileUtil.delete(path);
                                }
                                // 重新加载元数据
                                metaDataService.loadMetaData();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
            writeLog(id, "6 删除解压的插件包......");
            Path pluginDir = Paths.get(customWebConfig.getPluginPath(), plugin.getPackageName());
            if (Files.exists(pluginDir)) {
                WalkFileUtil.delete(pluginDir);
            }
            writeLog(id, "7 卸载RAG中的文档......");
            try {
                vectorStoreInitializerService.unloadDocFromRag(plugin.getPackageName().replaceAll("\\.", "_"));
            } catch (Exception e) {
                log.error("卸载RAG中的文档失败......", e);
                writeLog(id, "卸载RAG中的文档失败......，跳过");
            }
            writeLog(id, "8 卸载插件Skill......");
            try {
                skillService.uninstallPluginSkills(plugin.getPackageName());
            } catch (Exception e) {
                log.error("卸载插件Skill失败......", e);
                writeLog(id, "卸载插件Skill失败......，跳过");
            }
            writeLog(id, "9 更新插件状态......");
            plugin.setStatus(PluginStatusType.UN_INSTALL);
            pluginRepository.save(plugin);
            writeLog(id, "完成......");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean install(Long id) {
        writeLog(id, "插件检查......");
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin == null) {
            writeLog(id, "插件检查异常！！！");
            writeLog(id, "完成......");
            return false;
        }
        try {
            // 构建插件包解析类
            PluginPackTool pluginPackTool = new PluginPackTool()
                    .buildInstaller(customWebConfig.getPluginPath(), plugin.getPackageName(), plugin.getPluginPath())
                    .init()
                    .decompress();
            writeLog(id, "1-1 拷贝meta并加载......");
            pluginPackTool.listMetaFiles()
                    .forEach(path -> {
                        try {
                            Path metaPath = Paths.get(customWebConfig.getConfigPath())
                                    .resolve("meta_config")
                                    .resolve(plugin.getPackageName() + "_" + path.getFileName());
                            WalkFileUtil.copy(path, metaPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            writeLog(id, "1-2 初始化加载meta......");
            MetaData metaData = metaDataService.loadMetaData();
            writeLog(id, "1-3 初始化Scheme表......");
            clickhouseSchemeService.loadSchemeFromMetaData(metaData);
            writeLog(id, "2-1 读取push-task......");
            String pushTaskConfig = pluginPackTool.readPushTaskConfigFile();
            List<PushTaskDto> pushTaskDtoList = JacksonUtil.toList(pushTaskConfig, new TypeReference<List<PushTaskDto>>() {
            });
            writeLog(id, "2-2 存储任务......");
            pushTaskDtoList.forEach(pushTaskDto -> {
                if (pushTaskDto.getConfig().endsWith(".toml") || pushTaskDto.getConfig().endsWith(".yaml") || pushTaskDto.getConfig().endsWith(".json")) {
                    pushTaskDto.setConfig(pluginPackTool.readPushTaskConfigFile(pushTaskDto.getConfig()));
                }
                pushTaskDto.setSource("SYSTEM");
                pushTaskDto.setMark(plugin.getPackageName());
                pushTaskService.createAndStart(pushTaskDto);
            });
            writeLog(id, "3-1 读取api配置......");
            pluginPackTool.listApiFiles()
                    .forEach(jarPath -> {
                        try {
                            // 3-1加载到扩展插件
                            if (extendJarManager.load(plugin.getPackageName(), jarPath.toFile())) {
                                log.info("loaded");
                            } else {
                                log.info("loaded(already)");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
            writeLog(id, "4-1 拷贝ui配置......");
            Path pluginUIPath = pluginPackTool.getUiPath();
            if (Files.exists(pluginUIPath)) {
                Path uiPath = Paths.get(customWebConfig.getConfigPath()).resolve(plugin.getPackageName() + "_config");
                WalkFileUtil.copy(pluginUIPath, uiPath);
            }
            writeLog(id, "5-1 解析菜单配置......");
            String menuConfig = pluginPackTool.readMenuConfigFile();
            List<MenuDto> menuDtoList = JacksonUtil.toList(menuConfig, new TypeReference<List<MenuDto>>() {
            });
            writeLog(id, "5-2 存储菜单信息......");
            menuDtoList.forEach(menuDto -> {
                menuDto.setLevel(MenuLevel.LEVEL_1);
                menuDto.setParentId(0);
                menuDto.setOrderNumber(0);
                menuDto.setSource(plugin.getPackageName());
                menuService.create(menuDto);
            });
            writeLog(id, "6 更新插件状态为已安装......");
            plugin.setStatus(PluginStatusType.INSTALLED);
            pluginRepository.save(plugin);
            writeLog(id, "7 文档加载到RAG......");
            try {
                vectorStoreInitializerService.loadDocToRag(plugin.getPackageName().replaceAll("\\.", "_"), pluginPackTool.getDocPath());
            } catch (Exception e) {
                log.error("加载到RAG失败......", e);
                writeLog(id, "加载到RAG失败......，跳过");
            }
            writeLog(id, "8 加载插件Skill......");
            try {
                skillService.installPluginSkills(plugin.getPackageName(), pluginPackTool.getSkillPath());
            } catch (Exception e) {
                log.error("加载插件Skill失败......", e);
                writeLog(id, "加载插件Skill失败......，跳过");
            }
            writeLog(id, "完成......");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    @Override
    public String readme(Long id) {
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin != null) {
            try {
                if (plugin.getPluginPath() != null) {
                    return TarGzUtil.readRootFile(Paths.get(plugin.getPluginPath()), "README.md");
                } else {
                    return "# " + plugin.getName() + "\n\n" + "暂无";
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
            Plugin plugin = pluginRepository.findById(id).orElse(null);
            Path docPath = Paths.get(customWebConfig.getPluginPath(), plugin.getPackageName(), "00_doc");
            Path root = docPath.toAbsolutePath().normalize();
            FileTreeNodeVo tree = buildTree(root, root);
            return tree.getChildren();
        } catch (IOException e) {
            e.printStackTrace();
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
        Plugin plugin = pluginRepository.findById(id).orElse(null);
        if (plugin != null) {
            Path docPath = Paths.get(customWebConfig.getPluginPath(), plugin.getPackageName(), "00_doc");
            // 检查目录是否存在
            if (Files.exists(docPath)) {
                File docFile = docPath.resolve(file).toFile();
                if (docFile.getAbsolutePath().startsWith(docPath.toFile().getAbsolutePath())) {
                    try {
                        return Files.readString(docFile.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String getLogs(Long id) {
        return readLog(id);
    }

    private String writeLog(Long id, String log) {
        String oldLog = LOG_CACHE.get(id);
        if (oldLog == null) {
            LOG_CACHE.put(id, log);
        } else {
            LOG_CACHE.put(id, oldLog + "\n" + log);
        }
        return oldLog;
    }

    private String readLog(Long id) {
        String log = LOG_CACHE.get(id);
        if (log != null) {
            LOG_CACHE.remove(id);
        }
        return log;
    }

    private static void checkCreateOrUpdate(PluginDto pluginDto) {
        if (StringUtils.isEmpty(pluginDto.getName()) || StringUtils.isEmpty(pluginDto.getPackageName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
    }

    private static Path resolvePluginSkillPath(Path pluginDir) {
        Path skillPath = pluginDir.resolve(PLUGIN_SKILL_DIR_NAME);
        Path legacySkillPath = pluginDir.resolve(LEGACY_PLUGIN_SKILL_DIR_NAME);
        if (hasDirectoryContent(skillPath) || !hasDirectoryContent(legacySkillPath)) {
            return skillPath;
        }
        return legacySkillPath;
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
        private Path legacySkillPath;

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
            this.menuPath = pluginFilePath.resolve("05_menu");
            this.skillPath = pluginFilePath.resolve(PLUGIN_SKILL_DIR_NAME);
            this.legacySkillPath = pluginFilePath.resolve(LEGACY_PLUGIN_SKILL_DIR_NAME);
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
                WalkFileUtil.mkdir(menuPath);
                WalkFileUtil.mkdir(skillPath);
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
            try {
                return Files.readString(menuPath.resolve("config.json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public Path getSkillPath() {
            if (hasDirectoryContent(skillPath) || !hasDirectoryContent(legacySkillPath)) {
                return skillPath;
            }
            return legacySkillPath;
        }
    }

}
