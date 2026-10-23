package com.coolxer.service.dih;

import com.coolxer.model.dih.ContextCompactionEventData;

/**
 * Receives only real context compaction events. Implementations must not block
 * the model call path or allow observation failures to break the conversation.
 */
@FunctionalInterface
public interface ContextCompactionObserver {

    ContextCompactionObserver NOOP = new ContextCompactionObserver() {
        @Override
        public void onCompacted(ContextCompactionEventData event) {
            // Intentionally empty for ordinary chat and non-Agent Run callers.
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    void onCompacted(ContextCompactionEventData event);

    default boolean enabled() {
        return true;
    }
}
