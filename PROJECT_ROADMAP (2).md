# PROJECT_ROADMAP.md

Every phase is broken into tickets. Each ticket is one unit of work the AI proposes, you approve, the AI implements, and you review before the next ticket starts. No phase is "done" until every ticket in it is closed.

Ticket template (the AI must fill this out *before* writing code, and you approve it before implementation starts):

```
TICKET: <ID> — <title>
Maps to requirement: <FR-xxx / NFR-xxx / DECISION-xxx>
Objective: <what, in one sentence>
Files to create/modify: <list>
Dependencies: <what this needs to already exist>
Assumptions: <ASSUMPTION-tagged items, if any>
Definition of Done: <bullet list>
Common mistakes to avoid here: <1-3 bullets>
```

---

## PHASE 0 — Product Definition
**Objective:** Lock target user, MVP boundary, success criteria.
**Tickets:** P0-01 Confirm MVP scope doc, P0-02 Confirm non-goals, P0-03 Define success criteria (what "MVP works" means concretely).
**DoD:** `requirements.md` reviewed and accepted by you (already done — v2 in this delivery).

## PHASE 1 — Domain Modeling
**Objective:** Entities, relationships, and tenant scoping (DECISION-002).
**Tickets:**
- P1-01 Draft entity list + challenge it (Organization, Service, Api, Endpoint, Dependency, ApiChange, ImpactReport) — don't accept blindly, justify each.
- P1-02 Define cardinality/invariants/state transitions per entity.
- P1-03 Add `organization_id` + base scoped-repository pattern (NFR-009).
**Deliverables:** `/docs/domain-model.md`, `/docs/erd.md`.
**DoD:** Every entity has an owner, lifecycle, and tenant scope; ERD reviewed.
**Do NOT build yet:** persistence code, REST layer.

## PHASE 2 — Architecture
**Tickets:** P2-01 Component diagram (API Management, OpenAPI Ingestion, Dependency Analysis, Graph Engine, API Diff Engine, Impact Analysis, Reporting). P2-02 Sequence diagram for the core flow (ingest → diff → impact report). P2-03 First ADR: "Why Postgres, not a graph DB, for v1."
**Deliverables:** `/docs/architecture.md`, `/docs/component-diagram.md`, `/docs/sequence-diagrams.md`, `/docs/adr/ADR-001-...md`.

## PHASE 3 — OpenAPI Ingestion
**Tickets:** P3-01 Parser setup (use an existing OpenAPI parser library — do not hand-roll a YAML/JSON schema parser). P3-02 Extraction: endpoints/methods/paths/params/bodies/schemas. P3-03 `$ref`/`allOf` resolution (DECISION-003). P3-04 Error handling for malformed/unsupported specs (FR-003).
**Maps to:** FR-003, FR-004.
**Do NOT build yet:** dependency detection, diffing.

## PHASE 4 — Dependency Model
**Tickets:** P4-01 Manual dependency declaration API (DECISION-001 — `MANUAL`/`DECLARED` only, other enum values rejected). P4-02 Persistence + validation (no dangling references to nonexistent services/endpoints).
**Maps to:** FR-005 (amended).

## PHASE 5 — Graph Engine
**Tickets:** P5-01 Direct consumers/dependencies queries. P5-02 Upstream/downstream traversal. P5-03 Cycle detection. P5-04 Blast-radius traversal + documented complexity (Big-O, why it's acceptable at expected scale — reference the `UNKNOWN` scale assumptions honestly).
**Maps to:** FR-006, FR-007, FR-011.
**Explicit constraint:** no graph database. Use SQL/adjacency-list + in-app traversal.

## PHASE 6 — API Change Detection
**Tickets:** P6-01 Version comparison engine (DECISION-003 rules). P6-02 Breaking-change classification rules (endpoint removed, method changed, required param/field added, response field removed, type changed). P6-03 `oneOf`/`anyOf` fallback classification.
**Maps to:** FR-008, FR-009.

## PHASE 7 — Change Impact Analysis
**Tickets:** P7-01 Combine diff + graph → impact report with per-consumer explanation. P7-02 Severity classification (LOW/MEDIUM/HIGH/CRITICAL/UNKNOWN) with documented, deterministic rules — no invented scores.
**Maps to:** FR-010, FR-012, FR-013.
**Checkpoint:** this is the first point the whole core loop (ingest → diff → impact report) works end-to-end. Treat it as an MVP milestone before touching frontend/GitHub.

## PHASE 8 — Spring Boot Backend
**Tickets:** P8-01 REST endpoints for registration/ingestion/dependency/graph/comparison/impact. P8-02 DTOs + validation. P8-03 Consistent error response structure (FR-018). P8-04 API versioning strategy decision (ADR).

## PHASE 9 — React Frontend
**Tickets:** one per view — Dashboard, Services, Service Details, Dependency Graph, API Details, Change Analysis, Impact Report. Each ticket must name the user workflow it serves — no decorative UI.
**Maps to:** FR-014.

## PHASE 10 — GitHub Integration
**Gate:** only after Phase 7 checkpoint is stable and Phase 8/9 work.
**Tickets:** P10-01 Webhook receiver + signature validation. P10-02 PR-triggered diff+impact pipeline. P10-03 PR comment formatting.
**Maps to:** FR-016.

## PHASE 11 — Testing
**Tickets:** unit tests per engine (parser, diff, graph, impact, risk), integration tests (Spring+Postgres+REST), one end-to-end test of the full pipeline.

## PHASE 12 — Production Architecture
**Gate:** only after monolith MVP works. Tickets evaluate (not necessarily adopt) async processing, queues, caching, indexing, rate limiting, retries/idempotency, horizontal scaling — each against real observed need.

## PHASE 13 — Runtime Dependency Detection
**Tickets:** design `RUNTIME_DEPENDENCY` source (enum already exists per DECISION-001) — tracing/gateway-log/app-log based. Explicitly document that runtime data is never treated as complete.

## PHASE 14 — AI Layer
**Gate:** deterministic system must already establish evidence; AI only explains it, never determines the impact result (NFR-008).

## PHASE 15 — Security
**Tickets:** auth (FR-019), tenant **enforcement** (FR-020 — data scoping already exists from Phase 1 per DECISION-002), GitHub OAuth/App permissions, secret management, webhook signature validation, audit logging.

## PHASE 16 — Deployment
**Tickets:** Docker, CI/CD (GitHub Actions), environment tiers (local/dev/staging/prod), AWS deployment only after local correctness is proven, rollback strategy documented.

---

## How the loop works in practice
1. AI proposes the next ticket using the template above.
2. You approve, request changes, or reject.
3. AI implements only that ticket — smallest useful change.
4. AI provides tests + how to verify locally + likely failure modes.
5. You review and either close the ticket or send it back.
6. Only then does the AI propose the next ticket.

No phase is entered until the previous phase's tickets are closed. No ticket is proposed without a requirement ID it maps to.
