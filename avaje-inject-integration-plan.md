# Implementation Plan: Avaje Inject DI Integration

**Created**: 2026-03-06
**Updated**: 2026-03-22
**Status**: Revised v6 (reorganized into 18 granular phases; added 7 previously missing classes — no implementation started yet)

## 1. Objective

Integrate Avaje Inject (v12.5-RC1) as the dependency injection framework across all 3 modules (`agent_core`, `ui_test_execution_agent`, `api_test_execution_agent`), replacing static factories, static config, and manual singleton management with compile-time DI-managed beans.

## 2. Technical Dependencies

Three avaje-inject artifacts must be added, all at version `12.5-RC1`, managed via `<dependencyManagement>` in the parent POM:

- **`avaje-inject`** — runtime library (compile scope in each module)
- **`avaje-inject-generator`** — compile-time annotation processor (provided/optional scope; no `annotationProcessorPath` config needed when `maven.compiler.proc=full` is set in parent POM properties)
- **`avaje-inject-test`** — test utilities (test scope in each module)

All three modules declare the runtime and processor artifacts. The `maven.compiler.proc=full` property in the parent POM properties section enables the processor automatically.

Verify that `ServicesResourceTransformer` is already present in the shade plugin config — no change needed there (confirmed present in both ui and api module POMs).

## 3. Architectural Design

### Design Patterns

- **Factory Method via `@Factory`/`@Bean`**: Replace static factory classes with DI factory beans that produce third-party objects (LangChain4j models, Neo4j driver, singleton agents)
- **Singleton Scope**: All stateless services, config beans, shared resources, and stateless agents become `@Singleton`
- **Constructor Injection**: All dependencies declared via constructors — immutable, testable, explicit
- **Extracted mediator to break cycles**: `ProcedureKnowledgeCollectionService` is introduced to eliminate the circular dependency between `StepExecutionOrchestrator` and `KnowledgeBasedExecutionOrchestrator` (see §3.4)

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
| `TestCaseExtractor` (static utility) | `@Singleton` bean. Injects `ModelFactory`, `AgentConfig` |
| `AbstractServer` (abstract) | Becomes **concrete `@Singleton`** bean. Abstract factory methods (`createAgentExecutor()`, `createAgentCard()`, `getStartupLogMessage()`) are **removed**. Constructor injects `AgentExecutionResource` and `AgentConfig` directly. Each consuming module's concrete `Server extends AbstractServer` subclass is **eliminated** — replaced by a thin `Server` class with only a `main()` method that builds a `BeanScope` and retrieves this bean to call `start()` |
| `AgentExecutionResource` | `@Singleton` bean. Constructor already takes `AgentExecutor` + `AgentCard` — add annotation only. Implementations are provided by each consuming module's `@Factory` |
| `ChatModelEventListener` | `@Singleton` bean. **Injects `BudgetManager`** — replaces all direct static `BudgetManager.*` calls |
| `DefaultToolErrorHandler` | NOT a singleton — created per-agent with specific `RetryPolicy`/`RetryState`. Stays as `new` at call sites |
| `BudgetManager` | `@Singleton` bean. Static mutable fields (`toolCallUsage`, `tokenUsagePerModel`, `startTime`, budget limit fields) become instance fields. All static methods become instance methods |

**`@InjectModule` for `agent_core`:**
Declares `provides = [AgentConfig, BudgetManager, ModelFactory, ChatModelEventListener, TestCaseExtractor, AbstractServer, AgentExecutionResource]` and `requires = [AgentExecutor, AgentCard]`. The `requires` entries tell avaje that `AgentExecutionResource` (which depends on both interfaces) will have those dependencies satisfied by the consuming module at the consuming module's compile time — not at `agent_core` compile time.

### Component Structure — ui_test_execution_agent

| Current Class | DI Conversion |
|---------------|---------------|
| `UiTestAgentConfig` (extends `AgentConfig`, static) | `@Singleton` bean extending the refactored `AgentConfig`. Adds `getActionVerificationDelayMillis()` instance method (currently referenced by `StepExecutionOrchestrator` via static import) |
| `UiAbstractTools` (base class) | **Refactored** — the no-arg constructor that internally creates `UiStateCheckAgent` via `createUiStateCheckAgent()` is **removed**. The single protected constructor taking `UiStateCheckAgent` becomes the only constructor. All subclasses (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`, `KnowledgeElementTools`) pass the injected agent to `super(uiStateCheckAgent)` |
| `CommonTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` from `AgentFactory` — passes it to `super()` |
| `MouseTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` — passes it to `super()` |
| `KeyboardTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` — passes it to `super()` |
| `KnowledgeElementTools` | `@Singleton` bean extending `UiAbstractTools`. Injects `UiStateCheckAgent` (to `super()`), `ProcedureRepository`, `EmbeddingService`, `FailureContextService` |
| `SpinnerTools` | Does **not** extend `UiAbstractTools`. No DI dependencies. Stays as `new SpinnerTools()` at call sites inside `AgentFactory` |
| `Server` (extends `AbstractServer`) | **Eliminated.** Reduced to a class with only `main()`: builds `BeanScope` with shutdown hook and calls `scope.get(AbstractServer.class).start()`. Abstract method overrides disappear |
| `AgentFactory` (static) | **`@Factory` bean** (not `@Singleton` — avaje only recognises `@Bean` producer methods inside `@Factory` classes). Injects **only `ModelFactory` and `UiTestAgentConfig`** — injecting tool singletons into the constructor would create a circular dependency: those tools depend on `UiStateCheckAgent`, which is a `@Bean` produced by this factory (see §6 decision 15). **Stateless agents promoted to `@Bean @Singleton`**: `getUiStateCheckAgent()` is a NEW method — implementation extracted verbatim from `UiAbstractTools.createUiStateCheckAgent()`, which is then deleted; `getKnowledgeSuggestionAgent()` becomes `@Bean @Singleton`. `getKnowledgeCollectionElementResolutionAgent()` becomes `@Bean @Singleton` and receives `ElementLocatorTools` and `UiElementDbTools` as **`@Bean` method parameters** — avaje injects these at bean-creation time via method parameter injection, which is distinct from constructor injection on the `@Factory` class and therefore does not trigger the circular dependency (see §6 decision 16); the `LocationHistoryRecorder` and `Function<UUID, Optional<ElementLocationHistory>>` parameters are also injected as `@Bean` method parameters from the `KnowledgeServicesBeanFactory` (see below); `SpinnerTools` remains `new SpinnerTools()` inside this method. **Non-`@Bean` factory methods** `getTestStepVerificationAgent()` and `getPreconditionVerificationAgent()` remain regular instance methods and are not promoted to `@Bean @Singleton`; all static `UiTestAgentConfig.*` calls in them are replaced with calls on the injected `uiTestAgentConfig` instance. **Per-task methods** `getUiTestStepActionAgent()` and `getPreconditionActionAgent()` remain regular instance methods; their current signatures include `CommonTools`, `LocationHistoryRecorder`, and `Function<UUID, Optional<ElementLocationHistory>>` — after DI, `CommonTools` becomes an injected field, but `LocationHistoryRecorder` and the stability lookup function remain method parameters because they are extracted from `KnowledgeServices` at task time. All static `UiTestAgentConfig.*` and `ModelFactory.getModel()` import usages across all factory methods are replaced with calls on the injected instances |
| `KnowledgeServiceFactory` (static) | **Deleted**. Both `KnowledgeService` and `KnowledgeIngestionService` and all subsidiary services/repositories become `@Singleton` beans with constructor injection wired directly by avaje — no replacement `@Factory` class is required |
| `Neo4jConnectionManager` (static singleton, in `knowledge_graph` package) | **Replaced** by `Neo4jFactory` (`@Factory implements AutoCloseable`). Declares private `driver` and `databaseName` instance fields. Produces: (1) `@Bean @Singleton Driver` — connection pool config and error handling move here; the created driver is stored in the `driver` instance field before being returned; `verifyConnectivity()` called after creation; (2) `@Bean @Singleton EmbeddingStore<TextSegment>` — receives the injected `Driver` as a `@Bean` method parameter; configures `Neo4jEmbeddingStore` with label, id/embedding/text property names, index name, `dimension=384` (from `EmbeddingService.EMBEDDING_DIMENSION`), `initializeSchema=true`, full-text index name, and metadata prefix; reads database name from the stored `databaseName` field. **No `@Bean Session` is produced** — `Session` is short-lived; callers open sessions via `driver.session(SessionConfig.forDatabase(databaseName))`. `@PostConstruct initSchema()` calls the refactored `SchemaMigrationManager.migrateOnStartup(driver, databaseName)` using the stored instance fields; replaces `Server.initKnowledgePersistence()`. `close()` closes the stored `driver` field; avaje auto-calls via `AutoCloseable` on scope shutdown |
| `SchemaMigrationManager` (static utility, in `knowledge_graph.schema` package) | **Refactored**: `migrateOnStartup()` gains two parameters — `Driver driver` and `String databaseName`. The internal `Neo4jConnectionManager.getSession()` call is replaced with a session opened from the passed driver on the passed database name. The `Neo4jConnectionManager` import is removed. The class retains its package-private static utility structure and remains idempotent |
| `Neo4jRepositorySupport` (static utility) | **Refactored**: currently uses `Neo4jConnectionManager.getDriver()` and `Neo4jConnectionManager.getSession()` statically. Converted to an instance class (`@Singleton`) that receives `Driver` and database name via constructor injection. All static methods become instance methods. All repositories that call its static methods are updated to inject `Neo4jRepositorySupport` instead |
| `EmbeddingService` | `@Singleton` bean. Static `MODEL` field becomes an instance field. Adds `public static final int EMBEDDING_DIMENSION = 384` constant — value confirmed for `BgeSmallEnV15EmbeddingModel` |
| `ProcedureRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` (for query execution) and `EmbeddingStore<TextSegment>` (produced by `Neo4jFactory`). The no-arg constructor is removed. All `Neo4jConnectionManager` static calls are replaced with the injected support instance |
| `PhraseEmbeddingRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport`. Currently uses Neo4jConnectionManager statically via `Neo4jRepositorySupport` — after DI, calls go through the injected instance |
| `SatisfiesEdgeRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` |
| `FailureContextRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` |
| `ProcedureUsageByTestCaseTrackingRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` |
| `GraphHealthRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` |
| `UiElementRepository` | `@Singleton` bean. Injects `Neo4jRepositorySupport` and `EmbeddingModel` (from `EmbeddingService`). Removes the no-arg constructor that called `EmbeddingService.getModel()` statically |
| `DecompositionService` | `@Singleton` bean. Injects `ProcedureRepository` |
| `KnowledgeService` | `@Singleton` bean. Injects `ProcedureRepository`, `EmbeddingService`, `DecompositionService`, `PhraseEmbeddingRepository` |
| `KnowledgeIngestionService` | `@Singleton` bean. Constructor injection of `ProcedureRepository`, `EmbeddingService`, `DecompositionService`, `SatisfiesEdgeRepository`, `FailureContextService`, `PhraseEmbeddingRepository`. No factory wrapper needed — avaje wires all six dependencies directly |
| `SatisfiesEdgeService` | `@Singleton` bean. Injects `SatisfiesEdgeRepository`, `PhraseEmbeddingRepository` |
| `FailureContextService` | `@Singleton` bean. Injects `FailureContextRepository` |
| `ProcedureUsageByTestCaseTrackingService` | `@Singleton` bean. Injects `ProcedureUsageByTestCaseTrackingRepository` |
| `GraphHealthService` | `@Singleton` bean (package-private). Injects `GraphHealthRepository` |
| `GraphHealthHtmlReportGenerator` | Package-private, stateless, no DI dependencies. Stays as `new` inside `GraphHealthService` |
| `GraphHealthReportCli` | Standalone CLI entry point with its own `main()`. After DI, creates its own `BeanScope` to obtain `GraphHealthService`, or stays manual if simpler |
| **`ProcedureKnowledgeCollectionService`** (NEW) | `@Singleton` bean. Extracts `triggerNewProcedureFlow()` and `triggerEditProcedureFlow()` from `KnowledgeBasedExecutionOrchestrator`. Injects `AgentFactory` (to obtain `KnowledgeSuggestionAgent`). **`KnowledgeService` and `KnowledgeIngestionService` remain method parameters** — they are already passed from callers in the existing code; keeping them as parameters avoids unnecessary field injection and preserves the existing call contract. **Breaks the `StepExecutionOrchestrator` ↔ `KnowledgeBasedExecutionOrchestrator` circular dependency** (see §3.4) |
| **`KnowledgeServicesBeanFactory`** (NEW) | `@Factory` bean. Produces `@Bean @Singleton LocationHistoryRecorder` (backed by `ProcedureRepository::updateElementStability`) and `@Bean @Singleton Function<UUID, Optional<ElementLocationHistory>>` (backed by `ProcedureRepository::getElementStability`). These were previously created inline in `KnowledgeServiceFactory.createKnowledgeServices()` |
| `ElementLocatorTools` | `@Singleton` bean. Injects `UiElementRepository`, `UiStateCheckAgent` (to `super()`), `LocationHistoryRecorder`, and `Function<UUID, Optional<ElementLocationHistory>>` (stability lookup). Removes no-arg and 2-arg constructors; single constructor receives all injected dependencies |
| `UiElementDbTools` | `@Singleton` bean. Injects `UiElementRepository`, `ModelFactory`, `UiTestAgentConfig`, and `UiStateCheckAgent` — pass `UiStateCheckAgent` to `super()`. The two internal agents (`UiElementExtendedDescriptionAgent`, `DbUiElementSelectionAgent`) are currently created via private methods using static `AiServices.builder()` calls — after DI, those static import usages are replaced by calls on the injected `ModelFactory` and `UiTestAgentConfig` instances. `AgentFactory` is NOT injected here: `AgentFactory` injects `UiElementDbTools` via `@Bean` method parameter, so injecting `AgentFactory` back would be a direct cycle |
| `VerificationTools` (static) | `@Singleton` bean. Instance methods instead of static. Injects **`BudgetManager`** (for `resetToolCallUsage()` calls) and **`AgentConfig`** (for `getVerificationRetryPolicy()` call) |
| `StepExecutionOrchestrator` (static) | `@Singleton` bean. Injects `VerificationTools`, `UiTestAgentConfig`, `AgentFactory`, `ProcedureKnowledgeCollectionService`, `BudgetManager` (for `resetToolCallUsage()` calls), `CommonTools`. The `ACTION_VERIFICATION_DELAY_MILLIS` static constant (which already calls `getActionVerificationDelayMillis()`) becomes an instance field initialized from the injected `uiTestAgentConfig` |
| `KnowledgeBasedExecutionOrchestrator` (static) | `@Singleton` bean. Injects `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `StepExecutionOrchestrator`, `ProcedureKnowledgeCollectionService`, `SatisfiesEdgeService`, `CommonTools`, `LocationHistoryRecorder`, `Function<UUID, Optional<ElementLocationHistory>>`. **`CommonTools` parameter removed from `executeBasedOnKnowledge()`** — injected field used instead. `KnowledgeServices` record parameter removed — individual services injected as fields |
| `UiTestAgent` (static) | `@Singleton` bean. Injects `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `SatisfiesEdgeService`, `ProcedureUsageByTestCaseTrackingService`, `FailureContextService`, `StepExecutionOrchestrator`, `KnowledgeBasedExecutionOrchestrator`, `UiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`, `CommonTools`, `LocationHistoryRecorder`, `Function<UUID, Optional<ElementLocationHistory>>` (stability lookup). The `KnowledgeServices` record may be retained as a local convenience or eliminated — services are injected individually |
| `UiAgentExecutor` | `@Singleton` bean. Injects `UiTestAgent` |
| `AgentCard` (UI) | `@Bean @Singleton` produced by new `UiAgentBeanFactory` (`@Factory`) using `AgentCardProducer` |
| `UiElementRefinementHelper` (static utility, in `tools` package) | **`@Singleton` bean**. Currently all static methods calling `AgentConfig.getRetrieverTopN()` and `UiTestAgentConfig.getElementRetrievalMinGeneralScore()` via static imports. Injects `AgentConfig` and `UiTestAgentConfig`. All static methods become instance methods. Static import usages of config replaced with injected instance calls |
| `UiElementDialogHelper` (static utility, in `user_dialogs.knowledge` package) | **`@Singleton` bean**. Currently uses `AgentFactory.getKnowledgeCollectionElementResolutionAgent()` and `UiTestAgentConfig.getMaxActionExecutionDurationMillis()` via static imports, and creates repositories statically. Injects `AgentFactory`, `UiTestAgentConfig`, `UiElementRepository`, `UiElementRefinementHelper`. All static methods become instance methods |
| `ImageUtils` (static utility, in `utils` package) | **`@Singleton` bean**. Currently calls `UiTestAgentConfig.getScreenshotsSaveFolder()` via static import. Injects `UiTestAgentConfig`. Static methods that use the config become instance methods |
| `BoundingBox` (record, in `dto` package) | **Refactored** — stays as a record (records cannot be DI beans). The `getActualBoundingBox()` method currently calls `UiTestAgentConfig.isBoundingBoxAlreadyNormalized()` via static import. Refactored to `getActualBoundingBox(boolean isAlreadyNormalized)` — callers pass `uiTestAgentConfig.isBoundingBoxAlreadyNormalized()` from their injected config instance. The static import of `UiTestAgentConfig` is removed from the record |
| `AgentCardProducer` (UI, in `a2a` package) | **Refactored** — currently has a static field initialized from `AgentConfig.getExternalUrl()`. The static field is replaced: `AgentCardProducer` receives config values as constructor/method parameters. The `UiAgentBeanFactory` `@Factory` passes the injected `AgentConfig` values when calling `AgentCardProducer` |
| `ProcedureKnowledgeCollectionDialog` (in `user_dialogs.knowledge`) | **Updated** — currently uses `UiTestAgentConfig.getDialogDefaultFontSize()` and `getDialogDefaultFontType()` via static imports, and creates `new UiElementRepository()` statically. After DI: font config values are passed as constructor parameters from the caller (which injects `UiTestAgentConfig`), and `UiElementRepository` is passed as a constructor parameter. The dialog itself is NOT a DI bean — it is created on-demand in UI context |
| `ExistingProcedureLookupDialog` (in `user_dialogs.knowledge`) | **Updated** — currently uses `UiTestAgentConfig.getProcedureLookupDelayMs()` via static import. After DI: the delay value is passed as a constructor parameter from the caller (which injects `UiTestAgentConfig`). The dialog itself is NOT a DI bean — it is created on-demand |

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
| `AgentCardProducer` (API, in `a2a` package) | **Refactored** — same treatment as UI version. Static field from `AgentConfig.getExternalUrl()` replaced: config values passed as parameters from `ApiAgentBeanFactory` |

**`@InjectModule` for `api_test_execution_agent`:**
Declares `requires` listing: `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`.

### 3.4 Circular Dependency Resolution

`StepExecutionOrchestrator.promptUserAndDispatch()` calls `KnowledgeBasedExecutionOrchestrator.triggerEditProcedureFlow()` and `triggerNewProcedureFlow()`, while `KnowledgeBasedExecutionOrchestrator.executeBasedOnKnowledge()` calls `StepExecutionOrchestrator.executeAtomicStepWithRetryLoop()`. Direct mutual injection would create a compile-time cycle that avaje rejects.

**Resolution:** Extract both knowledge collection methods (`triggerNewProcedureFlow`, `triggerEditProcedureFlow`) from `KnowledgeBasedExecutionOrchestrator` into a new `ProcedureKnowledgeCollectionService` `@Singleton`. Both orchestrators inject `ProcedureKnowledgeCollectionService`. The dependency graph becomes:

```
StepExecutionOrchestrator  →  ProcedureKnowledgeCollectionService  ←  KnowledgeBasedExecutionOrchestrator
StepExecutionOrchestrator  ←  KnowledgeBasedExecutionOrchestrator   (one direction only — no cycle)
```

`ProcedureKnowledgeCollectionService` injects only `AgentFactory` (to obtain `KnowledgeSuggestionAgent`). `KnowledgeService` and `KnowledgeIngestionService` remain **method parameters** on both knowledge collection methods, preserving the existing call contract and avoiding unnecessary field injection.

### What Stays Manual (NOT DI-managed)

- **LangChain4j `AiServices` proxies with per-task state** — agents receiving `CommonTools` as a parameter stay as per-call factory method returns inside `AgentFactory`
- **`ApiRequestTools`, `ApiAssertionTools`, `TestContextDataTools`** — depend on per-task `ApiContext`/`TestExecutionContext`
- **`DefaultToolErrorHandler` / `UiToolErrorHandler`** — per-agent with specific `RetryPolicy`/`RetryState`
- **`RetryState`** — per-execution mutable state
- **`TestExecutionContext` / `UiTestExecutionContext`** — per-task mutable context
- **`ScreenRecorder`, `LogCapture`** — per-task lifecycle
- **`SpinnerTools`** — no `UiAbstractTools` dependency; stays as `new` inside `AgentFactory` factory methods
- **`ExecutionStateTracker`, `PreconditionResolver`** — per-invocation objects created inside `executeBasedOnKnowledge()`
- **`GraphHealthHtmlReportGenerator`** — package-private stateless helper, created by `GraphHealthService`
- **`KnowledgeServices`** record — may be eliminated or retained as local convenience; the bundled services are now injected individually
- **`ProcedureKnowledgeCollectionDialog`, `ExistingProcedureLookupDialog`** — Swing dialogs created on-demand in UI context; receive config values as constructor parameters from DI-managed callers
- **`InheritanceAwareToolProvider`** — created per-agent inside `AgentFactory` factory methods
- **`DialogConfig`, `ProcedureDialogUIBuilder`, `ChildStepRowBuilder`, `SimilaritySearchTask`** — UI helpers and data carriers with no static config dependencies

### Data Flow

1. `Server.main()` creates `BeanScope` with a shutdown hook
2. `BeanScope` auto-discovers all `@Singleton`, `@Factory` beans from both `agent_core` and the consuming module via generated `AvajeModule` service loader entries
3. `Neo4jFactory.driver()` initialises Neo4j `Driver`; `@PostConstruct` on `Neo4jFactory` triggers `SchemaMigrationManager.migrateOnStartup()`
4. Server retrieves `AbstractServer` bean → calls `start()` → Javalin binds routes
5. On task request: `AgentExecutionResource` → `UiAgentExecutor`/`ApiAgentExecutor` → `UiTestAgent`/`ApiTestAgent`
6. Agent bean uses injected `AgentFactory` to create per-task agent proxies — `LocationHistoryRecorder` and stability lookup extracted from injected beans and passed as arguments
7. On shutdown: `BeanScope.close()` → avaje calls `close()` on `Neo4jFactory` → `Driver` closed

## 4. Implementation Phases

### Phase 1: Maven Setup & Build Configuration

- [x] Add `avaje-inject` (runtime), `avaje-inject-generator` (provided/optional), and `avaje-inject-test` (test) — all at version `12.5-RC1` — to parent POM `<dependencyManagement>`
- [x] Add `maven.compiler.proc=full` to parent POM `<properties>`
- [x] Add runtime and processor dependencies to `agent_core/pom.xml`
- [x] Add runtime and processor dependencies to `ui_test_execution_agent/pom.xml`
- [x] Add runtime and processor dependencies to `api_test_execution_agent/pom.xml`
- [x] Add `avaje-inject-test` (test scope) to all 3 module POMs
- [x] Verify `ServicesResourceTransformer` already present in shade plugin config (confirmed — no change needed)
- [ ] Verify compilation succeeds with `mvn compile`

### Phase 2: AgentConfig Static-to-Instance Conversion

- [ ] Convert `AgentConfig` from static to `@Singleton`: move all ~40 `private static final ConfigProperty<T>` fields and the `static final Properties` field to instance fields; move their initializer expressions into the constructor body (load `Properties` first, then assign each `ConfigProperty` in order using the helper methods); convert the `protected static` helpers (`loadProperty()`, `getRequiredProperty()`, `loadPropertyAsInteger()`, `loadPropertyAsDouble()`, `getProperty()`) from static to `protected` instance methods; convert all `public static` getters to `public` instance methods
- [ ] Update all classes in `agent_core` that use static imports from `AgentConfig` to inject `AgentConfig` instead (compile will fail for ui/api modules — expected, fixed in later phases)
- [ ] Verify `mvn compile -pl agent_core` succeeds

### Phase 3: Core Utility Beans — BudgetManager & ChatModelEventListener

- [ ] Convert `BudgetManager` from static to `@Singleton` — static fields become instance fields; all static methods become instance methods
- [ ] Convert `ChatModelEventListener` to `@Singleton`. Inject `BudgetManager` — replace all `BudgetManager.staticMethod()` calls with the injected instance
- [ ] Verify `mvn compile -pl agent_core` succeeds

### Phase 4: Core Model & Extractor Beans

- [ ] Convert `ModelFactory` from static to `@Singleton`. Inject `AgentConfig`. `getModel()` becomes instance method
- [ ] Convert `TestCaseExtractor` from static to `@Singleton`. Inject `ModelFactory`, `AgentConfig`
- [ ] Verify `mvn compile -pl agent_core` succeeds

### Phase 5: Core Server Infrastructure & Module Declaration

- [ ] Refactor `AbstractServer` to **concrete `@Singleton`**: remove abstract declaration and all three abstract methods. Constructor injects `AgentExecutionResource` and `AgentConfig`. `start()` uses the injected resource directly
- [ ] Convert `AgentExecutionResource` to `@Singleton` — add annotation; constructor signature unchanged
- [ ] Add `@InjectModule` to `agent_core` with `provides = [AgentConfig, BudgetManager, ModelFactory, ChatModelEventListener, TestCaseExtractor, AbstractServer, AgentExecutionResource]` and **`requires = [AgentExecutor, AgentCard]`**. Place the annotation on a `package-info.java` file in the module's root package (e.g., `package org.tarik.ta.core;`); create the file if it does not exist
- [ ] Verify `mvn compile -pl agent_core` succeeds

### Phase 6: UI Config & UiAbstractTools Base Refactor

- [ ] Convert `UiTestAgentConfig` from static to `@Singleton` extending the refactored `AgentConfig`. Add `getActionVerificationDelayMillis()` instance method
- [ ] **Refactor `UiAbstractTools`**: remove the no-arg constructor and its internal static `createUiStateCheckAgent()` method. Replace with a single protected constructor that accepts `UiStateCheckAgent` and assigns it to the field. All subclasses will pass the injected agent to `super()`

### Phase 7: UI Basic Tool Singletons

- [ ] Convert `MouseTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`
- [ ] Convert `KeyboardTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`
- [ ] Convert `CommonTools` to `@Singleton`. Inject `UiStateCheckAgent` — pass to `super()`

### Phase 8: Neo4j Infrastructure

- [ ] Refactor `SchemaMigrationManager` (in `knowledge_graph.schema` package): add `Driver driver` and `String databaseName` parameters to `migrateOnStartup()`. Replace the `Neo4jConnectionManager.getSession()` call inside the method with a session opened from the passed driver on the passed database name. Remove the `Neo4jConnectionManager` import. The class remains a package-private static utility
- [ ] Refactor `Neo4jRepositorySupport`: convert from static utility to `@Singleton` instance class. Inject `Driver` and `UiTestAgentConfig` (for database name). All static methods become instance methods. All repositories that use its static methods are updated to inject the `Neo4jRepositorySupport` instance
- [ ] Create `Neo4jFactory` (`@Factory implements AutoCloseable`):
  - Declare private `driver` and `databaseName` instance fields; populate `databaseName` from the injected `UiTestAgentConfig` (can be done in the constructor)
  - `@Bean @Singleton Driver driver()` — replaces `Neo4jConnectionManager.getDriver()`; connection pool config and error handling move here; assigns the created driver to the `driver` instance field before returning it; `verifyConnectivity()` called after creation
  - `@Bean @Singleton EmbeddingStore<TextSegment> embeddingStore(Driver driver)` — receives injected `Driver` as a `@Bean` method parameter; configures `Neo4jEmbeddingStore` with label, id/embedding/text property names, index name, `dimension=384` (from `EmbeddingService.EMBEDDING_DIMENSION`), `initializeSchema=true`, full-text index name, and metadata prefix; uses the stored `databaseName` field
  - `@PostConstruct void initSchema()` — calls the refactored `SchemaMigrationManager.migrateOnStartup(driver, databaseName)` using the stored instance fields; replaces the logic from `Server.initKnowledgePersistence()`
  - `void close()` — closes the stored `driver` field; avaje auto-calls via `AutoCloseable` on scope shutdown
  - **No `@Bean Session` method** — sessions are short-lived; callers call `driver.session(SessionConfig.forDatabase(databaseName))` directly
- [ ] Delete `Neo4jConnectionManager` — its driver lifecycle and session logic have been moved into `Neo4jFactory` and `Neo4jRepositorySupport`

### Phase 9: EmbeddingService & Repositories

- [ ] Convert `EmbeddingService` to `@Singleton` — static `MODEL` field becomes an instance field. Add `public static final int EMBEDDING_DIMENSION = 384` constant (value confirmed for `BgeSmallEnV15EmbeddingModel`)
- [ ] Convert `ProcedureRepository` to `@Singleton`. Inject `Neo4jRepositorySupport` and `EmbeddingStore<TextSegment>`. Remove the no-arg constructor
- [ ] Convert `PhraseEmbeddingRepository` to `@Singleton`. Inject `Neo4jRepositorySupport`
- [ ] Convert `SatisfiesEdgeRepository` to `@Singleton`. Inject `Neo4jRepositorySupport`
- [ ] Convert `FailureContextRepository` to `@Singleton`. Inject `Neo4jRepositorySupport`
- [ ] Convert `ProcedureUsageByTestCaseTrackingRepository` to `@Singleton`. Inject `Neo4jRepositorySupport`
- [ ] Convert `GraphHealthRepository` to `@Singleton`. Inject `Neo4jRepositorySupport`
- [ ] Convert `UiElementRepository` to `@Singleton`. Inject `Neo4jRepositorySupport` and `EmbeddingModel` (from `EmbeddingService`). Remove the no-arg constructor that called `EmbeddingService.getModel()`

### Phase 10: Knowledge Graph Services

- [ ] Convert `DecompositionService` to `@Singleton`. Inject `ProcedureRepository`
- [ ] Convert `KnowledgeService` to `@Singleton`. Inject `ProcedureRepository`, `EmbeddingService`, `DecompositionService`, `PhraseEmbeddingRepository`
- [ ] Convert `KnowledgeIngestionService` to `@Singleton`. Constructor injection of `ProcedureRepository`, `EmbeddingService`, `DecompositionService`, `SatisfiesEdgeRepository`, `FailureContextService`, `PhraseEmbeddingRepository`. No factory wrapper needed — avaje wires all six dependencies directly
- [ ] Convert `SatisfiesEdgeService` to `@Singleton`. Inject `SatisfiesEdgeRepository`, `PhraseEmbeddingRepository`
- [ ] Convert `FailureContextService` to `@Singleton`. Inject `FailureContextRepository`
- [ ] Convert `ProcedureUsageByTestCaseTrackingService` to `@Singleton`. Inject `ProcedureUsageByTestCaseTrackingRepository`
- [ ] Convert `GraphHealthService` to `@Singleton`. Inject `GraphHealthRepository`
- [ ] Create `KnowledgeServicesBeanFactory` (`@Factory`): produces `@Bean @Singleton LocationHistoryRecorder` (backed by `ProcedureRepository::updateElementStability`) and `@Bean @Singleton Function<UUID, Optional<ElementLocationHistory>>` (backed by `ProcedureRepository::getElementStability`). These replace the inline lambdas previously created in `KnowledgeServiceFactory.createKnowledgeServices()`

### Phase 11: Specialized Tool Singletons

- [ ] Convert `KnowledgeElementTools` to `@Singleton`. Inject `UiStateCheckAgent` (to `super()`), `ProcedureRepository`, `EmbeddingService`, `FailureContextService`
- [ ] Convert `ElementLocatorTools` to `@Singleton`. Inject `UiElementRepository`, `UiStateCheckAgent`, `LocationHistoryRecorder`, and `Function<UUID, Optional<ElementLocationHistory>>` — pass `UiStateCheckAgent` to `super()`. Remove no-arg and 2-arg constructors
- [ ] Convert `UiElementDbTools` to `@Singleton`. Inject `UiElementRepository`, `ModelFactory`, `UiTestAgentConfig`, and `UiStateCheckAgent` — pass `UiStateCheckAgent` to `super()`. Replace static `AiServices.builder()` call usages in `createUiElementDescriptionMatcherAgent()` and `createDbElementSelectionAgent()` with calls on the injected `ModelFactory` and `UiTestAgentConfig` instances. Do NOT inject `AgentFactory` — doing so alongside `AgentFactory` injecting `UiElementDbTools` is a direct circular dependency
- [ ] Convert `VerificationTools` from static to `@Singleton` with instance methods. Inject **`BudgetManager`** (for `resetToolCallUsage()`) and **`AgentConfig`** (for `getVerificationRetryPolicy()`)

### Phase 12: UI Helper & Utility Class Refactoring

- [ ] Convert `UiElementRefinementHelper` to `@Singleton`. Inject `AgentConfig` and `UiTestAgentConfig`. All static methods become instance methods. Replace static imports of `AgentConfig.getRetrieverTopN()` and `UiTestAgentConfig.getElementRetrievalMinGeneralScore()` with injected instance calls
- [ ] Convert `UiElementDialogHelper` to `@Singleton`. Inject `AgentFactory`, `UiTestAgentConfig`, `UiElementRepository`, `UiElementRefinementHelper`. All static methods become instance methods. Replace static imports of `AgentFactory.getKnowledgeCollectionElementResolutionAgent()` and `UiTestAgentConfig.getMaxActionExecutionDurationMillis()` with injected instance calls. Remove internal static repository creation
- [ ] Convert `ImageUtils` to `@Singleton`. Inject `UiTestAgentConfig`. Static methods that use `UiTestAgentConfig.getScreenshotsSaveFolder()` become instance methods. Replace static import with injected instance call
- [ ] Refactor `BoundingBox` record: change `getActualBoundingBox()` to `getActualBoundingBox(boolean isAlreadyNormalized)`. Remove static import of `UiTestAgentConfig.isBoundingBoxAlreadyNormalized()`. Update all callers to pass `uiTestAgentConfig.isBoundingBoxAlreadyNormalized()` from their injected config
- [ ] Update `ProcedureKnowledgeCollectionDialog`: replace static imports of `UiTestAgentConfig.getDialogDefaultFontSize()` and `getDialogDefaultFontType()` with constructor parameters. Replace `new UiElementRepository()` with a constructor parameter. Update all callers (DI-managed) to pass the injected config values and repository
- [ ] Update `ExistingProcedureLookupDialog`: replace static import of `UiTestAgentConfig.getProcedureLookupDelayMs()` with a constructor parameter. Update all callers to pass the injected config value
- [ ] Refactor `AgentCardProducer` (UI version): replace static field initialized from `AgentConfig.getExternalUrl()` with a constructor/method parameter. The `UiAgentBeanFactory` passes the value from its injected `AgentConfig`

### Phase 13: Agent Factory Conversion

- [ ] Convert `AgentFactory` to **`@Factory`**. Inject only `ModelFactory` and `UiTestAgentConfig` in constructor — tool singletons are not constructor-injected (doing so would create a circular dependency via `UiStateCheckAgent`). Add new `@Bean @Singleton getUiStateCheckAgent()` method by extracting the creation logic from `UiAbstractTools.createUiStateCheckAgent()` — delete that static method from `UiAbstractTools` as part of this step. Annotate `getKnowledgeSuggestionAgent()` as `@Bean @Singleton`. Annotate `getKnowledgeCollectionElementResolutionAgent()` as `@Bean @Singleton` and add `ElementLocatorTools`, `UiElementDbTools`, `LocationHistoryRecorder`, and `Function<UUID, Optional<ElementLocationHistory>>` as `@Bean` method parameters. Convert non-`@Bean` methods `getTestStepVerificationAgent()` and `getPreconditionVerificationAgent()` from static to instance methods only — replace all static `UiTestAgentConfig.*` calls with calls on the injected `uiTestAgentConfig` instance; do not promote them to `@Bean @Singleton`. For per-task methods `getUiTestStepActionAgent()` and `getPreconditionActionAgent()`: their current params are `(CommonTools, LocationHistoryRecorder, Function<UUID, Optional<ElementLocationHistory>>)`; after DI, `CommonTools` can be injected as a field, leaving `LocationHistoryRecorder` and stability lookup as method parameters passed from callers. Replace all remaining static `UiTestAgentConfig.*` and `ModelFactory.getModel()` import usages across all factory methods with calls on the injected instances

### Phase 14: Circular Dependency Resolution & Orchestrators

- [ ] **Create `ProcedureKnowledgeCollectionService`** (`@Singleton`): move `triggerNewProcedureFlow()` and `triggerEditProcedureFlow()` out of `KnowledgeBasedExecutionOrchestrator`. Inject `AgentFactory`. Keep `KnowledgeService` and `KnowledgeIngestionService` as method parameters on both methods — do not inject them. Update all callers (`StepExecutionOrchestrator.promptUserAndDispatch()` and `KnowledgeBasedExecutionOrchestrator`) to inject and use this bean
- [ ] Convert `StepExecutionOrchestrator` from static to `@Singleton`. Inject `VerificationTools`, `UiTestAgentConfig`, `AgentFactory`, `ProcedureKnowledgeCollectionService`, `BudgetManager`, `CommonTools`. Replace `resetToolCallUsage()` static import calls with the injected `BudgetManager` instance. Convert the `ACTION_VERIFICATION_DELAY_MILLIS` static constant to an instance field initialized from the injected `uiTestAgentConfig`
- [ ] Convert `KnowledgeBasedExecutionOrchestrator` from static to `@Singleton`. Inject `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `StepExecutionOrchestrator`, `ProcedureKnowledgeCollectionService`, `SatisfiesEdgeService`, `CommonTools`, `LocationHistoryRecorder`, `Function<UUID, Optional<ElementLocationHistory>>`. Remove `KnowledgeServices` and `CommonTools` parameters from `executeBasedOnKnowledge()` — use injected fields

### Phase 15: UI Test Agent & Executor

- [ ] Convert `UiTestAgent` from static to `@Singleton`. Inject `AgentFactory`, `KnowledgeService`, `KnowledgeIngestionService`, `SatisfiesEdgeService`, `ProcedureUsageByTestCaseTrackingService`, `FailureContextService`, `StepExecutionOrchestrator`, `KnowledgeBasedExecutionOrchestrator`, `UiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`, `CommonTools`, `LocationHistoryRecorder`, `Function<UUID, Optional<ElementLocationHistory>>`
- [ ] Convert `UiAgentExecutor` to `@Singleton`. Inject `UiTestAgent`

### Phase 16: UI Module Wiring & Server

- [ ] Create `UiAgentBeanFactory` (`@Factory`): `@Bean @Singleton AgentCard agentCard()` using the refactored `AgentCardProducer` with injected `AgentConfig`
- [ ] Refactor UI `Server`: remove `extends AbstractServer` and all method overrides. Change `main()` signature to `public static void main(String[] args)` (current is package-private no-arg — not a valid JVM entry point). The method builds a `BeanScope` using its builder with the shutdown hook enabled, retrieves the `AbstractServer` bean from scope, and calls `start()`. The shutdown hook automatically triggers `BeanScope.close()` on JVM exit, which invokes `Neo4jFactory.close()` via `AutoCloseable` — the explicit `Neo4jConnectionManager` shutdown hook previously in `initKnowledgePersistence()` is no longer needed
- [ ] Add `@InjectModule` marker: `requires` listing `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`. Place the annotation on a `package-info.java` in the module's root package (e.g., `package org.tarik.ta;`); create the file if it does not exist
- [ ] Update all remaining static import usages throughout the UI module
- [ ] Verify `mvn compile -pl ui_test_execution_agent` succeeds

### Phase 17: API Module Conversion

- [ ] Convert `ApiTestAgentConfig` from static to `@Singleton` extending the refactored `AgentConfig`
- [ ] Convert `ApiTestAgent` from static to `@Singleton`. Inject `ModelFactory`, `ApiTestAgentConfig`, `TestCaseExtractor`, `BudgetManager`. Per-task tools remain `new` inside methods
- [ ] Convert `ApiAgentExecutor` to `@Singleton`. Inject `ApiTestAgent`
- [ ] Refactor `AgentCardProducer` (API version): replace static field initialized from `AgentConfig.getExternalUrl()` with a constructor/method parameter
- [ ] Create `ApiAgentBeanFactory` (`@Factory`): `@Bean @Singleton AgentCard agentCard()` using the refactored `AgentCardProducer` with injected `AgentConfig`
- [ ] Refactor API `Server`: remove `extends AbstractServer` and all method overrides. Change `main()` signature to `public static void main(String[] args)` (current is package-private no-arg). The method builds a `BeanScope` using its builder with the shutdown hook enabled, retrieves the `AbstractServer` bean from scope, and calls `start()`
- [ ] Add `@InjectModule` marker: `requires` listing `AgentConfig`, `ModelFactory`, `TestCaseExtractor`, `AbstractServer`, `AgentExecutionResource`, `BudgetManager`. Place the annotation on a `package-info.java` in the module's root package (e.g., `package org.tarik.ta.api;`); create the file if it does not exist
- [ ] Update all remaining static import usages in the API module
- [ ] Verify `mvn compile -pl api_test_execution_agent` succeeds

### Phase 18: Full Compilation, Test Fix & Cleanup

- [ ] Run `mvn compile` across all modules — fix any wiring errors
- [ ] Run `mvn test` across all modules — fix broken tests
- [ ] Update existing unit tests: replace static method mocking with constructor injection of mocks
- [ ] Where feasible, use `@InjectTest` with `@Mock`/`@Spy` for integration-style tests
- [ ] Verify all tests pass
- [ ] Delete now-unused static factory classes: `KnowledgeServiceFactory`, `Neo4jConnectionManager` (if not already deleted in Phase 8)
- [ ] Delete the old `AbstractServer`-extending `Server` class bodies from UI and API modules (replaced by thin `main()`-only classes)
- [ ] Remove redundant static imports across all files
- [ ] Evaluate whether to keep or eliminate the `KnowledgeServices` record
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

6. **Circular dependency resolved by extracting `ProcedureKnowledgeCollectionService`** — rather than using `Provider<T>` (which defers detection to runtime), extracting the shared knowledge collection methods (`triggerNewProcedureFlow`, `triggerEditProcedureFlow`) into a third class produces a clean acyclic object graph. `KnowledgeService` and `KnowledgeIngestionService` remain method parameters on the knowledge collection methods — they are already passed by callers and injecting them would add no value.

7. **Stateless agents promoted to `@Bean @Singleton`** — agents that need no per-task parameters (`UiStateCheckAgent`, `KnowledgeSuggestionAgent`, `KnowledgeCollectionElementResolutionAgent`) become `@Singleton` beans produced by `AgentFactory` (`@Factory`), enabling injection into `@Singleton` tool classes.

8. **`AgentFactory` injects only `ModelFactory` and `UiTestAgentConfig`** — injecting tool singletons (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`) into `AgentFactory`'s constructor would create a circular dependency: those tool classes inject `UiStateCheckAgent`, which is a `@Bean` produced by `AgentFactory` itself. To break the cycle, tool singletons are not constructor-injected into `AgentFactory`; instead, `@Bean` methods that need them use avaje's `@Bean` method parameter injection, and per-task factory methods receive tools from callers.

9. **`EmbeddingStore<TextSegment>` produced by `Neo4jFactory`** — keeps Neo4j infrastructure together. The store bean depends on the `Driver` bean via avaje's `@Bean` parameter injection. No `Session` bean is produced — sessions are short-lived resources that callers open and close per operation via `driver.session(DatabaseConfig)`.

10. **`UiAbstractTools` refactored to constructor injection** — the no-arg constructor that internally creates `UiStateCheckAgent` via static calls is removed. All six subclasses (`CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UiElementDbTools`, `KnowledgeElementTools`) become DI-managed singletons and pass their injected `UiStateCheckAgent` to `super()`. `SpinnerTools` does not extend `UiAbstractTools` and stays as `new` at call sites.

11. **`VerificationTools` injects `BudgetManager` and `AgentConfig`** — it calls `resetToolCallUsage()` and `getVerificationRetryPolicy()`, both of which are currently static imports that become instance method calls after DI.

12. **`StepExecutionOrchestrator` injects `BudgetManager`** — it calls `resetToolCallUsage()` four times directly; needs the injected instance.

13. **`ACTION_VERIFICATION_DELAY_MILLIS` is already sourced from `UiTestAgentConfig`** — `StepExecutionOrchestrator` already calls `getActionVerificationDelayMillis()` at field initialization time. After DI this becomes an instance field initialized from the injected config in the constructor.

14. **`UiElementDbTools` does not inject `AgentFactory`** — `UiElementDbTools` creates its internal agents (`UiElementExtendedDescriptionAgent`, `DbUiElementSelectionAgent`) directly via `AiServices.builder()` calls using static model and config imports. After DI, those static usages are replaced by calls on injected `ModelFactory` and `UiTestAgentConfig` instances. Injecting `AgentFactory` is avoided because `AgentFactory` injects `UiElementDbTools`, producing a direct `A → B → A` cycle that avaje rejects at compile time.

15. **`AgentFactory` ↔ tool singleton circular dependency resolved by parameter passing** — `AgentFactory` produces `UiStateCheckAgent` as a `@Bean @Singleton`; all six tool subclasses inject `UiStateCheckAgent`. If `AgentFactory` also injected those tool classes in its constructor, avaje could not satisfy the dependency graph at compile time. The resolution: `AgentFactory` injects only `ModelFactory` and `UiTestAgentConfig`; `@Bean` methods that need tool singletons receive them as `@Bean` method parameters; per-task factory methods that need tools receive them from callers.

16. **`@Bean` method parameter injection breaks the `getKnowledgeCollectionElementResolutionAgent()` tool dependency problem** — avaje injects parameters of `@Bean` producer methods at bean-creation time independently from the `@Factory` class constructor. `getKnowledgeCollectionElementResolutionAgent()` can therefore declare `ElementLocatorTools` and `UiElementDbTools` as method parameters and receive them as ready singletons.

17. **`AgentConfig` static field initializers must migrate into the constructor body** — Java instance fields cannot invoke instance methods in their initializers. All ~40 `ConfigProperty` assignments and the `Properties` loading must appear as sequential statements in the `AgentConfig` constructor. The `protected static` helper methods are converted to `protected` instance methods so that subclass constructors can call them after `super()` returns without triggering static-context issues.

18. **`BoundingBox` stays as a record — config passed as parameter** — records cannot be DI beans (final, no mutable fields). The `getActualBoundingBox()` method is refactored to accept a `boolean isAlreadyNormalized` parameter instead of calling `UiTestAgentConfig.isBoundingBoxAlreadyNormalized()` statically. Callers (which are DI-managed) pass the value from their injected config.

19. **Swing dialogs receive config as constructor parameters, not DI beans** — `ProcedureKnowledgeCollectionDialog` and `ExistingProcedureLookupDialog` are created on-demand in Swing EDT context. Making them DI beans is inappropriate. Instead, their callers (which are DI-managed `@Singleton` beans) pass the required config values and repository instances as constructor parameters.

20. **`UiElementRefinementHelper`, `UiElementDialogHelper`, and `ImageUtils` promoted to `@Singleton`** — these static utility classes call `AgentConfig`/`UiTestAgentConfig` static methods. After DI, they become `@Singleton` beans that inject the config instances. Their static methods become instance methods. All callers that are already DI-managed inject these helpers instead of using static calls.

21. **`AgentCardProducer` refactored to receive config as parameters** — both UI and API versions have a static field initialized from `AgentConfig.getExternalUrl()`. After DI, the external URL is passed as a parameter from the `@Factory` `@Bean` method that produces `AgentCard`, which injects `AgentConfig`.
