package com.coolxer.service.retrieval;

/**
 * Extension point for future entity/field/row retrieval authorization.
 * The default implementation preserves the current authenticated-user behavior.
 */
public interface RetrievalAccessPolicy {

    void checkRead(String entity);
}
