package com.coolxer.model.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievalRequestDto {

    @JsonAlias("rule_id")
    @JsonProperty(required = false)
    private Integer id;

    @JsonProperty(required = false)
    private String type;

    @JsonProperty(required = true)
    private String entity;

    @JsonProperty(required = false)
    private List<RequestCriteriaDto> criteriaList;

    @JsonProperty(required = false)
    private String criteriaLogic;

    @JsonProperty(required = true)
    private List<RequestDisplayDto> displayList;

    @JsonProperty(required = false)
    private String token;

    @JsonProperty(required = false)
    private String ruleName;

    @JsonProperty(required = false)
    private String ruleDescription;

    @JsonProperty(required = false)
    private String sql;

    @JsonProperty(required = false)
    private Integer page;

    @JsonProperty(required = false)
    private Integer size;

    @JsonProperty(required = false)
    private String sortBy;

    @JsonProperty(required = false)
    private String order;

}
