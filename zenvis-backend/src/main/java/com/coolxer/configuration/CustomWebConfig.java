package com.coolxer.configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.time.Duration;

/**
 * 加载配置参数
 */
@Repository
@Getter
@ToString
public class CustomWebConfig {

    /**
     * DIH会话工作空间目录
     */
    @Value("${app.paths.session.workspace}")
    private String sessionWorkspacePath;

    /**
     * 系统插件目录
     */
    @Value("${app.paths.plugins}")
    private String pluginPath;

    /**
     * AI Skill 配置目录
     */
    @Value("${app.paths.skills:${app.paths.config.base:.}/skill_config}")
    private String skillPath;

    /**
     * 系统信息文件目录
     */
    @Value("${app.paths.system.config}")
    private String systemConfigPath;

    /**
     * 静态页面存储路径
     */
    @Value("${app.paths.html.pages}")
    private String htmlPagePath;

    /**
     * 配置文件根目录
     */
    @Value("${app.paths.config.base}")
    private String configPath;

    @PostConstruct
    public void init() {
        // 如果configPath是相对路径，转换为绝对路径
        if (configPath != null && !configPath.isEmpty()) {
            File configFile = new File(configPath);
            if (configFile.exists() && !configFile.isAbsolute()) {
                configPath = configFile.getAbsolutePath();
            }
        }
    }

    /**
     * 权限拦截器检查路径
     */
    @Value("${app.security.interceptor.check.path}")
    private String needCheckPath;

    /**
     * 检索元数据文件路径
     */
    @Value("${app.paths.retrieval.metadata}")
    private String retrievalMetaFilePath;

    /**
     * 数据服务URL
     */
    @Value("${app.services.data.url}")
    private String dataServiceUrl;

    /**
     * 数据服务 Bearer Token
     */
    @ToString.Exclude
    @Value("${app.services.data.bearer-token:}")
    private String dataServiceBearerToken;

    @Value("${trend.data.size:12}")
    private int trendDateSize;

    @Value("${server.servlet.session.timeout}")
    private Duration sessionTimeout;

}
