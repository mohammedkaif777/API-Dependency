package com.apiproject.repository;

/**
 * Thrown when a repository write targets an entity whose organization differs from the current
 * tenant (NFR-009, scoped-repository.md rule 2, X-INV-01). Cross-tenant writes are structurally
 * impossible: two buckets in the store, the wrong one never touched.
 */
public final class TenantMismatchException extends RuntimeException {

    private final String expectedOrganizationId;
    private final String actualOrganizationId;

    public TenantMismatchException(String expectedOrganizationId, String actualOrganizationId) {
        super("tenant mismatch: current organization '" + expectedOrganizationId
                + "' but entity belongs to '" + actualOrganizationId + "'");
        this.expectedOrganizationId = expectedOrganizationId;
        this.actualOrganizationId = actualOrganizationId;
    }

    public String getExpectedOrganizationId() {
        return expectedOrganizationId;
    }

    public String getActualOrganizationId() {
        return actualOrganizationId;
    }
}