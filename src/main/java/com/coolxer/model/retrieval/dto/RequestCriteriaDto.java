package com.coolxer.model.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RequestCriteriaDto {

    @JsonProperty(required = true)
    private String attribute;

    @JsonProperty(required = true)
    private String operator;

    @JsonProperty(required = true)
    private List<String> valueList;

}
