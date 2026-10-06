# Architecture

**Status:** Design — P2-01 complete (target architecture for Phases 3–9; reconciled with real code in the same ticket that lands each part, per §14)
**Last updated:** 2026-09-24
**Companion:** `docs/component-diagram.md` (visual), `docs/scoped-repository.md` (persistence contract)

---

## 1. Purpose

This document defines the component-level architecture for the MVP: a single Spring Boot monolith exposing a REST API to a React frontend, backed by PostgreSQL. **FACT:** as of this phase no application code exists; this is the target design Phases 3–9 implement, not a description of built software.

The system answers one question: *"If I change this API, which known consumers could be affected and why?"* by executing the MVP loop — ingest → diff → impact report — over the Phase 1 domain model.

## 2. Principles (DECISION)

1. **Monolith, default stack.** React → Spring Boot → PostgreSQL. A "component" here is a logical module (package boundary), never a deployable service, container, or queue. No technology enters without a demonstrated need and an ADR (AI_GUARDRAILS §6, §10).
2. **Determinism (NFR-008).** The four analysis engines — Ingestion, Graph, Diff, Impact — contain the core logic and are testable with no HTTP, DB, or GitHub. They depend only on *ports* (repository interfaces); the Spring Data implementations are injected by thin orchestration. Identical input must produce identical output.
3. **Tenant isolation at the repository layer (DECISION-002, NFR-009).** Every component persists exclusively through the `ScopedRepository` contract (P1-03). No component reaches for an unscoped query, now or later.
4. **No invented machinery.** Sync, in-process everything for MVP. Async, queues, caches, indexes are Phase 12 evaluations against observed need — not architectural features.

## 3. Components

| Component | Responsibility | Owning domain entities | Depends on |
|-----------|----------------|------------------------|------------|
| API Management | Service/Api registration, version listing, endpoint extraction trigger, Orchestration entry for everything REST-facing | Organization, Service, Api, Endpoint | Ingestion, Diff, Graph, Impact |
| OpenAPI Ingestion | Parse OpenAPI 3.x via a library (not hand-rolled); dereference per DECISION-003; reject malformed/circular specs (FR-003); normalize paths; produce Endpoint snapshots. **STATUS: parse (P3-01/P3-04) + surface extraction of methods/paths/params/bodies (P3-02) + `$ref` expansion and `allOf` merge (P3-03, `SpecResolver`) implemented — deterministic, in-place mutation, rejects circular (`CIRCULAR_REF`) and non-schema/external refs (`UNSUPPORTED_REF`) per DECISION-003. Error taxonomy (P3-04): `MALFORMED_SPEC` (syntax/required-fields/paths missing; non-3.x docs surface here because the library refuses them with a null model) + resolver codes `CIRCULAR_REF`/`UNSUPPORTED_REF`/`UNKNOWN_REF`; parser warnings carried non-fatally; codes only — HTTP 400 mapping is the later API layer's job.** | Endpoint, ExtractedEndpoint | repository ports (write) |
| Dependency Analysis | Manual dependency declaration API per DECISION-001 (`MANUAL`/`DECLARED` only; else `400`); same-service rule (INV-DEP-04); cross-column org check (INV-DEP-07); soft-remove. **STATUS: P4-01+P4-02 implemented** — pure `DependencyDeclarationService` (codes `UNSUPPORTED_DETECTION_SOURCE`/`UNKNOWN_CONSUMER_SERVICE`/`UNKNOWN_PROVIDER_ENDPOINT`/`ORGANIZATION_MISMATCH`/`SELF_DEPENDENCY`/`DUPLICATE_DEPENDENCY`), full retained `DetectionSource`/`Confidence` enums, immutable `Dependency` model (id+status ACTIVE/REMOVED, X-INV-02), `DependencyLookup` port; persistence = in-memory org-scoped port of the P1-03 contract (`TenantContext`, `TenantMismatchException`, `ScopedRepository<T,ID>` base, `InMemoryStore`, `Organization/Service/Endpoint/Dependency` repositories, `StoredDependencyLookup`) enforcing NFR-009 structurally + dangling-ref/duplicate validation (INV-DEP-02/03/06/07). JPA/Postgres implementation verified later at P8-01 per scoped-repository.md. | Dependency | repository ports |
| Graph Engine | Direct consumers/dependencies, upstream/downstream traversal, cycle detection, blast radius (FR-006/007/011). SQL adjacency list + in-app traversal. **No graph DB** (see ADR-001, P2-03). **STATUS: P5-01 implemented** — pure `GraphEngine` over immutable org-scoped `GraphSnapshot` (built from P4-02 scoped repos): `consumersOfService`, `callsOfService`, `consumersOfEndpoint` (sorted, deterministic, NFR-008); transitive traversal/cycle detection/blast radius → P5-02/03/04 | reads Dependency | repository ports (read) |
| API Diff Engine | Version comparison per DECISION-003; breaking-change classification; `oneOf`/`anyOf` → `POTENTIALLY_BREAKING` fallback | ApiChange | repository ports (write) |
| Impact Analysis | Combine ApiChange + blast-radius consumers → per-consumer explanations; deterministic severity (LOW/MEDIUM/HIGH/CRITICAL/UNKNOWN); FR-010/012/013 | ImpactReport | Diff (ApiChange), Graph (blast radius), repository ports |
| Reporting | Materialize/serve change history, impact reports, dashboard data to the Phase 9 React views; FR-014/015/017 | ApiChange, ImpactReport (read) | repository ports |

**Ports rule (NFR-008):** engines depend on repository *interfaces* only. The orchestration layer implements those interfaces with the `ScopedRepository`-backed Spring Data repositories. In unit tests, ports are stubbed — engines never construct an entity manager or execute a query themselves.

## 4. The core loop (data flow)

```
Service/Api registered (API Management)
  → spec uploaded → OpenAPI Ingestion dereferences + extracts Endpoints
  → Endpoint snapshots persisted (org-scoped, P1-03)
  → consumers manually declare "Service A calls Endpoint X" (Dependency Analysis)
  → Graph Engine traverses Dependency rows for affected consumers
  → a new Api version lands → API Diff Engine compares against prior version
  → ApiChange (with has_breaking_changes) persisted
  → Impact Analysis joins ApiChange × blast-radius consumers → ImpactReport
  → Reporting exposes report + history to the frontend
```

All synchronous, single request/response. Nothing is queued or retried in MVP — that is Phase 12 material (UNKNOWN volume, deliberately deferred per §8).

## 5. Layering & boundaries

```
React (Phase 9) ──REST──► Spring Boot
  Presentation (REST controllers, Phase 8: validation, FR-018 error model, auth boundary placeholder)
  Application / Orchestration (API Management, Dependency Analysis, Reporting)
  Domain Engines (Ingestion, Graph, Diff, Impact)  ← pure, port-based
  Persistence (ScopedRepository contract → PostgreSQL)   ← P1-03, only entry point to the DB
```

- **Engines below orchestration:** they never see an `HttpServletRequest`, a datasource, or a GitHub client (NFR-008).
- **Persistence boundary:** nothing calls JPA/`EntityManager`/JDBC except the `ScopedRepository` base implementation. P1-03 rule 4.
- **Organization:** `TenantContext` (ThreadLocal) is set at the REST boundary; repositories read the current org from it. Engines receive the org as an explicit parameter from orchestration so they stay pure. FACT: the exact boundary-injection mechanism is finished at Phase 8; the contract is P1-03.

## 6. Cross-cutting concerns

- **Tenant isolation:** NFR-009 enforced by P1-03 contract; the audit rule at each phase boundary.
- **Error model:** FR-018 consistent error structure — one convention implemented at Phase 8 (P8-03); `TenantMismatchException` maps to `403` then (`400`-ish/non-2xx otherwise), `UNSUPPORTED_DETECTION_SOURCE` → `400` per DECISION-001.
- **Determinism:** severity and classification rules are documented, deterministic functions. No scores invented (FR-013/NFR-008). AI (Phase 14) may only explain evidence, never determine a result.

## 7. Technology choices (DECISION / ASSUMPTION)

| Choice | Tag | Rationale (short) |
|--------|-----|-------------------|
| Spring Boot monolith + Spring Data JPA | DECISION | default stack; ports pattern keeps NFR-008 intact |
| React for frontend | DECISION (roadmap) | Phase 9 views each map to a user workflow |
| PostgreSQL | DECISION | relational fit, org-scoping, history of Append-only snapshots |
| Graph traversal: SQL adjacency + in-app traversal | DECISION | no graph DB; **ADR-001 in P2-03** justifies |
| OpenAPI parser: `io.swagger.parser.v3:swagger-parser:2.1.47` | DECISION (P3-01) | existing library per guardrail §6 — no hand-rolled YAML/JSON schema parser. Full OpenAPI 3.0.x support; **3.1 support is partial** (documented gap, not silently relied on) |
| Postgres Row-Level Security | DEFERRED | candidate Phase 15 hardening (P1-03), needs ADR |

## 8. Deferred / UNKNOWN

```
UNKNOWN: expected services/organization, concurrent request volume (scale — affects
         traversal strategy and NFR-001; won't guess, revisited with real usage data).
DEFERRED: GitHub integration (Phase 10), REST layer (Phase 8), auth enforcement (Phase 15),
          async/queues/caching/scaling (Phase 12), runtime dependency detection (Phase 13),
          AI layer (Phase 14).
```

## 9. Doc consistency rule (§14)

As each phase lands code, this document and `component-diagram.md` are updated **in the same ticket** that introduces the divergence. Docs never go fictional: if a component described here isn't built yet, it says so.