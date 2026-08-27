package com.coolxer.model.retrieval.rule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalPageable {

    private Integer page;

    private Integer size;

    private String sortBy;

    private String order;

}
