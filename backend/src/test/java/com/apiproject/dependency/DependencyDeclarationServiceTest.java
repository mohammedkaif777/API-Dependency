package com.apiproject.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DependencyDeclarationServiceTest {

    private static final String ORG = "org-1";
    private static final String CONSUMER = "service-billing";
    private static final String ENDPOINT = "endpoint-1";
    private static final String ENDPOINT_OWNER = "service-orders";

    private final StubLookup lookup = new StubLookup();
    private final DependencyDeclarationService service = new DependencyDeclarationService(lookup);

    private static final class StubLookup implements DependencyLookup {
        final Map<String, String> consumerOrgs = new HashMap<>();
        final Map<String, DependencyLookup.ProviderEndpoint> endpoints = new HashMap<>();

        @Override
        public Optional<String> findConsumerServiceOrganization(String consumerServiceId) {
            return Optional.ofNullable(consumerOrgs.get(consumerServiceId));
        }

        @Override
        public Optional<DependencyLookup.ProviderEndpoint> findProviderEndpoint(String providerEndpointId) {
            return Optional.ofNullable(endpoints.get(providerEndpointId));
        }
    }

    private DependencyDeclarationCommand command(
            DetectionSource source, Confidence confidence, String consumer, String endpoint) {
        return new DependencyDeclarationCommand(ORG, consumer, endpoint, source, confidence);
    }

    @Test
    void declaresValidManualDependency() {
        lookup.consumerOrgs.put(CONSUMER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        Dependency dependency = service.declare(
                command(DetectionSource.MANUAL, Confidence.DECLARED, CONSUMER, ENDPOINT));

        assertEquals(ORG, dependency.getOrganizationId());
        assertEquals(CONSUMER, dependency.getConsumerServiceId());
        assertEquals(ENDPOINT, dependency.getProviderEndpointId());
        assertEquals(DetectionSource.MANUAL, dependency.getDetectionSource());
        assertEquals(Confidence.DECLARED, dependency.getConfidence());
    }

    @Test
    void rejectsNonManualDetectionSources() {
        lookup.consumerOrgs.put(CONSUMER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        for (DetectionSource source : DetectionSource.values()) {
            if (source == DetectionSource.MANUAL) {
                continue;
            }
            DependencyDeclarationException ex = assertThrows(
                    DependencyDeclarationException.class,
                    () -> service.declare(command(source, Confidence.DECLARED, CONSUMER, ENDPOINT)));
            assertEquals(DependencyDeclarationException.UNSUPPORTED_DETECTION_SOURCE, ex.getCode());
        }
    }

    @Test
    void rejectsNonDeclaredConfidenceValues() {
        lookup.consumerOrgs.put(CONSUMER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        for (Confidence confidence : Confidence.values()) {
            if (confidence == Confidence.DECLARED) {
                continue;
            }
            DependencyDeclarationException ex = assertThrows(
                    DependencyDeclarationException.class,
                    () -> service.declare(command(DetectionSource.MANUAL, confidence, CONSUMER, ENDPOINT)));
            assertEquals(DependencyDeclarationException.UNSUPPORTED_DETECTION_SOURCE, ex.getCode());
        }
    }

    @Test
    void rejectsNullDetectionSource() {
        lookup.consumerOrgs.put(CONSUMER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(command(null, null, CONSUMER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.UNSUPPORTED_DETECTION_SOURCE, ex.getCode());
    }

    @Test
    void rejectsSelfDependency() {
        lookup.consumerOrgs.put(ENDPOINT_OWNER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(
                        command(DetectionSource.MANUAL, Confidence.DECLARED, ENDPOINT_OWNER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.SELF_DEPENDENCY, ex.getCode());
    }

    @Test
    void rejectsConsumerFromAnotherOrganization() {
        lookup.consumerOrgs.put(CONSUMER, "org-other");
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(
                        command(DetectionSource.MANUAL, Confidence.DECLARED, CONSUMER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.ORGANIZATION_MISMATCH, ex.getCode());
    }

    @Test
    void rejectsEndpointFromAnotherOrganization() {
        lookup.consumerOrgs.put(CONSUMER, ORG);
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint("org-other", ENDPOINT_OWNER));

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(
                        command(DetectionSource.MANUAL, Confidence.DECLARED, CONSUMER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.ORGANIZATION_MISMATCH, ex.getCode());
    }

    @Test
    void rejectsUnknownConsumerService() {
        lookup.endpoints.put(ENDPOINT, new DependencyLookup.ProviderEndpoint(ORG, ENDPOINT_OWNER));

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(
                        command(DetectionSource.MANUAL, Confidence.DECLARED, CONSUMER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.UNKNOWN_CONSUMER_SERVICE, ex.getCode());
    }

    @Test
    void rejectsUnknownProviderEndpoint() {
        lookup.consumerOrgs.put(CONSUMER, ORG);

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> service.declare(
                        command(DetectionSource.MANUAL, Confidence.DECLARED, CONSUMER, ENDPOINT)));
        assertEquals(DependencyDeclarationException.UNKNOWN_PROVIDER_ENDPOINT, ex.getCode());
    }

    @Test
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> service.declare(null));
    }
}