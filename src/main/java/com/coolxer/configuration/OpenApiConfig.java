package com.coolxer.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置类
 * 配置 Swagger UI 文档信息
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("sessionCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("普通 Web API 使用服务端 Session/Cookie 鉴权"))
                        .addSecuritySchemes("mcpBearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("仅 MCP Server SSE/消息端点使用 Bearer Token 鉴权")))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie"))
                .info(new Info()
                        .title("ZenVis API")
                        .version("v1")
                        .description("ZenVis API 文档。统一业务响应格式为 { status, msg, data }，status=0 表示成功；JSON 字段使用 snake_case。")
                        .contact(new Contact()
                                .name("Coolxer")
                                .email("coolxer@163.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
