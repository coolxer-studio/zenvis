package com.coolxer.model.retrieval.rule;

import com.coolxer.model.retrieval.meta.DataEntity;
import lombok.Data;

@Data
public class RetrievalSql {

    private DataEntity entity;

    private String sql;

}
