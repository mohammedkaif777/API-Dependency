package com.apiproject.repository;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * Tenant root repository (scoped-repository.md rule 5): id-based read only, backed by the shared
 * store under org == its own id. This is the documented exception from {@link ScopedRepository}.
 */
public final class OrganizationRepository {

    private final InMemoryStore store;

    public OrganizationRepository(InMemoryStore store) {
        this.store = requireNonNull(store, "store");
    }

    public void register(Organization organization) {
        requireNonNull(organization, "organization");
        store.put(Organization.class, organization.id(), organization.id(), organization);
    }

    public Optional<Organization> readCurrent() {
        String current = TenantContext.require();
        return store.get(Organization.class, current, current).map(Organization.class::cast);
    }
}