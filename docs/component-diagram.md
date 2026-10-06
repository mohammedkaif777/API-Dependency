# Component Diagram

**Status:** Design — P2-01 complete
**Last updated:** 2026-09-24
**Companion:** `docs/architecture.md` (detail), `docs/scoped-repository.md` (persistence contract)

## Mermaid Component Diagram

```mermaid
flowchart LR
    subgraph FE["External (post-checkpoint)"]
        GH["GitHub Integration<br/>(Phase 10, deferred)"]:::future
        UI["React Frontend<br/>(Phase 9)"]:::future
    end

    subgraph ORCH["Orchestration layer (Spring Boot)"]
        APIM["API Management<br/>registration, versions, orchestration entry"]
        DEP["Dependency Analysis<br/>manual declaration, DECISION-001"]
        REP["Reporting<br/>history + report views, FR-014/015/017"]
    end

    subgraph ENG["Core engines (NFR-008: pure, no HTTP/DB)"]
        ING["OpenAPI Ingestion<br/>dereference per DECISION-003"]
        GRAPH["Graph Engine<br/>traversal, cycle, blast radius"]
        DIFF["API Diff Engine<br/>version comparison + classification"]
        IMPACT["Impact Analysis<br/>consumers x severity"]
    end

    PERS["ScopedRepository contract (P1-03)<br/>org filter on every query"]
    DB["PostgreSQL"]:::storage

    UI -->|REST (Phase 8)| APIM
    APIM -->|spec upload| ING
    APIM -->|register / query| DEP
    APIM -->|blast radius / consumers| GRAPH
    APIM -->|compare versions| DIFF
    APIM -->|run analysis| IMPACT

    ING -->|Endpoint snapshots| PERS
    DEP -->|Dependency rows| PERS
    GRAPH -->|reads Dependency via port| PERS
    DIFF -->|writes ApiChange via port| PERS
    IMPACT -->|writes ImpactReport via port| PERS
    REP -->|reads ApiChange / ImpactReport| PERS

    PERS --- DB
    GH -.->|PR-triggered pipeline<br/>(Phase 10, post-checkpoint)| APIM

    classDef future stroke-dasharray: 5 5,stroke:#888;
    classDef storage stroke:#003;
```

**Ports detail:** the arrows from `GRAPH`/`DIFF`/`IMPACT`/`INGEST` to `PERS` are *repository interfaces* (ports), satisfied by orchestration using the `ScopedRepository` base. Engines never touch Spring Data or the datasource directly — they are stub-testable (NFR-008).

**FACT:** the React frontend, GitHub integration, REST controllers, and the OpenAPI/parser library do not exist yet. Dashed outlines and the `future` class mark planned (not built) boundaries.

---

## Component Notes

### API Management
- **Responsibility:** service/api registration, version listing, and the orchestration entry point for everything REST-facing.
- **Owning entities:** Organization, Service, Api, Endpoint (lifecycle facades).
- **Depends on:** Ingestion, Graph, Diff, Impact (orchestrates them), ports for persistence.

### OpenAPI Ingestion
- **Responsibility:** parse OpenAPI 3.x via an existing library; resolve `$ref`, merge `allOf`, detect `oneOf`/`anyOf`, reject circular `$ref` chains and malformed specs (FR-003); normalize paths (INV-EP-03); produce resolved Endpoint snapshots (INV-EP-05).
- **Owning entities:** writes Endpoint rows under an Api.
- **Deterministic:** same spec in → same endpoints out.

### Dependency Analysis
- **Responsibility:** manual dependency declaration only (`MANUAL`/`DECLARED`; any other value → `400 UNSUPPORTED_DETECTION_SOURCE`, DECISION-001); enforce same-service rule (INV-DEP-04) and cross-column org consistency (INV-DEP-07); soft-remove (`REMOVED`, X-INV-02).
- **Owning entities:** Dependency.

### Graph Engine
- **Responsibility:** direct consumers/dependencies, upstream/downstream traversal, cycle detection, blast-radius traversal with documented Big-O (FR-006/007/011). Traverses the implicit Service↔Endpoint M:N via Dependency rows (adjacency list + in-app traversal).
- **No graph database:** justification in ADR-001 (P2-03).
- **Boundary:** pure algorithm over fetched rows; reads via a repository port.

### API Diff Engine
- **Responsibility:** compare two Api versions per DECISION-003; classify breaking changes (endpoint removed, method changed, required param/field added, response field removed, type changed); `oneOf`/`anyOf` branch changes → `POTENTIALLY_BREAKING` with `"complex schema composition changed — manual review required"`. Produces materialized `has_breaking_changes` (INV-ACH-03).
- **Owning entities:** ApiChange.

### Impact Analysis
- **Responsibility:** join an ApiChange with the blast-radius consumer set → per-consumer `{service_id, endpoint_id, severity, explanation}`; deterministic severity (LOW/MEDIUM/HIGH/CRITICAL/UNKNOWN) via documented rules (FR-013, NFR-008); empty-consumer case per INV-IMR-05 (decided Phase 7).
- **Owning entities:** ImpactReport.

### Reporting
- **Responsibility:** expose change history (FR-015), analysis history (FR-017), and report views (FR-014) to the React frontend. Reads only.
- **Owning entities:** ApiChange, ImpactReport (read side).

---

## Persistence boundary (P1-03)

- All arrows into `PERS` are the single, structural path to the database. No other component talks to JPA/JDBC.
- Every persisted entity carries `organization_id`; every repository query filters on it; writes to a foreign org throw `TenantMismatchException`; deletes cannot touch foreign rows.
- `Organization` is the documented exception: id-based read of the current org only.

## Deferred boundaries (not in the MVP core loop)

- **React frontend (Phase 9)** — external boundary, consumes REST.
- **GitHub Integration (Phase 10)** — PR-triggered diff+impact pipeline; gated on the Phase 7 checkpoint being stable.
- **Auth / tenant enforcement (Phase 15)** — only the data-isolation plumbing exists from Phase 1 onward.