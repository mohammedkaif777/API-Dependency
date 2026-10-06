package com.apiproject.repository;

/** Org-scoped repository for {@link Endpoint} rows. */
public final class EndpointRepository extends ScopedRepository<Endpoint, String> {

    public EndpointRepository(InMemoryStore store) {
        super(store, Endpoint.class);
    }

    @Override
    protected String idOf(Endpoint entity) {
        return entity.id();
    }

    @Override
    protected String organizationOf(Endpoint entity) {
        return entity.organizationId();
    }
}