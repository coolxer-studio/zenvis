package com.coolxer.configuration;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Propagates an owning user to JPA auditing for background work that has no HTTP request context.
 */
public final class JpaAuditorContext {

    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();

    private JpaAuditorContext() {
    }

    public static Optional<Integer> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static <T> T callWith(Integer auditorId, Supplier<T> action) {
        Integer previous = CURRENT.get();
        try {
            if (auditorId == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(auditorId);
            }
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
