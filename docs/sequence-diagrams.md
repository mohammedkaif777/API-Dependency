# Sequence Diagrams — Core Loop

**Status:** Design — P2-02 complete
**Last updated:** 2026-09-24
**Companion:** `docs/architecture.md`, `docs/component-diagram.md`

These diagrams show the MVP loop — **ingest → diff → impact report** — in three parts. Participants are
exactly the P2-01 components (plus `User`, the backend boundary, and the scoped-repository port). Every call
is synchronous in-process; there are no queues, events, or async signals anywhere (Phase 12 is deferred).
**FACT:** REST terminology in the diagrams (`POST /…`) is the Phase 8 target shape; no code exists yet.

---

## 1. Spec Ingest (FR-003, DECISION-003)

```mermaid
sequenceDiagram
    participant U as "User / React (Phase 9)"
    participant AM as "API Management"
    participant PORT as "ScopedRepository (port, P1-03)"
    participant ING as "OpenAPI Ingestion"

    U->>AM: POST /services/{id}/apis (version, spec doc)
    AM->>AM: set TenantContext org (boundary)
    AM->>PORT: save Api (org-scoped)
    PORT-->>AM: Api row
    AM->>ING: extract(Api, resolved spec)  [pure]
    ING->>ING: resolve $ref / merge allOf (DECISION-003)
    alt malformed spec / invalid schema
        ING-->>AM: FAILURE(code = MALFORMED_SPEC)
        AM-->>U: 400 { code: MALFORMED_SPEC, ... }  [FR-018]
    else circular $ref chain
        ING-->>AM: FAILURE(code = CIRCULAR_REF)
        AM-->>U: 400 { code: CIRCULAR_REF, ... }  [FR-003]
    else valid
        ING->>PORT: save Endpoint rows (resolved, org-scoped)
        ING-->>AM: endpoint list + count
        AM-->>U: 201 { apiId, endpointCount }
    end
```

Notes: endpoints are stored fully dereferenced per DECISION-003 (INV-EP-05); `oneOf`/`anyOf` branches are
preserved, not flattened — the Diff Engine treats them specially (Diagram 2).

---

## 2. Version Diff (FR-008)

```mermaid
sequenceDiagram
    participant U as "User / React (Phase 9)"
    participant AM as "API Management"
    participant PORT as "ScopedRepository (port, P1-03)"
    participant DIFF as "API Diff Engine"
    participant PERS as "PostgreSQL (via P1-03 base)"

    U->>AM: POST /api-diffs (fromVersion, toVersion)
    AM->>PORT: load both Api snapshots (org-scoped)
    alt fromVersion == toVersion  [INV-ACH-02]
        AM-->>U: 400 { code: SAME_VERSION, ... }  [FR-018]
    else distinct versions
        AM->>DIFF: compare(fromSpec, toSpec)  [pure]
        loop over changed endpoints
            DIFF->>DIFF: classify per P6-02 rules
            alt oneOf/anyOf branch changed  [DECISION-003]
                DIFF->>DIFF: classify = POTENTIALLY_BREAKING
                Note over DIFF: "complex schema composition changed — manual review required"
            end
        end
        DIFF->>PERS: save ApiChange (change_summary, has_breaking_changes)
        DIFF-->>AM: ApiChange (id, summary)
        AM-->>U: 201 { apiChangeId, hasBreakingChanges, summary }
    end
```

Notes: classification is deterministic (identical inputs → identical `change_summary`, NFR-008);
`has_breaking_changes` is materialized at write time and never drifts (INV-ACH-03).

---

## 3. Impact Report (FR-010/012/013)

```mermaid
sequenceDiagram
    participant U as "User / React (Phase 9)"
    participant AM as "API Management"
    participant IMP as "Impact Analysis"
    participant GR as "Graph Engine"
    participant PORT as "ScopedRepository (port, P1-03)"
    participant REP as "Reporting"

    U->>AM: POST /impact-reports (apiChangeId)
    AM->>AM: set TenantContext org (boundary)
    AM->>IMP: analyze(apiChangeId)
    IMP->>GR: blastRadius(changed endpoints)  [pure]
    GR->>PORT: read Dependency rows (org-scoped)
    GR-->>IMP: affected consumers {service, endpoint, reason}
    alt zero known consumers  [INV-IMR-05]
        IMP->>IMP: report with empty affected_consumers
        Note over IMP: severity representation decided in Phase 7 (P7-XX)
    else consumers found
        loop each affected consumer
            IMP->>IMP: classify severity [LOW|MEDIUM|HIGH|CRITICAL|UNKNOWN]
        end
        IMP->>IMP: derive overall_severity deterministically (P7 rules)
    end
    IMP->>PORT: save ImpactReport (org-scoped)
    IMP-->>AM: ImpactReport (id, severity, consumers)
    AM-->>U: 202 { reportId, overallSeverity }
    U->>AM: GET /impact-reports/{id}
    AM->>REP: fetch(reportId)
    REP->>PORT: read ImpactReport + ApiChange (org-scoped)
    REP-->>AM: formatted report
    AM-->>U: 200 report (FR-017 history)
```

---

## Traceability

| Diagram | Flow | Maps to | Failure branches shown |
|---------|------|---------|------------------------|
| 1 | ingest → endpoints | FR-003, DECISION-003, INV-EP-05 | malformed spec, circular `$ref` |
| 2 | version diff → ApiChange | FR-008, DECISION-003, INV-ACH-02/03 | same-version, `oneOf`/`anyOf` manual review |
| 3 | impact → report | FR-010/012/013, NFR-008, INV-IMR-05(open) | zero consumers |

## Boundary & determinism rules applied to every diagram

- `TenantContext` is set at the API Management boundary before the first port call; co-scoped reads/writes
  are implied by every `PERS`/`PORT` interaction (P1-03 contract).
- The analysis engines (Ingestion, Diff, Graph, Impact) perform no I/O that is not a declared port call;
  nothing reaches PostgreSQL except through the `ScopedRepository` base (NFR-008, P1-03 rule 4).
- No async signals (`-->` is used only for returns), no queues, no event bus, no GitHub actor in the MVP
  loop.