package com.apiproject.dependency;

/**
 * Thrown when a manual dependency declaration violates an invariant. Codes mirror the API-layer
 * contract (sequence diagrams show {@code 400 { code: ... }}); HTTP mapping is Phase 8's job.
 *
 * <p>Codes:</p>
 * <ul>
 *   <li>{@link #UNSUPPORTED_DETECTION_SOURCE} — detectionSource != MANUAL or confidence != DECLARED
 *       (DECISION-001/INV-DEP-05).</li>
 *   <li>{@link #UNKNOWN_CONSUMER_SERVICE} — the consumer service has no known organization
 *       (dangling-reference surface; storage-backed lookup lands in P4-02).</li>
 *   <li>{@link #UNKNOWN_PROVIDER_ENDPOINT} — the provider endpoint is unknown.</li>
 *   <li>{@link #ORGANIZATION_MISMATCH} — consumer service or provider endpoint belongs to a different
 *       organization than the declaration (INV-DEP-02/03/07).</li>
 *   <li>{@link #SELF_DEPENDENCY} — consumer is the service that owns the provider endpoint
 *       (INV-DEP-04).</li>
 *   <li>{@link #DUPLICATE_DEPENDENCY} — a non-REMOVED dependency with the same
 *       (consumer, endpoint) already exists (INV-DEP-06 default).</li>
 * </ul>
 */
public final class DependencyDeclarationException extends RuntimeException {

    public static final String UNSUPPORTED_DETECTION_SOURCE = "UNSUPPORTED_DETECTION_SOURCE";
    public static final String UNKNOWN_CONSUMER_SERVICE = "UNKNOWN_CONSUMER_SERVICE";
    public static final String UNKNOWN_PROVIDER_ENDPOINT = "UNKNOWN_PROVIDER_ENDPOINT";
    public static final String ORGANIZATION_MISMATCH = "ORGANIZATION_MISMATCH";
    public static final String SELF_DEPENDENCY = "SELF_DEPENDENCY";
    public static final String DUPLICATE_DEPENDENCY = "DUPLICATE_DEPENDENCY";

    private final String code;

    public DependencyDeclarationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "DependencyDeclarationException(" + code + "): " + getMessage();
    }
}