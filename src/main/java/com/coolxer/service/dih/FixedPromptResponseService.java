package com.coolxer.service.dih;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class FixedPromptResponseService {

    private static final String RESPONSE_FILE = "classpath:fixed-chat-responses.json";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private Map<String, String> fixedResponses = Collections.emptyMap();

    public FixedPromptResponseService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        Resource resource = resourceLoader.getResource(RESPONSE_FILE);
        if (!resource.exists()) {
            log.info("Fixed prompt response file not found: {}", RESPONSE_FILE);
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, String> responses = objectMapper.readValue(
                    inputStream,
                    new TypeReference<LinkedHashMap<String, String>>() {
                    }
            );
            fixedResponses = Collections.unmodifiableMap(responses == null ? Collections.emptyMap() : responses);
            log.info("Loaded {} fixed prompt responses from {}", fixedResponses.size(), RESPONSE_FILE);
        } catch (Exception e) {
            fixedResponses = Collections.emptyMap();
            log.warn("Failed to load fixed prompt responses from {}", RESPONSE_FILE, e);
        }
    }

    public Optional<String> findResponse(String prompt) {
        if (!StringUtils.hasText(prompt) || fixedResponses.isEmpty()) {
            return Optional.empty();
        }
        String response = fixedResponses.get(prompt);
        if (response == null) {
            response = fixedResponses.get(prompt.trim());
        }
        return Optional.ofNullable(response);
    }
}
