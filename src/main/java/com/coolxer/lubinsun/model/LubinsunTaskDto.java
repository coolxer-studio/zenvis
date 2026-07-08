package com.coolxer.lubinsun.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class LubinsunTaskDto {

    private String name;

    private String skill;

    private String agent;

    @JsonAlias("external_id")
    private String externalId;

    private String title;

    @JsonAlias("task_type")
    private String taskType;

    @JsonAlias({"ip", "target_ip"})
    private String ip;

    @JsonAlias({"raw_log", "rawLog", "original_log", "log_text"})
    private String rawLog;

    @JsonAlias("input_json")
    private String inputJson;

    @JsonAlias("metadata_json")
    private String metadataJson;
}
