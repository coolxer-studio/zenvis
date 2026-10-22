package com.coolxer.plugin.lab.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public final class LabNoteRequests {

    private LabNoteRequests() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Upsert(
            String title,
            String content,
            String status,
            String owner) {
    }
}
