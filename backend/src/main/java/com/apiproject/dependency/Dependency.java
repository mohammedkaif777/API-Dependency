package com.apiproject.dependency;

/**
 * Immutable model of a manual dependency declaration: "Service {@code consumerServiceId} calls
 * Endpoint {@code providerEndpointId} in organization {@code organizationId}" (INV-DEP-01…07).
 *
 * <p>The {@code id} is assigned by the persistence layer (P4-02) after the declaration is validated;
 * a freshly declared Dependency has a null id and {@link Status#ACTIVE}. Soft lifecycle per X-INV-02:
 * removal is {@link Status#REMOVED}, never a hard delete. Tenant scoping follows DECISION-002/X-INV-01:
 * the Dependency carries {@code organizationId} and every referent must belong to the same organization
 * (INV-DEP-07).
 */
public final class Dependency {

    public enum Status {
        ACTIVE,
        REMOVED
    }

    private final String id;
    private final String organizationId;
    private final String consumerServiceId;
    private final String providerEndpointId;
    private final DetectionSource detectionSource;
    private final Confidence confidence;
    private final Status status;

    public Dependency(
            String organizationId,
            String consumerServiceId,
            String providerEndpointId,
            DetectionSource detectionSource,
            Confidence confidence) {
        this(null, organizationId, consumerServiceId, providerEndpointId, detectionSource, confidence, Status.ACTIVE);
    }

    public Dependency(
            String id,
            String organizationId,
            String consumerServiceId,
            String providerEndpointId,
            DetectionSource detectionSource,
            Confidence confidence,
            Status status) {
        this.id = id;
        this.organizationId = organizationId;
        this.consumerServiceId = consumerServiceId;
        this.providerEndpointId = providerEndpointId;
        this.detectionSource = detectionSource;
        this.confidence = confidence;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getConsumerServiceId() {
        return consumerServiceId;
    }

    public String getProviderEndpointId() {
        return providerEndpointId;
    }

    public DetectionSource getDetectionSource() {
        return detectionSource;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public Status getStatus() {
        return status;
    }

    /** Copy with {@link Status#REMOVED} (soft-remove, X-INV-02); id and tenant preserved. */
    public Dependency removed() {
        return new Dependency(id, organizationId, consumerServiceId, providerEndpointId,
                detectionSource, confidence, Status.REMOVED);
    }

    @Override
    public String toString() {
        return "Dependency{id='" + id + "', consumerServiceId='" + consumerServiceId
                + "', providerEndpointId='" + providerEndpointId + "', organizationId='" + organizationId
                + "', detectionSource=" + detectionSource + ", confidence=" + confidence
                + ", status=" + status + "}";
    }
}