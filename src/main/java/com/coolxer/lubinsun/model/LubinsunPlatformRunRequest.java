package com.coolxer.lubinsun.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LubinsunPlatformRunRequest {

    private String skill;

    private JsonNode input;

    private JsonNode metadata;

    private String externalId;

    private String title;

    private String taskType;

    private String agent;
}
