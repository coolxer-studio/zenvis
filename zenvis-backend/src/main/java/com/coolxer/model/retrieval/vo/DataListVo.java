package com.coolxer.model.retrieval.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DataListVo<T> {

    private BigDecimal total;

    @JsonProperty("datalist")
    private List<T> dataList;

}
