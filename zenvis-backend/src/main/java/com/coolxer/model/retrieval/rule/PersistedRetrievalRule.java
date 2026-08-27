package com.coolxer.model.retrieval.rule;

import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/** Canonical, metadata-independent persisted retrieval rule schema. */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PersistedRetrievalRule {

    private Integer schemaVersion = 2;

    private String type;

    private String entity;

    private List<RequestCriteriaDto> criteriaList;

    private String criteriaLogic;

    private String sql;

    private List<RequestDisplayDto> displayList;

    private Integer page;

    private Integer size;

    private String sortBy;

    private String order;
}
