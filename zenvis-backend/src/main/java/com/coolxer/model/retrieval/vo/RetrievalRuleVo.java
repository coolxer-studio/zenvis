package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.Date;

@Data
public class RetrievalRuleVo {

    private Integer id;

    private String name;

    private String description;

    private Date createTime;

    private Date updateTime;

    private String status;

    private Integer issueCount;

}
