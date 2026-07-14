package com.coolxer.model.retrieval.vo;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievalRuleConfigVo {

    private String type;

    private String entity;

    private List<RequestCriteriaDto> criteriaList;

    private String criteriaLogic;

    private String sql;

    private List<RequestDisplayDto> displayList;
}
