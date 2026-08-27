package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataAttributeResultVo {

    private String entity;

    private List<DataAttributeVo> attributeList;

    private List<SelectAttributeVo> selectAttributeList;

    private String criteriaLogic;

    private String sql;

}
