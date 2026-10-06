package com.apiproject.repository;

import com.apiproject.dependency.Dependency;
import com.apiproject.dependency.DependencyDeclarationException;
import java.util.List;
import java.util.UUID;

/**
 * Org-scoped repository for {@link Dependency} rows. Adds the dependency-specific invariants on top
 * of the base scoping guards:
 * <ul>
 *   <li>duplicate uniqueness (INV-DEP-06): a second non-REMOVED (consumer, endpoint) in the current
 *       org is rejected with {@code DUPLICATE_DEPENDENCY},</li>
 *   <li>ids are assigned here (P4-02) — the declared model has none,</li>
 *   <li>soft lifecycle (X-INV-02): {@link #softRemove} flips ACTIVE → REMOVED; {@link #findAllActive}
 *       and duplicate checking never see REMOVED rows, so a removed declaration can be re-declared.</li>
 * </ul>
 *
 * <p>Cross-column consistency (INV-DEP-07) needs no extra check here: referents are resolved through
 * org-scoped repositories before we get here, so a foreign-org referent is invisible and never
 * reaches {@link #create}.
 */
public final class DependencyRepository extends ScopedRepository<Dependency, String> {

    public DependencyRepository(InMemoryStore store) {
        super(store, Dependency.class);
    }

    @Override
    protected String idOf(Dependency entity) {
        return entity.getId();
    }

    @Override
    protected String organizationOf(Dependency entity) {
        return entity.getOrganizationId();
    }

    /** Validates and persists a freshly declared dependency; returns the assigned id. */
    public String create(Dependency draft) {
        String current = currentOrganization();
        if (!current.equals(draft.getOrganizationId())) {
            throw new TenantMismatchException(current, draft.getOrganizationId());
        }
        boolean duplicate = findAllActive().stream().anyMatch(
                existing -> existing.getConsumerServiceId().equals(draft.getConsumerServiceId())
                        && existing.getProviderEndpointId().equals(draft.getProviderEndpointId()));
        if (duplicate) {
            throw new DependencyDeclarationException(
                    DependencyDeclarationException.DUPLICATE_DEPENDENCY,
                    "dependency already declared (INV-DEP-06): consumer=" + draft.getConsumerServiceId()
                            + ", endpoint=" + draft.getProviderEndpointId());
        }
        String id = UUID.randomUUID().toString();
        Dependency stored = new Dependency(
                id, draft.getOrganizationId(), draft.getConsumerServiceId(), draft.getProviderEndpointId(),
                draft.getDetectionSource(), draft.getConfidence(), Dependency.Status.ACTIVE);
        save(stored);
        return id;
    }

    /** Soft-removes the current-org row if present (X-INV-02); no-op for foreign rows (scoped). */
    public boolean softRemove(String id) {
        return findById(id)
                .map(current -> {
                    Dependency removed = current.removed();
                    save(removed);
                    return true;
                })
                .orElse(false);
    }

    /** Only ACTIVE rows of the current org. */
    public List<Dependency> findAllActive() {
        return findAll().stream()
                .filter(dependency -> dependency.getStatus() == Dependency.Status.ACTIVE)
                .toList();
    }
}