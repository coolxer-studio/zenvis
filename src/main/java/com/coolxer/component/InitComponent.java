package com.coolxer.component;


import com.coolxer.commons.enums.PluginStatusType;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.extend.ExtendJarManager;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.service.core.ClickhouseSchemeService;
import com.coolxer.service.core.MysqlSchemeService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.system.PluginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 启动加载类
 */
@Component
@Slf4j
@Order(value = 1)
@ImportResource(locations = {"classpath:kaptcha.xml"})
public class InitComponent implements CommandLineRunner {

    @Autowired
    private ClickhouseSchemeService clickhouseSchemeService;

    @Autowired
    private MysqlSchemeService mysqlSchemeService;
    @Autowired
    private DataInitiator dataInitiator;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private MetaDataService metaDataService;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private ExtendJarManager extendJarManager;

    @Override
    public void run(String... args) throws Exception {
        // 输出关键信息
        log.info("InitComponent is run");
        log.info("totalMemory:{}M", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        log.info("userDir is {}", System.getProperty("user.dir"));
        log.info("config:{}", customWebConfig.toString());


        dataInitiator.initData();
        // mysql初始化，固定/init/mysql-init.sql，仅首次启动执行
        Path mysqlInitFlag = Paths.get(customWebConfig.getSystemConfigPath(), "init", ".mysql-init.flag");
        if (!Files.exists(mysqlInitFlag)) {
            mysqlSchemeService.initScheme(customWebConfig.getSystemConfigPath() + "/init/mysql-init.sql");
            Files.createFile(mysqlInitFlag);
        }
        // clickhouse初始化/init/clickhouse-init.sql，仅首次启动执行
        Path clickhouseInitFlag = Paths.get(customWebConfig.getSystemConfigPath(), "init", ".clickhouse-init.flag");
        if (!Files.exists(clickhouseInitFlag)) {
            clickhouseSchemeService.initScheme(customWebConfig.getSystemConfigPath() + "/init/clickhouse-init.sql");
            Files.createFile(clickhouseInitFlag);
        }
        
        // 初始化加载meta
        MetaData metaData = metaDataService.loadMetaData();
        // 初始化Clickhouse表
        clickhouseSchemeService.loadSchemeFromMetaData(metaData);

        // TODO 扫描已经安装的插件，执行加载操作
        pluginService.findAll().stream().filter(plugin -> plugin.getStatus() == PluginStatusType.INSTALLED).forEach(pluginVo -> {
            Path apiPath = Paths.get(customWebConfig.getPluginPath(), pluginVo.getPackageName(), "03_api");
            try (Stream<Path> paths = Files.walk(apiPath)) {
                List<Path> apiJars = paths.filter(Files::isRegularFile) // 过滤出文件
                        .filter(path -> path.toString().endsWith(".jar")) // 过滤
                        .sorted()
                        .toList();
                if (apiJars.size() > 1) {
                    log.error("插件 {} 包含多个 API Jar，跳过加载", pluginVo.getPackageName());
                } else if (apiJars.size() == 1) {
                    if (extendJarManager.load(pluginVo.getPackageName(), apiJars.get(0).toFile())) {
                        log.info("插件 API 已加载: {}", pluginVo.getPackageName());
                    } else {
                        log.info("插件 API 已加载（无需重复）: {}", pluginVo.getPackageName());
                    }
                }
            } catch (IOException e) {
                log.error("扫描插件 API 失败: {}", pluginVo.getPackageName(), e);
            } catch (Exception e) {
                log.error("加载插件 API 失败: {}", pluginVo.getPackageName(), e);
            }
        });
    }


}
