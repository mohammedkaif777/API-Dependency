package com.apiproject.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apiproject.dependency.Confidence;
import com.apiproject.dependency.Dependency;
import com.apiproject.dependency.DependencyDeclarationException;
import com.apiproject.dependency.DetectionSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DependencyRepositoryTest {

    private static final String ORG_A = "org-a";
    private static final String ORG_B = "org-b";
    private static final String CONSUMER = "svc-consumer";
    private static final String ENDPOINT = "ep-provider";

    private final InMemoryStore store = new InMemoryStore();
    private final DependencyRepository repository = new DependencyRepository(store);

    @BeforeEach
    void setTenant() {
        TenantContext.set(ORG_A);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Dependency draft() {
        return new Dependency(ORG_A, CONSUMER, ENDPOINT, DetectionSource.MANUAL, Confidence.DECLARED);
    }

    @Test
    void createAssignsIdAndPersists() {
        String id = repository.create(draft());

        assertNotNull(id);
        assertTrue(repository.findById(id).isPresent());
        assertEquals(1, repository.findAllActive().size());
        assertEquals(Dependency.Status.ACTIVE, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void duplicateIsRejectedButRedeliverableAfterSoftRemove() {
        String firstId = repository.create(draft());

        DependencyDeclarationException ex =
                assertThrows(DependencyDeclarationException.class, () -> repository.create(draft()));
        assertEquals(DependencyDeclarationException.DUPLICATE_DEPENDENCY, ex.getCode());

        assertTrue(repository.softRemove(firstId));
        assertEquals(Dependency.Status.REMOVED,
                repository.findById(firstId).orElseThrow().getStatus(), "row stays persisted but REMOVED");
        assertTrue(repository.findAllActive().isEmpty(), "REMOVED rows are excluded from active reads");

        String secondId = repository.create(draft());
        assertNotNull(secondId);
        assertFalse(secondId.equals(firstId), "re-declaration gets a fresh id");
        assertEquals(1, repository.findAllActive().size());
    }

    @Test
    void rowsAreInvisibleAcrossTenants() {
        String id = repository.create(draft());

        TenantContext.set(ORG_B);
        assertTrue(repository.findAllActive().isEmpty());
        assertTrue(repository.findById(id).isEmpty());
        assertFalse(repository.softRemove(id), "foreign row cannot be soft-removed");
        repository.deleteById(id);
        TenantContext.set(ORG_A);
        assertTrue(repository.findById(id).isPresent(), "foreign delete must not touch the row");
    }

    @Test
    void crossOrganizationWriteIsRejected() {
        Dependency foreign = new Dependency(ORG_B, CONSUMER, ENDPOINT,
                DetectionSource.MANUAL, Confidence.DECLARED);

        TenantMismatchException ex =
                assertThrows(TenantMismatchException.class, () -> repository.save(foreign));
        assertEquals(ORG_A, ex.getExpectedOrganizationId());
        assertEquals(ORG_B, ex.getActualOrganizationId());
    }

    @Test
    void createRejectsForeignOrganizationDraft() {
        Dependency foreign = new Dependency(ORG_B, CONSUMER, ENDPOINT,
                DetectionSource.MANUAL, Confidence.DECLARED);

        TenantMismatchException ex =
                assertThrows(TenantMismatchException.class, () -> repository.create(foreign));
        assertEquals(ORG_B, ex.getActualOrganizationId());
    }
}