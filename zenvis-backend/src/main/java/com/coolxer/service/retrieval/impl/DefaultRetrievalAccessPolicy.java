package com.coolxer.service.retrieval.impl;

import com.coolxer.service.retrieval.RetrievalAccessPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultRetrievalAccessPolicy implements RetrievalAccessPolicy {

    @Override
    public void checkRead(String entity) {
        // Intentionally preserves current behavior until a role/data-scope model is defined.
    }
}
