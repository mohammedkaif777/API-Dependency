package com.apiproject.graph;

import com.apiproject.dependency.Dependency;
import com.apiproject.repository.Endpoint;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, stateless direct-dependency queries over a {@link GraphSnapshot} (FR-006, ADR-001). This
 * class deliberately has NO access to TenantContext, repositories, or any other IO — snapshot in,
 * answers out (NFR-008). The snapshot is loaded scoped by the caller; every result is returned
 * sorted (lexicographic) so identical input always yields identical output.
 *
 * <p>P5-01 scope: DIRECT relationships only — no transitive hops (P5-02), no cycle detection
 * (P5-03), no blast radius (P5-04).
 */
public final class GraphEngine {

    private GraphEngine() {}

    /**
     * Services that consume at least one endpoint owned by {@code serviceId}, sorted.
     * @throws IllegalArgumentException if {@code serviceId} is not in the snapshot
     */
    public static List<String> consumersOfService(GraphSnapshot snapshot, String serviceId) {
        requireService(snapshot, serviceId);
        return snapshot.dependencies().stream()
                .filter(dependency -> owns(snapshot, dependency.getProviderEndpointId(), serviceId))
                .map(Dependency::getConsumerServiceId)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Every endpoint {@code serviceId} consumes, as (endpoint, owning service) pairs, sorted by
     * endpoint id.
     * @throws IllegalArgumentException if {@code serviceId} is not in the snapshot
     */
    public static List<ProviderCall> callsOfService(GraphSnapshot snapshot, String serviceId) {
        requireService(snapshot, serviceId);
        return snapshot.dependencies().stream()
                .filter(dependency -> dependency.getConsumerServiceId().equals(serviceId))
                .map(dependency -> new ProviderCall(
                        dependency.getProviderEndpointId(),
                        ownerOf(snapshot, dependency.getProviderEndpointId())))
                .distinct()
                .sorted(Comparator.comparing(ProviderCall::providerEndpointId))
                .toList();
    }

    /**
     * Services that consume {@code endpointId} directly, sorted.
     * @throws IllegalArgumentException if {@code endpointId} is not in the snapshot
     */
    public static List<String> consumersOfEndpoint(GraphSnapshot snapshot, String endpointId) {
        requireEndpoint(snapshot, endpointId);
        return snapshot.dependencies().stream()
                .filter(dependency -> dependency.getProviderEndpointId().equals(endpointId))
                .map(Dependency::getConsumerServiceId)
                .distinct()
                .sorted()
                .toList();
    }

    private static void requireService(GraphSnapshot snapshot, String serviceId) {
        if (!snapshot.services().containsKey(serviceId)) {
            throw new IllegalArgumentException("unknown service in snapshot: " + serviceId);
        }
    }

    private static void requireEndpoint(GraphSnapshot snapshot, String endpointId) {
        if (!snapshot.endpoints().containsKey(endpointId)) {
            throw new IllegalArgumentException("unknown endpoint in snapshot: " + endpointId);
        }
    }

    private static boolean owns(GraphSnapshot snapshot, String endpointId, String serviceId) {
        return ownerOf(snapshot, endpointId).equals(serviceId);
    }

    private static String ownerOf(GraphSnapshot snapshot, String endpointId) {
        Endpoint endpoint = snapshot.endpoints().get(endpointId);
        if (endpoint == null) {
            throw new IllegalArgumentException("dependency references endpoint not in snapshot: " + endpointId);
        }
        return endpoint.serviceId();
    }
}