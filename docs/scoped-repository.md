# Scoped Repository Pattern (NFR-009)

**Status:** Approved — P1-03 complete
**Last updated:** 2026-09-24
**Maps to:** NFR-009, DECISION-002, X-INV-01, X-INV-03

---

## Purpose

NFR-009 requires multi-tenant data isolation enforced **structurally** at the repository layer, not by
convention: a missed controller/service-level authorization check must never be able to leak cross-tenant
data. This document is the Phase 1 contract for that enforcement.

It is deliberately implementation-light. The precise Spring Data customization technique is confirmed when
the first concrete repository is actually built (Phase 8, P8-01). The **contract itself** — the guarantee
mechanism, the per-entity mapping, and the audit rule — is fixed now and binding on every repository the
project ever adds.

---

## The guarantee mechanism (DECISION)

Every concrete repository in the codebase extends exactly one abstract base repository,
`ScopedRepository<T, ID>`, which:

1. **Filters every default read path** on `organization_id = <current org>` via JPA Criteria — `findAll`
   (plain, `Sort`, `Pageable`), `findAllById`, `findById`, `getById`, `count`, `existsById`. A derided
   repository inherits this behavior; it does not have to remember to apply a filter.
2. **Guards every write path**: `save` / `saveAll` assert `entity.organization_id == current org`; a
   mismatch throws `TenantMismatchException` (mapped to `403` at Phase 15; a non-2xx error at Phase 8).
   Cross-tenant writes are impossible even if a caller holds a valid foreign-row ID.
3. **Scopes deletes**: `deleteById` / `delete(entity)` resolve against the current org — a delete targeting
   another org's row finds nothing and no-ops. A foreign row is never mutated or removed.

**Alternatives considered and rejected for the base mechanism:**
- *Hibernate `@Filter`* — annotation-per-entity, easy to forget, not centralized; enforcement lives next to
  each entity rather than in one base class.
- *PostgreSQL Row-Level Security* — the strongest possible guarantee (enforcement in the DB itself), but
  NFR-009 explicitly names the repository layer; also, in consequence, leaves an escape hatch in the app if
  only RLS is used, and needs auth plumbing not present in Phase 1. **DECISION:** noted as candidate Phase 15
  hardening requiring its own ADR, not a Phase 1 mechanism.

**Current-org source (ASSUMPTION, consistent with DECISION-002):** a `TenantContext` (ThreadLocal) set at
the request/login boundary. Before auth exists (Phase 15), every request sets it to the single bootstrap
default organization, so the filter path runs in every test from day one. The concrete context-injection
mechanism (interceptor/filter) is a Phase 8 detail; the contract only requires that *some* boundary code sets
it before any repository call.

---

## The contract (binding rules)

1. `ScopedRepository<T, ID>` is marked `@NoRepositoryBean` and is the **only** interface any concrete
   repository may extend. A repository that cannot extend it must be justified in a review and this doc
   amended — it is not a per-author judgment call.
2. All default CRUD is org-scoped per the mechanism above. Derived interfaces must **not** redefine
   `findById` / `findAll` to drop the filter.
3. Any query method a derived interface declares (e.g. `findByPathAndMethod`) must carry the org criterion.
   A method that reads by ID or lists rows without the current org is a guardrail violation (§19).
4. Write validation in derived repositories passes through the base org guard; no derived code may call
   `EntityManager` / `JpaRepository` directly, bypassing the base class.
5. `Organization` is the tenant root: `OrganizationRepository` only reads the current org row (`findById`
   against `currentOrgId()`). `findAll` over organizations is never exposed — a multi-org "list my orgs"
   feature, if it ever exists, is scoped to the current org id by the same base rule.

---

## Entity mapping (X-INV-01)

Every MVP entity carries its own `organization_id` column (ERD, DECISION-002), so all map identically:

| Entity | Repository extends `ScopedRepository` | Org source | Notes |
|--------|---------------------------------------|------------|-------|
| Organization | No — root, id-based read only | its own `id` | Only documented exception |
| Service | Yes | own column | |
| Api | Yes | own column | org redundant with parent chain but kept per DECISION-002 |
| Endpoint | Yes | own column | same |
| Dependency | Yes | own column | **plus** cross-column rule below |
| ApiChange | Yes | own column | |
| ImpactReport | Yes | own column | |

**Dependency cross-column consistency (INV-DEP-07):** a Dependency's `consumer_service_id` and
`provider_endpoint_id` referents must belong to the same org as the Dependency. Because the base repo
guarantees a reader only ever sees current-org Services/Endpoints, no cross-org referent can ever *resolve*
at write time (a foreign-org id simply doesn't exist from the scoped repositories' perspective). One explicit
cross-column assertion remains in the Dependency write validation as defense-in-depth — a foreign id would
fail either way.

---

## The audit rule (§19 guardrail)

The escape hatch that defeats any repository-base pattern is a derived repository that overrides inherited
methods and drops the filter. Mitigations, in force from this phase:

- **Structural:** the base class is the only entry point to JPA (rule 4). Overriding an inherited org-scoped
  method is visible in code review and caught by the audits below.
- **Grep audit (manual, each phase boundary until CI exists in Phase 16):**
  - Concrete `*Repository` classes/interfaces NOT extending `ScopedRepository` (excluding the documented
    `OrganizationRepository`).
  - Declared `@Query` / Criteria / Specification methods in any repository that reference the entity by ID or
    list rows without an org predicate.
  - Service-layer direct `findById` / `findAll` call sites that are not reaching a `ScopedRepository` method.
- **CI (Phase 16):** the same checks become an automated test/archunit-style rule.

---

## When the contract is first verified

P8-01 (first Spring Boot persistence ticket) builds a concrete repository against this contract and must
demonstrate, in order: (a) default scoped reads return only current-org rows, (b) writes to a foreign org are
rejected, (c) deletes cannot touch a foreign row, and (d) the Dependency cross-column rule. Until then this
document is the authoritative reference and the deferred-choice note below applies.

---

## Deferred choices and open items

- **Implementation mechanism (P8-01):** the Spring Data customization route (custom `repositoryBaseClass`
  subclassing `SimpleJpaRepository` vs. repository fragments) is a Phase 8 choice. This contract does not
  depend on which one is selected; either satisfies rules 1–5.
- **ASSUMPTION — write-guard semantics:** `save` directly on an entity with the wrong org throws. Fragmented
  updates not routed through `save` (JPA dirty-tracking across a request) are out of scope for MVP because
  entities are effectively append-only snapshots (X-INV-02, Phase 1 lifecycle section). If Phase 8 introduces
  entity mutation through persistence context, revisit.
- **UNKNOWN — RLS adoption:** Postgres Row-Level Security remains a candidate Phase 15 hardening ADR; it
  would remove even the audit-visible escape hatch. Deliberately not decided now.