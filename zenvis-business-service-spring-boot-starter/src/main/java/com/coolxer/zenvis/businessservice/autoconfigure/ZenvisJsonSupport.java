package com.coolxer.zenvis.businessservice.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;

final class ZenvisJsonSupport {

    private final ObjectMapper objectMapper;

    ZenvisJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }
}
