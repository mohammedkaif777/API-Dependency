package com.apiproject.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Org-bucketed backing store shared by all repositories. Rows live under an implied org key (known at
 * write time), so an org-scoped read can only ever see current-org rows: a foreign row is not a
 * security-filtered row, it is simply in a different bucket. This makes NFR-009 structural rather
 * than conventional. Single-JVM only — this is the Phase 4 in-memory port of the P1-03 contract; the
 * JPA/Postgres port is verified at P8-01 (scoped-repository.md).
 */
public final class InMemoryStore {

    private final Map<Class<?>, Map<String, Map<Object, Object>>> byType = new ConcurrentHashMap<>();

    public InMemoryStore() {}

    public void put(Class<?> type, String organizationId, Object id, Object entity) {
        bucket(type, organizationId).put(id, entity);
    }

    public Optional<Object> get(Class<?> type, String organizationId, Object id) {
        return Optional.ofNullable(bucket(type, organizationId).get(id));
    }

    public boolean contains(Class<?> type, String organizationId, Object id) {
        return bucket(type, organizationId).containsKey(id);
    }

    public void remove(Class<?> type, String organizationId, Object id) {
        bucket(type, organizationId).remove(id);
    }

    public List<Object> values(Class<?> type, String organizationId) {
        return new ArrayList<>(bucket(type, organizationId).values());
    }

    private Map<Object, Object> bucket(Class<?> type, String organizationId) {
        return byType.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(organizationId, ignored -> new ConcurrentHashMap<>());
    }
}