package com.coolxer.model.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RequestDisplayDto {

    @JsonProperty(required = true)
    private String entity;

    @JsonProperty(required = true)
    private List<String> attributeList;

}
