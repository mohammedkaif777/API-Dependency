package com.apiproject.graph;

import com.apiproject.dependency.Dependency;
import com.apiproject.repository.DependencyRepository;
import com.apiproject.repository.Endpoint;
import com.apiproject.repository.EndpointRepository;
import com.apiproject.repository.Service;
import com.apiproject.repository.ServiceRepository;
import com.apiproject.repository.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, org-scoped snapshot of everything the Graph Engine needs to answer a query — plain
 * adjacency (Dependency rows) plus the referents they point at (ADR-001: no graph DB).
 *
 * <p>Two constructions, same invariants:
 * <ul>
 *   <li>{@link #from} — loads the current tenant's ACTIVE rows from the P4-02 scoped repositories
 *       (NFR-009: a foreign row is invisible and never enters the snapshot),</li>
 *   <li>{@link #of} — builds from caller-supplied collections (tests) with the same consistency
 *       checks.</li>
 * </ul>
 *
 * <p>Invariants enforced at construction (defense for X-INV-01/INV-DEP-07): every service, endpoint
 * and dependency belongs to the snapshot's organization; every dependency's consumer service and
 * provider endpoint exist in the snapshot; every endpoint's owner service exists. A violation is a
 * programming error → {@link IllegalArgumentException}.
 */
public final class GraphSnapshot {

    private final String organizationId;
    private final Map<String, Service> services;
    private final Map<String, Endpoint> endpoints;
    private final List<Dependency> dependencies;

    private GraphSnapshot(
            String organizationId,
            Map<String, Service> services,
            Map<String, Endpoint> endpoints,
            List<Dependency> dependencies) {
        this.organizationId = organizationId;
        this.services = Map.copyOf(services);
        this.endpoints = Map.copyOf(endpoints);
        this.dependencies = List.copyOf(dependencies);
    }

    public String getOrganizationId() {
        return organizationId;
    }

    /** Unmodifiable id → service map, all belonging to {@link #getOrganizationId()}. */
    public Map<String, Service> services() {
        return services;
    }

    /** Unmodifiable id → endpoint map, all belonging to {@link #getOrganizationId()}. */
    public Map<String, Endpoint> endpoints() {
        return endpoints;
    }

    /** Unmodifiable ACTIVE dependency rows, all org-consistent. */
    public List<Dependency> dependencies() {
        return dependencies;
    }

    /** Loads the current tenant's ACTIVE rows through the org-scoped repositories. */
    public static GraphSnapshot from(
            ServiceRepository services,
            EndpointRepository endpoints,
            DependencyRepository dependencies) {
        String org = TenantContext.require();
        return of(org, services.findAll(), endpoints.findAll(), dependencies.findAllActive());
    }

    /** Builds a snapshot from caller-supplied collections, enforcing the construction invariants. */
    public static GraphSnapshot of(
            String organizationId,
            List<Service> services,
            List<Endpoint> endpoints,
            List<Dependency> dependencies) {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(dependencies, "dependencies");

        Map<String, Service> serviceMap = new LinkedHashMap<>();
        for (Service service : services) {
            requireOwn(organizationId, service.organizationId(), "service " + service.id());
            serviceMap.put(service.id(), service);
        }
        Map<String, Endpoint> endpointMap = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            requireOwn(organizationId, endpoint.organizationId(), "endpoint " + endpoint.id());
            if (!serviceMap.containsKey(endpoint.serviceId())) {
                throw new IllegalArgumentException("inconsistent snapshot: endpoint " + endpoint.id()
                        + " references unknown owner service " + endpoint.serviceId());
            }
            endpointMap.put(endpoint.id(), endpoint);
        }
        List<Dependency> active = new java.util.ArrayList<>();
        for (Dependency dependency : dependencies) {
            if (dependency.getStatus() != Dependency.Status.ACTIVE) {
                continue;
            }
            requireOwn(organizationId, dependency.getOrganizationId(),
                    "dependency " + dependency);
            if (!serviceMap.containsKey(dependency.getConsumerServiceId())) {
                throw new IllegalArgumentException("inconsistent snapshot: dependency references unknown"
                        + " consumer service " + dependency.getConsumerServiceId());
            }
            if (!endpointMap.containsKey(dependency.getProviderEndpointId())) {
                throw new IllegalArgumentException("inconsistent snapshot: dependency references unknown"
                        + " provider endpoint " + dependency.getProviderEndpointId());
            }
            active.add(dependency);
        }
        return new GraphSnapshot(organizationId, serviceMap, endpointMap, active);
    }

    private static void requireOwn(String organizationId, String actual, String what) {
        if (!organizationId.equals(actual)) {
            throw new IllegalArgumentException("inconsistent snapshot: " + what + " belongs to organization "
                    + actual + " but snapshot is for " + organizationId + " (X-INV-01)");
        }
    }

    @Override
    public String toString() {
        return "GraphSnapshot{" + organizationId + ": " + services.size() + " services, "
                + endpoints.size() + " endpoints, " + dependencies.size() + " dependencies}";
    }
}