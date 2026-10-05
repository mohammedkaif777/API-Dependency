# PRODUCT REQUIREMENTS SPECIFICATION
## API Dependency Intelligence / API Impact Analyzer

Status: v2 — resolves 3 gaps found in architecture review (dependency detection source, tenant scoping timing, OpenAPI schema resolution depth).

---

## 1. PRODUCT VISION

> In a microservices environment, engineers often don't have reliable visibility into which services consume which APIs. Changing an API can unintentionally break downstream consumers.

MVP question to answer: **"If I change this API, which known consumers could be affected and why?"**

---

## 2. MVP SCOPE

```
Service registration → OpenAPI ingestion → API/endpoint extraction →
Dependency representation → Dependency graph → API version comparison →
Breaking-change detection → Impact analysis → Impact report
```

Stack: Java/Spring Boot, REST + OpenAPI 3.x, PostgreSQL. GitHub integration only after the core analyzer works end-to-end.

---

## 3. RESOLVED DECISIONS (from architecture review)

### DECISION-001 — Dependency detection source for MVP
**Problem:** FR-005 required `detection_source` and `confidence` fields, but the MVP has no code-analysis or runtime-detection capability to populate them meaningfully.

**Decision:** MVP supports exactly one detection source: **manual declaration**. A user explicitly states "Service A calls Endpoint X on Service B."

```
detection_source: MANUAL | STATIC_CODE_ANALYSIS | RUNTIME
confidence:        DECLARED | INFERRED | OBSERVED
```

Only `MANUAL` / `DECLARED` are implemented in MVP. The other enum values exist in the schema now (so the field never needs a breaking migration later) but are rejected by validation until their detection phases (13) are built. Attempting to set them in MVP returns `400 UNSUPPORTED_DETECTION_SOURCE`.

### DECISION-002 — Tenant scoping is designed in Phase 1, enforced in Phase 15
**Problem:** Multi-tenancy (FR-020) was scheduled as a Phase 15 (Security) concern, but retrofitting `organization_id` onto an already-built schema and query layer is expensive and bug-prone.

**Decision:** Every domain entity gets an `organization_id` foreign key and every repository method is organization-scoped **starting in Phase 1 (Domain Modeling)** — even before authentication exists. In local dev, a single default organization is used and the scoping code path still runs (so it's exercised by every test from day one). Authorization enforcement (rejecting cross-org access at the API layer) is still a Phase 15 deliverable — only the *data isolation plumbing* moves earlier.

### DECISION-003 — OpenAPI schema resolution depth for diffing (v1)
**Problem:** FR-008's examples show flat objects; real specs use `$ref`, `allOf`/`oneOf`/`anyOf`, and reused component schemas. Diffing is not "compare two JSON objects."

**Decision for v1:**
- All `$ref` pointers are resolved (dereferenced) before diffing. The diff engine never sees a `$ref` — it sees the fully expanded schema.
- `allOf` is merged into a single effective schema before diffing.
- `oneOf` / `anyOf` are **not** deeply diffed in v1. A change to any branch inside a `oneOf`/`anyOf` is classified as `POTENTIALLY_BREAKING` with reason `"complex schema composition changed — manual review required"`, rather than pretending to know the semantic effect.
- Circular `$ref` chains are detected and rejected at ingestion time (FR-003 error condition) rather than causing infinite recursion in the diff engine.

This is documented as a known v1 limitation, not silently glossed over — it should be stated explicitly in the Impact Report when it applies.

---

## 4. FUNCTIONAL REQUIREMENTS (unchanged from v1 except where noted)

Same FR-001 through FR-020 as the original spec, with these amendments:

- **FR-005 (Dependency Registration)** — amended per DECISION-001. Renamed conceptually to "Manual Dependency Declaration" for MVP; full enum retained in schema.
- **FR-008 (API Version Comparison)** — amended per DECISION-003 (resolution depth, `oneOf`/`anyOf` handling, circular-ref rejection).
- **FR-020 (Multi-Tenancy)** — priority split: *data-model scoping* is now `MUST HAVE` starting Phase 1 (was implicitly Phase 15); *enforcement* remains `MUST HAVE FOR PRODUCTION` at Phase 15.

All other FRs (organization creation, service registration, API discovery, dependency graph construction/query, breaking-change detection, impact/blast-radius/severity analysis, impact report, visualization, change history, GitHub integration, analysis history, error handling, authentication) carry over unchanged. Reproduce the full FR-001…FR-020 text from the original spec into this file before implementation begins — this version only documents the deltas so the resolution is traceable.

---

## 5. NON-FUNCTIONAL REQUIREMENTS

Unchanged from v1 (NFR-001 Performance … NFR-008 Determinism). No new NFRs introduced by the resolutions above, except:

**NFR-009 — Data Isolation (new).** Every query against a scoped entity must include an organization filter at the repository layer, not only at the controller/service layer, so a missed authorization check can never leak cross-tenant data. This must be enforced structurally (e.g., a base repository that always injects the filter), not by convention alone.

---

## 6. NON-GOALS

Unchanged — see original spec (no Kubernetes, Kafka, runtime traffic analysis, service mesh, graph DB, multi-language source analysis, LLM architecture assistant, automated remediation, enterprise SSO, complex billing, mobile app).

---

## 7. OPEN UNKNOWNS (explicitly deferred, not resolved)

```
UNKNOWN: Expected max services/organization (affects graph traversal strategy).
UNKNOWN: Expected concurrent analysis request volume.
UNKNOWN: Whether confidence scoring becomes numeric once STATIC_CODE_ANALYSIS/RUNTIME
         sources are added (Phase 13) — deliberately not designed now.
```

These stay `UNKNOWN` rather than being guessed at, per the anti-hallucination rules.
