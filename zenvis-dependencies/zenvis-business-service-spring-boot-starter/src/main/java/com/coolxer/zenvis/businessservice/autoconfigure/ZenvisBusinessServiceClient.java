package com.coolxer.zenvis.businessservice.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

final class ZenvisBusinessServiceClient implements ZenvisBusinessServiceTransport {

    private static final Logger log = LoggerFactory.getLogger(ZenvisBusinessServiceClient.class);
    private static final String HEARTBEAT_PATH = "/api/v1/public/business-services/heartbeat";
    private static final String EVENTS_PATH = "/api/v1/public/business-services/events";
    private static final ParameterizedTypeReference<ZenvisResponse<BusinessServiceHeartbeatAck>>
            HEARTBEAT_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ZenvisResponse<BusinessServiceEventAck>>
            EVENT_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final ZenvisBusinessServiceProperties properties;

    ZenvisBusinessServiceClient(RestTemplate restTemplate,
                                ZenvisBusinessServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public boolean reportHeartbeat(BusinessServiceHeartbeatRequest request) {
        return post(HEARTBEAT_PATH, request, HEARTBEAT_RESPONSE_TYPE, "心跳");
    }

    @Override
    public boolean reportEvent(BusinessServiceEventRequest request) {
        return post(EVENTS_PATH, request, EVENT_RESPONSE_TYPE, "事件");
    }

    private <T> boolean post(String path,
                             Object request,
                             ParameterizedTypeReference<ZenvisResponse<T>> responseType,
                             String operation) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            log.warn("Zenvis {}上报失败: base-url 未配置", operation);
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            ResponseEntity<ZenvisResponse<T>> response = restTemplate.exchange(
                    endpoint(path),
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    responseType);
            ZenvisResponse<T> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Zenvis {}上报失败: HTTP {}", operation, response.getStatusCode().value());
                return false;
            }
            if (body == null || !body.successful()) {
                log.warn("Zenvis {}上报业务失败: status={}, msg={}",
                        operation,
                        body == null ? null : body.status(),
                        body == null ? null : body.msg());
                return false;
            }
            return true;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Zenvis {}上报失败: {}", operation, e.getMessage());
            return false;
        }
    }

    private String endpoint(String path) {
        String baseUrl = properties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }
}
