package com.apiproject.repository;

import java.util.List;
import java.util.Optional;

/**
 * Abstract org-scoped repository (scoped-repository.md): the ONLY base any concrete repository may
 * extend (rule 1). Enforces NFR-009 structurally, in one place:
 * <ul>
 *   <li>reads ({@link #findById}, {@link #findAll}, {@link #existsById}) resolve against the current
 *       tenant's bucket only (rule 3, 5),</li>
 *   <li>{@link #save} asserts {@code entity.organization_id == current org} before writing —
 *       mismatch throws {@link TenantMismatchException} (rule 2),</li>
 *   <li>{@link #deleteById} targets only the current tenant's bucket — a foreign row is never found
 *       and no-ops (rule 3 for deletes).</li>
 * </ul>
 *
 * <p>In Phase 4 this is backed by {@link InMemoryStore}; the identical guarantees are re-verified at
 * P8-01 against JPA/Postgres per scoped-repository.md.
 */
public abstract class ScopedRepository<T, ID> {

    private final InMemoryStore store;
    private final Class<T> type;

    protected ScopedRepository(InMemoryStore store, Class<T> type) {
        this.store = store;
        this.type = type;
    }

    protected final String currentOrganization() {
        return TenantContext.require();
    }

    protected abstract ID idOf(T entity);

    protected abstract String organizationOf(T entity);

    @SuppressWarnings("unchecked")
    public Optional<T> findById(ID id) {
        return store.get(type, currentOrganization(), id).map(value -> (T) value);
    }

    @SuppressWarnings("unchecked")
    public List<T> findAll() {
        return store.values(type, currentOrganization()).stream().map(value -> (T) value).toList();
    }

    public boolean existsById(ID id) {
        return store.contains(type, currentOrganization(), id);
    }

    public T save(T entity) {
        String expected = currentOrganization();
        String actual = organizationOf(entity);
        if (!expected.equals(actual)) {
            throw new TenantMismatchException(expected, actual);
        }
        store.put(type, actual, idOf(entity), entity);
        return entity;
    }

    public void deleteById(ID id) {
        store.remove(type, currentOrganization(), id);
    }
}