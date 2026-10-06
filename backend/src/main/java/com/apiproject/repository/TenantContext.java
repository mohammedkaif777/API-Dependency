package com.apiproject.repository;

import java.util.Optional;

/**
 * Current-organization holder for repository scoping (NFR-009, DECISION-002, scoped-repository.md).
 * Set at the request boundary (in Phase 4: the test boundary); every {@link ScopedRepository} call
 * resolves the org from here so the filter path runs everywhere from day one.
 *
 * <p>Before auth exists (Phase 15) callers set the single default organization; after that the same
 * code path is used, fed by the request boundary instead of a constant.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String organizationId) {
        CURRENT.set(organizationId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<String> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Current org or an {@link IllegalStateException} if none is set. */
    public static String require() {
        String value = CURRENT.get();
        if (value == null) {
            throw new IllegalStateException("no tenant set in TenantContext");
        }
        return value;
    }
}