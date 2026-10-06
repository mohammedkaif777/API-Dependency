package com.apiproject.dependency;

import java.util.Optional;

/**
 * Organization-scoped facts the dependency validator must consult. Implemented against the real
 * repository in P4-02; tests provide an in-memory stub. Kept minimal so the declaration logic stays
 * pure and deterministic (NFR-008): the port answers "which organization owns X", nothing more.
 */
public interface DependencyLookup {

    /** Organization that owns the consumer service, if it exists (INV-DEP-02). */
    Optional<String> findConsumerServiceOrganization(String consumerServiceId);

    /** Organization that owns the provider endpoint plus the service it belongs to (INV-DEP-03/04/07). */
    Optional<ProviderEndpoint> findProviderEndpoint(String providerEndpointId);

    record ProviderEndpoint(String organizationId, String ownerServiceId) {}
}