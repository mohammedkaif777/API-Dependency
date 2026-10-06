package com.apiproject.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.dependency.Confidence;
import com.apiproject.dependency.Dependency;
import com.apiproject.dependency.DependencyDeclarationCommand;
import com.apiproject.dependency.DependencyDeclarationException;
import com.apiproject.dependency.DependencyDeclarationService;
import com.apiproject.dependency.DetectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end wiring: seed referents → declare → persist → read back, all org-scoped. */
class DependencyPersistenceTest {

    private static final String ORG = "org-1";
    private static final String OTHER_ORG = "org-2";
    private static final String CONSUMER = "svc-billing";
    private static final String PROVIDER = "svc-orders";
    private static final String ENDPOINT = "ep-orders-123";

    private final InMemoryStore store = new InMemoryStore();
    private final OrganizationRepository organizations = new OrganizationRepository(store);
    private final ServiceRepository services = new ServiceRepository(store);
    private final EndpointRepository endpoints = new EndpointRepository(store);
    private final DependencyRepository dependencies = new DependencyRepository(store);
    private final StoredDependencyLookup lookup = new StoredDependencyLookup(services, endpoints);
    private final DependencyDeclarationService declarationService = new DependencyDeclarationService(lookup);

    @BeforeEach
    void seedCurrentOrg() {
        TenantContext.set(ORG);
        organizations.register(new Organization(ORG, "Bootstrap Org"));
        services.save(new Service(CONSUMER, ORG, "Billing"));
        services.save(new Service(PROVIDER, ORG, "Orders"));
        endpoints.save(new Endpoint(ENDPOINT, ORG, PROVIDER));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private DependencyDeclarationCommand command(String consumer, String endpoint) {
        return new DependencyDeclarationCommand(ORG, consumer, endpoint,
                DetectionSource.MANUAL, Confidence.DECLARED);
    }

    @Test
    void declarePersistsAndReadsBack() {
        Dependency declared = declarationService.declare(command(CONSUMER, ENDPOINT));
        assertEquals(DetectionSource.MANUAL, declared.getDetectionSource());

        String id = dependencies.create(declared);
        assertNotNull(id);

        Dependency persisted = dependencies.findById(id).orElseThrow();
        assertEquals(CONSUMER, persisted.getConsumerServiceId());
        assertEquals(ENDPOINT, persisted.getProviderEndpointId());
        assertEquals(Dependency.Status.ACTIVE, persisted.getStatus());
        assertEquals(ORG, persisted.getOrganizationId());
        assertEquals(1, dependencies.findAllActive().size());
    }

    @Test
    void duplicateDeclarationRejectedAtPersistence() {
        dependencies.create(declarationService.declare(command(CONSUMER, ENDPOINT)));

        DependencyDeclarationException ex = assertThrows(DependencyDeclarationException.class,
                () -> dependencies.create(declarationService.declare(command(CONSUMER, ENDPOINT))));
        assertEquals(DependencyDeclarationException.DUPLICATE_DEPENDENCY, ex.getCode());
    }

    @Test
    void danglingReferenceRejectedEndToEnd() {
        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> declarationService.declare(command(CONSUMER, "ep-does-not-exist")));
        assertEquals(DependencyDeclarationException.UNKNOWN_PROVIDER_ENDPOINT, ex.getCode());
    }

    @Test
    void foreignOrganizationReferentIsInvisible() {
        TenantContext.set(OTHER_ORG);
        endpoints.save(new Endpoint("ep-other", OTHER_ORG, "svc-other"));
        TenantContext.set(ORG);

        DependencyDeclarationException ex = assertThrows(
                DependencyDeclarationException.class,
                () -> declarationService.declare(command(CONSUMER, "ep-other")));
        assertEquals(DependencyDeclarationException.UNKNOWN_PROVIDER_ENDPOINT, ex.getCode(),
                "a foreign-org endpoint must be invisible from ORG, not mismatch — INV-DEP-07 defense");
    }

    @Test
    void dependencyRowsAreIsolatedByOrganization() {
        dependencies.create(declarationService.declare(command(CONSUMER, ENDPOINT)));

        TenantContext.set(OTHER_ORG);
        assertTrue(dependencies.findAllActive().isEmpty());
    }

    @Test
    void crossOrganizationServiceWriteIsRejected() {
        TenantMismatchException ex = assertThrows(TenantMismatchException.class,
                () -> services.save(new Service("svc-foreign", OTHER_ORG, "Foreign")));
        assertEquals(ORG, ex.getExpectedOrganizationId());
    }

    @Test
    void organizationRepositoryReadsCurrentTenantOnly() {
        TenantContext.set(OTHER_ORG);
        assertTrue(organizations.readCurrent().isEmpty());
        TenantContext.set(ORG);
        assertEquals(ORG, organizations.readCurrent().orElseThrow().id());
    }
}