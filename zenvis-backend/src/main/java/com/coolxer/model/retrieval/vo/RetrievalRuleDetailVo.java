package com.coolxer.model.retrieval.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievalRuleDetailVo {

    private Integer id;

    private String name;

    private String description;

    private Date createTime;

    private Date updateTime;

    private RetrievalRuleConfigVo config;

    private String status;

    private List<RetrievalRuleIssueVo> issues;

    private List<DataEntityVo> entityList;

    private List<DataAttributeVo> attributeList;
}
