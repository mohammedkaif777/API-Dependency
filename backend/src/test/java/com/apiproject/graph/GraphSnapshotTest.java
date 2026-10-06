package com.apiproject.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.dependency.Confidence;
import com.apiproject.dependency.Dependency;
import com.apiproject.dependency.DetectionSource;
import com.apiproject.repository.DependencyRepository;
import com.apiproject.repository.Endpoint;
import com.apiproject.repository.EndpointRepository;
import com.apiproject.repository.InMemoryStore;
import com.apiproject.repository.Organization;
import com.apiproject.repository.OrganizationRepository;
import com.apiproject.repository.Service;
import com.apiproject.repository.ServiceRepository;
import com.apiproject.repository.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphSnapshotTest {

    private static final String ORG = "org-1";
    private static final String OTHER = "org-2";
    private static final String CONSUMER = "svc-billing";
    private static final String PROVIDER = "svc-orders";
    private static final String ENDPOINT = "ep-orders-123";

    private final InMemoryStore store = new InMemoryStore();
    private final OrganizationRepository organizations = new OrganizationRepository(store);
    private final ServiceRepository services = new ServiceRepository(store);
    private final EndpointRepository endpoints = new EndpointRepository(store);
    private final DependencyRepository dependencies = new DependencyRepository(store);

    @BeforeEach
    void seed() {
        TenantContext.set(ORG);
        organizations.register(new Organization(ORG, "Org"));
        services.save(new Service(CONSUMER, ORG, "Billing"));
        services.save(new Service(PROVIDER, ORG, "Orders"));
        endpoints.save(new Endpoint(ENDPOINT, ORG, PROVIDER));
        dependencies.create(new Dependency(ORG, CONSUMER, ENDPOINT,
                DetectionSource.MANUAL, Confidence.DECLARED));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void loadsCurrentOrgActiveRows() {
        GraphSnapshot snapshot = GraphSnapshot.from(services, endpoints, dependencies);

        assertEquals(ORG, snapshot.getOrganizationId());
        assertEquals(2, snapshot.services().size());
        assertEquals(1, snapshot.endpoints().size());
        assertEquals(1, snapshot.dependencies().size());
        assertEquals(List.of(CONSUMER),
                GraphEngine.consumersOfEndpoint(snapshot, ENDPOINT));
    }

    @Test
    void foreignOrganizationRowsAreInvisible() {
        TenantContext.set(OTHER);
        services.save(new Service("svc-other", OTHER, "Other"));
        endpoints.save(new Endpoint("ep-other", OTHER, "svc-other"));
        dependencies.create(new Dependency(OTHER, "svc-other", "ep-other",
                DetectionSource.MANUAL, Confidence.DECLARED));
        TenantContext.set(ORG);

        GraphSnapshot snapshot = GraphSnapshot.from(services, endpoints, dependencies);
        assertEquals(2, snapshot.services().size(),
                "other-org service must not leak into this snapshot (NFR-009)");
        assertEquals(1, snapshot.endpoints().size());
        assertEquals(1, snapshot.dependencies().size());
    }

    @Test
    void rejectsCrossOrganizationRowOnManualConstruction() {
        List<Service> foreign = List.of(new Service("svc-foreign", OTHER, "Foreign"));
        assertThrows(IllegalArgumentException.class,
                () -> GraphSnapshot.of(ORG, foreign, List.of(), List.of()));
    }

    @Test
    void rejectsDependencyReferencingMissingReferent() {
        Dependency dangling = new Dependency(ORG, "svc-ghost", ENDPOINT,
                DetectionSource.MANUAL, Confidence.DECLARED);
        assertThrows(IllegalArgumentException.class,
                () -> GraphSnapshot.of(ORG,
                        List.of(new Service(CONSUMER, ORG, "Billing"), new Service(PROVIDER, ORG, "Orders")),
                        List.of(new Endpoint(ENDPOINT, ORG, PROVIDER)),
                        List.of(dangling)));
    }

    @Test
    void rejectsEndpointReferencingMissingOwnerService() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GraphSnapshot.of(ORG,
                        List.of(new Service(CONSUMER, ORG, "Billing")),
                        List.of(new Endpoint("ep-x", ORG, "svc-ghost")),
                        List.of()));
        assertTrue(ex.getMessage().contains("owner service"), ex.getMessage());
    }
}