package com.apiproject.dependency;

import java.util.Objects;

/**
 * Pure, deterministic validator + builder for manual dependency declarations (FR-005 amended per
 * DECISION-001, INV-DEP-02…07). No IO, no shared mutable state (NFR-008). A repository-backed
 * {@link DependencyLookup} implementation, persistence, duplicate uniqueness (INV-DEP-06) and
 * soft-remove land in P4-02; this class only defines the checkable-in-isolation rules.
 */
public final class DependencyDeclarationService {

    private final DependencyLookup lookup;

    public DependencyDeclarationService(DependencyLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public Dependency declare(DependencyDeclarationCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.detectionSource() != DetectionSource.MANUAL
                || command.confidence() != Confidence.DECLARED) {
            throw failure(
                    DependencyDeclarationException.UNSUPPORTED_DETECTION_SOURCE,
                    "MVP supports only detectionSource=MANUAL and confidence=DECLARED (DECISION-001): "
                            + "detectionSource=" + command.detectionSource()
                            + ", confidence=" + command.confidence());
        }
        String consumerOrg = lookup.findConsumerServiceOrganization(command.consumerServiceId())
                .orElseThrow(() -> failure(
                        DependencyDeclarationException.UNKNOWN_CONSUMER_SERVICE,
                        "unknown consumer service: " + command.consumerServiceId()));
        DependencyLookup.ProviderEndpoint provider = lookup.findProviderEndpoint(command.providerEndpointId())
                .orElseThrow(() -> failure(
                        DependencyDeclarationException.UNKNOWN_PROVIDER_ENDPOINT,
                        "unknown provider endpoint: " + command.providerEndpointId()));
        if (!consumerOrg.equals(command.organizationId()) || !provider.organizationId().equals(command.organizationId())) {
            throw failure(
                    DependencyDeclarationException.ORGANIZATION_MISMATCH,
                    "consumer service and provider endpoint must belong to organization "
                            + command.organizationId() + " (INV-DEP-07, X-INV-01): consumer org="
                            + consumerOrg + ", endpoint org=" + provider.organizationId());
        }
        if (provider.ownerServiceId().equals(command.consumerServiceId())) {
            throw failure(
                    DependencyDeclarationException.SELF_DEPENDENCY,
                    "a service cannot declare a dependency on its own endpoint (INV-DEP-04): "
                            + command.consumerServiceId());
        }
        return new Dependency(
                command.organizationId(),
                command.consumerServiceId(),
                command.providerEndpointId(),
                DetectionSource.MANUAL,
                Confidence.DECLARED);
    }

    private static DependencyDeclarationException failure(String code, String message) {
        return new DependencyDeclarationException(code, message);
    }
}