package com.apiproject.dependency;

/**
 * Immutable input to a manual dependency declaration (FR-005, amended per DECISION-001).
 *
 * @param organizationId the tenant this declaration belongs to (DECISION-002)
 * @param consumerServiceId the calling service
 * @param providerEndpointId the endpoint being called
 * @param detectionSource must be {@link DetectionSource#MANUAL} in MVP
 * @param confidence must be {@link Confidence#DECLARED} in MVP
 */
public record DependencyDeclarationCommand(
        String organizationId,
        String consumerServiceId,
        String providerEndpointId,
        DetectionSource detectionSource,
        Confidence confidence) {}