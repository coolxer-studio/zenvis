package com.coolxer.model.retrieval.vo;

import lombok.Data;

@Data
public class RetrievalDataListVo<T> extends DataListVo<T> {

    private String token;

}
