package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.query.DataQueryContext;
import com.coolxer.model.retrieval.rule.RetrievalRule;

public interface DataQueryService {

    DataQueryContext query(RetrievalRule retrievalRule);

}
