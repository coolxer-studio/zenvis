package com.coolxer.model.retrieval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievalRequestDto {

    private Integer id;

    private String type;

    private String entity;

    private List<RequestCriteriaDto> criteriaList;

    private String criteriaLogic;

    private List<RequestDisplayDto> displayList;

    private String token;

    private String ruleName;

    private String ruleDescription;

    private String sql;

    private Integer page;

    private Integer size;

    private String sortBy;

    private String order;

}
