package com.coolxer.lubinsun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lubinsun.platform")
public class LubinsunPlatformProperties {

    private String baseUrl = "https://agent.lubinsun.2333123.xyz/api";

    private String integrationToken = "";

    private long pollIntervalMs = 2000L;

    private int eventLimit = 200;

    private int sipLogLimit = 100;
}
