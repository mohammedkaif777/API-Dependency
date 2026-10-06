# ADR-001 — Why PostgreSQL over a graph database for v1

**Status:** Accepted — P2-03
**Date:** 2026-09-24
**Applies to:** Graph Engine (FR-006, FR-007, FR-011), Phase 5
**Supersedes:** nothing (first ADR). If this is revisited, a new ADR supersedes it.

**Context in one sentence:** the impact question is a graph problem — "which known consumers could be affected
by changing this API?" — so the natural reflex is a graph database. This ADR records why v1 does not use one.

---

## Decision

Implement the dependency graph as **SQL adjacency (Dependency rows) + in-app traversal in Spring Boot**. No
graph database, no recursive-CTE reliance, no new infrastructure, and no additional datastore. A dedicated
graph engine technology is considered only if the re-entry condition in Consequence is met.

This is the DECISION behind roadmap Phase 5's explicit constraint: *"no graph database. Use SQL/adjacency-list
+ in-app traversal."*

---

## The five questions (AI_GUARDRAILS §10)

### 1. What problem would a graph DB solve?

Cleaner, declarative multi-hop traversal (upstream/downstream dependencies, blast radius) with a purpose-built
query language and index strategy, instead of hand-written traversal code. For very large or deeply recursive
graphs it also provides scale benefits over per-hop SQL.

### 2. Why is the current stack insufficient — for v1?

The honest assessment is that **it is not clearly insufficient at MVP scale, and the scale is `UNKNOWN`**
(requirements §7). What v1 actually needs:
- Traverse a *sparse* M:N graph: Services → Endpoints via Dependency rows.
- Queries: direct dependencies/consumers (P5-01), upstream/downstream (P5-02), cycle detection (P5-03),
  blast radius (P5-04).
That workload is a bounded set of well-defined traversals over a graph populated only by manual declarations
(DECISION-001). Subject to the `UNKNOWN` scale envelope, PostgreSQL + indexed adjacency handles it, and has no
demonstrated ceiling that a graph DB removes — *yet*.

### 3. What alternatives exist?

| Alternative | Verdict for v1 | Reason |
|-------------|----------------|--------|
| Neo4j (dedicated graph DB) | **Rejected** | Adds a second system of record, a separate server/driver, ops and licensing surface, a consistency problem (dual-write with Postgres), and forces re-implementing the NFR-009 scoping contract against a new driver. Our graph is small and simple; the expressiveness of Cypher buys little at this size. |
| PostgreSQL **recursive CTE** | Rejected as the *mechanism* | Same Postgres, but recursive CTE planning/performance is less predictable, depth limits (`MAX_RECURSION`) bite on deep graphs, and the core logic sits in SQL — hard to unit-test without a live DB, violating NFR-008 ("core logic testable with no HTTP/DB"). |
| **In-app adjacency traversal** (chosen) | **Accepted** | Traversal code lives in the Graph Engine as a pure algorithm over rows fetched through the P1-03 scoped repositories. Testable with no DB (NFR-008), no new infrastructure, predictable Big-O, and a single source of truth (Postgres). |
| Index over Postgres table `(consumer_service_id, provider_endpoint_id)` | Accepted as the concrete data layout | This powers the chosen path; covered in the same decision. |
| Elasticsearch / vector DB | Rejected without a demonstrated need | A technology looking for a problem; violates §10. |

### 4. What complexity does it add (versus a graph DB)?

Honest costs of the chosen path:
- We write and own traversal code (P5-01…P5-04) instead of issuing Cypher.
- **Query shape risk:** naive per-hop queries are N+1. Mitigation: batch-load each BFS/DFS level with a single
  `IN` query — bounded round-trips, handled in P5-04's blast-radius ticket.
- **Worst case:** a blast-radius query is O(V+E) of the reachable subgraph (see Complexity). A graph DB with
  appropriate indexes does not change the theoretical complexity; it changes constant factors.
- **No graph index** (e.g. path-indexing for `all paths between A and B`). v1 does not require path queries —
  only reachability/boundary queries. UNKNOWN: if path-level queries become a product need, revisit.

### 5. Why now, not later?

Because mobilizing a second datastore now would force consistency, scoping, and ops work onto *every* Phase
5–7 ticket, for a benefit 10s of thousands of Dependency rows probably never realize (`UNKNOWN`, tagged
ASSUMPTION). If (and only if) measured scale breaks the chosen approach, a new ADR reverses this — at that
point the evidence exists to justify the added complexity, which is the correct order (guardrail §11: never
invent scale; adopt against demonstrated need).

---

## Traversal complexity and failure modes (documented for P5-04)

- **Complexity:** breadth/depth-first traversal over adjacency-indexed rows is **O(V+E)** of the *reachable*
  subgraph per query (each visited vertex expanded exactly once via a visited set). Blast radius for a change
  on endpoints `E₀` is O(V + E) over the union of consumers reachable from `E₀`.
- **Failure modes → mitigations:**
  - *Recursion depth / stack overflow on deep graphs* → iterative traversal with explicit stack/queue (never
    recursion).
  - *Cycles (A→B→A) causing infinite loops* → visited-set guard (also the basis of P5-03 cycle detection).
  - *N+1 query blow-up on wide graphs* → level-batched `IN` reads (single query per depth level).
  - *Cross-tenant leakage during traversal* → all rows read through the P1-03 scoped repositories; the filter is
    injected structurally, traversal never sees another org's rows.

---

## Consequence

- Phase 5 is implemented with adjacency + in-app traversal. Per Phases 3–9 ticket discipline, no graph-DB
  dependency appears anywhere.
- **Re-entry condition (new ADR required):** any one of these observed, tagged `ASSUMPTION` until measured at
  Phase 11/12:
  1. End-to-end blast-radius latency exceeds **~10s** at real workloads/scale (threshold tagged ASSUMPTION).
  2. Production Dependency volume/metric makes O(V+E) in-app traversal memory- or latency-unsustainable, backed
     by evidence (not prediction).
  3. A recurring product requirement for multi-hop *path* queries (not just reachability) emerges.
  Before any of these, no graph DB enters the project; after one triggers, this ADR is superseded by evidence.

## Tags

- DECISION: adjacency + in-app traversal for v1.
- ASSUMPTION: scale envelope, latency threshold (~10s), and the "tens of thousands of rows" reasoning.
- UNKNOWN: expected services/organization, concurrent request volume (requirements §7) — the variables that set
  the re-entry condition.
- FACT: nothing was installed or coded in this ticket.