package com.coolxer.plugin.lab.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LabCapability(
        String code,
        String name,
        String category,
        String description,
        String demoEntry,
        boolean readOnly) {
}
