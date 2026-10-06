package com.apiproject.repository;

import com.apiproject.dependency.DependencyLookup;
import java.util.Optional;

/**
 * {@link DependencyLookup} backed by the org-scoped repositories. Because the underlying reads are
 * scoped, a referent that belongs to another organization is invisible (returns empty) and the
 * declaration is rejected as unknown — this is the structural enforcement of INV-DEP-07/X-INV-01
 * described in scoped-repository.md.
 */
public final class StoredDependencyLookup implements DependencyLookup {

    private final ServiceRepository services;
    private final EndpointRepository endpoints;

    public StoredDependencyLookup(ServiceRepository services, EndpointRepository endpoints) {
        this.services = services;
        this.endpoints = endpoints;
    }

    @Override
    public Optional<String> findConsumerServiceOrganization(String consumerServiceId) {
        return services.findById(consumerServiceId).map(Service::organizationId);
    }

    @Override
    public Optional<ProviderEndpoint> findProviderEndpoint(String providerEndpointId) {
        return endpoints.findById(providerEndpointId)
                .map(endpoint -> new ProviderEndpoint(endpoint.organizationId(), endpoint.serviceId()));
    }
}