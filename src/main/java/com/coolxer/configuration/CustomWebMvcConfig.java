package com.coolxer.configuration;

import com.coolxer.aop.AuthorityInterceptor;
import com.coolxer.aop.McpBearerTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;


/**
 * web配置类
 */
@Configuration
@Slf4j
public class CustomWebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthorityInterceptor authorityInterceptor;

    @Autowired
    private McpBearerTokenInterceptor mcpBearerTokenInterceptor;

    @Autowired
    private CustomWebConfig customWebConfig;

    @Value("${spring.ai.mcp.server.sse-endpoint:/sse}")
    private String mcpSseEndpoint;

    @Value("${spring.ai.mcp.server.sse-message-endpoint:/mcp/message}")
    private String mcpMessageEndpoint;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        List<String> mcpPatterns = mcpEndpointPatterns();
        if (!mcpPatterns.isEmpty()) {
            registry
                    .addInterceptor(mcpBearerTokenInterceptor)
                    .addPathPatterns(mcpPatterns)
                    .order(1);
        }

        registry
                .addInterceptor(authorityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/risk_index/**",
                        "/index.html",
                        "/static/**",
                        "/system-files/**",
                        "/actuator/**"
                );

    }

    private List<String> mcpEndpointPatterns() {
        List<String> patterns = new ArrayList<>();
        addEndpointPattern(patterns, mcpSseEndpoint);
        addEndpointPattern(patterns, mcpMessageEndpoint);
        return patterns;
    }

    private void addEndpointPattern(List<String> patterns, String endpoint) {
        String path = normalizeEndpoint(endpoint);
        if (path.isEmpty()) {
            return;
        }

        patterns.add(path);
        if (!"/".equals(path)) {
            patterns.add(path + "/**");
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }

        String path = endpoint.trim();
        if (path.isEmpty()) {
            return "";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置系统信息文件访问路径
        // 前端可以通过 /system-files/{filename} 访问 sysInfoPath 目录下的文件,主要存放系统图标
        registry.addResourceHandler("/system-files/**")
                .addResourceLocations("file:" + customWebConfig.getSystemConfigPath() + "/");
        // 前端可以通过 /html-page/{filename} 访问 html-page 目录下的文件,主要存放静态页面
        registry.addResourceHandler("/html-page/**")
                .addResourceLocations("file:" + customWebConfig.getHtmlPagePath() + "/");
    }

}
