package com.coolxer.model.retrieval.dto;

import lombok.Data;

import java.util.List;

/** Shared logical rule fields for create/update wire DTOs. */
@Data
public abstract class RetrievalRuleConfigDto {

    private String type;

    private String entity;

    private List<RequestCriteriaDto> criteriaList;

    private String criteriaLogic;

    private List<RequestDisplayDto> displayList;

    private String sql;

    private Integer page;

    private Integer size;

    private String sortBy;

    private String order;

    protected void copyConfigTo(RetrievalRequestDto target) {
        target.setType(type);
        target.setEntity(entity);
        target.setCriteriaList(criteriaList);
        target.setCriteriaLogic(criteriaLogic);
        target.setDisplayList(displayList);
        target.setSql(sql);
        target.setPage(page);
        target.setSize(size);
        target.setSortBy(sortBy);
        target.setOrder(order);
    }
}
