# Implementation Plan: Avaje Inject DI Integration

**Created**: 2026-03-06
**Updated**: 2026-03-06
**Status**: Revised v4 (all gaps from independent code review addressed)

## 1. Objective

Integrate Avaje Inject (v12.5-RC1) as the dependency injection framework across all 3 modules (`agent_core`, `ui_test_execution_agent`, `api_test_execution_agent`), replacing static factories, static config, and manual singleton management with compile-time DI-managed beans.

## 2. Technical Dependencies

Three avaje-inject artifacts must be added, all at version `12.5-RC1`, managed via `<dependencyManagement>` in the parent POM:

- **`avaje-inject`** — runtime library (compile scope in each module)
- **`avaje-inject-generator`** — compile-time annotation processor (provided/optional scope; no `annotationProcessorPath` config needed when `maven.compiler.proc=full` is set in parent POM properties)
- **`avaje-inject-test`** — test utilities (test scope in each module)

All three modules declare the runtime and processor artifacts. The `maven.compiler.proc=full` property in the parent POM properties section enables the processor automatically.

Verify that `ServicesResourceTransformer` is already present in the shade plugin config — no change needed there.

## 3. Architectural Design

### Design Patterns

- **Factory Method via `@Factory`/`@Bean`**: Replace static factory classes with DI factory beans that produce third-party objects (LangChain4j models, Neo4j driver, retrievers, embedding stores, singleton agents)
- **Strategy via `@Named`**: Config-driven selection of retriever implementations (`ChromaRetriever`, `QdrantRetriever`, `Neo4jRetriever`)
- **Singleton Scope**: All stateless services, config beans, shared resources, and stateless agents become `@Singleton`
- **Constructor Injection**: All dependencies declared via constructors — immutable, testable, explicit
- **Extracted mediator to break cycles**: `ProcedureKnowledgeCollectionService` is introduced to eliminate the circular dependency between `StepExecutionOrchestrator` and `KnowledgeExecutionOrchestrator` (see §3.4)

### Module Dependency Graph (DI perspective)

```
agent_core (@InjectModule provides core beans)
    ↑ requires
    ├── ui_test_execution_agent (@InjectModule requires agent_core beans)
    └── api_test_execution_agent (@InjectModule requires agent_core beans)
```

### Component Structure — agent_core

| Current Class | DI Conversion |
|---------------|---------------|
| `AgentConfig` (static) | `@Singleton` bean with instance methods. The ~40 `private static final ConfigProperty<T>` fields and the `static final Properties` field all become instance fields. Their static field initializers are moved into the constructor body in order: `Properties` is loaded first using the existing `loadConfigPropertiesFromFile()` logic (becomes a private instance method), then each `ConfigProperty` field is assigned in sequence using the `protected` instance helpers (`loadProperty()`, `getRequiredProperty()`, `loadPropertyAsInteger()`, `loadPropertyAsDouble()`, `getProperty()`), which are converted from `protected static` to `protected` instance methods. Subclasses (`UiTestAgentConfig`, `ApiTestAgentConfig`) call `super()` first, then initialise their own `ConfigProperty` instance fields in their constructors using the inherited instance helpers — no static field initializers remain in any subclass |
| `ModelFactory` (static) | `@Singleton` bean. Injects `AgentConfig`. `getModel()` becomes instance method |
| `TestCaseExtractor` (static) | `@Singleton` bean. Injects `ModelFactory`, `AgentConfig` |
| `AbstractServer` (abstract) | Becomes **concrete `@Singleton`** bean. Abstract factory methods (`createAgentExecutor()`, `createAgentCard()`, `getStartupLogMessage()`) are **removed**. Constructor injects `AgentExecutionResource` and `AgentConfig` directly. Each consuming module's concrete `Server extends AbstractServer` subclass is **eliminated** — replaced by a thin `Server` class with only a `main()` method that builds a `BeanScope` and retrieves this bean to call `start()` |
| `AgentExecutionResource` | `@Singleton` bean. Constructor already takes `AgentExecutor` + `AgentCard` — add annotation only. Implementations are provided by each consuming module's `@Factory` |
| `ChatModelEventListener` | `@Singleton` bean. **Injects `BudgetManager`** — replaces all direct static `BudgetManager.*` calls |
| `DefaultToolErrorHandler` | NOT a singleton — created per-agent with specific `RetryPolicy`/`RetryState`. Stays as `new` at call sites |
| `BudgetManager` | `@Singleton` bean. Static mutable fields (`toolCallUsage`, `tokenUsagePerModel`, `startTime`, budget limit fields) become instance fields. All static methods become instance methods |

**`@InjectModule` for `agent_core`:**
Declares `provides = [AgentConfig, BudgetManager, ModelFactory, ChatModelEventListener, TestCaseExtractor, AbstractServer, AgentExecutionResource]` and `requires = [AgentExecutor, AgentCard]`. The `requires` entries tell avaje that `AgentExecutionResource` (which depends on both interfaces) will have those dependencies satisfied by the consuming module at the consuming module's compile time — not at `agent_core` compile time. Without this, avaje's annotation processor would fail to wire `AbstractServer → AgentExecutionResource → AgentExecutor/AgentCard` within `agent_core` since neither interface implementation exists there.

### Component Structure — ui_test_execution_agent

| Current Class | DI Conversion |
|---------------|---------------|
| `UiTestAgentConfig` (extends `AgentConfig`, static) | `@Singleton` bean extending the refactored `AgentConfig`. Adds `getActionVerificationDelayMillis()` instance method (moves `ACTION_VERIFICATION_DELAY_MILLIS` value here from `UiTestAgent`) |
| `UiAbstractTools` (base class) | **Refactored** — the no-arg constructor that internally creates `UiStateCheckAgent` via static calls is **removed**. A single constructor taking `UiStateCheckAgent` becomes the only constructor. All subclasses (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`) pass the injected agent to `super(uiStateCheckAgent)` |
| `CommonTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` from `AgentFactory` — passes it to `super()` |
| `MouseTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` — passes it to `super()` |
| `KeyboardTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` — passes it to `super()` |
| `SpinnerTools` | Does **not** extend `UiAbstractTools`. No DI dependencies. Stays as `new SpinnerTools()` at call sites inside `AgentFactory` |
| `Server` (extends `AbstractServer`) | **Eliminated.** Reduced to a class with only `main()`: builds `BeanScope` with shutdown hook and calls `scope.get(AbstractServer.class).start()`. Abstract method overrides disappear |
| `AgentFactory` (static) | **`@Factory` bean** (not `@Singleton` — avaje only recognises `@Bean` producer methods inside `@Factory` classes). Injects **only `ModelFactory` and `UiTestAgentConfig`** — injecting tool singletons into the constructor would create a circular dependency: those tools depend on `UiStateCheckAgent`, which is a `@Bean` produced by this factory (see §6 decision 16). **Stateless agents promoted to `@Bean @Singleton`**: `getUiStateCheckAgent()` is a NEW method — implementation extracted verbatim from `UiAbstractTools.createUiStateCheckAgent()`, which is then deleted; `getKnowledgeSuggestionAgent()` becomes `@Bean @Singleton`. `getKnowledgeCollectionElementResolutionAgent()` becomes `@Bean @Singleton` and receives `ElementLocatorTools` and `UiElementDbTools` as **`@Bean` method parameters** — avaje injects these at bean-creation time via method parameter injection, which is distinct from constructor injection on the `@Factory` class and therefore does not trigger the circular dependency (see §6 decision 24); `SpinnerTools` remains `new SpinnerTools()` inside this method. **Non-`@Bean` factory methods** `getTestStepVerificationAgent()` and `getPreconditionVerificationAgent()` remain regular instance methods and are not promoted to `@Bean @Singleton`; all static `UiTestAgentConfig.*` calls in them are replaced with calls on the injected `uiTestAgentConfig` instance. **Per-task methods** `getUiTestStepActionAgent()` and `getPreconditionActionAgent()` remain regular instance methods; both currently create `new MouseTools()`, `new KeyboardTools()`, `new ElementLocatorTools()` alongside the existing `commonTools` parameter — after DI these are singletons and cannot be `new`'d; the expanded signature for both methods is `(CommonTools, MouseTools, KeyboardTools, ElementLocatorTools)`. Callers (`StepExecutionOrchestrator` and any other orchestrators that invoke these methods) inject all four singletons and pass them at each call site. All static `UiTestAgentConfig.*` and `ModelFactory.getModel()` import usages across all factory methods are replaced with calls on the injected instances |
| `KnowledgeServiceFactory` (static) | **Deleted**. `buildExistingStateContext()` moves to `KnowledgeService` as an instance method. Both `KnowledgeService` and `KnowledgeIngestionService` become `@Singleton` beans with constructor injection wired directly by avaje — no replacement `@Factory` class is required |
| `RetrieverFactory` (static) | **Replaced** by `RagFactory` (`@Factory` bean). A single `@Bean UiElementRetriever` method switches on `UiTestAgentConfig.getVectorDbProvider()` |
| `Neo4jConnectionManager` (static singleton) | **Replaced** by `Neo4jFactory` (`@Factory implements AutoCloseable`). Declares private `driver` and `databaseName` instance fields. Produces: (1) `@Bean @Singleton Driver` — connection pool config and error handling move here; the created driver is stored in the `driver` instance field before being returned; `verifyConnectivity()` called after creation; (2) `@Bean @Singleton EmbeddingStore<TextSegment>` — receives the injected `Driver` as a `@Bean` method parameter; transfers the complete `Neo4jEmbeddingStore` builder configuration from the deleted `ProcedureEmbeddingStoreFactory`: label, id/embedding/text property names, index name, `dimension=384` (from `EmbeddingService.EMBEDDING_DIMENSION`), `initializeSchema=true`, full-text index name, and metadata prefix; reads database name from the stored `databaseName` field. **No `@Bean Session` is produced** — `Session` is short-lived; callers open sessions via `driver.session(SessionConfig.forDatabase(databaseName))`. `@PostConstruct initSchema()` calls the refactored `SchemaMigrationManager.migrateOnStartup(driver, databaseName)` using the stored instance fields; replaces `Server.initKnowledgePersistence()`. `close()` closes the stored `driver` field; avaje auto-calls via `AutoCloseable` on scope shutdown |
| `ProcedureEmbeddingStoreFactory` (static singleton) | **Deleted**. Its `Neo4jEmbeddingStore` builder configuration — label, id/embedding/text property names, index name, `dimension=384`, `initializeSchema=true`, full-text index name, and metadata prefix — is transferred verbatim into `Neo4jFactory.@Bean EmbeddingStore<TextSegment>`. Confirms `EMBEDDING_DIMENSION = 384` (see §6 decision 20) |
| `SchemaMigrationManager` (static utility) | **Refactored**: `migrateOnStartup()` gains two parameters — `Driver driver` and `String databaseName`. The internal `Neo4jConnectionManager.getSession()` call is replaced with a session opened from the passed driver on the passed database name. The `Neo4jConnectionManager` import is removed. The class retains its package-private static utility structure and remains idempotent |
| `EmbeddingService` | `@Singleton` bean. Static `MODEL` field becomes an instance field. Adds `public static final int EMBEDDING_DIMENSION = 384` constant — value confirmed from the deleted `ProcedureEmbeddingStoreFactory` which already used this dimension for `BgeSmallEnV15EmbeddingModel` |
| `ProcedureRepository` | `@Singleton` bean. Injects `EmbeddingStore<TextSegment>` (produced by `Neo4jFactory`), `Driver` (produced by `Neo4jFactory`), and `UiTestAgentConfig` (for the Neo4j database name). Stores the database name from config as a field. All `Neo4jConnectionManager.getSession()` static import calls are replaced with `driver.session(SessionConfig.forDatabase(databaseName))`. The no-arg constructor (which called `ProcedureEmbeddingStoreFactory.getInstance()`) is removed |
| `DecompositionService` | `@Singleton` bean. Injects `ProcedureRepository` |
| `KnowledgeService` | `@Singleton` bean. Injects `ProcedureRepository`, `EmbeddingService`, `DecompositionService`. **`buildExistingStateContext()` added as an instance method** (moved from `KnowledgeServiceFactory`) |
| `KnowledgeIngestionService` | `@Singleton` bean. Constructor injection of `ProcedureRepository`, `EmbeddingService`, `DecompositionService`, and `Driver`. No factory wrapper needed — avaje wires all four dependencies directly from the DI graph. Static `Neo4jConnectionManager.getDriver()` import calls are replaced with the injected `Driver` instance |
| **`ProcedureKnowledgeCollectionService`** (NEW) | `@Singleton` bean. Extracts `triggerNewKnowledgeCollectionFlow()` and `triggerEditProcedureKnowledgeCollectionFlow()` from `KnowledgeExecutionOrchestrator`. Injects `AgentFactory` (to obtain `KnowledgeSuggestionAgent`). **`KnowledgeService` and `KnowledgeIngestionService` remain method parameters** — they are already passed from callers in the existing code; keeping them as parameters avoids unnecessary field injection and preserves the existing call contract. **Breaks the `StepExecutionOrchestrator` ↔ `KnowledgeExecutionOrchestrator` circular dependency** (see §3.4) |
| `ElementLocatorTools` | `@Singleton` bean. Injects `UiElementRetriever` (from `RagFactory`) and `UiStateCheckAgent` (from `AgentFactory @Bean`) — passes `UiStateCheckAgent` to `super()` |
| `UiElementDbTools` | `@Singleton` bean. Injects `UiElementRetriever`, `ModelFactory`, `UiTestAgentConfig`, and `UiStateCheckAgent` — pass `UiStateCheckAgent` to `super()`. The two internal agents (`UiElementExtendedDescriptionAgent`, `DbUiElementSelectionAgent`) are currently created via private methods using static `AiServices.builder()` calls — after DI, those static import usages are replaced by calls on the injected `ModelFactory` and `UiTestAgentConfig` instances. `AgentFactory` is NOT injected here: the plan previously said `AgentFactory` injects `UiElementDbTools` AND `UiElementDbTools` injects `AgentFactory`, which is a direct cycle |
| `VerificationTools` (static) | `@Singleton` bean. Instance methods instead of static. Injects **`BudgetManager`** (for `resetToolCallUsage()` calls) and **`AgentConfig`** (for `getVerificationRetryPolicy()` call) |
| `StepExecutionOrchestrator` (static) | `@Singleton` bean. Injects `VerificationTools`, `UiTestAgentConfig`, `AgentFactory`, `ProcedureKnowledgeCollectionService`, `BudgetManager` (for `resetToolCallUsage()` calls), `CommonTools`, `MouseTools`, `KeyboardTools`, and `ElementLocatorTools` — the last four are the tool singletons passed to the expanded factory methods `getUiTestStepActionAgent()` and `getPreconditionActionAgent()`. `ACTION_VERIFICATION_DELAY_MILLIS` constant reference is replaced by `uiTestAgentConfig.getActionVerificationDelayMillis()` in `verifyTestStep()` |
| `KnowledgeExecutionOrchestrator` (static) | `@Singleton` bean. Injects `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `StepExecutionOrchestrator`, `ProcedureKnowledgeCollectionService`, `CommonTools`. **`CommonTools` parameter removed from `executeWithKnowledge()`** — injected field used instead. `loadSuggestionsWithSpinner()` calls `knowledgeService.buildExistingStateContext()` instead of the old static import |
| `UiTestAgent` (static) | `@Singleton` bean. Injects `AgentFactory`, `KnowledgeService`, `StepExecutionOrchestrator`, `KnowledgeExecutionOrchestrator`, `UiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`, `CommonTools`. **Does not inject `KnowledgeIngestionService`** — it is only used internally by `KnowledgeExecutionOrchestrator`, which already has it injected |
| `UiAgentExecutor` | `@Singleton` bean. Injects `UiTestAgent` |
| `AgentCard` (UI) | `@Bean @Singleton` produced by new `UiAgentBeanFactory` (`@Factory`) using `AgentCardProducer` |
| Retriever implementations | Constructed in `RagFactory @Bean` method — not annotated directly |

**`@InjectModule` for `ui_test_execution_agent`:**
Declares `requires` listing: `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`.

### Component Structure — api_test_execution_agent

| Current Class | DI Conversion |
|---------------|---------------|
| `ApiTestAgentConfig` (extends `AgentConfig`, static) | `@Singleton` bean extending the refactored `AgentConfig` |
| `Server` (extends `AbstractServer`) | **Eliminated** — same thin `main()`-only pattern as UI module |
| `ApiTestAgent` (static) | `@Singleton` bean. Injects `ModelFactory`, `ApiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`. Per-task tools (`ApiRequestTools`, `ApiAssertionTools`, `TestContextDataTools`) remain `new` inside methods |
| `ApiAgentExecutor` | `@Singleton` bean. Injects `ApiTestAgent` |
| `AgentCard` (API) | `@Bean @Singleton` produced by new `ApiAgentBeanFactory` (`@Factory`) using `AgentCardProducer` |

**`@InjectModule` for `api_test_execution_agent`:**
Declares `requires` listing: `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`.

### 3.4 Circular Dependency Resolution

`StepExecutionOrchestrator.promptUserAndDispatch()` calls `KnowledgeExecutionOrchestrator.triggerEditProcedureKnowledgeCollectionFlow()`, while `KnowledgeExecutionOrchestrator.executeWithKnowledge()` calls `StepExecutionOrchestrator.executeAtomicStepWithRetryLoop()`. Direct mutual injection would create a compile-time cycle that avaje rejects.

**Resolution:** Extract both knowledge collection methods (`triggerNewKnowledgeCollectionFlow`, `triggerEditProcedureKnowledgeCollectionFlow`) from `KnowledgeExecutionOrchestrator` into a new `ProcedureKnowledgeCollectionService` `@Singleton`. Both orchestrators inject `ProcedureKnowledgeCollectionService`. The dependency graph becomes:

```
StepExecutionOrchestrator  →  ProcedureKnowledgeCollectionService  ←  KnowledgeExecutionOrchestrator
StepExecutionOrchestrator  ←  KnowledgeExecutionOrchestrator   (one direction only — no cycle)
```

`ProcedureKnowledgeCollectionService` injects only `AgentFactory` (to obtain `KnowledgeSuggestionAgent` for `loadSuggestionsWithSpinner()`). `KnowledgeService` and `KnowledgeIngestionService` remain **method parameters** on both knowledge collection methods, preserving the existing call contract and avoiding unnecessary field injection.

### What Stays Manual (NOT DI-managed)

- **LangChain4j `AiServices` proxies with per-task state** — agents receiving `CommonTools` as a parameter stay as per-call factory method returns inside `AgentFactory`
- **`ApiRequestTools`, `ApiAssertionTools`, `TestContextDataTools`** — depend on per-task `ApiContext`/`TestExecutionContext`
- **`DefaultToolErrorHandler` / `UiToolErrorHandler`** — per-agent with specific `RetryPolicy`/`RetryState`
- **`RetryState`** — per-execution mutable state
- **`TestExecutionContext` / `UiTestExecutionContext`** — per-task mutable context
- **`ScreenRecorder`, `LogCapture`** — per-task lifecycle
- **`SpinnerTools`** — no `UiAbstractTools` dependency; stays as `new` inside `AgentFactory` factory methods
- **`ExecutionStateTracker`, `PreconditionResolver`** — per-invocation objects created inside `executeWithKnowledge()`

### Data Flow

1. `Server.main()` creates `BeanScope` with a shutdown hook
2. `BeanScope` auto-discovers all `@Singleton`, `@Factory` beans from both `agent_core` and the consuming module via generated `AvajeModule` service loader entries
3. `Neo4jFactory.driver()` initialises Neo4j `Driver`; `@PostConstruct` on `Neo4jFactory` triggers `SchemaMigrationManager.migrateOnStartup()`
4. Server retrieves `AbstractServer` bean → calls `start()` → Javalin binds routes
5. On task request: `AgentExecutionResource` → `UiAgentExecutor`/`ApiAgentExecutor` → `UiTestAgent`/`ApiTestAgent`
6. Agent bean uses injected `AgentFactory` to create per-task agent proxies — tool singletons required by each factory method are injected into the calling orchestrator and passed as arguments
7. On shutdown: `BeanScope.close()` → avaje calls `close()` on `Neo4jFactory` → `Driver` closed

## 4. Implementation Phases

### Phase 1: Maven Setup & Core Config Bean

- [ ] Add `avaje-inject` (runtime), `avaje-inject-generator` (provided/optional), and `avaje-inject-test` (test) — all at version `12.5-RC1` — to parent POM `<dependencyManagement>`
- [ ] Add `maven.compiler.proc=full` to parent POM `<properties>`
- [ ] Add runtime and processor dependencies to `agent_core/pom.xml`
- [ ] Add runtime and processor dependencies to `ui_test_execution_agent/pom.xml`
- [ ] Add runtime and processor dependencies to `api_test_execution_agent/pom.xml`
- [ ] Add `avaje-inject-test` (test scope) to all 3 module POMs
- [ ] Verify `ServicesResourceTransformer` already present in shade plugin config (it is — no change needed)
- [ ] Verify compilation succeeds with `mvn compile`

### Phase 2: Convert `agent_core` to DI

- [ ] Convert `AgentConfig` from static to `@Singleton`: move all ~40 `private static final ConfigProperty<T>` fields and the `static final Properties` field to instance fields; move their initializer expressions into the constructor body (load `Properties` first, then assign each `ConfigProperty` in order using the helper methods); convert the `protected static` helpers (`loadProperty()`, `getRequiredProperty()`, `loadPropertyAsInteger()`, `loadPropertyAsDouble()`, `getProperty()`) from static to `protected` instance methods; convert all `public static` getters to `public` instance methods. In `UiTestAgentConfig` and `ApiTestAgentConfig`: add constructors that call `super()` first, then initialise their own `ConfigProperty` instance fields using the inherited instance helpers — no static field initializers remain in any class in the hierarchy
- [ ] Convert `BudgetManager` from static to `@Singleton` — static fields become instance fields; all static methods become instance methods
- [ ] Convert `ModelFactory` from static to `@Singleton`. Inject `AgentConfig`. `getModel()` becomes instance method
- [ ] Convert `ChatModelEventListener` to `@Singleton`. Inject `BudgetManager` — replace all `BudgetManager.staticMethod()` calls with the injected instance
- [ ] Convert `TestCaseExtractor` from static to `@Singleton`. Inject `ModelFactory`, `AgentConfig`
- [ ] Refactor `AbstractServer` to **concrete `@Singleton`**: remove abstract declaration and all three abstract methods. Constructor injects `AgentExecutionResource` and `AgentConfig`. `start()` uses the injected resource directly
- [ ] Convert `AgentExecutionResource` to `@Singleton` — add annotation; constructor signature unchanged
- [ ] Add `@InjectModule` to `agent_core` with `provides = [AgentConfig, BudgetManager, ModelFactory, ChatModelEventListener, TestCaseExtractor, AbstractServer, AgentExecutionResource]` and **`requires = [AgentExecutor, AgentCard]`** so avaje defers wiring of `AgentExecutionResource`'s `AgentExecutor`/`AgentCard` dependencies to the consuming module's compile-time scope. Place the annotation on a `package-info.java` file in the module's root package (e.g., `package org.tarik.ta.core;`); create the file if it does not exist
- [ ] Update all classes in `agent_core` using static imports from `AgentConfig` to inject `AgentConfig` instead
- [ ] Verify `mvn compile -pl agent_core` succeeds

### Phase 3: Convert `ui_test_execution_agent` to DI

- [ ] **Fix existing compilation errors in modified files first** — `KnowledgeExecutionOrchestrator` has a stale call to `getUiTestStepActionAgent` with a `RetryState` second argument (the factory method only accepts `CommonTools`); `StepExecutionOrchestrator` has a call to `executeSinglePrecondition` with an extra `expectedResults` argument that is not in the method signature; `handleMissingPreconditionsKnowledgeCollection` call arguments don't match the method signature — fix all before proceeding
- [ ] Convert `UiTestAgentConfig` from static to `@Singleton` extending the refactored `AgentConfig`. Add `getActionVerificationDelayMillis()` instance method that returns the value previously held as `ACTION_VERIFICATION_DELAY_MILLIS` static constant in `UiTestAgent`
- [ ] **Refactor `UiAbstractTools`**: remove the no-arg constructor and its internal static `createUiStateCheckAgent()` method. Replace with a single constructor that accepts `UiStateCheckAgent` and assigns it to the field. All subclasses will pass the injected agent to `super()`
- [ ] Convert `MouseTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`
- [ ] Convert `KeyboardTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`
- [ ] Convert `CommonTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`
- [ ] Refactor `SchemaMigrationManager`: add `Driver driver` and `String databaseName` parameters to `migrateOnStartup()`. Replace the `Neo4jConnectionManager.getSession()` call inside the method with a session opened from the passed driver on the passed database name. Remove the `Neo4jConnectionManager` import. The class remains a package-private static utility
- [ ] Create `Neo4jFactory` (`@Factory implements AutoCloseable`):
  - Declare private `driver` and `databaseName` instance fields; populate `databaseName` from the injected `UiTestAgentConfig` (can be done in the constructor)
  - `@Bean @Singleton Driver driver()` — replaces `Neo4jConnectionManager.getDriver()`; connection pool config and error handling move here; assigns the created driver to the `driver` instance field before returning it; `verifyConnectivity()` called after creation
  - `@Bean @Singleton EmbeddingStore<TextSegment> embeddingStore(Driver driver)` — receives injected `Driver` as a `@Bean` method parameter; transfers the complete `Neo4jEmbeddingStore` builder configuration from the deleted `ProcedureEmbeddingStoreFactory`: label, id/embedding/text property names, index name, `dimension=384` (from `EmbeddingService.EMBEDDING_DIMENSION`), `initializeSchema=true`, full-text index name, and metadata prefix; uses the stored `databaseName` field
  - `@PostConstruct void initSchema()` — calls the refactored `SchemaMigrationManager.migrateOnStartup(driver, databaseName)` using the stored instance fields; replaces the logic from `Server.initKnowledgePersistence()`
  - `void close()` — closes the stored `driver` field; avaje auto-calls via `AutoCloseable` on scope shutdown
  - **No `@Bean Session` method** — sessions are short-lived; callers call `driver.session(SessionConfig.forDatabase(databaseName))` directly
- [ ] Delete `ProcedureEmbeddingStoreFactory` — its builder configuration has been moved into `Neo4jFactory.embeddingStore()`
- [ ] Create `RagFactory` (`@Factory`): single `@Bean UiElementRetriever retriever()` switching on `UiTestAgentConfig.getVectorDbProvider()`. Replaces `RetrieverFactory`
- [ ] Convert `EmbeddingService` to `@Singleton` — static `MODEL` field becomes an instance field. Add `public static final int EMBEDDING_DIMENSION = 384` constant (value confirmed from the deleted `ProcedureEmbeddingStoreFactory`; referenced by `Neo4jFactory.embeddingStore()`)
- [ ] Convert `ProcedureRepository` to `@Singleton`. Inject `EmbeddingStore<TextSegment>`, `Driver`, and `UiTestAgentConfig` via constructor. Store the database name from `UiTestAgentConfig` in a field. Replace all `Neo4jConnectionManager.getSession()` static import calls with `driver.session(SessionConfig.forDatabase(databaseName))`. Remove the no-arg constructor (which called `ProcedureEmbeddingStoreFactory.getInstance()`)
- [ ] Convert `DecompositionService` to `@Singleton`. Inject `ProcedureRepository`
- [ ] Convert `KnowledgeService` to `@Singleton`. Inject `ProcedureRepository`, `EmbeddingService`, `DecompositionService`. Add `buildExistingStateContext()` as an instance method (moved from `KnowledgeServiceFactory`)
- [ ] Convert `KnowledgeIngestionService` to `@Singleton`. Add `Driver` as a fourth constructor parameter alongside the existing `ProcedureRepository`, `EmbeddingService`, `DecompositionService`. Replace the `Neo4jConnectionManager.getDriver()` static import call with the injected `Driver` instance. No `KnowledgeFactory` wrapper is needed — avaje wires all four constructor dependencies directly. `KnowledgeServiceFactory` is deleted as part of Phase 6 cleanup
- [ ] Convert `AgentFactory` to **`@Factory`**. Inject only `ModelFactory` and `UiTestAgentConfig` in constructor — tool singletons are not constructor-injected (doing so would create a circular dependency via `UiStateCheckAgent`). Add new `@Bean @Singleton getUiStateCheckAgent()` method by extracting the creation logic from `UiAbstractTools.createUiStateCheckAgent()` — delete that static method from `UiAbstractTools` as part of this step. Annotate `getKnowledgeSuggestionAgent()` as `@Bean @Singleton`. Annotate `getKnowledgeCollectionElementResolutionAgent()` as `@Bean @Singleton` and add `ElementLocatorTools` and `UiElementDbTools` as `@Bean` method parameters — avaje injects these at bean-creation time via method parameter injection, which does not trigger the circular dependency (see §6 decision 24); `SpinnerTools` remains `new SpinnerTools()` inside this method. Convert non-`@Bean` methods `getTestStepVerificationAgent()` and `getPreconditionVerificationAgent()` from static to instance methods only — replace all static `UiTestAgentConfig.*` calls with calls on the injected `uiTestAgentConfig` instance; do not promote them to `@Bean @Singleton`. Expand per-task method signatures: `getUiTestStepActionAgent()` currently creates `new MouseTools()`, `new KeyboardTools()`, `new ElementLocatorTools()` alongside `commonTools` — after DI these are singletons; expand the signature to `(CommonTools, MouseTools, KeyboardTools, ElementLocatorTools)`. Apply the same expansion to `getPreconditionActionAgent()`. Update all callers to inject all four tool singletons and pass them at each call site. Replace all remaining static `UiTestAgentConfig.*` and `ModelFactory.getModel()` import usages across all factory methods with calls on the injected instances
- [ ] Convert `ElementLocatorTools` to `@Singleton`. Inject `UiElementRetriever` and `UiStateCheckAgent` — pass `UiStateCheckAgent` to `super()`
- [ ] Convert `UiElementDbTools` to `@Singleton`. Inject `UiElementRetriever`, `ModelFactory`, `UiTestAgentConfig`, and `UiStateCheckAgent` — pass `UiStateCheckAgent` to `super()`. Replace static `AiServices.builder()` call usages in `createUiElementDescriptionMatcherAgent()` and `createDbElementSelectionAgent()` with calls on the injected `ModelFactory` and `UiTestAgentConfig` instances. Do NOT inject `AgentFactory` — doing so alongside `AgentFactory` injecting `UiElementDbTools` is a direct circular dependency
- [ ] Convert `VerificationTools` from static to `@Singleton` with instance methods. Inject **`BudgetManager`** (for `resetToolCallUsage()`) and **`AgentConfig`** (for `getVerificationRetryPolicy()`)
- [ ] **Create `ProcedureKnowledgeCollectionService`** (`@Singleton`): move `triggerNewKnowledgeCollectionFlow()` and `triggerEditProcedureKnowledgeCollectionFlow()` out of `KnowledgeExecutionOrchestrator`. Inject `AgentFactory`. Keep `KnowledgeService` and `KnowledgeIngestionService` as method parameters on both methods — do not inject them. Update all callers (`StepExecutionOrchestrator.promptUserAndDispatch()` and `KnowledgeExecutionOrchestrator`) to inject and use this bean
- [ ] Convert `StepExecutionOrchestrator` from static to `@Singleton`. Inject `VerificationTools`, `UiTestAgentConfig`, `AgentFactory`, `ProcedureKnowledgeCollectionService`, `BudgetManager`, `CommonTools`, `MouseTools`, `KeyboardTools`, and `ElementLocatorTools` — the last four are the tool singletons passed to the expanded factory methods `getUiTestStepActionAgent()` and `getPreconditionActionAgent()`. Replace `resetToolCallUsage()` static import calls with the injected `BudgetManager` instance. Remove the `ACTION_VERIFICATION_DELAY_MILLIS` constant — replace its use in `verifyTestStep()` with `uiTestAgentConfig.getActionVerificationDelayMillis()`
- [ ] Convert `KnowledgeExecutionOrchestrator` from static to `@Singleton`. Inject `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `StepExecutionOrchestrator`, `ProcedureKnowledgeCollectionService`, `CommonTools`. Remove `CommonTools` parameter from `executeWithKnowledge()` — use injected field. Update `loadSuggestionsWithSpinner()` to call `knowledgeService.buildExistingStateContext()` instead of the old static import
- [ ] Convert `UiTestAgent` from static to `@Singleton`. Inject `AgentFactory`, `KnowledgeService`, `StepExecutionOrchestrator`, `KnowledgeExecutionOrchestrator`, `UiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`, `CommonTools`. Remove the `ACTION_VERIFICATION_DELAY_MILLIS` static constant (value moved to `UiTestAgentConfig`). **Do not inject `KnowledgeIngestionService`** — it is only used internally by `KnowledgeExecutionOrchestrator`
- [ ] Convert `UiAgentExecutor` to `@Singleton`. Inject `UiTestAgent`
- [ ] Create `UiAgentBeanFactory` (`@Factory`): `@Bean @Singleton AgentCard agentCard()` using `AgentCardProducer`
- [ ] Refactor UI `Server`: remove `extends AbstractServer` and all method overrides. Change `main()` signature to `public static void main(String[] args)` (was package-private no-arg — not a valid JVM entry point). The method builds a `BeanScope` using its builder with the shutdown hook enabled, retrieves the `AbstractServer` bean from scope, and calls `start()`. The shutdown hook automatically triggers `BeanScope.close()` on JVM exit, which invokes `Neo4jFactory.close()` via `AutoCloseable` — the explicit `Neo4jConnectionManager` shutdown hook previously in `initKnowledgePersistence()` is no longer needed
- [ ] Add `@InjectModule` marker: `requires` listing `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`. Place the annotation on a `package-info.java` in the module's root package (e.g., `package org.tarik.ta;`); create the file if it does not exist
- [ ] Update all static import usages throughout the module
- [ ] Verify `mvn compile -pl ui_test_execution_agent` succeeds

### Phase 4: Convert `api_test_execution_agent` to DI

- [ ] Convert `ApiTestAgentConfig` from static to `@Singleton` extending the refactored `AgentConfig`
- [ ] Convert `ApiTestAgent` from static to `@Singleton`. Inject `ModelFactory`, `ApiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`. Per-task tools remain `new` inside methods
- [ ] Convert `ApiAgentExecutor` to `@Singleton`. Inject `ApiTestAgent`
- [ ] Create `ApiAgentBeanFactory` (`@Factory`): `@Bean @Singleton AgentCard agentCard()` using `AgentCardProducer`
- [ ] Refactor API `Server`: remove `extends AbstractServer` and all method overrides. Change `main()` signature to `public static void main(String[] args)` (was package-private no-arg). The method builds a `BeanScope` using its builder with the shutdown hook enabled, retrieves the `AbstractServer` bean from scope, and calls `start()`
- [ ] Add `@InjectModule` marker: `requires` listing `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`. Place the annotation on a `package-info.java` in the module's root package (e.g., `package org.tarik.ta.api;`); create the file if it does not exist
- [ ] Update all static import usages
- [ ] Verify `mvn compile -pl api_test_execution_agent` succeeds

### Phase 5: Full Compilation & Test Fix

- [ ] Run `mvn compile` across all modules — fix any wiring errors
- [ ] Run `mvn test` across all modules — fix broken tests
- [ ] Update existing unit tests: replace static method mocking with constructor injection of mocks
- [ ] Where feasible, use `@InjectTest` with `@Mock`/`@Spy` for integration-style tests
- [ ] Verify all tests pass

### Phase 6: Cleanup & Documentation

- [ ] Delete now-unused static factory classes: `RetrieverFactory`, `KnowledgeServiceFactory`, `Neo4jConnectionManager`, `ProcedureEmbeddingStoreFactory`
- [ ] Delete the old `AbstractServer`-extending `Server` class bodies from UI and API modules (replaced by thin `main()`-only classes)
- [ ] Remove redundant static imports across all files
- [ ] Update `README.md` with DI framework info and setup instructions
- [ ] Verify final `mvn clean compile test` passes

## 5. Security Checklist

- [x] No hardcoded secrets — config still loaded from properties/env vars
- [x] No new external API exposure — DI is internal wiring only
- [x] No logging changes — existing patterns preserved
- [x] Dependency is compile-time annotation processor — no runtime reflection attack surface

## 6. Key Technical Decisions

1. **`AgentConfig` stays as a class hierarchy** — `UiTestAgentConfig extends AgentConfig` preserved, converted from static to instance. Minimises refactoring while gaining injectability.

2. **Per-task objects stay manual** — `AiServices` proxies receiving `CommonTools` as a runtime parameter, tool instances with task-specific context, retry state, and execution context are NOT DI-managed. Created by DI-managed factory beans.

3. **`BeanScope` owned by `main()` only** — each module's `Server.main()` creates one `BeanScope` for the application lifecycle. `shutdownHook(true)` ensures cleanup.

4. **`@InjectModule` for cross-module wiring** — `provides`/`requires` attributes declare cross-module bean dependencies; avaje validates wiring at compile time.

5. **`AbstractServer` becomes concrete `@Singleton`** — removes the template-method abstract pattern. `AgentExecutor` and `AgentCard` implementations are provided by each consuming module's `@Factory` beans and resolved by avaje at wire time through interface binding. `agent_core`'s `@InjectModule` must declare `requires = [AgentExecutor, AgentCard]` so avaje does not attempt to wire `AgentExecutionResource`'s interface dependencies within `agent_core` itself.

6. **Circular dependency resolved by extracting `ProcedureKnowledgeCollectionService`** — rather than using `Provider<T>` (which defers detection to runtime), extracting the shared knowledge collection methods into a third class produces a clean acyclic object graph. `KnowledgeService` and `KnowledgeIngestionService` remain method parameters on the knowledge collection methods — they are already passed by callers and injecting them would add no value.

7. **Stateless agents promoted to `@Bean @Singleton`** — agents that need no per-task parameters (`UiStateCheckAgent`, `KnowledgeSuggestionAgent`, `KnowledgeCollectionElementResolutionAgent`) become `@Singleton` beans produced by `AgentFactory` (`@Factory`), enabling injection into `@Singleton` tool classes. Per-task agent methods have their signatures expanded to accept the tool singletons required for their `AiServices` tool list as explicit parameters; callers inject those singletons and pass them at call time.

8. **`AgentFactory` injects only `ModelFactory` and `UiTestAgentConfig`** — injecting tool singletons (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`) into `AgentFactory`'s constructor would create a circular dependency: those tool classes inject `UiStateCheckAgent`, which is a `@Bean` produced by `AgentFactory` itself. To break the cycle, tool singletons are not constructor-injected into `AgentFactory`; instead, per-task factory methods that need them receive them as method parameters from their callers. Avaje only recognises `@Bean` producer methods inside a `@Factory` class; the `@Factory` class instance is itself a managed singleton injectable wherever `AgentFactory` is needed.

9. **`buildExistingStateContext()` moves to `KnowledgeService`** — the method only consumes `KnowledgeService` data, so it belongs there as an instance method. Eliminates the last meaningful method from `KnowledgeServiceFactory`.

10. **`EmbeddingStore<TextSegment>` produced by `Neo4jFactory`** — keeps Neo4j infrastructure together. The store bean depends on the `Driver` bean via avaje's `@Bean` parameter injection. No `Session` bean is produced — sessions are short-lived resources that callers open and close per operation via `driver.session(DatabaseConfig)`.

11. **`UiAbstractTools` refactored to constructor injection** — the no-arg constructor that internally creates `UiStateCheckAgent` via static calls is removed. All five subclasses (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`) become DI-managed singletons and pass their injected `UiStateCheckAgent` to `super()`. `SpinnerTools` does not extend `UiAbstractTools` and stays as `new` at call sites.

12. **`VerificationTools` injects `BudgetManager` and `AgentConfig`** — it calls `resetToolCallUsage()` and `getVerificationRetryPolicy()`, both of which are currently static imports that become instance method calls after DI.

13. **`StepExecutionOrchestrator` injects `BudgetManager`** — it calls `resetToolCallUsage()` three times (after action and verification agent calls). Missing from the initial plan.

14. **`ACTION_VERIFICATION_DELAY_MILLIS` moves to `UiTestAgentConfig`** — exposed as `getActionVerificationDelayMillis()` instance method; both `UiTestAgent` (which previously held the constant) and `StepExecutionOrchestrator` (which copied it via a static reference) are converted to use the injected config.

15. **`UiTestAgent` does not inject `KnowledgeIngestionService`** — it is only used internally by `KnowledgeExecutionOrchestrator`, which already has it injected. Adding it to `UiTestAgent` would be unnecessary coupling.

16. **`AgentFactory` ↔ tool singleton circular dependency resolved by parameter passing** — `AgentFactory` produces `UiStateCheckAgent` as a `@Bean @Singleton`; all five tool subclasses inject `UiStateCheckAgent`. If `AgentFactory` also injected those tool classes in its constructor, avaje could not satisfy the dependency graph at compile time — to create `AgentFactory` it would need `CommonTools`, but `CommonTools` needs `UiStateCheckAgent`, which requires `AgentFactory` to already exist. The resolution: `AgentFactory` injects only `ModelFactory` and `UiTestAgentConfig`; per-task factory methods that need tool singletons for their `AiServices` tool list declare them as explicit method parameters. Callers (e.g., `StepExecutionOrchestrator`, `KnowledgeExecutionOrchestrator`) inject the required tool singletons and pass them at each call site.

17. **`UiElementDbTools` does not inject `AgentFactory`** — `UiElementDbTools` creates its internal agents (`UiElementExtendedDescriptionAgent`, `DbUiElementSelectionAgent`) directly via `AiServices.builder()` calls using static model and config imports. After DI, those static usages are replaced by calls on injected `ModelFactory` and `UiTestAgentConfig` instances. Injecting `AgentFactory` is avoided because `AgentFactory` injects `UiElementDbTools`, producing a direct `A → B → A` cycle that avaje rejects at compile time.

18. **`AgentConfig` static field initializers must migrate into the constructor body** — Java instance fields cannot invoke instance methods in their initializers. All ~40 `ConfigProperty` assignments and the `Properties` loading must appear as sequential statements in the `AgentConfig` constructor. The `protected static` helper methods are converted to `protected` instance methods so that subclass constructors can call them after `super()` returns without triggering static-context issues.

19. **`Server.main()` signature must be `public static void main(String[] args)`** — the current package-private no-arg `main()` is not a valid JVM entry point. The `BeanScope` is built via its builder with the shutdown hook enabled, which replaces all explicit resource-cleanup hooks previously in `Server.initKnowledgePersistence()` — `Neo4jFactory.close()` is called automatically through `AutoCloseable` on scope shutdown.

20. **`EmbeddingService.EMBEDDING_DIMENSION = 384` is confirmed** — the value was already established in `ProcedureEmbeddingStoreFactory` (now deleted), which configured `Neo4jEmbeddingStore` with `dimension=384` for `BgeSmallEnV15EmbeddingModel`. Set the constant to `384`.

21. **`Neo4jFactory` stores `Driver` and `databaseName` as instance fields** — the `@Bean Driver driver()` method assigns the constructed driver to a private instance field before returning it; the database name from `UiTestAgentConfig` is stored in a `databaseName` field. Both fields are then accessible to `@PostConstruct initSchema()` and `close()` without avaje needing to inject the driver back into the same `@Factory` class as a constructor parameter.

22. **`SchemaMigrationManager.migrateOnStartup()` gains `Driver` and `databaseName` parameters** — removing the `Neo4jConnectionManager.getSession()` dependency makes the migration logic independently testable and decoupled from the static connection manager. `Neo4jFactory.@PostConstruct` provides both values from its stored fields.

23. **`KnowledgeIngestionService` is `@Singleton` with direct constructor injection — no `KnowledgeFactory` needed** — the class's constructor receives `ProcedureRepository`, `EmbeddingService`, and `DecompositionService`; adding `Driver` makes all four dependencies avaje-wirable directly. The originally planned `KnowledgeFactory @Factory` class is not created.

24. **`@Bean` method parameter injection breaks the `getKnowledgeCollectionElementResolutionAgent()` tool dependency problem** — avaje injects parameters of `@Bean` producer methods at bean-creation time independently from the `@Factory` class constructor. `getKnowledgeCollectionElementResolutionAgent()` can therefore declare `ElementLocatorTools` and `UiElementDbTools` as method parameters and receive them as ready singletons. The circular dependency (constructor injection of tool singletons whose transitive dep is `UiStateCheckAgent` produced by the same factory) applies only to constructor injection, not to `@Bean` method parameters.

25. **`@InjectModule` is placed on `package-info.java` in the module root package** — avaje-inject's annotation processor scans for `@InjectModule` at the package level. One `package-info.java` file per module, in the root package, carries the annotation. Create the file if it does not already exist.

## 7. Gap Resolutions (changes from draft plan v1)

| # | Issue | Resolution |
|---|-------|------------|
| 1 | `StepExecutionOrchestrator` ↔ `KnowledgeExecutionOrchestrator` circular dependency | New `ProcedureKnowledgeCollectionService` @Singleton extracts knowledge collection methods; both orchestrators inject it |
| 2 | `KnowledgeServiceFactory.buildExistingStateContext()` not addressed | Moved to `KnowledgeService` as an instance method |
| 3 | `EmbeddingStore<TextSegment>` @Bean production chain undefined | Defined as `@Bean @Singleton` in `Neo4jFactory` with explicit `Driver` dependency and dimension constant |
| 4 | `KnowledgeIngestionService` missing from `KnowledgeExecutionOrchestrator` deps | Added to its injection list |
| 5 | `ChatModelEventListener` needed `BudgetManager` injection | Added to §3 table and Phase 2 steps |
| 6 | `AgentCard` / `AgentCardProducer` production not specified | New `UiAgentBeanFactory` and `ApiAgentBeanFactory` `@Factory` classes defined in Phase 3 & 4 |
| 7 | Concrete `Server` subclass fate after `AbstractServer` refactoring | Explicitly eliminated; replaced by thin `main()`-only classes |
| 8 | `UiStateCheckAgent` not designated as injectable @Bean | `AgentFactory` promotes stateless agents to `@Bean @Singleton`; tool classes inject from there |
| 9 | `@InjectModule` `provides`/`requires` values not specified | Explicit class lists added for all 3 modules |
| 10 | Avaje version number missing from POM snippets | `12.5-RC1` added to all version references in §2 |
| 11 | `ACTION_VERIFICATION_DELAY_MILLIS` referenced from `UiTestAgent` static field | Constant moved to `UiTestAgentConfig` as `getActionVerificationDelayMillis()`; Phase 3 has explicit steps |
| 12 | `CommonTools` parameter on `executeWithKnowledge()` redundant after DI | Removal of the parameter noted in `KnowledgeExecutionOrchestrator` conversion step |
| 13 | `AgentFactory` annotated as `@Singleton` instead of `@Factory` | Corrected to `@Factory` throughout — avaje requires `@Factory` for classes containing `@Bean` producer methods |
| 14 | `VerificationTools` injection list missing | Added: injects `BudgetManager` (for `resetToolCallUsage()`) and `AgentConfig` (for `getVerificationRetryPolicy()`) |
| 15 | `StepExecutionOrchestrator` injection list missing `BudgetManager` | Added: calls `resetToolCallUsage()` three times directly; needs the injected instance |
| 16 | `UiAbstractTools` base class not addressed | Added explicit refactoring step: no-arg constructor removed, single `UiStateCheckAgent` constructor becomes the injection point for all subclasses |
| 17 | `MouseTools` and `KeyboardTools` extend `UiAbstractTools` but were not in plan | Both added as `@Singleton` beans injecting `UiStateCheckAgent`. They are NOT injected into `AgentFactory`; instead, per-task factory methods that need them receive them as method parameters from callers. `SpinnerTools` does not extend `UiAbstractTools` — stays as `new` |
| 18 | `Neo4jFactory @Bean Session` is a lifecycle antipattern | Removed — `Session` is short-lived; callers use `driver.session(DatabaseConfig)` directly |
| 19 | `AbstractServer` cross-module wiring fails at `agent_core` compile time | `agent_core`'s `@InjectModule` adds `requires = [AgentExecutor, AgentCard]` so avaje defers those dependencies to the consuming module's compile scope |
| 20 | `ProcedureKnowledgeCollectionService` injection list unclear | Injects only `AgentFactory`; `KnowledgeService` and `KnowledgeIngestionService` remain method parameters |
| 21 | Per-task agent method signatures contained `RetryState` parameter | Removed: no `RetryState` in any factory method signature. Signatures are expanded to include the tool singleton parameters required for the `AiServices` tool list; callers pass them |
| 22 | Existing compilation errors in modified files not addressed | Explicit Phase 3 step added to fix stale calls before DI conversion begins |
| 23 | `UiTestAgent` unnecessarily listed `KnowledgeIngestionService` in injection list | Removed — `KnowledgeIngestionService` is only used inside `KnowledgeExecutionOrchestrator`, which already injects it |
| 24 | `UiElementDbTools` silent `UiStateCheckAgent` dependency via `UiAbstractTools` parent | Injection list updated to include `UiStateCheckAgent` (passed to `super()` in constructor) |
| 25 | `AgentFactory` circular dependency via tool singleton constructor injection | `AgentFactory` injecting tool singletons whose transitive dep is `UiStateCheckAgent` (produced by `AgentFactory`) creates a cycle avaje rejects. Fixed: `AgentFactory` injects only `ModelFactory` + `UiTestAgentConfig`; tool singletons are passed as per-task method parameters by callers |
| 26 | `AgentFactory` ↔ `UiElementDbTools` mutual injection is a direct cycle | `UiElementDbTools` now injects `ModelFactory` + `UiTestAgentConfig` instead of `AgentFactory`; internal agent creation methods use those injected instances |
| 27 | `getUiStateCheckAgent()` new method — implementation source not specified | Explicitly stated: logic extracted from `UiAbstractTools.createUiStateCheckAgent()` (that static method is then deleted from `UiAbstractTools`) |
| 28 | `AgentConfig` 40+ static field initializers — constructor migration not detailed | Phase 2 step expanded: fields move to instance fields, initializer expressions move to constructor body, `protected static` helpers become `protected` instance methods, subclass constructors call `super()` first |
| 29 | `EmbeddingService.EMBEDDING_DIMENSION` value not verified | Value confirmed as `384` from `ProcedureEmbeddingStoreFactory`; constant set directly without additional verification |
| 30 | `Server.main()` BeanScope pattern missing; signature incorrect | Phase 3 and 4 steps updated: signature changed to `public static void main(String[] args)`; BeanScope builder with shutdown hook pattern described; `initKnowledgePersistence()` shutdown hook removed as redundant |
| 31 | `@InjectModule provides` list vague in `agent_core` | Exact class names enumerated: `AgentConfig`, `BudgetManager`, `ModelFactory`, `ChatModelEventListener`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource` |
| 32 | `ProcedureRepository` injection list missing `Driver` and database name | `Driver` (produced by `Neo4jFactory`) and `UiTestAgentConfig` (for database name) added to injection list; all `Neo4jConnectionManager.getSession()` calls replaced with `driver.session(SessionConfig.forDatabase(databaseName))`; no-arg constructor removed |
| 33 | `KnowledgeIngestionService` injection list wrong — plan stated "injects KnowledgeService"; actual constructor takes `ProcedureRepository, EmbeddingService, DecompositionService` | Corrected to `@Singleton` with constructor injection of `ProcedureRepository, EmbeddingService, DecompositionService, Driver`; `KnowledgeFactory` concept dropped entirely |
| 34 | `KnowledgeIngestionService` uses `Neo4jConnectionManager.getDriver()` directly — not addressed in plan | `Driver` added as a fourth constructor parameter; static import replaced with the injected instance |
| 35 | `SchemaMigrationManager` still calls `Neo4jConnectionManager.getSession()` internally — plan's `@PostConstruct` call would break at runtime | `migrateOnStartup()` gains `Driver` and `databaseName` parameters; `Neo4jFactory.@PostConstruct` supplies them from stored instance fields |
| 36 | `Neo4jFactory.@PostConstruct` had no way to access the `Driver` it produces | `Neo4jFactory` stores `driver` and `databaseName` as private instance fields; `@Bean` method assigns the field before returning |
| 37 | `ProcedureEmbeddingStoreFactory` not mentioned in plan — its `Neo4jEmbeddingStore` builder configuration was invisible | Class added to component table (deleted); builder configuration transferred to `Neo4jFactory.embeddingStore()`; `EMBEDDING_DIMENSION = 384` confirmed from this class |
| 38 | `getKnowledgeCollectionElementResolutionAgent()` creates `new ElementLocatorTools()`, `new UiElementDbTools()` — DI handling unspecified | Method promoted to `@Bean @Singleton` with `ElementLocatorTools` and `UiElementDbTools` as `@Bean` method parameters; §6 decision 24 explains why this does not cause a circular dependency |
| 39 | Per-task factory method expanded signatures not enumerated — left as "determined during implementation" | Signatures enumerated: both `getUiTestStepActionAgent()` and `getPreconditionActionAgent()` expand to `(CommonTools, MouseTools, KeyboardTools, ElementLocatorTools)`; `StepExecutionOrchestrator` injection list updated accordingly |
| 40 | `getTestStepVerificationAgent()` and `getPreconditionVerificationAgent()` not addressed after static-to-instance conversion | Remain as non-`@Bean` instance methods; all static `UiTestAgentConfig.*` calls replaced with injected `uiTestAgentConfig` instance calls |
| 41 | `@InjectModule` annotation placement not specified | Specified: placed on `package-info.java` in each module's root package; §6 decision 25 added |