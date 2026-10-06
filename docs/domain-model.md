# Domain Model

**Status:** Draft — P1-01, P1-02, P1-03 complete (Phase 1 DoD met)
**Last updated:** 2026-09-24

---

## Entity List

Every entity is scoped to an `organization_id` from inception per DECISION-002 and NFR-009.

### 1. Organization

**Purpose:** Tenant root. Every other entity belongs to exactly one Organization. Authorization enforcement is Phase 15; data isolation plumbing (foreign key + repository filter) starts here.

**Maps to:** FR-020, DECISION-002, NFR-009

**Key attributes (draft — refined in P1-02):**
- `id` (PK)
- `name`
- `created_at`

---

### 2. Service

**Purpose:** Represents a single deployable unit (a microservice, a library, a gateway). A Service can own APIs (provider) and consume endpoints on other Services (consumer). A Service without any registered API is valid — it may exist purely as a consumer initially.

**Maps to:** FR-001 (register service), FR-002 (service metadata), FR-005 (consumer in a dependency declaration)

**Key attributes (draft):**
- `id` (PK)
- `organization_id` (FK → Organization)
- `name` (unique within org)
- `description`
- `created_at`

---

### 3. Api

**Purpose:** One versioned OpenAPI specification bound to a Service. An Api groups related Endpoints under a single version string and spec artifact. Separate from Service because:
- A Service can have multiple API versions registered simultaneously (for diffing and transition tracking).
- FR-008 (version comparison) requires two Api records representing different versions.
- The Api holds spec-level metadata (version string, checksum for staleness, content hash) that doesn't belong on individual Endpoints.

**Maps to:** FR-003 (ingest OpenAPI spec), FR-004 (API metadata), FR-008 (compare two versions)

**Key attributes (draft):**
- `id` (PK)
- `service_id` (FK → Service)
- `organization_id` (FK → Organization)
- `version` (e.g., "2.1.0")
- `spec_content` (raw OpenAPI JSON/YAML, stored for re-diffing)
- `content_hash` (SHA-256, for staleness/change detection)
- `created_at`

---

### 4. Endpoint

**Purpose:** A single method+path extracted from an Api's spec. This is the atomic unit that consumers depend on and that diffing operates on. One Api contains many Endpoints.

**Maps to:** FR-004 (extract endpoints from spec), FR-008 (compare endpoints across versions), FR-005 (dependency targets an endpoint, not an entire API)

**Key attributes (draft):**
- `id` (PK)
- `api_id` (FK → Api)
- `organization_id` (FK → Organization)
- `method` (GET, POST, PUT, DELETE, PATCH)
- `path` (e.g., "/users/{id}")
- `summary`
- `request_body_schema` (JSON, resolved per DECISION-003)
- `response_schema` (JSON, resolved per DECISION-003)
- `parameters` (JSON array, resolved per DECISION-003)
- `created_at`

---

### 5. Dependency

**Purpose:** A declared relationship: "Service A (consumer) calls Endpoint X on Service B (provider)." In MVP this is always manually declared (DECISION-001). The entity records who calls what, with a detection source and confidence level that are locked to MANUAL/DECLARED for MVP but pre-defined for future extensibility.

**Maps to:** FR-005 (manual dependency declaration), DECISION-001

**Key attributes (draft):**
- `id` (PK)
- `organization_id` (FK → Organization)
- `consumer_service_id` (FK → Service)
- `provider_endpoint_id` (FK → Endpoint)
- `detection_source` (enum: MANUAL — only value in MVP)
- `confidence` (enum: DECLARED — only value in MVP)
- `created_at`

**Constraint:** `consumer_service_id` and the service owning `provider_endpoint_id` must not be the same Service (a service cannot declare a dependency on its own endpoint — that's an internal call, not an inter-service dependency).

---

### 6. ApiChange

**Purpose:** A record of a single version-comparison run between two Api snapshots. Captures which Endpoints were added, removed, or modified, and the nature of each modification. Persisted (not just in-memory) because FR-015 requires change history and the storage cost is low.

**Maps to:** FR-008 (version comparison), FR-009 (breaking-change classification), FR-015 (change history)

**Key attributes (draft):**
- `id` (PK)
- `organization_id` (FK → Organization)
- `api_id` (FK → Api — the API whose versions are being compared)
- `from_version` (FK → Api or version string)
- `to_version` (FK → Api or version string)
- `change_summary` (JSON — list of per-endpoint changes: added/removed/modified + classification)
- `has_breaking_changes` (boolean, materialized for query filtering)
- `created_at`

---

### 7. ImpactReport

**Purpose:** A materialized result of a single impact analysis run. Given a set of ApiChanges, identifies which consumer Services are affected and classifies the severity. Persisted because FR-017 requires analysis history and past reports must be shareable without re-running the analysis.

**Maps to:** FR-010 (impact analysis), FR-012 (blast-radius), FR-013 (severity), FR-017 (analysis history)

**Key attributes (draft):**
- `id` (PK)
- `organization_id` (FK → Organization)
- `api_change_id` (FK → ApiChange — which change triggered this report)
- `affected_consumers` (JSON — array of {service_id, endpoint_id, severity, explanation})
- `overall_severity` (enum: LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN)
- `created_at`

---

## Cardinalities, Invariants & State Transitions (P1-02)

This section formalizes, per entity: ownership, relationship cardinalities, the invariants that must always hold, and the lifecycle (state set + allowed transitions). Invariants marked **[P:FE]** are enforced purely by foreign-key/pointer integrity (persistence concern, built in P1-03/Phase 8); those marked **[P:LOGIC]** require application-layer validation in Phase 8 or a base-repository rule from P1-03.

Notation: `1..1` = exactly one, `0..1` = zero or one, `1..*` = one or more, `0..*` = zero or more, `*` = many.

---

### Organization

**Ownership:** Root. No other entity creates it; it is created directly (bootstrap).

**Cardinalities:**
- `Organization 1..1 --> 0..* Service` (an org may own zero or many services)
- `Organization 1..1 --> 0..* Api` (via owned services)
- `Organization 1..1 --> 0..* Dependency`
- `Organization 1..1 --> 0..* ApiChange`
- `Organization 1..1 --> 0..* ImpactReport`

**Invariants:**
- INV-ORG-01: `id` is immutable and globally unique. [P:FE]
- INV-ORG-02: `name` is non-empty. [P:LOGIC]
- INV-ORG-03: An Organization must exist before any Service, Dependency, ApiChange, or ImpactReport referencing it can exist. [P:FE]

**Lifecycle / State transitions:**
- States: `{ACTIVE}` only.
- Transitions: none in MVP. Creation is the only operation; no archive/delete transition is defined. Deletion of an org is intentionally out of scope (would cascade-destroy tenant data; revisit only if a product need emerges).

---

### Service

**Ownership:** Created by an Organization (tenant root).

**Cardinalities:**
- `Service 1 : * Api` (a service owns zero-to-many APIs; each API belongs to exactly one service)
- `Service 1 : * Dependency (as consumer)` (a service may be the consumer in zero-to-many dependency declarations)
- `Service 0..* : * Endpoint (as provider, via owned APIs)` (a service provides endpoints only through its APIs; reached transitively, not directly)

**Invariants:**
- INV-SVC-01: `service.name` is unique within the owning Organization. [P:LOGIC]
- INV-SVC-02: `service.name` is non-empty. [P:LOGIC]
- INV-SVC-03: `organization_id` must reference an existing Organization. [P:FE]
- INV-SVC-04: A Service may exist with zero APIs (valid pure-consumer). [P:LOGIC]
- INV-SVC-05: A Service may exist with zero outgoing dependencies (valid pure-provider). [P:LOGIC]

**Lifecycle / State transitions:**
- States: `{ACTIVE, ARCHIVED}`.
- Transitions:
  - `ACTIVE --> ARCHIVED` (deterministic: a user archives a service; no longer discoverable in default queries; does not delete its APIs/endpoints/dependencies — history must remain intact for FR-015/FR-017).
  - `ARCHIVED --> ACTIVE` (unarchive allowed; nothing is lost).
- Rationale for ARCHIVED (not DELETE): deleting a Service would force cascade deletion or dangling FKs on Dependency/ApiChange/ImpactReport, destroying the history that FR-015 and FR-017 require. Archiving preserves referential integrity while marking the service inactive.
- No other transitions in MVP.

---

### Api

**Ownership:** Created by a Service (an API is a versioned spec artifact owned by exactly one service).

**Cardinalities:**
- `Api 1 : * Endpoint` (an API owns zero-to-many endpoints; each endpoint belongs to exactly one API)
- A Service may have multiple Api records (different versions) simultaneously — required for FR-008 diffing.

**Invariants:**
- INV-API-01: `api.service_id` must reference an existing Service. [P:FE]
- INV-API-02: `api.content_hash` must be non-empty (SHA-256 of the spec_content at ingestion) and is immutable once set. [P:LOGIC]
- INV-API-03: `api.spec_content` must be a single, versioned OpenAPI 3.x document that parsed successfully at ingestion (P3); malformed specs never create an Api row. [P:LOGIC], enforced in Phase 3.
- INV-API-04 (version uniqueness — OPEN): Whether `(service_id, version)` must be unique. **Defaulted to YES** for MVP: a Service should not register the same version string twice, because ApiChange and impact history key on version identity. If a real use case for re-uploading the same version arises, revisit with a migration. [P:LOGIC]

**Lifecycle / State transitions:**
- States: `{ACTIVE}`.
- Transitions: none in MVP. Api is created immutable (its spec_content, version, and content_hash never change after insertion; any revision is a *new* Api row, never an UPDATE to the existing one). `ACTIVE` only; no archive/delete — deleting an Api would break ApiChange `from_version`/`to_version` references.

---

### Endpoint

**Ownership:** Created by its parent Api (endpoints are derived from the API's spec during ingestion, Phase 3).

**Cardinalities:**
- `Api 1 : * Endpoint`
- `Endpoint 1 : * Dependency` (an endpoint may be the provider target in zero-to-many dependency declarations)
- A Service reaches its provided endpoints transitively via its APIs.

**Invariants:**
- INV-EP-01: `endpoint.api_id` must reference an existing Api. [P:FE]
- INV-EP-02: `method` ∈ `{GET, POST, PUT, DELETE, PATCH}` (mirrors draft). [P:LOGIC]
- INV-EP-03: `path` is non-empty and normalized (e.g., leading `/`, path params in `{param}` form). [P:LOGIC], normalized at ingestion.
- INV-EP-04: `(method, path)` is unique within the owning Api. [P:LOGIC]
- INV-EP-05: `request_body_schema`, `response_schema`, `parameters` are stored resolved per DECISION-003 (no unresolved `$ref`). [P:LOGIC], enforced at ingestion (Phase 3).

**Lifecycle / State transitions:**
- States: `{ACTIVE}`.
- Transitions: none in MVP. Endpoints are immutable snapshots derived from an Api at ingestion time. A change to an endpoint's shape is represented as a *new* Endpoint under a new Api version, compared by P6 — never by mutating an existing Endpoint row. Deleting an endpoint would break Dependency references, so it is not allowed.

---

### Dependency

**Ownership:** Created by the Organization (a user of that org manually declares it).

**Cardinalities:**
- `Service (consumer) * : 1 Dependency` (a dependency has exactly one consumer service)
- `Endpoint (provider) * : 1 Dependency` (a dependency targets exactly one provider endpoint)
- `Service (consumer) 0..* : * Endpoint (provider)` via Dependency declarations (the implicit M:N graph; not a junction table — expressed through Dependency rows).

**Invariants:**
- INV-DEP-01: `organization_id` must reference an existing Organization. [P:FE]
- INV-DEP-02: `consumer_service_id` must reference an existing Service in the *same* organization. [P:FE + cross-column check]
- INV-DEP-03: `provider_endpoint_id` must reference an existing Endpoint in the *same* organization. [P:FE + cross-column check]
- INV-DEP-04 (**same-service rule**): the Service that owns `provider_endpoint_id`'s Api must NOT equal `consumer_service_id` (a service cannot declare a dependency on its own endpoint — internal calls are not inter-service dependencies). [P:LOGIC], carried over from the draft.
- INV-DEP-05: `detection_source == MANUAL` and `confidence == DECLARED` in MVP. Any other value is rejected (`400 UNSUPPORTED_DETECTION_SOURCE` per DECISION-001). [P:LOGIC]
- INV-DEP-06 (uniqueness — OPEN): Whether `(consumer_service_id, provider_endpoint_id)` can repeat. **Defaulted to NO duplicate for MVP** (a consumer either calls an endpoint or it does not; a second identical declaration carries no information and risks divergent metadata). Revisit if Phase 4 demonstrates a need for multiplicity. [P:LOGIC]
- INV-DEP-07: all cross-entity referents (`consumer_service_id`, `provider_endpoint_id`) belong to the same `organization_id` as the Dependency itself. [P:FE]

**Lifecycle / State transitions:**
- States: `{ACTIVE, REMOVED}`.
- Transitions:
  - `ACTIVE --> REMOVED` (a user deletes/removes a dependency declaration. It is soft-removed, not hard-deleted, so that past ApiChange/ImpactReport historical references remain interpretable).
  - `REMOVED --> ACTIVE` (re-declaration allowed; becomes a fresh Declaration with a new created_at).
- Rationale for soft-removal: hard delete would orphan the dependency references embedded in historical ImpactReport/ApiChange JSON. Soft state preserves history (FR-015, FR-017) while excluding the dependency from active graph queries.

---

### ApiChange

**Ownership:** Created by the API version-comparison engine (Phase 6) for a given Api.

**Cardinalities:**
- `Api 1 : * ApiChange` (an API generates zero-to-many comparison records over its version history)
- `ApiChange 1 : 0..* ImpactReport` (a change may later be associated with zero-to-many impact reports)

**Invariants:**
- INV-ACH-01: `api_id` must reference an existing Api. [P:FE]
- INV-ACH-02: `from_version` and `to_version` must reference *two different* Api records (or two distinct version strings) belonging to `api_id`'s service; `from_version != to_version`. [P:LOGIC]
- INV-ACH-03: `has_breaking_changes` must equal the presence of any classification `BREAKING`/`POTENTIALLY_BREAKING` in `change_summary` (materialized, must never drift out of sync with the JSON). [P:LOGIC]
- INV-ACH-04: `change_summary` must be non-empty (a comparison always yields at least the classification set; an identical pair may produce an explicit empty diff but still records the comparison). [P:LOGIC]

**Lifecycle / State transitions:**
- States: `{ACTIVE}`.
- Transitions: none in MVP. ApiChange is a write-once, immutable result of a comparison run. No editing, archiving, or deletion in MVP — history must be append-only for FR-015.

---

### ImpactReport

**Ownership:** Created by the impact analysis engine (Phase 7) for a given ApiChange.

**Cardinalities:**
- `ApiChange 1 : 0..* ImpactReport` (each run over a set of ApiChanges may produce one ImpactReport; re-runs append new reports)
- `ImpactReport` references `api_change_id` exactly one. [P:FE]

**Invariants:**
- INV-IMR-01: `api_change_id` must reference an existing ApiChange. [P:FE]
- INV-IMR-02: `overall_severity` ∈ `{LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN}`. [P:LOGIC]
- INV-IMR-03: `affected_consumers` is an array of `{service_id, endpoint_id, severity, explanation}`; each `service_id`/`endpoint_id` must be a *consumer* of the changed endpoint(s) per the dependency graph. [P:LOGIC], enforced by Phase 7.
- INV-IMR-04: `overall_severity` must be derivable deterministically from the per-consumer severities via the Phase 7 severity rules (no free-form scores; NFR-008 determinism). [P:LOGIC]
- INV-IMR-05: `affected_consumers` may be empty (a change with zero known consumers still produces a valid report with severity `NONE`-equivalent — represent as empty array + `overall_severity = LOW` default or explicit "no consumers" marker, decided in Phase 7). [P:LOGIC]

**Lifecycle / State transitions:**
- States: `{ACTIVE}`.
- Transitions: none in MVP. ImpactReport is an immutable, shareable artifact (FR-017). No edit/delete in MVP.

---

## Cross-Entity Invariants

- X-INV-01: Every entity referenced by another (Service, Api, Endpoint, ApiChange) always carries the same `organization_id` as the referencing entity — the tenant-scoping invariant that P1-03's base scoped-repository must enforce structurally (NFR-009), not by convention. [P:FE]
- X-INV-02: No hard delete of any entity that is referenced by a persisted entity in this model (Service, Api, Endpoint, ApiChange, Dependency, ImpactReport) during MVP. History preservation (FR-015/FR-017) requires append-only semantics. Deletes are modeled as soft state (ARCHIVED / REMOVED) where a lifecycle exists. [P:LOGIC]
- X-INV-03: Multi-tenant data isolation is structural: any repository query must apply the organization filter (P1-03), so a missed controller/service check can never leak data across orgs. [P:FE], NFR-009.

---

## OPEN Items (deferred to their owning phase)

- **INV-API-04** (version uniqueness) and **INV-DEP-06** (dependency uniqueness): defaulted to YES/NO respectively for MVP; both marked OPEN because a real-world need could override — revisit at Phase 4 (dependency model) without a schema migration if the default proves wrong.
- Severity representation of a "no consumers" ImpactReport (INV-IMR-05): decided at Phase 7, not now.

---

## Challenge Decisions

### Api vs Endpoint — kept separate
**Challenge:** If one OpenAPI spec = one Service = one Api, does Api add value over putting `api_version` directly on Endpoint?
**Decision:** Keep separate. Api carries independent state (version string, raw spec content, content hash) that is meaningful for diffing (FR-008) and spec retrieval. Collapsing would require duplicating spec-level data across every Endpoint row or losing the ability to diff two full spec snapshots.

### ApiChange — persisted
**Challenge:** MVP only needs diff results during an active run. Is this table premature?
**Decision:** Persist. FR-015 (change history) explicitly requires it. Schema cost is one table. Retrofitting persistence later means migration work plus backfill logic. Keeping it from day one is lower total cost.

### ImpactReport — persisted
**Challenge:** Could be computed on-demand to reduce schema surface in Phase 1.
**Decision:** Persist. FR-017 (analysis history) explicitly requires it. Users must be able to review/share past reports without re-running the analysis pipeline. The `affected_consumers` JSON column keeps the schema flexible without needing a junction table during MVP.

### Soft state (ARCHIVED / REMOVED) — added in P1-02, justified per entity
**Challenge:** MVP described most entities as effectively immutable. Should any have delete/archive?
**Decision:** Yes, minimally — only where preservation of *history* (FR-015/FR-017) collides with a *current-state* need (excluding a service or dependency from active queries). This yields `Service.ARCHIVED` and `Dependency.REMOVED`. All other entities stay immutable/append-only; hard deletes are disallowed model-wide (X-INV-02) to protect historical referential integrity.

---

## Entities Explicitly Deferred

These entities serve later phases and are NOT part of MVP domain modeling:

- **User / AuthSession** — Phase 15 (auth enforcement). Not needed until login exists.
- **AuditLog** — Phase 15. No audit FR in MVP scope.
- **GitHubPullRequest / WebhookEvent** — Phase 10. GitHub integration is post-checkpoint.
- **AnalysisRun** — Could wrap ApiChange + ImpactReport as a single execution unit, but MVP doesn't need this abstraction until the pipeline runs automatically (Phase 10+). Add if a later ticket demonstrates a concrete need.
