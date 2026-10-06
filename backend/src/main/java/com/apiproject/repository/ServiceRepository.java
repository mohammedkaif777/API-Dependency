package com.apiproject.repository;

/** Org-scoped repository for {@link Service} rows. */
public final class ServiceRepository extends ScopedRepository<Service, String> {

    public ServiceRepository(InMemoryStore store) {
        super(store, Service.class);
    }

    @Override
    protected String idOf(Service entity) {
        return entity.id();
    }

    @Override
    protected String organizationOf(Service entity) {
        return entity.organizationId();
    }
}