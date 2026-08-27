package com.coolxer.model.retrieval.vo;

import lombok.Data;

import java.util.List;

@Data
public class DataEntityResultVo {

    private List<DataEntityVo> entityList;

    private List<String> selectedEntity;

}
