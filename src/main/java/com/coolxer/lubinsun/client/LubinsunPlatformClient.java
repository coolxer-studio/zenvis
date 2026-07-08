package com.coolxer.lubinsun.client;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.lubinsun.config.LubinsunPlatformProperties;
import com.coolxer.lubinsun.model.LubinsunPlatformRunRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class LubinsunPlatformClient {

    private final RestTemplate restTemplate;
    private final LubinsunPlatformProperties properties;
    private final ObjectMapper objectMapper;

    public LubinsunPlatformClient(RestTemplate restTemplate, LubinsunPlatformProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper == null ? JacksonConfig.OBJECT_MAPPER : objectMapper;
    }

    public JsonNode createRun(LubinsunPlatformRunRequest request) {
        return exchangeJson("/platform/runs", HttpMethod.POST, request);
    }

    public JsonNode getRun(String runId) {
        return exchangeJson("/platform/runs/" + encodePath(runId), HttpMethod.GET, null);
    }

    public List<JsonNode> getEvents(String runId, long after, int limit) {
        JsonNode response = exchangeJson(UriComponentsBuilder
                        .fromPath("/platform/runs/" + encodePath(runId) + "/events")
                        .queryParam("after", after)
                        .queryParam("limit", limit)
                        .build(false)
                        .toUriString(),
                HttpMethod.GET,
                null);
        if (response == null || !response.isArray()) {
            return List.of();
        }
        List<JsonNode> events = new ArrayList<>();
        response.forEach(events::add);
        return events;
    }

    private JsonNode exchangeJson(String pathWithQuery, HttpMethod method, Object body) {
        validateProperties();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
            headers.set("X-Integration-Token", properties.getIntegrationToken());

            String requestBody = body == null ? null : objectMapper.writeValueAsString(body);
            HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    normalizeBaseUrl() + pathWithQuery,
                    method,
                    entity,
                    String.class
            );
            String responseBody = response.getBody();
            if (StringUtils.isBlank(responseBody)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.warn("调用 Lubinsun 平台失败: {} {}", method, pathWithQuery, e);
            throw new IllegalStateException("调用 Lubinsun 平台失败: " + e.getMessage(), e);
        }
    }

    private void validateProperties() {
        if (StringUtils.isBlank(properties.getBaseUrl())) {
            throw new IllegalStateException("Lubinsun Base URL 未配置");
        }
        if (StringUtils.isBlank(properties.getIntegrationToken())) {
            throw new IllegalStateException("Lubinsun Integration Token 未配置");
        }
    }

    private String normalizeBaseUrl() {
        return StringUtils.removeEnd(properties.getBaseUrl().trim(), "/");
    }

    private static String encodePath(String value) {
        return UriComponentsBuilder.fromPath(value == null ? "" : value).build().encode().toUriString();
    }
}
