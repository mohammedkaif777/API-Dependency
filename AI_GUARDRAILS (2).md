# AI_GUARDRAILS.md

Enforceable rules for any AI (or engineer) working on this project. Not generic advice — every rule exists to prevent a specific failure mode.

## 1. Project purpose
Detect potentially breaking API changes and identify affected downstream consumers before a change reaches production. See `requirements.md` for the authoritative scope.

## 2. Scope
MVP = static OpenAPI ingestion + manually-declared dependencies + version diffing + impact reporting, for Java/Spring Boot/REST/PostgreSQL. Nothing outside `requirements.md` §2/§6 without a logged scope-change decision (§9 below).

## 3. Non-goals
Kubernetes, Kafka, runtime traffic analysis, service mesh, graph database, multi-language source analysis, LLM architecture assistant, automated remediation, enterprise SSO, complex billing, mobile app. These require an explicit, documented decision to bring into scope — not silent creep.

## 4. Anti-hallucination rules
- Never assume a class, endpoint, table, package, dependency, config value, or library behavior exists unless it has been shown (via file read, grep, or explicit statement).
- If uncertain: state `"I don't have enough evidence to determine this"` and say exactly what's needed to resolve it.
- Tag every non-obvious claim as **FACT / ASSUMPTION / DECISION / UNKNOWN**. Never present an assumption as a fact.

## 5. Coding rules
- Inspect real files before modifying them. No edits against guessed structure.
- Don't rename unrelated classes, rewrite working code without cause, swallow exceptions, catch generic `Exception`, hardcode credentials/config, use deprecated APIs without justification, leave TODOs on critical paths, or fake a placeholder as production code.
- Never claim code compiles/passes unless actually verified. If not run: say `"This code has not been executed in the current environment."`

## 6. Architecture rules
- Default stack: React → Spring Boot → PostgreSQL. Any new technology requires answering: what problem does it solve, why is the current stack insufficient, what alternatives exist, what complexity does it add, why now not later.
- Tenant scoping (`organization_id`) is present on every domain entity and repository query starting at Phase 1 — see `requirements.md` DECISION-002. This is not deferred.
- Core logic (diff engine, graph engine, impact engine, risk classification) must be testable with no HTTP, DB, or GitHub dependency.

## 7. Debugging rules
On an error: observed error → possible causes → evidence needed → most likely cause → minimal fix → regression test. Never rewrite code before this sequence. Ask for logs/stack traces/config instead of guessing.

## 8. Testing rules
No feature is done without unit tests for its core logic and, where it touches HTTP/DB, integration tests. Deterministic components (diff engine, graph engine, impact engine) must have tests proving identical input → identical output.

## 9. Decision-making / scope-change protocol
When a new feature or change is requested, classify it first:
```
Existing requirement? | New requirement? | Scope change? | Architecture change?
```
Then state impact on MVP, implementation complexity, dependencies, risks, and whether it should be postponed. If it meaningfully expands scope, say so explicitly before doing any work.

## 10. Technology introduction criteria
A new technology enters the project only when a demonstrated requirement exists (not "might need it later"). Document it as an ADR (see `PROJECT_ROADMAP.md` template) before adopting it.

## 11. Rules for handling uncertainty
Prefer stating an `UNKNOWN` over inventing a plausible-sounding default. Provisional numeric targets (performance, scale) must be labeled `ASSUMPTION` and revisited once real usage data exists.

## 12. Rules for modifying existing code
Read the current file. Preserve existing conventions. Change only what the task requires. Flag anything that looks like a pre-existing bug rather than silently "fixing" it out of scope.

## 13. Rules for preventing scope creep
Every task ticket (see `PROJECT_ROADMAP.md`) maps to exactly one FR/NFR ID. A task with no traceable requirement ID is a scope-change request, not a task — route it through §9.

## 14. Documentation rules
`requirements.md`, `architecture.md`, `domain-model.md` and friends must describe the *actual* implementation. If code and docs diverge, update the doc in the same task, or explicitly flag the conflict — never let docs go fictional.

## 15. Git rules
Small, scoped commits (`feat:`, `fix:`, `test:`, `docs:`). No "initial project" mega-commits.

## 16. Security rules
Least privilege, secure secret management, encryption where appropriate, auth + authz, audit logging. Source code metadata, API specs, org info, and GitHub credentials are sensitive by default.

## 17. AI usage rule
Sequence is Explain → Design → Review → Implement → Test → Debug. Never "generate everything, hope it works." The engineer must be able to defend every decision.

## 18. Definition of Done
A feature is done only when: requirement defined → design understood → code implemented → tests exist and pass → error handling exists → docs updated → assumptions documented → security implications considered → locally verifiable → committed.

## 19. Self-review of these guardrails
Known weak points to watch for over time:
- "FACT vs ASSUMPTION" tagging is easy to skip under time pressure — if a response has no tags on a non-trivial claim, treat that as a guardrail violation, not a style choice.
- Scope-creep classification (§9) only works if every request actually gets routed through it — a task that "sounds small" is the most common way it gets skipped.
- Tenant-scoping (§6) depends on a base repository pattern being adopted from Phase 1 forward — if a later phase adds a repository that doesn't extend it, isolation silently breaks. Worth a periodic grep-based audit.
