package com.coolxer.zenvis.businessservice.autoconfigure;

import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 延迟解析并固定当前进程在 Zenvis 中的服务身份。
 */
final class ZenvisBusinessServiceIdentity {

    private static final int SERVICE_CODE_MAX_LENGTH = 64;
    private static final int SERVICE_NAME_MAX_LENGTH = 128;
    private static final int INSTANCE_ID_MAX_LENGTH = 128;
    private static final int HOST_MAX_LENGTH = 255;
    private static final int VERSION_MAX_LENGTH = 64;
    private static final int ENVIRONMENT_MAX_LENGTH = 64;
    private static final int MANAGEMENT_URL_MAX_LENGTH = 512;

    private final ZenvisBusinessServiceProperties properties;
    private final Environment environment;
    private final BuildProperties buildProperties;
    private volatile BusinessServiceIdentity resolved;

    ZenvisBusinessServiceIdentity(ZenvisBusinessServiceProperties properties,
                                  Environment environment,
                                  BuildProperties buildProperties) {
        this.properties = properties;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    BusinessServiceIdentity current() {
        BusinessServiceIdentity current = resolved;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (resolved == null) {
                resolved = resolve();
            }
            return resolved;
        }
    }

    private BusinessServiceIdentity resolve() {
        String applicationName = environment.getProperty("spring.application.name");
        String serviceCode = normalizeIdentifier(
                properties.getServiceCode(),
                defaultIfBlank(applicationName, "spring-boot-service"),
                "[^A-Za-z0-9._-]",
                SERVICE_CODE_MAX_LENGTH);
        String serviceName = truncate(
                defaultIfBlank(properties.getServiceName(), serviceCode),
                SERVICE_NAME_MAX_LENGTH);
        String host = truncate(defaultIfBlank(properties.getHost(), resolveLocalHost()), HOST_MAX_LENGTH);
        Integer port = resolvePort();
        String derivedInstanceId = serviceCode + "-" + host + "-"
                + (port == null ? "no-port" : port);
        String instanceId = normalizeIdentifier(
                properties.getInstanceId(),
                derivedInstanceId,
                "[^A-Za-z0-9._:-]",
                INSTANCE_ID_MAX_LENGTH);
        String version = nullableTruncate(
                defaultIfBlank(properties.getVersion(), buildVersion()),
                VERSION_MAX_LENGTH);
        String deploymentEnvironment = nullableTruncate(
                defaultIfBlank(properties.getEnvironment(), activeProfile()),
                ENVIRONMENT_MAX_LENGTH);
        String managementUrl = nullableTruncate(
                properties.getManagementUrl(),
                MANAGEMENT_URL_MAX_LENGTH);
        Map<String, Object> configuredMetadata = properties.getMetadata() == null
                ? Collections.emptyMap()
                : properties.getMetadata();

        return new BusinessServiceIdentity(
                serviceCode,
                serviceName,
                instanceId,
                version,
                deploymentEnvironment,
                host,
                port,
                managementUrl,
                Collections.unmodifiableMap(new LinkedHashMap<>(configuredMetadata)));
    }

    private Integer resolvePort() {
        Integer port = properties.getPort();
        if (!validPort(port)) {
            port = environment.getProperty("local.server.port", Integer.class);
        }
        if (!validPort(port)) {
            port = environment.getProperty("server.port", Integer.class);
        }
        return validPort(port) ? port : null;
    }

    private String buildVersion() {
        return buildProperties == null ? null : buildProperties.getVersion();
    }

    private String activeProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 0 ? "default" : activeProfiles[0];
    }

    private static boolean validPort(Integer port) {
        return port != null && port >= 1 && port <= 65535;
    }

    private static String resolveLocalHost() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            return StringUtils.hasText(hostName) ? hostName : "localhost";
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private static String normalizeIdentifier(String value,
                                              String fallback,
                                              String invalidPattern,
                                              int maxLength) {
        String normalized = defaultIfBlank(value, fallback).trim().replaceAll(invalidPattern, "-");
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        if (!Character.isLetterOrDigit(normalized.charAt(0))) {
            normalized = "service-" + normalized;
        }
        return truncate(normalized, maxLength);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String nullableTruncate(String value, int maxLength) {
        return StringUtils.hasText(value) ? truncate(value.trim(), maxLength) : null;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
