# Implementation Plan: Neo4j Knowledge Graph Enhancements (v2)

**Created**: 2026-03-11 | **Updated**: 2026-03-19 | **Status**: Draft

---

## 1. Objective

Implement 8 enhancements leveraging Neo4j's graph-native capabilities for smarter, faster, and more resilient test execution.

---

## 2. Features Overview

### 2.1 Persistent `SATISFIES` Edges on Procedure Nodes (Analysis §2.3)

**What**: Create `SATISFIES` relationship edges directly between Procedure nodes to record that an effect of Procedure A satisfies a
precondition of Procedure B. Each edge carries a cosine similarity `score`, the matched `effectPhrase`/`precondPhrase` texts, and lifecycle
timestamps (`createdAt`, `lastVerifiedAt`).

**Why**: Today, precondition satisfaction is computed at runtime via in-memory cosine similarity on every execution. Specifically,
`KnowledgeService.reRankByStateCompatibility()` (called from `findBestMatch()`) calls `isPreconditionSemanticallyMet()` for every candidate
procedure on every step match — iterating over all precondition embeddings against all accumulated effect embeddings. Persisting `SATISFIES`
edges turns this O(N×M) per-step computation into a single graph traversal query during re-ranking, replacing `isPreconditionSemanticallyMet()`
with a pre-computed edge lookup. It also enables cross-run caching — once a match is discovered, it never needs to be recomputed until the
procedure is edited. This is the foundational feature: it enables ordering conflict detection (§2.5) and stale-edge health checks (§2.8).

**Key behaviors**: Edges are created lazily after `stateTracker.addEffects()` succeeds. Edges are invalidated (deleted) when a procedure's
effects or preconditions are edited. Stale edges (not verified in N days) are flagged for maintenance cleanup. Phrase text (not indices) is
used as the edge key for resilience against reordering.

### 2.2 Knowledge-Driven Failure Recovery (Analysis §2.4)

**What**: Introduce `FailureContext` nodes linked to Procedure nodes via `HAS_FAILURE_CONTEXT` edges. Each node stores a failure `symptom`,
`category` (reusing `ErrorCategory`), user-provided `resolution`, occurrence count, and a `mode` (`SUPERVISED` / `SUPERVISED_TIMEOUT` /
`UNATTENDED`). A single new `@Tool` method (`queryProcedureFailureHints`) lets agents query past failure patterns.

**Why**: When a procedure fails repeatedly for the same reason (e.g., "element takes 3 seconds to appear after animation"), the system
currently has no memory of that. Agents retry blindly. With FailureContext, the system learns from past failures: before executing a
procedure, it retrieves known failure hints and injects them into the agent's context so it can proactively work around known issues (e.g.,
call `waitSeconds()` before locating a slow element).

**Key behaviors**: In supervised mode, a dialog captures failure context from the user after retry exhaustion (with a timeout that
auto-captures as `SUPERVISED_TIMEOUT`). In unattended mode, failure context is auto-captured from the error category and tool error message.
`SUPERVISED_TIMEOUT` entries are excluded from hints (they have empty resolutions). Deduplication via `MERGE` on
`(procedureId, category, symptomNormalized)` increments `occurrences` on repeat failures.

### 2.3 Procedure Usage Tracking (Analysis §2.5)

**What**: Create `TestCase` nodes and `USES_PROCEDURE` edges to track which test cases use which procedures. Edges are created/updated at
execution time and cleaned up after successful runs.

**Why**: When a user edits a procedure via the knowledge collection dialog, they have no visibility into how many test cases depend on it. A
change to a shared procedure could break multiple test cases silently. With usage tracking, the system shows a warning ("This procedure is
used by 5 test cases: [list]. Editing it may affect all of them.") before the edit proceeds, enabling informed decisions.

**Key behaviors**: `USES_PROCEDURE` edges are merged (upserted) after each successful procedure match. Stale edges (procedures no longer
used by a test case) are cleaned up in the `finally` block after execution completes. Orphaned `TestCase` nodes are handled by maintenance
cleanup.

### 2.4 Element Stability Index (Analysis §3.3)

**What**: Store element-level reliability metadata directly on `UiElement` nodes: `stabilityScore` (EWMA of success/failure, α=0.3),
`avgLocationTimeMs`, `locationStrategy`, `failedLocationCount`, and `lastLocatedAt`.

**Why**: Some UI elements are inherently flaky — they appear after animations, load asynchronously, or shift position. Today, element
location uses a one-size-fits-all approach. With stability tracking, the system knows which elements are unreliable and can: (a) apply a
try-first, wait-and-retry strategy for unstable elements instead of a proactive sleep, (b) deprioritize procedures targeting unstable
elements during matching (stability penalty in re-ranking), and (c) flag unstable elements to users in supervised mode.

**Key behaviors**: `StabilityRecorder` functional interface is wired as a lambda delegating to
`ProcedureRepository.updateElementStability()`. Optimistic initialization at `1.0` — first failure drops to `0.70`. `ElementLocatorTools`
gains a two-constructor approach: existing no-arg delegates to new `(StabilityRecorder)` with a no-op default.

### 2.5 Ordering Conflict Detection (Analysis §3.4)

**What**: At the start of test execution (supervised mode only), use `SATISFIES` edges to detect when a test case specifies step B before
step A, but B's preconditions require an effect of A — a test case authoring error.

**Why**: Misordered test steps cause runtime failures that are hard to diagnose. The test appears correct but fails because a precondition
isn't met. By leveraging the persisted `SATISFIES` graph, the system can detect these ordering conflicts before execution begins and warn
the user via an `InformationalPopup`, saving debugging time.

**Key behaviors**: Only runs in supervised mode. Skipped entirely if no `SATISFIES` edges exist yet (cold graph). Conflicts are displayed as
warnings, not blockers — the user decides whether to proceed.

### 2.6 Procedure Execution Timing Profiles (Analysis §4.1)

**What**: Store per-procedure timing data as rolling averages (EWMA, α=0.2) on Procedure nodes: `avgExecutionMs`, `avgVerificationDelayMs`,
`maxVerificationDelayMs` (with decay), and `lastTimingUpdate`.

**Why**: Today, every atomic step uses a global `ACTION_VERIFICATION_DELAY_MILLIS` constant for the pause between action completion and
verification. Some procedures are inherently slow (page loads) while others are instant (checkbox clicks). A fixed delay wastes time on fast
procedures and causes false verification failures on slow ones. With per-procedure timing profiles, the verification delay adapts: fast
procedures verify sooner, slow procedures get more time — with a configurable floor to prevent premature verification.

**Key behaviors**: `TimingRecorder` functional interface wired as `writeRepository::updateTimingProfile`. Adaptive delay uses
`Math.max(minDelay, avgVerificationDelayMs)`. `maxVerificationDelayMs` decays gradually (0.95 factor) so one-off spikes don't permanently
inflate the wait. Falls back to the global constant when no timing data exists.

### 2.7 Procedure Ancestry Context for Intelligent Matching (Analysis §4.3)

**What**: Track a bounded sliding window of recently-matched parent procedure IDs in `ExecutionStateTracker`. During re-ranking, candidates
sharing a parent (via existing `CONTAINS` relationship) with recently-executed procedures receive an ancestry affinity boost.

**Why**: When `findBestMatch()` returns multiple candidates with similar semantic scores, the current re-ranking by state compatibility
sometimes picks the wrong one. For example, "Set the date" might match both a rental form procedure and a profile settings procedure. If the
previous steps were all under "Rental search form", the rental variant is almost certainly correct. Ancestry context uses the graph's
hierarchy — already stored via `CONTAINS` edges — to disambiguate without any new graph structures.

**Key behaviors**: `Deque<UUID> recentParentIds` bounded to `getAncestryWindowSize()` (default 5). Evicts oldest when over limit. Cleared on
`reset()`. `findSharedParentCount()` Cypher query leverages existing `CONTAINS` relationship.

### 2.8 Knowledge Graph Health Dashboard (Analysis §4.4)

**What**: A set of read-only Cypher health-check queries exposed via `GraphHealthService.logHealthReport()`: orphaned UI elements, leaf
procedures without target elements, deep hierarchies, disconnected procedures, missing effects, stale `SATISFIES` edges, and orphaned
`FailureContext` nodes.

**Why**: As the knowledge graph grows through iterative knowledge collection, structural quality degrades silently — orphaned nodes
accumulate, stale edges persist, and deep hierarchies become hard to maintain. Without visibility, these issues compound. The health service
provides a structured report (logged at INFO) for proactive quality management. A Swing dashboard was considered unnecessary — logging
provides the same functionality without UI complexity.

**Key behaviors**: `GraphHealthRepository` runs its own queries (no cross-repo dependencies). `GraphHealthService` generates a
`GraphHealthReport` record. Stale `SATISFIES` edge cleanup is an explicit action (not auto-deleted), controlled by `getSatisfiesStaleDays()`
config. The report is also rendered as a standalone HTML file via `GraphHealthHtmlReportGenerator` — a self-contained generator that builds
HTML using `String.formatted()` and `HtmlUtils.escapeHtml()` (no external templating library). The HTML report includes a color-coded
summary section (green/yellow/red per category based on finding count), collapsible detail sections per health-check category, and a
generation timestamp. A CLI script (`generate-health-report.bat` / `generate-health-report.sh`) is provided so users can generate reports
outside the running application (e.g., scheduled via cron or CI).

---

## 3. Architectural Design

### 3.1 New Components

#### Repositories

| Repository                       | Responsibility                                                                                 |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `ProcedureRepository` (existing) | Existing CRUD + `findSharedParentCount()`, timing CRUD, usage tracking, element stability CRUD |
| `SatisfiesEdgeRepository`        | `SATISFIES` edge CRUD + ordering conflict detection                                            |
| `FailureContextRepository`       | `FailureContext` node CRUD + `HAS_FAILURE_CONTEXT` edge CRUD                                   |
| `GraphHealthRepository`          | Read-only health-check queries (no cross-repo deps)                                            |

#### Services

| Service                       | Responsibility                                                                         |
|-------------------------------|----------------------------------------------------------------------------------------|
| `KnowledgeService` (existing) | Procedure matching, decomposition, re-ranking (ancestry affinity + stability penalty)  |
| `SatisfiesEdgeService`        | Async SATISFIES edge computation (vector queries + dedup + threshold + batch persist)  |
| `FailureContextService`       | Failure context capture, hint retrieval (filters `SUPERVISED_TIMEOUT`), orphan cleanup |
| `GraphHealthService`          | Health-check report generation, HTML export, stale edge cleanup, log output            |

**No `ExecutionMetadataService`** — `SatisfiesEdgeService` handles SATISFIES logic (async + structured concurrency + dedup). Timing, usage
tracking, stability → orchestrator calls `ProcedureRepository` directly via `KnowledgeServices.writeRepository()`. All repositories are
stateless and thread-safe.

#### Records & DTOs

| Record                       | Location       | Purpose                                                                    |
|------------------------------|----------------|----------------------------------------------------------------------------|
| `SatisfiesEdgeDto`           | `knowledge_graph/model/` | SATISFIES edge data transfer                                               |
| `FailureContext`             | `knowledge_graph/model/` | Failure context node data (uses `ErrorCategory`)                           |
| `TimingProfile`              | `knowledge_graph/model/` | Rolling-average timing data                                                |
| `ElementStability`           | `knowledge_graph/model/` | Stability metrics for a UI element                                         |
| `GraphHealthReport`          | `knowledge_graph/model/` | Structured health-check results (with per-category severity)               |
| `HealthCheckCategory`        | `knowledge_graph/model/` | Record: `String name`, `List<String> findings`, `Severity severity`        |
| `AtomicStepExecutionContext` | `knowledge_graph/model/` | Bundles execution-scoped params for `StepExecutionOrchestrator` call chain |
| `KnowledgeServices`          | `knowledge_graph/`       | Service facade for static orchestrator methods                             |

#### Functional Interfaces

| Interface           | Location       | Signature                                                                            |
|---------------------|----------------|--------------------------------------------------------------------------------------|
| `TimingRecorder`    | `knowledge_graph/model/` | `void record(UUID procedureId, long executionMs, long verificationDelayMs)`          |
| `StabilityRecorder` | `knowledge_graph/model/` | `void record(UUID elementId, boolean located, long locationTimeMs, String strategy)` |

### 3.2 Key Design Decisions

1. **No `FailureCategory` enum** — reuse `ErrorCategory`. If a mapping from `ErrorCategory` exists for dialog pre-selection, they're not
   independent.

2. **No `TimingContext` wrapper** — `AtomicStepExecutionContext` holds `Optional<TimingProfile>` and `TimingRecorder` directly. Procedure ID
   available in scope.

3. **`AtomicStepExecutionContext` bundles only leaf-propagated params** — `currentEffects` used only in `executeAtomicStepWithRetryLoop()` (
   edit/knowledge-collection flow), never passed deeper. Stays as standalone parameter.
    - Fields: `Optional<TimingProfile> timingProfile`, `TimingRecorder timingRecorder`, `List<String> failureHints`, `String elementId`,
      `String effectiveExpectedResults`
    - **Param reduction**: 14 existing → 12 (net -2 despite adding `timingProfile`, `timingRecorder`, `failureHints`)

4. **`KnowledgeServices` evolution** — Phase 2: 4 fields (`knowledgeService`, `ingestionService`, `satisfiesEdgeService`,
   `writeRepository`). Phase 7A: adds 5th field `failureContextService`.

5. **No semaphore backpressure for async SATISFIES** — virtual threads don't block OS threads. Connection saturation controlled at pool
   level.

6. **`ElementLocatorTools` two-constructor** — no-arg delegates to `(StabilityRecorder)` with no-op default. New constructor must also
   initialize existing dependencies (`UiElementRetriever`, `UiElementBoundingBoxAgent`, `BestUiElementMatchSelectionAgent`). Existing
   callers unchanged.

7. **`deleteDescendants(UUID)` made private** after adding transactional overload `deleteDescendants(UUID, TransactionContext)`. Prevents
   bypassing SATISFIES cleanup. `KnowledgeIngestionService.update()` (currently the only external caller) migrates to the transactional
   overload.

8. **No `KnowledgeRecoveryTools` class** — single `@Tool` method added to `KnowledgeElementTools` (package-private, in `tools/`).

9. **No `GraphHealthDashboardDialog`** — `logHealthReport()` suffices.

10. **Schema migrations co-located with phases** — each phase creates own constraints/indexes.

11. **Adaptive verification delay has a floor** — `Math.max(getTimingVerificationMinDelayMs(), avgVerificationDelayMs)`.

### 3.3 Data Flow

1. After `stateTracker.addEffects()` → `satisfiesEdgeService.persistSatisfiesEdgesAsync(atomicStep.id(), atomicStep.effects())` (virtual
   thread, non-blocking)
2. After successful match → `writeRepository.mergeUsesProcedure(testCase.name(), procedure.id())`
3. After action/verification → `execContext.timingRecorder().record(procedureId, executionMs, verificationDelayMs)` → delegates to
   `writeRepository.updateTimingProfile()`
4. Before action/verification → fetch `failureContextService.findFailureHints()` → include in `AtomicStepExecutionContext.failureHints` →
   injected into message builders by `StepExecutionOrchestrator` (no service dependency)
5. Failure paths → after retry exhaustion: supervised shows `FailureContextCaptureDialog` (timeout → `SUPERVISED_TIMEOUT`); unattended
   auto-captures with `mode: UNATTENDED`. Dedup via `MERGE` on `(procedureId, category, symptomNormalized)`
6. Element location → `StabilityRecorder` lambda → `writeRepository.updateElementStability()`
7. Procedure matching → `reRankByStateCompatibility()` applies ancestry affinity + stability penalty (elements with
   `stabilityScore < threshold` deprioritized)
8. Execution teardown → `cleanupStaleUsesProcedure()` in `finally` block
9. Procedure deletion → `KnowledgeIngestionService.update()` wraps descendant + SATISFIES edge deletion in single transaction, then calls
   `failureContextService.cleanupOrphanedFailureContexts()`

### 3.4 Transactional SATISFIES + Descendant Deletion

`deleteDescendants(UUID)` becomes private. New `deleteDescendants(UUID, TransactionContext)` runs within provided transaction.
`SatisfiesEdgeRepository.deleteSatisfiesEdges(UUID, TransactionContext)` likewise. `KnowledgeIngestionService.update()` wraps both in single
write transaction.

### 3.5 Async SATISFIES Edge Computation

`persistSatisfiesEdgesAsync()` fires a virtual thread. `StructuredTaskScope.ShutdownOnFailure` runs N vector queries in parallel (one per
effect embedding). Results deduplicated by max similarity per consumer procedure ID, filtered by `getSatisfiesSimilarityThreshold()`, batch
persisted.

### 3.6 Wiring Strategy

```
KnowledgeServiceFactory
  ├── ProcedureRepository()                — write instance for KnowledgeServices
  ├── SatisfiesEdgeRepository()
  ├── FailureContextRepository()           — Phase 7A
  ├── SatisfiesEdgeService(SatisfiesEdgeRepository, config)
  ├── FailureContextService(FailureContextRepository)  — Phase 7A
  ├── GraphHealthService(GraphHealthRepository)        — Phase 8
  ├── KnowledgeService(ProcedureRepository[read], EmbeddingService, DecompositionService, config)
  └── KnowledgeIngestionService(KnowledgeService, SatisfiesEdgeRepository)
      // Note: currently created via createKnowledgeIngestionService(KnowledgeService),
      // which extracts ProcedureRepository, EmbeddingService, DecompositionService
      // from KnowledgeService internally. SatisfiesEdgeRepository is the new dependency.
```

### 3.7 Tunable Parameters

All added to `UiTestAgentConfig`:

| Parameter                      | Config key                               | Default |
|--------------------------------|------------------------------------------|---------|
| EWMA α for timing              | `timing.ewma.alpha`                      | `0.2`   |
| EWMA α for stability           | `stability.ewma.alpha`                   | `0.3`   |
| Min verification delay         | `timing.verification.min.delay.ms`       | `500`   |
| SATISFIES similarity threshold | `satisfies.similarity.threshold`         | TBD     |
| Ancestry window size           | `ancestry.window.size`                   | `5`     |
| Stale SATISFIES edge days      | `satisfies.stale.days`                   | TBD     |
| Stability penalty threshold    | `stability.penalty.threshold`            | `0.5`   |
| Failure capture dialog timeout | `failure.capture.dialog.timeout.seconds` | `60`    |
| Health report output path      | `health.report.output.path`              | `reports/graph-health-report.html` |
| Health warning threshold       | `health.warning.threshold`               | `3`     |
| Health critical threshold      | `health.critical.threshold`              | `10`    |

### 3.8 Schema Migration

Existing `SchemaVersion` node + startup migration pattern. Each phase adds own constraints/indexes. Rollback scripts are separate `.cypher`
files for manual use.

---

## 4. Implementation Phases

### Phase 1: Foundation — Graph Model & Constants

- [ ] **[MODIFY]** `GraphRelationships.java` — add `REL_SATISFIES`, `REL_USES_PROCEDURE`, `REL_HAS_FAILURE_CONTEXT`,
  `LABEL_FAILURE_CONTEXT`, `LABEL_TEST_CASE`
- [ ] **[NEW]** `SatisfiesEdgeDto.java` (`knowledge_graph/model/`) — record with fields: `UUID producerId`, `UUID consumerId`,
  `double score`, `String effectPhrase`, `String precondPhrase`
- [ ] **[NEW]** `FailureContext.java` (`knowledge_graph/model/`) — record with fields: `UUID id`, `String symptom`, `ErrorCategory category`,
  `String resolution`, `int occurrences`, `Instant lastOccurred`, `Mode mode` (enum: `SUPERVISED`/`SUPERVISED_TIMEOUT`/`UNATTENDED`).
  Compact constructor validates `requireNonNull` on `symptom`, `category`, `resolution`, `mode`
- [ ] **[NEW]** `TimingProfile.java` (`knowledge_graph/model/`) — record: `long avgExecutionMs`, `long avgVerificationDelayMs`,
  `long maxVerificationDelayMs`, `Instant lastTimingUpdate`
- [ ] **[NEW]** `TimingRecorder.java` (`knowledge_graph/model/`) — `@FunctionalInterface`:
  `void record(UUID procedureId, long executionMs, long verificationDelayMs)`
- [ ] **[NEW]** `ElementStability.java` (`knowledge_graph/model/`) — record: `double stabilityScore`, `long avgLocationTimeMs`,
  `String locationStrategy`, `int failedLocationCount`, `Instant lastLocatedAt`
- [ ] **[NEW]** `StabilityRecorder.java` (`knowledge_graph/model/`) — `@FunctionalInterface`:
  `void record(UUID elementId, boolean located, long locationTimeMs, String strategy)`
- [ ] **[MODIFY]** `UiTestAgentConfig.java` — add getters for all tunable parameters (§3.7)

---

### Phase 2: SATISFIES Edges + `KnowledgeServices` Facade

Prerequisite for Phase 6 (ordering conflict detection).

- [ ] **[NEW]** `SatisfiesEdgeRepository.java` (`knowledge_graph/repository/`)
    - `persistSatisfiesEdges(List<SatisfiesEdgeDto>)` — batch `UNWIND` with `MERGE`/`ON CREATE SET`/`ON MATCH SET`
    - `deleteSatisfiesEdges(UUID procedureId, TransactionContext tx)` — transactional, for `KnowledgeIngestionService.update()`
    - `refreshSatisfiesEdge(UUID producerId, UUID consumerId)` — updates `lastVerifiedAt`
    - `findStaleSatisfiesEdges(int staleDays)` — edges not verified within `staleDays`
    - `findOrderingConflicts(List<String> orderedProcedureIds, Map<String, Integer> indexMap)` — callers handle UUID→String conversion
    - `hasSatisfiesEdges()` — fast existence check

- [ ] **[NEW]** `SatisfiesEdgeService.java` (`knowledge_graph/service/`)
    - `persistSatisfiesEdgesAsync(UUID executedProcedureId, List<PhraseEmbedding> effectEmbeddings)` — fires virtual thread, structured
      concurrency for N parallel vector queries, dedup by max score, threshold filter, batch persist
    - `hasSatisfiesEdges()` / `findOrderingConflicts(...)` — delegate to repository

- [ ] **[NEW]** `KnowledgeServices.java` (`knowledge_graph/`) — record with 4 fields: `knowledgeService`, `ingestionService`,
  `satisfiesEdgeService`, `writeRepository`

- [ ] **[MODIFY]** `ProcedureRepository.java` — add transactional `deleteDescendants(UUID, TransactionContext)`, make original
  `deleteDescendants(UUID)` private

- [ ] **[MODIFY]** `KnowledgeIngestionService.java` — add `SatisfiesEdgeRepository` dependency. In `update()`, wrap descendant + SATISFIES
  deletion in single transaction

- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java` — replace `KnowledgeService` param with `KnowledgeServices services`. Remove
  internal `ingestionService` creation (currently `createKnowledgeIngestionService(knowledgeService)` via static factory import). After
  `stateTracker.addEffects()`, call `persistSatisfiesEdgesAsync()`

- [ ] **[MODIFY]** `StepExecutionOrchestrator.java` — replace `KnowledgeService` + `KnowledgeIngestionService` params with
  `KnowledgeServices` in `executeAtomicStepWithRetryLoop()`

- [ ] **[MODIFY]** `KnowledgeServiceFactory.java` — add `createKnowledgeServices()`, factory methods for `SatisfiesEdgeRepository`,
  `SatisfiesEdgeService`

- [ ] **[MODIFY]** `UiTestAgent.java` — replace `createKnowledgeService()` + manual passing with `createKnowledgeServices()` +
  `KnowledgeServices`

- [ ] **Schema**: Index on `SATISFIES.lastVerifiedAt`
- [ ] **Rollback**: `phase2-rollback.cypher` — delete all SATISFIES edges, drop index

---

### Phase 3: Matching Enhancements — Ancestry Context + Element Stability

Merges ancestry affinity and stability penalty — both modify `KnowledgeService.reRankByStateCompatibility()`.

#### Ancestry Context

- [ ] **[MODIFY]** `ExecutionStateTracker.java` — add bounded `Deque<UUID> recentParentIds` (max `getAncestryWindowSize()`, default 5),
  `addRecentParent(UUID)`, `getRecentParentIds()` → `Set<UUID>`, clear on `reset()`
- [ ] **[MODIFY]** `ProcedureRepository.java` — add `findSharedParentCount(UUID candidateId, Set<UUID> recentParentIds)` using existing
  `CONTAINS` relationship
- [ ] **[MODIFY]** `KnowledgeService.java` — extend `findBestMatch()` with `Set<UUID> recentParentIds` param. In
  `reRankByStateCompatibility()`, add ancestry affinity boost
- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java` — after matching, call `stateTracker.addRecentParent()`. Pass
  `stateTracker.getRecentParentIds()` to `findBestMatch()`

#### Element Stability

- [ ] **[MODIFY]** `ProcedureRepository.java`
    - `updateElementStability(UUID, boolean located, long locationTimeMs, String strategy)` — EWMA with α=0.3, optimistic init at `1.0`,
      first failure → `0.70`
    - `getElementStability(UUID)` → `Optional<ElementStability>`
- [ ] **[MODIFY]** `ElementLocatorTools.java` — add `(StabilityRecorder)` constructor (no-arg delegates with no-op). Call
  `locationHistoryRecorder.record()` after each location attempt. Replace proactive sleep with try-first, wait-and-retry for unstable elements
- [ ] **[MODIFY]** `KnowledgeService.java` — in `reRankByStateCompatibility()`, add stability penalty:
  `stabilityScore < getStabilityPenaltyThreshold()` → deprioritize. No target element → no penalty
- [ ] **[MODIFY]** `KnowledgeServiceFactory.java` — wire `StabilityRecorder` lambda into `ElementLocatorTools`
- [ ] **Rollback**: `phase3-rollback.cypher` — remove stability properties from UiElement nodes

---

### Phase 4: Timing Profiles + `AtomicStepExecutionContext`

Phase 7B's hint injection builds on this phase's `AtomicStepExecutionContext`.

- [ ] **[NEW]** `AtomicStepExecutionContext.java` (`knowledge_graph/model/`) — record with 5 fields: `Optional<TimingProfile> timingProfile`,
  `TimingRecorder timingRecorder`, `List<String> failureHints`, `String elementId`, `String effectiveExpectedResults`

- [ ] **[MODIFY]** `ProcedureRepository.java`
    - `updateTimingProfile(UUID, long actualExecutionMs, long actualVerificationDelayMs)` — EWMA α=0.2, decaying `maxVerificationDelayMs` (
      0.95 factor when actual < max, replace when actual > max)
    - `getTimingProfile(UUID)` → `Optional<TimingProfile>`

- [ ] **[MODIFY]** `StepExecutionOrchestrator.java`
    - Replace `elementId` + `effectiveExpectedResults` params with `AtomicStepExecutionContext` in: `executeAtomicStepWithRetryLoop`,
      `executeAtomicStep`, `executeSingleTestStep`, `executeSinglePrecondition`
    - In `verifyTestStep()`, replace constant delay with adaptive: `Math.max(minDelay, timingProfile.avgVerificationDelayMs)`, fallback to
      `ACTION_VERIFICATION_DELAY_MILLIS`
    - After successful verification, record actual delay via `execContext.timingRecorder().record()`

- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java` — fetch `timingProfile`, create `TimingRecorder` as
  `writeRepository::updateTimingProfile`, construct `AtomicStepExecutionContext` (empty `failureHints` until Phase 7B)

- [ ] **Rollback**: `phase4-rollback.cypher` — remove timing properties from Procedure nodes

---

### Phase 5: Procedure Usage Tracking

Bookkeeping with zero dependencies on other phases.

- [ ] **[MODIFY]** `ProcedureRepository.java`
    - `mergeUsesProcedure(String testCaseName, UUID procedureId)` — `MERGE` TestCase + USES_PROCEDURE edge
    - `findTestCasesUsingProcedure(UUID)` → `List<String>`
    - `cleanupStaleUsesProcedure(String testCaseName, List<UUID> usedProcedureIds)` — deletes stale edges

- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java`
    - After successful match: `writeRepository.mergeUsesProcedure(...)`
    - Track used IDs in `Set<UUID>`, cleanup in `finally` block
    - In `triggerEditProcedureKnowledgeCollectionFlow()`: check usage, show `InformationalPopup` WARNING if non-empty

- [ ] **Schema**: Uniqueness constraint on `TestCase.name`
- [ ] **Rollback**: `phase5-rollback.cypher` — detach delete TestCase nodes, drop constraint

---

### Phase 6: Ordering Conflict Detection

Requires Phase 2. Supervised mode only.

- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java` — at start of `executeBasedOnKnowledge()`, supervised mode only:
    1. `hasSatisfiesEdges()` — skip if false
    2. Resolve procedure IDs for test steps; build `Map<String, Integer> indexMap`
    3. `findOrderingConflicts(orderedIds, indexMap)`
    4. If conflicts → `InformationalPopup` WARNING

---

### Phase 7A: Failure Recovery — Persistence Layer

- [ ] **[NEW]** `FailureContextRepository.java` (`knowledge_graph/repository/`)
    - `persistFailureContext(UUID procedureId, FailureContext)` — dedup via `MERGE` on `(procedureId, category, symptomNormalized)`
    - `findFailureContexts(UUID)` → `List<FailureContext>`
    - `deleteOrphanedFailureContexts()` — nodes with no `HAS_FAILURE_CONTEXT` incoming edge

- [ ] **[NEW]** `FailureContextService.java` (`knowledge_graph/service/`)
    - `captureFailureContext(UUID, FailureContext)` → delegates to repository
    - `findFailureHints(UUID)` → `List<String>` — filters `mode == SUPERVISED_TIMEOUT`, formats as `"[category] symptom → resolution"`
    - `cleanupOrphanedFailureContexts()` → delegates to repository

- [ ] **[MODIFY]** `KnowledgeServices.java` — add 5th field: `FailureContextService failureContextService`

- [ ] **[MODIFY]** `KnowledgeServiceFactory.java` — add factory methods for `FailureContextRepository`, `FailureContextService`. Update
  `createKnowledgeServices()`

- [ ] **[MODIFY]** `KnowledgeElementTools.java` (package-private, in `tools/`) — add `@Tool queryProcedureFailureHints(UUID)` delegating
  to `FailureContextService.findFailureHints()`

- [ ] **Schema**: Index on `FailureContext.id`
- [ ] **Rollback**: `phase7-rollback.cypher` — detach delete FailureContext nodes, drop index

---

### Phase 7B: Failure Recovery — UI & Integration

Requires Phase 7A + Phase 4.

- [ ] **[NEW]** `FailureContextCaptureDialog.java` (`user_dialogs/`) — Swing dialog for supervised mode after retry exhaustion. Category
  dropdown from `ErrorCategory` (pre-selected from failure). Symptom pre-filled from last error. Resolution free-text. Blocking with
  countdown timeout (`getFailureCaptureDialogTimeoutSeconds()`, default 60s). OK → `SUPERVISED`, Cancel → no persist, Timeout →
  `SUPERVISED_TIMEOUT` with empty resolution

- [ ] **[MODIFY]** `StepExecutionOrchestrator.java` — in `getTestStepActionUserMessage()` and `getPreconditionExecutionUserMessage()`: read
  `failureHints` from `AtomicStepExecutionContext`, if non-empty append "Known issues with this procedure:" section. No service dependencies

- [ ] **[MODIFY]** `KnowledgeBasedExecutionOrchestrator.java`
    - Before constructing context: fetch hints via `failureContextService.findFailureHints(procedure.id())`
    - After `TERMINATE_EXECUTION`: supervised → show `FailureContextCaptureDialog`; unattended → auto-create with `mode: UNATTENDED`

- [ ] **[MODIFY]** `KnowledgeIngestionService.java` — add `FailureContextService` dependency. In `update()`, after transactional deletion,
  call `cleanupOrphanedFailureContexts()`

---

### Phase 8: Knowledge Graph Health

- [ ] **[NEW]** `GraphHealthRepository.java` (`knowledge_graph/repository/`) — read-only queries: `findOrphanedUiElements()`,
  `findLeafProceduresWithoutElement()`, `findDeepHierarchies(int maxDepth)`, `findDisconnectedProcedures()`,
  `findProceduresWithMissingEffects()`, `findStaleSatisfiesEdges(int staleDays)`, `findOrphanedFailureContexts()`,
  `deleteStaleSatisfiesEdges(int staleDays)`

- [ ] **[NEW]** `HealthCheckCategory.java` (`knowledge_graph/model/`) — record: `String name`, `String description`,
  `List<String> findings`, `Severity severity`. `Severity` is a nested enum: `OK` (0 findings), `WARNING` (1–threshold), `CRITICAL`
  (above threshold). Severity is computed from finding count using category-specific thresholds

- [ ] **[NEW]** `GraphHealthReport.java` (`knowledge_graph/model/`) — record with `List<HealthCheckCategory> categories` +
  `Instant generatedAt` + `int totalFindings()` convenience method. Each category maps to one health-check query result. Categories:
  orphaned UI elements, leaf procedures without target, deep hierarchies, disconnected procedures, missing effects, stale SATISFIES edges,
  orphaned failure contexts

- [ ] **[NEW]** `GraphHealthHtmlReportGenerator.java` (`knowledge_graph/service/`) — stateless class, package-private. Single method:
  `String generateHtml(GraphHealthReport report)`. Builds a standalone HTML document using `String.formatted()` and
  `HtmlUtils.escapeHtml()` (no external templating dependency). Layout:
    - Header with report title + generation timestamp
    - Summary dashboard: one card per `HealthCheckCategory` with color-coded badge (green=OK, yellow=WARNING, red=CRITICAL) and finding
      count
    - Detail sections: one collapsible `<details>/<summary>` block per category listing all findings. Empty categories show "No issues
      found"
    - Inline CSS (no external stylesheets) — self-contained single-file output
    - Responsive layout using CSS grid for summary cards
    - Footer with generation metadata

- [ ] **[NEW]** `GraphHealthService.java` (`knowledge_graph/service/`) — `runFullHealthCheck()` → `GraphHealthReport`,
  `runStaleSatisfiesEdgeCleanup()`, `logHealthReport()` (INFO level), `generateHtmlReport(Path outputPath)` — calls
  `runFullHealthCheck()`, delegates to `GraphHealthHtmlReportGenerator.generateHtml()`, writes result to `outputPath` using
  `Files.writeString()`. Returns the `Path` for caller convenience. Default output location: `reports/graph-health-report.html`
  relative to working directory

- [ ] **[NEW]** `generate-health-report.bat` (project root `scripts/`) — Windows batch script. Invokes
  `mvn exec:java -pl ui_test_execution_agent -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli` with optional
  `--output <path>` argument. Validates `JAVA_HOME` and Neo4j connection properties (from `application.properties` or env vars)

- [ ] **[NEW]** `generate-health-report.sh` (project root `scripts/`) — Unix shell equivalent of the `.bat` script. Executable
  (`chmod +x`). Same invocation pattern

- [ ] **[NEW]** `GraphHealthReportCli.java` (`knowledge_graph/service/`) — package-private class with `main(String[])`. Parses
  `--output <path>` (defaults to `reports/graph-health-report.html`). Creates `Neo4jConnectionManager`, `GraphHealthRepository`,
  `GraphHealthService` via factory. Calls `generateHtmlReport()`, prints output path to stdout, exits. Handles connection errors
  gracefully with user-friendly messages

- [ ] **[MODIFY]** `KnowledgeServiceFactory.java` — add factory methods for `GraphHealthRepository`, `GraphHealthService`

---

### Phase 9: Testing

#### Unit Tests (JUnit 5 + AssertJ + Mockito)

| Test Class                           | Phase | Key Scenarios                                                                                                                              |
|--------------------------------------|-------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `SatisfiesEdgeServiceTest`           | 2     | Zero effects → no edges; below threshold → no edges; dedup; SATISFIES invalidation in same tx; async fires virtual thread                  |
| `ExecutionStateTrackerTest` (modify) | 3     | Bounded sliding window: add, evict, reset                                                                                                  |
| `MatchingEnhancementsTest`           | 3     | Ancestry affinity boost; stability penalty below/above threshold; no target → no penalty; equal scores, different stability → stable first |
| `TimingProfileTest`                  | 4     | Rolling average (first-ever with COALESCE, overflow guard); delay floor enforcement; context construction                                  |
| `UsageTrackingTest`                  | 5     | `mergeUsesProcedure` idempotency; stale cleanup; `findTestCasesUsingProcedure`                                                             |
| `FailureContextTest`                 | 7A    | Dedup (same symptom → incremented occurrences); hint formatting; `SUPERVISED_TIMEOUT` filtering; edge cases                                |
| `HintInjectionTest`                  | 7B    | Empty hints → no section; non-empty → formatted; null → graceful                                                                           |
| `FailureContextCaptureDialogTest`    | 7B    | Timeout → `SUPERVISED_TIMEOUT`; submitted → `SUPERVISED`; cancelled → no capture                                                           |
| `GraphHealthServiceTest`             | 8     | Health-check queries + report structure + HTML generation trigger                                                                           |
| `GraphHealthHtmlReportGeneratorTest` | 8     | Empty report → all-green summary; mixed severities → correct colors; HTML-escapes finding text; all categories rendered                     |

#### Integration Tests (Testcontainers + Neo4j)

| Test Class                      | Scope                                                                         |
|---------------------------------|-------------------------------------------------------------------------------|
| `SatisfiesEdgeIntegrationTest`  | Edge creation, stale detection, batch UNWIND, ordering conflicts              |
| `MatchingIntegrationTest`       | `findSharedParentCount` query + element stability persistence                 |
| `UsageAndTimingIntegrationTest` | TestCase/USES_PROCEDURE lifecycle, rolling average updates, concurrent timing |
| `FailureContextIntegrationTest` | Creation, dedup, orphan cleanup, transactional consistency                    |
| `GraphHealthIntegrationTest`    | Various graph states, health queries, report structure, HTML file output      |

---

### Phase 10: Documentation

- [ ] **[MODIFY]** `README.md` — document all new graph relationships/nodes, `KnowledgeServices` facade, `AtomicStepExecutionContext`,
  `SatisfiesEdgeService` async computation, `FailureContextService` (modes, timeout exclusion), `StabilityRecorder`/`TimingRecorder`
  callbacks, stability penalty in matching, `GraphHealthService` API (including HTML report generation and CLI usage),
  usage/stale-edge cleanup lifecycle, `scripts/generate-health-report.sh|.bat` usage instructions
- [ ] **[VERIFY]** Rollback scripts exist for Phases 2, 3, 4, 5, 7 (Phases 1, 6, 7B, 8 have no schema artifacts)

---

## 5. Verification Plan

### Automated Tests

```bash
# Per-phase
mvn test -pl ui_test_execution_agent -Dtest="SatisfiesEdgeServiceTest,SatisfiesEdgeIntegrationTest"                          # Phase 2
mvn test -pl ui_test_execution_agent -Dtest="ExecutionStateTrackerTest,MatchingEnhancementsTest,MatchingIntegrationTest"      # Phase 3
mvn test -pl ui_test_execution_agent -Dtest="TimingProfileTest,UsageAndTimingIntegrationTest"                                 # Phase 4
mvn test -pl ui_test_execution_agent -Dtest="UsageTrackingTest,UsageAndTimingIntegrationTest"                                 # Phase 5
mvn test -pl ui_test_execution_agent -Dtest="FailureContextTest,HintInjectionTest,FailureContextCaptureDialogTest,FailureContextIntegrationTest"  # Phase 7
mvn test -pl ui_test_execution_agent -Dtest="GraphHealthServiceTest,GraphHealthHtmlReportGeneratorTest,GraphHealthIntegrationTest"  # Phase 8

# Full regression
mvn test -pl ui_test_execution_agent -f pom.xml
```

### Compilation Gate

After each phase: `mvn compile -pl ui_test_execution_agent -f pom.xml`

### Manual Verification

Requires running Neo4j instance.

1. **SATISFIES edges**: Run test → inspect Neo4j for edges. Edit procedure → edges deleted. Async (no delay in logs)
2. **Ancestry context**: Shared-parent procedures ranked higher. Window boundary: 6th evicts 1st
3. **Element stability + matching**: Try-first strategy (no pre-sleep). Init at `1.0`, first failure → `0.70`. Low-stability deprioritized
4. **Timing profiles**: Second run uses adaptive delay (with floor). `maxVerificationDelayMs` decays after spike
5. **USES_PROCEDURE**: Run test → query edges. Fail mid-run → cleanup still happens (`finally`)
6. **Ordering conflicts**: Reorder steps violating SATISFIES → warning popup (supervised only). No warning on cold graph
7. **Failure recovery (supervised)**: Exhaust retries → dialog with pre-filled symptom. Timeout → `SUPERVISED_TIMEOUT`. Re-run → hints
   injected (excluding timeout)
8. **Failure recovery (unattended)**: Failure → `UNATTENDED` context. Repeat → `occurrences` increments
9. **Transactional consistency**: `update()` removes descendants + SATISFIES atomically
10. **Health service**: `logHealthReport()` → structured log. Stale cleanup works
10a. **HTML report**: `generateHtmlReport()` → valid HTML file. Open in browser → summary cards color-coded, detail sections
   collapsible, all categories present. Run `scripts/generate-health-report.sh` → report generated at default path
10b. **HTML escaping**: Inject `<script>` into a procedure name → verify it renders as escaped text in HTML report (no XSS)
11. **Schema migration**: Fresh DB → constraints/indexes created
12. **Rollback**: Apply scripts → schema reverts cleanly
13. **Orphan cleanup**: Delete procedure with FailureContexts → orphans cleaned up
