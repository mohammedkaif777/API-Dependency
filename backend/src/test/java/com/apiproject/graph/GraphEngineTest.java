package com.apiproject.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apiproject.dependency.Confidence;
import com.apiproject.dependency.Dependency;
import com.apiproject.dependency.DetectionSource;
import com.apiproject.repository.Endpoint;
import com.apiproject.repository.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphEngineTest {

    private static final String ORG = "org-1";
    private static final String A = "svc-a";
    private static final String B = "svc-b";
    private static final String C = "svc-c";
    private static final String EP1 = "ep-1";
    private static final String EP2 = "ep-2";
    private static final String EP3 = "ep-3";

    private static final List<Service> SERVICES = List.of(
            new Service(A, ORG, "A"),
            new Service(B, ORG, "B"),
            new Service(C, ORG, "C"));

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(EP1, ORG, B),
            new Endpoint(EP2, ORG, B),
            new Endpoint(EP3, ORG, C));

    private static Dependency dependency(String consumer, String endpoint) {
        return new Dependency(ORG, consumer, endpoint, DetectionSource.MANUAL, Confidence.DECLARED);
    }

    private static GraphSnapshot snapshot(List<Dependency> dependencies) {
        return GraphSnapshot.of(ORG, SERVICES, ENDPOINTS, dependencies);
    }

    private static final List<Dependency> STANDARD = List.of(
            dependency(A, EP1),
            dependency(A, EP2),
            dependency(C, EP1));

    @Test
    void consumersOfServiceReturnsDirectConsumers() {
        assertEquals(List.of(A, C), GraphEngine.consumersOfService(snapshot(STANDARD), B));
        assertEquals(List.of(), GraphEngine.consumersOfService(snapshot(STANDARD), C),
                "no one consumes C's endpoint (EP3)");
    }

    @Test
    void serviceOwningNoEndpointsHasNoConsumers() {
        assertEquals(List.of(), GraphEngine.consumersOfService(snapshot(STANDARD), A));
    }

    @Test
    void callsOfServiceReturnsProviderPairsSorted() {
        assertEquals(
                List.of(new ProviderCall(EP1, B), new ProviderCall(EP2, B)),
                GraphEngine.callsOfService(snapshot(STANDARD), A));
        assertEquals(
                List.of(new ProviderCall(EP1, B)),
                GraphEngine.callsOfService(snapshot(STANDARD), C));
        assertEquals(List.of(), GraphEngine.callsOfService(snapshot(STANDARD), B));
    }

    @Test
    void consumersOfEndpointReturnsDirectConsumers() {
        assertEquals(List.of(A, C), GraphEngine.consumersOfEndpoint(snapshot(STANDARD), EP1));
        assertEquals(List.of(A), GraphEngine.consumersOfEndpoint(snapshot(STANDARD), EP2));
        assertEquals(List.of(), GraphEngine.consumersOfEndpoint(snapshot(STANDARD), EP3));
    }

    @Test
    void removedDependenciesAreInvisible() {
        Dependency removed = dependency(C, EP3).removed();
        GraphSnapshot withRemoved = snapshot(List.of(removed));

        assertEquals(List.of(), GraphEngine.consumersOfEndpoint(withRemoved, EP3));
        assertEquals(List.of(), GraphEngine.callsOfService(withRemoved, C));
    }

    @Test
    void resultsAreIndependentOfInputOrder() {
        List<Dependency> shuffled = new ArrayList<>(STANDARD);
        Collections.reverse(shuffled);

        assertEquals(
                GraphEngine.consumersOfService(snapshot(STANDARD), B),
                GraphEngine.consumersOfService(snapshot(shuffled), B));
        assertEquals(
                GraphEngine.consumersOfEndpoint(snapshot(STANDARD), EP1),
                GraphEngine.consumersOfEndpoint(snapshot(shuffled), EP1));
        assertEquals(
                GraphEngine.callsOfService(snapshot(STANDARD), A),
                GraphEngine.callsOfService(snapshot(shuffled), A));
    }

    @Test
    void unknownServiceIdIsRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GraphEngine.consumersOfService(snapshot(STANDARD), "svc-ghost"));
        assertEquals("unknown service in snapshot: svc-ghost", ex.getMessage());
    }

    @Test
    void unknownEndpointIdIsRejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GraphEngine.consumersOfEndpoint(snapshot(STANDARD), "ep-ghost"));
        assertEquals("unknown endpoint in snapshot: ep-ghost", ex.getMessage());
    }
}