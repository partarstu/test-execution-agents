# AI-Powered UI Test Execution Agent

This project is a Java-based agent that leverages Generative AI models and Retrieval-Augmented Generation (RAG) to execute test
cases written in a natural language form at the graphical user interface (GUI) level. It understands explicit test case instructions
(both actions and verifications), performs corresponding actions using its tools (like the mouse and keyboard), locates the required UI
elements on the screen (if needed), and verifies whether actual results correspond to the expected ones using computer vision capabilities.

[![Package Project](https://github.com/partarstu/ui-test-execution-agent/actions/workflows/package.yml/badge.svg)](https://github.com/partarstu/ui-test-execution-agent/actions/workflows/package.yml)

Here the corresponding article on
Medium: [AI Agent That's Rethinking UI Test Automation](https://medium.com/@partarstu/meet-the-ai-agent-thats-rethinking-ui-test-automation-d8ef9742c6d5)

This agent can be a part of any distributed testing framework which uses A2A protocol for communication between agents. An example of
such a framework is [Agentic QA Framework](https://github.com/partarstu/agentic-qa-framework). This agent has been tested as
a part of this framework for executing a sample test case inside Google Cloud.

## Key Features

* **Modular Agent Architecture:**
    * The agent itself is built around a modular sub-agent architecture with specialized AI sub-agents:
        * **[UiPreconditionActionAgent](src/main/java/org/tarik/ta/agents/UiPreconditionActionAgent.java):** Handles the execution of
          precondition actions before test case execution. Receives a screenshot of the current screen state to provide visual context for
          tool selection.
        * **[UiPreconditionVerificationAgent](src/main/java/org/tarik/ta/agents/UiPreconditionVerificationAgent.java):** Verifies that
          preconditions are fully met.
        * **[UiTestStepActionAgent](src/main/java/org/tarik/ta/agents/UiTestStepActionAgent.java):** Executes individual test step actions.
          Receives a screenshot of the current screen state to provide visual context for tool selection.
        * **[UiTestStepVerificationAgent](src/main/java/org/tarik/ta/agents/UiTestStepVerificationAgent.java):** Verifies the expected results
          after each test step.
        * **[TestCaseExtractionAgent](../agent_core/src/main/java/org/tarik/ta/core/agents/TestCaseExtractionAgent.java):** Extracts and parses test case
          from received task content.
        * **[UiElementBoundingBoxAgent](src/main/java/org/tarik/ta/agents/UiElementBoundingBoxAgent.java):** Identifies UI element bounding
          boxes on screen (visual grounding).
        * **[BestUiElementMatchSelectionAgent](src/main/java/org/tarik/ta/agents/BestUiElementMatchSelectionAgent.java):** Selects the best and correct
          element from multiple candidates (visual grounding).
        * **[UiElementDescriptionAgent](src/main/java/org/tarik/ta/agents/UiElementDescriptionAgent.java):** Generates new UI element info
          suggestions in order accelerate the execution in supervised mode.
        * **[UiStateCheckAgent](src/main/java/org/tarik/ta/agents/UiStateCheckAgent.java):** Checks the current state of the UI against
          an expected one.
        * **[DbUiElementSelectionAgent](src/main/java/org/tarik/ta/agents/DbUiElementSelectionAgent.java):** When
          multiple UI elements in the database match the description (same or similar names), this agent analyzes the current screenshot and selects the
          best matching element based on all element information (name, description, location details/parent context, and parent element info).
        * **[UiElementExtendedDescriptionAgent](src/main/java/org/tarik/ta/agents/UiElementExtendedDescriptionAgent.java):** Generates
          extended descriptions for UI elements based on screenshots and initial descriptions.
        * **[ImageVerificationAgent](src/main/java/org/tarik/ta/agents/ImageVerificationAgent.java):** Performs visual verification of test
          step results against expected outcomes using screenshots.
        * **[PageDescriptionAgent](src/main/java/org/tarik/ta/agents/PageDescriptionAgent.java):** Describes the current page context. This
          can be
          used for various purposes such as understanding the current UI state.
        * **[KnowledgeSuggestionAgent](src/main/java/org/tarik/ta/agents/KnowledgeSuggestionAgent.java):** AI agent that suggests
          prerequisites, effects, and child steps for procedures. When creating a new procedure, it receives the test case context and
          accumulated execution effects to ground its suggestions in the current state. When editing an existing procedure, it receives the parent chain, the
          procedure's established prerequisites (which must be reused), and the test case context.
    * Each agent can be independently configured with its own AI model (name and provider) and system prompt version via
      `config.properties`.

* **Budget Management:**
    * The [BudgetManager](../agent_core/src/main/java/org/tarik/ta/core/manager/BudgetManager.java) provides comprehensive execution
      control:
        * **Time Budget:** Configurable maximum execution time for the test case execution(`agent.execution.time.budget.seconds`).
        * **Token Budget:** Limits total token consumption across all models during test case execution (`agent.token.budget`).
        * **Tool Call Budget:** Limits max tool calls for each agent (`agent.tool.calls.budget`).
        * Tracks token usage per model (input, output, cached, total).
        * Automatically interrupts execution in unattended mode if budget is exceeded.

* **Enhanced Error Handling:**
    * Structured error handling with [ErrorCategory](../agent_core/src/main/java/org/tarik/ta/core/error/ErrorCategory.java) enum:
        * `TERMINATION_BY_USER`: User-initiated interruption (no retry).
        * `VERIFICATION_FAILED`: Verification failures (retryable).
        * `TRANSIENT_TOOL_ERROR`: Temporary failures like network issues (retryable).
        * `NON_RETRYABLE_ERROR`: Fatal errors (no retry).
        * `TIMEOUT`: Execution timeouts (bounded retry if budget allows).
    * [RetryPolicy](../agent_core/src/main/java/org/tarik/ta/core/error/RetryPolicy.java) for configurable retry behavior:
        * Maximum retries, delay between retries, and total timeout.
    * [DefaultToolErrorHandler](../agent_core/src/main/java/org/tarik/ta/core/model/DefaultToolErrorHandler.java) provides centralized
      error handling with retry logic.
    * [RetryState](../agent_core/src/main/java/org/tarik/ta/core/error/RetryState.java) for tracking retry attempts and elapsed time.

* **Element Location Prefetching:**
    * Configurable UI element location prefetching (`prefetching.enabled`) for improved performance in unattended mode.
    * When enabled, the UI element from the next test step (if applicable) will be located on the screen without waiting for the test
      step verification of the previous step to complete. This allows to reduce test execution time, especially if the used LLM is slow
      in visual grounding tasks.

* **Screen Video Recording:**
    * Built-in screen video recording capability for debugging and documentation:
        * `screen.recording.active`: Enable/disable recording.
        * `screen.recording.output.dir`: Output directory for recordings.
        * `recording.bit.rate`: Video bitrate configuration.
        * `recording.file.format`: Output format (default: mp4).
        * `recording.fps`: Frames per second for recording.

* **HDR Screenshot Correction:**
    * Optional sRGB gamma correction for screenshots captured on HDR-enabled monitors.
    * Controlled by `hdr.color.correction.enabled` or `HDR_COLOR_CORRECTION_ENABLED`.
    * Keeps Windows HDR enabled while avoiding washed-out screenshot colors in model inputs and saved captures.

* **AI Model Integration:**
    * Utilizes the [LangChain4j](https://github.com/langchain4j/langchain4j) library to seamlessly interact with various Generative AI
      models.
    * Supports all major LLMs, provides explicit configuration for models from Google (via AI Studio or Vertex AI), Azure OpenAI,
      Groq and Anthropic. Configuration is managed through `config.properties` and `AgentConfig.java`, allowing specification of providers,
      model names, API keys/tokens, endpoints, and generation parameters (temperature, topP, max output tokens, retries).
    * Each specialized agent can use a different AI model, configured independently:
        * Model name: `<agent>.model.name` (e.g., `precondition.agent.model.name`)
        * Model provider: `<agent>.model.provider` (e.g., `precondition.agent.model.provider`)
        * Prompt version: `<agent>.prompt.version` (e.g., `precondition.agent.prompt.version`)
    * Uses structured prompts stored in versioned directories under `src/main/resources/prompt_templates/system/agents/` and
      `../agent_core/src/main/resources/prompt_templates/system/agents/`.
    * Includes options for model logging (`model.logging.enabled`) and outputting the model's thinking process (`thinking.output.enabled`).

* **RAG:**
    * Employs a Retrieval-Augmented Generation (RAG) approach to manage information about UI elements.
    * Uses a vector database to store and retrieve UI element details (name, element description, location description, parent element
      description, and screenshot). It supports Chroma DB, Qdrant, and Neo4j, configured via `vector.db.provider` and `vector.db.url` in
      `config.properties`.
    * RAG components are located in the UI module: [RetrieverFactory](src/main/java/org/tarik/ta/rag/RetrieverFactory.java),
      [ChromaRetriever](src/main/java/org/tarik/ta/rag/ChromaRetriever.java), [QdrantRetriever](src/main/java/org/tarik/ta/rag/QdrantRetriever.java),
      [Neo4jRetriever](src/main/java/org/tarik/ta/rag/Neo4jRetriever.java), and
      [UiElementRetriever](src/main/java/org/tarik/ta/rag/UiElementRetriever.java).
    * Stores UI element information as `UiElement` records, which include a name, self-description, description of surrounding
      elements (anchors), a parent element description, and a screenshot (`UiElement.Screenshot`).
    * Retrieves the top N (`retriever.top.n` in config) most relevant UI elements based on semantic similarity between the query (derived
      from the test step action) and based on the stored element names. Minimum similarity scores (`element.retrieval.min.target.score`,
      `element.retrieval.min.general.score`, `element.retrieval.min.page.relevance.score` in config) are used to filter results for target
      element identification and potential refinement suggestions.

* **Computer Vision:**
    * Employs a hybrid approach combining large vision models with traditional computer vision algorithms (OpenCV's ORB and Template
      Matching) for robust UI element location.
    * Leverages a vision-capable AI model to:
        * Identify potential bounding boxes for UI elements on the screen.
        * Disambiguate when multiple visual matches are found or to confirm that a single visual match, if found, corresponds to the target
          element's description and surrounding element information.
    * Uses OpenCV (via `org.bytedeco.opencv`) for visual pattern matching (ORB and Template Matching) to find occurrences of an element's
      stored screenshot on the current screen.
    * Intelligent logic in `ElementLocator` combines results from the vision model and algorithmic matching, considering intersections and
      relevance, to determine the best match.
    * Configurable zoom scaling for element location (`element.locator.zoom.scale.factor`) in case the LLM can't efficiently work with
      high resolutions or the focus on a specific part of the screen is needed in order to avoid too much surrounding noise.
    * Algorithmic search can be enabled/disabled (`element.locator.algorithmic.search.enabled`).
    * Screenshot size conversion logic in case the LLM requires specific dimensions or size (e.g. Claude Sonnet 4.5):
        * `bbox.screenshot.longest.allowed.dimension.pixels`: Maximum dimension for screenshots.
        * `bbox.screenshot.max.size.megapixels`: Maximum screenshot size in megapixels.

* **GUI Interaction Tools:**
    * Provides a set of [tools](src/main/java/org/tarik/ta/tools) for interacting with the GUI using Java's `Robot` class.
    * [MouseTools](src/main/java/org/tarik/ta/tools/MouseTools.java) offer actions for working with the mouse (clicks, hover,
      click-and-drag, etc.).
    * [KeyboardTools](src/main/java/org/tarik/ta/tools/KeyboardTools.java) provide actions for working with the keyboard (typing text into
      specific elements, clearing data from input fields, pressing single keys or key combinations, etc.).
    * [CommonTools](src/main/java/org/tarik/ta/tools/CommonTools.java) include common actions like waiting for a specified duration and
      opening the Chrome browser.
    * [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java ) provides the whole logic for locating a specific
      UI element on the screen based on its description.
    * [CommonUserInteractionTools](src/main/java/org/tarik/ta/tools/CommonUserInteractionTools.java) facilitates user interactions via dialogs for
      element creation, refinement, and verification. **Note:** These tools are only available when running in supervised
      mode.
    * [KnowledgeElementTools](src/main/java/org/tarik/ta/tools/KnowledgeElementTools.java) provides mode-aware UI element handling for
      the knowledge persistence feature. Supports collecting knowledge flow (searches for or creates elements by description) and execution flow
      (locates known elements directly by UUID, bypassing vector DB search).

* **Execution Modes:**
    * Supports three execution modes controlled by the `execution.mode` property in `config.properties`.    
    * **Supervised Mode (`execution.mode=SUPERVISED`):** The agent operates autonomously but allows the operator to intervene.
        * **Countdown Halt:** Displays a countdown popup (configurable duration) after test step actions, allowing the operator to click "
          Halt".
        * **Verification Failure Notification:** When a verification fails (after all automatic retries), the operator is notified with
          details about the failure via a popup before the test execution is terminated.
        * **Element Selection Confirmation:** Displays a popup with a countdown when an element is automatically selected. The operator can
          see the selected element, intended action, and the agent's assessment of whether the located element matches the description, and
          choose to "Proceed" (default), "Create new element", or take "Other action" (prompting the agent).
        * **Operator Intervention:** On halt or failure, the system reuses `UserChoiceDialog` — the same dialog used for ambiguous match resolution — giving the operator the full set of actions: Retry the step, Edit or Browse the current atomic procedure, Create a new procedure, or Cancel (terminates execution).
        * Suitable for monitoring execution without constant clicking, while retaining control to fix issues on the fly.
    * **Unattended Mode (`execution.mode=UNATTENDED`):** The agent executes the test case without any human assistance. It relies entirely
      on the information stored in the RAG database and the AI models' ability to interpret instructions and locate elements based on stored
      data. Errors during element location or verification will cause the execution to fail. This mode is suitable for integration into
      CI/CD pipelines. Budget checks are automatically enforced in this mode.

* **Server mode:**
    * The [Server](src/main/java/org/tarik/ta/Server.java) class
      extends [AbstractServer](../agent_core/src/main/java/org/tarik/ta/core/AbstractServer.java) and is the entry point where a
      Javalin web server is started. The agent registers its capabilities and listens for A2A JSON-RPC requests on the root endpoint (`/`) (
      port configured via `port` in `config.properties`). The endpoint serves the streaming `message/stream` (and `tasks/resubscribe`)
      methods as a Server-Sent Events (SSE) stream and all other methods as a single JSON-RPC response; the agent card advertises
      `capabilities.streaming = true`. The server accepts only one test case execution at a time (the agent has been
      designed as a static utility for simplicity purposes). Upon receiving a valid request when idle, it returns `200 OK` and starts the
      test case execution. If busy, it returns `429 Too Many Requests`.
    * The runtime request lifecycle now mirrors the API agent: [UiAgentExecutor](src/main/java/org/tarik/ta/a2a/UiAgentExecutor.java)
      opens a child request `BeanScope` through [UiAgentRequestScopeFactory](src/main/java/org/tarik/ta/UiAgentRequestScopeFactory.java)
      and resolves a request-scoped [UiTestAgent](src/main/java/org/tarik/ta/UiTestAgent.java) from that scope.
    * The UI request scope now explicitly depends on the shared base request scope, so `LogCapture`, shared test-context tooling, and the
      UI-only runtime `VisualState` are all assembled in the same child `BeanScope` without duplicate default `@InjectModule`
      declarations.

* **Knowledge Persistence (Neo4j):**
    * Optional knowledge persistence layer backed by Neo4j 5.x that enables the agent to learn and remember procedures (reusable
      test action sequences) across sessions.
    * **Procedure Graph:** Stores hierarchical procedures (composite and atomic) as a Neo4j graph with CONTAINS (parent-child) and
      TARGETS (step-to-UI-element) relationships. To ensure data integrity, each procedure is restricted to target at most/exactly one `UiElement`; linking a procedure to a new element automatically detaches and deletes any existing `TARGETS` relationship.
    * **PDDL-Lite Planning:** Prerequisite/effect state tracking enables automatic prerequisite resolution and procedure branching during test execution.
    * **Procedure Branching:** When multiple semantically similar procedures exist with different prerequisites, the execution engine automatically selects the one whose prerequisites are satisfied by the current execution state. This allows defining alternative procedure variants (branches) that execute conditionally based on accumulated effects.
    * **State-Aware Candidate Re-Ranking:** Before selecting the best match, candidates are re-ranked by state compatibility using semantic matching between each procedure's prerequisites and the accumulated execution effects. Prerequisite satisfaction is checked via Cypher vector index queries against native `PhraseEmbedding` nodes (see below), replacing the previous O(N×M) in-memory cosine loop. Procedures with no prerequisites always score `1.0` (universally applicable). Among prerequisite-bearing procedures, the score is the proportion of prerequisites semantically met (similarity ≥ high-confidence threshold). Within equal proportion scores, semantic similarity breaks ties; ancestry affinity is the final tiebreaker. When the top semantic match is demoted by re-ranking, a DEBUG log entry is emitted showing the original semantic score, how many prerequisites were met vs. total, which prerequisites were unmet, whether `effectNodeIds` was empty, and which procedure was selected instead — enabling diagnosis of re-ranking decisions.
    * **Native Phrase Embedding Nodes:** Prerequisites and effects are stored as first-class `PhraseEmbeddingNode` Neo4j nodes (`label: PhraseEmbedding`, properties: `id`, `phrase`, `embedding: float[384]`, `type: PREREQUISITE|EFFECT`) connected to their parent `Procedure` via `HAS_PREREQUISITE {sequence}` and `HAS_EFFECT {sequence}` relationships. A dedicated vector index (`phrase_embedding_vector_index`, cosine similarity, dimension 384) and a phrase-text index enable efficient Cypher-native similarity queries without any Java-side vector arithmetic. Embeddings are computed once at ingestion time by `KnowledgeIngestionService` and batch-created via `PhraseEmbeddingRepository`. `ExecutionStateTracker` tracks accumulated effects as `Map<String, UUID>` (phrase → node ID); precondition satisfaction is resolved by querying the vector index directly in Cypher rather than loading float arrays into memory. If a procedure's `HAS_EFFECT` phrase nodes are missing at execution time (legacy data), a WARN log is emitted and state tracking falls back to string-only mode, which degrades prerequisite semantic matching for subsequent steps.
    * **Startup Phrase Node Migration:** On startup, `PhraseNodeMigrationService` scans for procedures that have non-empty `effects` or `prerequisites` node properties but are missing the corresponding `HAS_EFFECT` or `HAS_PREREQUISITE` phrase embedding edges (a symptom of data ingested before phrase-node creation was part of the pipeline). For each such procedure it deletes any partial phrase nodes and recreates them atomically via `KnowledgeIngestionService`. This ensures `SATISFIES` edge computation and prerequisite semantic matching work correctly for all procedures on first use. Any forward migration or backward repair failure during this startup sequence triggers an `IllegalStateException`, immediately aborting startup with clear diagnostic logs.
    * **Async Persistence Reliability:** All asynchronous background state persistence operations (such as saving steps, test cases, execution nodes, and run timings) are run on virtual threads and wrapped in try-catch blocks with robust error logging, ensuring background database operations do not silently fail or impact execution flow.
    * **Jackson JSON Node Storage:** Each `Procedure` Neo4j node stores its scalar properties (name, description, prerequisites/effects as plain phrase strings, timing/stability metadata, etc.) as a single Jackson-serialized JSON string in a `data` property. The `id`, `description`, and `embedding` properties are kept as dedicated Neo4j properties for vector index use. Phrase embedding vectors are stored separately as `PhraseEmbeddingNode` nodes — not inside the Procedure JSON — keeping the Procedure payload lightweight and enabling selective loading.
    * **Queue-Based Execution:** Replaces the sequential for-loop with a dynamic execution queue that injects prerequisite steps when
      prerequisites are unmet.
    * **Human-in-the-Loop Collecting knowledge:** In SUPERVISED mode, the agent triggers a Swing dialog for operators to collect knowledge new
      procedures when an unknown action is encountered. The dialog is shown **immediately** without waiting for AI suggestions — AI
      suggestions are loaded concurrently on a background virtual thread and injected into the still-open dialog once ready.
        * **Ambiguous Match Resolution:** If the agent cannot automatically resolve the procedure — due to low confidence, unmet
          prerequisites, or no match at all — it presents a `UserChoiceDialog` allowing the operator to choose an existing
          procedure to edit, retry the search, create a new one, or **Browse All...** to open a scored lookup of all semantically
          matching procedures. The browse lookup option is now always offered in the fallback dialog, even if zero semantic matches are found.
          The browse lookup (`MatchingProcedureBrowseDialog`) and the existing-procedure lookup
          (`ExistingProcedureLookupDialog`) share a common abstract base (`ProcedureLookupDialog`) that handles the search field,
          debounced search, scored results list (showing prerequisite satisfaction as `satisfied/total` badges colored green/orange/red),
          and spinner overlay. Selecting a procedure from the browse dialog opens it for editing; cancelling returns to `UserChoiceDialog`.
          When editing a child step, the "Edit Parent" navigation button in recursive dialogs directly returns the operator to the originating
          parent's dialog only, bypassing the parent selection dialog even if the child is shared among multiple parent procedures.
          The three previous `ProcedureLookup` variants (`LowConfidenceMatch`, `NoProcedureWithFulfilledPrerequisites`, `NoMatchFound`)
          are collapsed into a single `NeedsUserResolution` record carrying an optional `MatchResult` and missing-prerequisite list,
          from which a precise reason message is derived.
        * **Element Selection During Collecting knowledge:** When collecting knowledge an atomic procedure that targets a UI element, the operator is prompted
          to describe the target element. The system performs a semantic search against the vector DB to find a matching element. If found,
          its UUID is linked to the step being collected. If not found, the element will be created during knowledge ingestion.        
        * **Auto-Select Element (Supervised Mode):** When the collecting knowledge dialog opens in Supervised mode, the system automatically
          queries the vector store for a high-score element match (above `element.retrieval.min.target.score`). If found, the element is
          selected automatically and its screenshot is shown in the dialog. The operator can override via the "Refine Elements..." popup.
          If no high-score element exists, the dialog falls back to agent-driven element resolution.
        * **Confirmation Popup Scoping:** The post-execution confirmation popup (`ProcedureExecutionConfirmationPopup`) is only shown for
          **pre-existing** procedures. Newly collected procedures skip the popup because the operator just interacted with the collecting knowledge
          dialog and there is nothing additional to confirm.
        * **Prerequisite Failure Handling:** If no procedure branch has its prerequisites satisfied by the current execution state, the test execution fails with a descriptive error listing which prerequisites are missing. In SUPERVISED mode the operator is prompted with the standard EDIT/CREATE/RETRY selection; in UNATTENDED mode the test terminates immediately.
        * **Procedure Usage Tracking:** The agent automatically tracks which test cases use which procedures using `USES_PROCEDURE` edges in the graph. When an operator edits a procedure that is used by multiple test cases, a warning popup is displayed to highlight the potential impact across the test suite. Stale usage edges are automatically cleaned up with dedicated error handling only upon successful test execution runs to avoid premature cleanup on aborted or failed executions.
    * **SATISFIES Edges — Pre-Computed Precondition Satisfaction:** Persists `SATISFIES` relationships directly between Procedure nodes to record that an effect of one procedure satisfies a precondition of another. Each edge carries a cosine similarity `score`, matched `effectPhrase`/`precondPhrase` texts, and lifecycle timestamps (`createdAt`, `lastVerifiedAt`). Computed asynchronously by `SatisfiesEdgeService` after each successful step: a virtual thread fires N parallel similarity comparisons (one per effect embedding), deduplicates by maximum score per consumer procedure, filters by `satisfies.similarity.threshold` (default `0.85`), and batch-persists the results. This replaces the previous O(N×M) per-step cosine loop with a single graph traversal during re-ranking and enables cross-run caching — a match is never recomputed until the procedure is edited. Edges are deleted transactionally when a procedure is modified. Stale edges (not verified within `satisfies.stale.days`) are flagged during health checks and cleaned up via `GraphHealthService.runStaleSatisfiesEdgeCleanup()`. In unattended mode, persistence happens asynchronously before execution starts. In supervised mode, SATISFIES persistence is fully synchronous to ensure consistency when a user edits or retries procedures.
    * **Ordering Conflict Detection:** At the start of execution in supervised mode only, the engine queries the `SATISFIES` graph to detect when a test step B appears before step A but B's preconditions require an effect that A produces — a test authoring error. Conflicts are displayed as a warning `InformationalPopup` and do not block execution. Skipped entirely when no `SATISFIES` edges exist yet (cold graph).
    * **Knowledge-Driven Failure Recovery:** The system learns from past failures via `FailureContext` nodes linked to procedures via `HAS_FAILURE_CONTEXT` edges. Each node stores a failure `symptom`, `category` (using `ErrorCategory`), user-provided `resolution`, `occurrences` count, a `mode` (`SUPERVISED`, `SUPERVISED_TIMEOUT`, or `UNATTENDED`), and timestamps. Before executing a procedure, the agent retrieves failure hints via `FailureContextService.findFailureHints()` — hints with `mode == SUPERVISED_TIMEOUT` (auto-captured on dialog timeout, with no resolution) are excluded. Non-empty hints are injected into the agent's prompt as a "Known issues" section by `StepExecutionOrchestrator`. After retry exhaustion: in supervised mode a `FailureContextCaptureDialog` prompts the operator (countdown, default 60s) with error category pre-selected and symptom pre-filled; in unattended mode context is auto-captured from the error category. Deduplication via `MERGE` on `(procedureId, category, symptomNormalized)` increments `occurrences` on repeat failures. Orphaned `FailureContext` nodes are cleaned up by `FailureContextService.cleanupOrphanedFailureContexts()` after procedure deletion.
    * **Element Stability Index:** Reliability metrics stored on `UiElement` nodes: `stabilityScore` (EWMA α=0.3, initialised optimistically at `1.0`; first failure drops to `0.70`), `avgLocationTimeMs`, `locationStrategy`, `failedLocationCount`, and `lastLocatedAt`. `StabilityRecorder` is a `@FunctionalInterface` (`record(UUID elementId, boolean located, long locationTimeMs, String strategy)`) wired as a lambda delegating to `ProcedureRepository.updateElementStability()`. `ElementLocatorTools` calls it after every location attempt. Elements with `stabilityScore < stability.penalty.threshold` are flagged as low-stability: a warning is logged in all execution modes, and in supervised mode a warning popup is shown right before the procedure starts executing. Unstable elements use a try-first, wait-and-retry strategy instead of a proactive pre-sleep.
    * **Procedure Execution Timing Profiles:** Per-procedure timing stored as rolling averages (EWMA α=0.2) on Procedure nodes: `avgExecutionMs`, `avgVerificationDelayMs`, `maxVerificationDelayMs` (with 0.95 decay factor), and `lastTimingUpdate`. `TimingRecorder` is a `@FunctionalInterface` (`record(UUID procedureId, long executionMs, long verificationDelayMs)`) wired via `ProcedureRepository::updateTimingProfile`. The post-action verification delay adapts per-procedure: `Math.max(timing.verification.min.delay.ms, avgVerificationDelayMs)`, falling back to the global `action.verification.delay.millis` when no profile exists yet. `maxVerificationDelayMs` decays gradually so one-off spikes don't permanently inflate wait times.
    * **Ancestry Context for Matching:** A bounded sliding window (`ancestry.window.size`, default 5) of recently-matched parent procedure IDs is tracked in `ExecutionStateTracker`. During candidate re-ranking, procedures that share a parent (via `CONTAINS` relationship) with recently-executed procedures receive an ancestry affinity boost, helping disambiguate procedures with similar semantic scores that belong to different workflow contexts.
    * **`KnowledgeServices` Facade:** A single `KnowledgeServices` record bundles the five core knowledge components: `KnowledgeService`, `KnowledgeIngestionService`, `SatisfiesEdgeService`, `ProcedureRepository` (write instance), and `FailureContextService`. Created by `KnowledgeServiceFactory.createKnowledgeServices()` and passed as a single object to `KnowledgeBasedExecutionOrchestrator` and `StepExecutionOrchestrator`, replacing the previous pattern of passing individual services.
    * **`AtomicStepExecutionContext`:** A record that bundles execution-scoped parameters flowing through the atomic step call chain: `Optional<TimingProfile> timingProfile`, `TimingRecorder timingRecorder`, `List<String> failureHints`, `String elementId`, and `String effectiveExpectedResults`. Replaces individual parameters in `StepExecutionOrchestrator` methods, enabling failure hint injection without adding service dependencies in the orchestrator.
    * **Knowledge Graph Health Dashboard:** `GraphHealthService` runs seven read-only health-check queries and returns a `GraphHealthReport` (record with `List<HealthCheckCategory>` and `generatedAt` timestamp). Each `HealthCheckCategory` carries a `Severity` (`OK` / `WARNING` / `CRITICAL`) computed from finding count against configurable thresholds (`health.warning.threshold`, `health.critical.threshold`). Checks covered: orphaned UI elements, leaf procedures without a target element, deep hierarchies (above `knowledge.max.depth`), disconnected procedures (procedures with no parent in the hierarchy), procedures with missing effects, stale `SATISFIES` edges, and orphaned `FailureContext` nodes. At startup, the service automatically runs stale edge cleanup to delete `SATISFIES` edges not verified within `satisfies.stale.days`. Key API:
        * `logHealthReport()` — logs a structured report at INFO level.
        * `generateHtmlReport(Path outputPath)` — writes a self-contained HTML file with color-coded summary cards (green=OK, yellow=WARNING, red=CRITICAL) and collapsible detail sections. No external template dependencies. Defaults to the `health.report.output.path` config path.
        * `runStaleSatisfiesEdgeCleanup()` — deletes `SATISFIES` edges not verified within `satisfies.stale.days`.
    * **Unified Vector Store:** UI element storage can use Neo4j (via `langchain4j-community-neo4j`), providing both graph relationships
      and vector search in a single database.
    * `neo4j.password` (or `NEO4J_PASSWORD`) must be set to a non-blank value.
    * `location.history.and.failure.hints.collection.enabled` (or `LOCATION_HISTORY_AND_FAILURE_HINTS_COLLECTION_ENABLED`) - disable location history and failure hints collection by default (recommended for local or supervised mode). Set to `true` to enable collection (recommended for CI/CD pipelines). Retrieval of existing hints and history remains enabled regardless of this setting.

## Test Case Execution Workflow

The test execution process, orchestrated by the [UiTestAgent](src/main/java/org/tarik/ta/UiTestAgent.java) class using the `KnowledgeBasedExecutionOrchestrator`, follows these steps:

1. **Test Case Processing:** The agent parses the received message (task)
   using [TestCaseExtractor](../agent_core/src/main/java/org/tarik/ta/core/utils/TestCaseExtractor.java),
   extracts the required information and converts it into a test case object. This file contains the overall test case name, optional
   `preconditions` (natural language description of the required state before execution), and a list of `TestStep`s. Each `TestStep`
   includes a `stepDescription` (natural language instruction), optional `testData` (inputs for the step), and `expectedResults`
   (natural language description of the expected state after the step).
2. **Starting Step Selection:** In Supervised mode, the operator can choose to start execution from a specific test step.
   In Unattended mode, execution always starts from the first step.
3. **Queue Creation:** Both test case preconditions (if any) and test steps are combined into a single unified `ExecutionQueue`. The `ExecutionStateTracker` is initialized to track accumulated effects throughout the test run.
4. **Knowledge-Based Iteration:** For each item in the queue (precondition or test step):
    * **Procedure Matching:** The agent queries the knowledge base for a matching procedure based on the item's description.
    * **State-Aware Resolution:** If candidates are found, the agent evaluates their prerequisites against the current execution state to select a feasible procedure.
    * **Human-in-the-Loop Fallback:** If no match is found, or if no candidate's prerequisites are met, the agent triggers a knowledge collection flow (in Supervised mode) for the operator to create or refine a procedure. In Unattended mode, the execution fails.
    * **Decomposition:** Composite procedures are decomposed into their constituent atomic child steps.
5. **Atomic Step Execution:** For each resolved atomic step:
    * **Screenshot Capture:** A screenshot of the current screen is captured to provide visual context.
    * **Target Resolution:** If the atomic step is linked to a known UI element, the agent attempts to locate it directly, bypassing full semantic searches. Otherwise, standard element location logic applies.
    * **Action Execution:** The configured action agent (e.g., `UiTestStepActionAgent` or `UiPreconditionActionAgent`) interacts with the system using appropriate tools.
    * **Verification:** Following a short delay (`action.verification.delay.millis`), the verification agent confirms the step's expected results against a new screenshot using a vision model.
    * **State Tracking:** Upon successful verification, the atomic step's effects are added to the `ExecutionStateTracker`, influencing subsequent prerequisite resolution.
    * **Retry Logic:** If a tool execution or verification reports that retrying makes sense, the agent retries the execution/verification after a short delay up to a configured timeout.
6. **Completion/Termination:** Execution continues until the queue is exhausted or an interruption (error, unrecoverable verification failure, or user termination) occurs. The final `UiTestExecutionResult` is returned.

### UI Element Location Workflow

The [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java ) class is responsible for finding the coordinates
of a target UI element based on its natural language description provided by the instruction model during an action step. This involves a
combination of RAG, computer vision, analysis, and potentially user interaction (if run in a supervised mode):

1. **RAG Retrieval:** The provided UI element's description is used to query the vector database, where the top N (`retriever.top.n`) most
   semantically similar `UiElement` records are retrieved based on their stored names, using embeddings generated by
   the `all-MiniLM-L6-v2` model. Results are filtered based on configured minimum similarity scores (`element.retrieval.min.target.score`
   for high confidence, `element.retrieval.min.general.score` for potential matches) and `element.retrieval.min.page.relevance.score` for
   relevance to the current page.
2. **Handling Retrieval Results:**
    * **High-Confidence Match(es) Found:** If one or more elements exceed the `MIN_TARGET_RETRIEVAL_SCORE` and/or
      `MIN_PAGE_RELEVANCE_SCORE`:
        * **Hybrid Visual Matching:**
            * A vision model is used to identify potential bounding boxes for UI elements that visually resemble the target element on the
              current screen.
            * Concurrently, OpenCV's ORB and Template Matching algorithms are used to find additional visual matches of the element's stored
              screenshot on the current screen.
            * The results from both the vision model and algorithmic matching are combined and analyzed to find common or best-fitting
              bounding boxes.
        * **Disambiguation (if needed):** If multiple candidate bounding boxes are found, the vision model is employed to select the single
          best match that corresponds to the target element's description and the description of surrounding elements (anchors), based on a
          screenshot showing all candidate bounding boxes highlighted with specific color and having unique ID labels.
    * **Low-Confidence/No Match(es) Found:** If no elements meet the `MIN_TARGET_RETRIEVAL_SCORE` or `MIN_PAGE_RELEVANCE_SCORE`, but some
      meet the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Supervised Mode:** The agent displays a popup showing a list of the low-scoring potential UI element candidates. The user can
          choose to:
            * **Update** one of the candidates by refining its name, description, anchors, or parent element info and save the updated
              information to the vector DB.
            * **Delete** a deprecated element from the vector DB.
            * **Create New Element** (see below).
            * **Retry Search** (useful if elements were manually updated).
            * **Terminate** the test execution (e.g., due to an AUT bug).
        * **Unattended Mode:** The location process fails.
    * **No Matches Found:** If no elements meet even the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Supervised Mode:** The user is guided through the new element creation flow:
            1. The user draws a bounding box around the target element on a full-screen capture.
            2. The captured element screenshot with its description are sent to the vision model to generate a suggested detailed name,
               self-description, surrounding elements (anchors) description, and parent element info.
            3. The user reviews and confirms/edits the information suggested by the model.
            4. The new `UiElement` record (with UUID, name, descriptions, parent element info, screenshot) is stored into the vector DB.
        * **Unattended Mode:** The location process fails.

## Setup Instructions

### Prerequisites

* Java Development Kit (JDK) - Version 25 or later recommended.
* Apache Maven - For building the project.
* Chroma, Qdrant, or Neo4j vector database.
* Subscription to an AI model provider (Google Cloud/AI Studio, Azure OpenAI, or Groq).
* (Optional) Neo4j 5.x for knowledge persistence feature.

### Maven Setup

This project uses Maven for dependency management and building.

1. **Clone the Repository:**
   ```bash
   git clone <repository_url>
   cd <project_directory>
   ```

2. **Build the Project:**
   ```bash
   mvn clean package
   ```
   This command downloads dependencies, compiles the code, runs tests (if any), and packages the application into a standalone JAR file in
   the `target/` directory.

### Vector DB Setup

Instructions for setting up the supported vector databases (Chroma DB or Qdrant) can be found on their official websites.

### Neo4j Setup (for Knowledge Persistence)

For local development, start a Neo4j Community Edition container:

```bash
docker run -d --name neo4j-knowledge \
  -p 7687:7687 -p 7474:7474 \
  -e NEO4J_AUTH="neo4j/your-secure-password" \
  -v neo4j-data:/data \
  neo4j:5-community
```

Configure the agent by setting the following in `config.properties` or via environment variables:

```properties
vector.db.url=bolt://localhost:7687
neo4j.username=neo4j
vector.db.key=your-secure-password
neo4j.database=neo4j
```

The Vector DB key is always required. The agent will throw an error at startup if the key is not configured.

### Configuration

Configure the agent by editing the [config.properties](src/main/resources/config.properties) file or by setting environment variables. *
*Environment variables
override properties file settings.**

**Key Configuration Properties:**

**Basic Agent Configuration:**

* `execution.mode` (Env: `EXECUTION_MODE`): Mode of execution (`SUPERVISED`, `UNATTENDED`). Default: `UNATTENDED`.
* `supervised.countdown.seconds` (Env: `SUPERVISED_COUNTDOWN_SECONDS`): Duration in seconds for the countdown popup in supervised
  mode. Default: `5`.
* `debug.mode` (Env: `DEBUG_MODE`): `true` enables debug mode, which saves intermediate screenshots (e.g., with bounding boxes drawn)
  during element location for debugging purposes. `false` disables this. Default: `false`.
* `port` (Env: `PORT`): Port for the server mode. Default: `8005`.
* `host` (Env: `AGENT_HOST`): Host address for the server mode. Default: `localhost`.
* `LOG_LEVEL` (Env: `LOG_LEVEL`): Global log level for the agent and key dependencies like LangChain4j and A2A SDK. Default: `INFO`.

**RAG Configuration:**

* `vector.db.provider` (Env: `VECTOR_DB_PROVIDER`): Vector database provider. Default: `chroma`.
* `vector.db.url` (Env: `VECTOR_DB_URL`): Required URL for the vector database connection. Default: `http://localhost:8020`.
* `retriever.top.n` (Env: `RETRIEVER_TOP_N`): Number of top similar elements to retrieve from the vector DB based on semantic element name
  similarity. Default: `5`.

**Neo4j Configuration:**

* `neo4j.username` (Env: `NEO4J_USERNAME`): Neo4j username. Default: `neo4j`.
* `neo4j.database` (Env: `NEO4J_DATABASE`): Neo4j database name. Default: `neo4j`.

**Knowledge Configuration:**

* `knowledge.embedding.model` (Env: `KNOWLEDGE_EMBEDDING_MODEL`): Embedding model used for knowledge-graph semantic search and procedure matching. Default: `multilingual-e5-small`. Supported values:
    * `multilingual-e5-small` (default) — 384-dimensional multilingual model. On first startup the ONNX model files (`model.onnx`, `tokenizer.json`) are automatically downloaded from Hugging Face (`intfloat/multilingual-e5-small`) to `~/.cache/test-execution-agents/models/multilingual-e5-small/`. Document embeddings (ingestion) use the `"passage: "` prefix; query embeddings (semantic search) use the `"query: "` prefix.
    * `bge-small-en-v15` — 384-dimensional English-only model bundled in the JAR; no download required. Suitable for English-only test suites.
    * **Rollout / Migration Note:** Switching active embedding models after data has been ingested requires re-embedding all stored procedures to avoid vector mismatch. The agent performs startup validation checks to ensure the resolved model's dimensions match the required Neo4j vector indexes (384-dimensional). Unsupported values or dimension mismatches will abort application startup.
* `knowledge.max.depth` (Env: `KNOWLEDGE_MAX_DEPTH`): Maximum procedure decomposition depth. Default: `3`.
* `knowledge.embedding.batch.size` (Env: `KNOWLEDGE_EMBEDDING_BATCH_SIZE`): Batch size for embedding generation. Default: `10`.
* `knowledge.match.confidence.high` (Env: `KNOWLEDGE_MATCH_CONFIDENCE_HIGH`): High-confidence match threshold. Default: `0.85`.
* `knowledge.match.confidence.low` (Env: `KNOWLEDGE_MATCH_CONFIDENCE_LOW`): Low-confidence match threshold. Default: `0.5`.
* `knowledge.query.timeout.seconds` (Env: `KNOWLEDGE_QUERY_TIMEOUT_SECONDS`): Neo4j query timeout in seconds. Default: `60`.

**Knowledge Graph Enhancement Configuration:**

* `satisfies.similarity.threshold` (Env: `SATISFIES_SIMILARITY_THRESHOLD`): Minimum cosine similarity for creating a `SATISFIES` edge between procedures. Default: `0.85`.
* `satisfies.stale.days` (Env: `SATISFIES_STALE_DAYS`): Number of days after which a `SATISFIES` edge not verified is considered stale. Default: `30`.
* `ancestry.window.size` (Env: `ANCESTRY_WINDOW_SIZE`): Number of recent parent procedure IDs tracked for ancestry affinity boosting during re-ranking. Default: `5`.
* `timing.ewma.alpha` (Env: `TIMING_EWMA_ALPHA`): EWMA smoothing factor for procedure execution timing profiles. Default: `0.2`.
* `timing.verification.min.delay.ms` (Env: `TIMING_VERIFICATION_MIN_DELAY_MS`): Minimum verification delay (floor) applied even when the timing profile suggests a shorter wait. Default: `500`.
* `stability.ewma.alpha` (Env: `STABILITY_EWMA_ALPHA`): EWMA smoothing factor for element stability score updates. Default: `0.3`.
* `stability.penalty.threshold` (Env: `STABILITY_PENALTY_THRESHOLD`): Elements with a stability score below this value are flagged as low-stability. A warning is logged in all modes; in supervised mode a warning popup is shown right before the procedure starts executing. Default: `0.5`.
* `failure.capture.dialog.timeout.seconds` (Env: `FAILURE_CAPTURE_DIALOG_TIMEOUT_SECONDS`): Countdown timeout for the failure context capture dialog in supervised mode. Auto-captures as `SUPERVISED_TIMEOUT` on expiry. Default: `60`.
* `health.report.output.path` (Env: `HEALTH_REPORT_OUTPUT_PATH`): Output path for the generated HTML health report. Default: `reports/graph-health-report.html`.
* `health.warning.threshold` (Env: `HEALTH_WARNING_THRESHOLD`): Finding count at which a health check category escalates to WARNING severity. Default: `3`.
* `health.critical.threshold` (Env: `HEALTH_CRITICAL_THRESHOLD`): Finding count at which a health check category escalates to CRITICAL severity. Default: `10`.

**Model Configuration:**

* `model.max.output.tokens` (Env: `MAX_OUTPUT_TOKENS`): Maximum amount of tokens for model responses. Default: `8192`.
* `model.temperature` (Env: `TEMPERATURE`): Sampling temperature for model responses. Default: `0.0`.
* `model.top.p` (Env: `TOP_P`): Top-P sampling parameter. Default: `1.0`.
* `model.max.retries` (Env: `MAX_RETRIES`): Max retries for model API calls. Default: `10`.
* `model.logging.enabled` (Env: `LOG_MODEL_OUTPUT`): Enable/disable model logging. Default: `false`.
* `thinking.output.enabled` (Env: `OUTPUT_THINKING`): Enable/disable thinking process output. Default: `false`.
* `gemini.thinking.budget` (Env: `GEMINI_THINKING_BUDGET`): Budget for Gemini thinking process. Default: `0`.
* `verification.model.max.retries` (Env: `VERIFICATION_MODEL_MAX_RETRIES`): Retries for verification models. Default: `3`.

**Google API Configuration:**

* `google.api.provider` (Env: `GOOGLE_API_PROVIDER`): Google API provider (`studio_ai` or `vertex_ai`). Default: `studio_ai`.
* `google.api.token` (Env: `GOOGLE_API_KEY`): API Key for Google AI Studio. Required if using AI Studio.
* `google.project` (Env: `GOOGLE_PROJECT`): Google Cloud Project ID. Required if using Vertex AI.
* `google.location` (Env: `GOOGLE_LOCATION`): Google Cloud location (region). Required if using Vertex AI.

**Azure OpenAI API Configuration:**

* `azure.openai.api.key` (Env: `OPENAI_API_KEY`): API Key for Azure OpenAI. Required if using OpenAI.
* `azure.openai.endpoint` (Env: `OPENAI_API_ENDPOINT`): Endpoint URL for Azure OpenAI. Required if using OpenAI.

**Groq API Configuration:**

* `groq.api.key` (Env: `GROQ_API_KEY`): API Key for Groq. Required if using Groq.
* `groq.endpoint` (Env: `GROQ_ENDPOINT`): Endpoint URL for Groq. Required if using Groq.

**Anthropic API Configuration:**

* `anthropic.api.provider` (Env: `ANTHROPIC_API_PROVIDER`): Anthropic API provider (`anthropic_api` or `vertex_ai`). Default:
  `anthropic_api`.
* `anthropic.api.key` (Env: `ANTHROPIC_API_KEY`): API Key for Anthropic. Required if using Anthropic.
* `anthropic.endpoint` (Env: `ANTHROPIC_ENDPOINT`): Endpoint URL for Anthropic. Default: `https://api.anthropic.com/v1/`.

**Timeout and Retry Configuration:**

* `test.step.execution.retry.timeout.millis` (Env: `TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed test case
  actions. Default: `5000 ms`.
* `test.step.execution.retry.interval.millis` (Env: `TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS`): Delay between test case action retries.
  Default: `1000 ms`.
* `verification.retry.timeout.millis` (Env: `VERIFICATION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed verifications. Default:
  `10000 ms`.
* `action.verification.delay.millis` (Env: `ACTION_VERIFICATION_DELAY_MILLIS`): Delay after executing a test case action before performing
  the corresponding verification. Default: `500 ms`.
* `max.action.execution.duration.millis` (Env: `MAX_ACTION_EXECUTION_DURATION_MILLIS`): Maximum duration for a single action execution.
  Default: `30000 ms`.

**Budget Management Configuration:**

* `agent.token.budget` (Env: `AGENT_TOKEN_BUDGET`): Maximum total tokens that can be consumed across all models. Default: `1000000`.
* `agent.tool.calls.budget` (Env: `AGENT_TOOL_CALLS_BUDGET`): Maximum tool calls per agent. Default: `10`.
* `agent.execution.time.budget.seconds` (Env: `AGENT_EXECUTION_TIME_BUDGET_SECONDS`): Maximum execution time in seconds. Default: `3000`.

**Screen Recording Configuration:**

* `screen.recording.active` (Env: `SCREEN_RECORDING_ENABLED`): Enable/disable screen recording. Default: `false`.
* `screen.recording.output.dir` (Env: `SCREEN_RECORDING_FOLDER`): Output directory for recordings. Default: `./videos`.
* `recording.bit.rate` (Env: `VIDEO_BITRATE`): Video bitrate. Default: `2000000`.
* `recording.file.format` (Env: `SCREEN_RECORDING_FORMAT`): Recording file format. Default: `mp4`.
* `recording.fps` (Env: `SCREEN_RECORDING_FRAME_RATE`): Frames per second for recording. Default: `10`.

**Prefetching Configuration:**

* `prefetching.enabled` (Env: `PREFETCHING_ENABLED`): Enable/disable element location prefetching in unattended mode. Default: `false`.

**Element Location Configuration:**

* `element.bounding.box.color` (Env: `BOUNDING_BOX_COLOR`): Required color name (e.g., `green`) for the bounding box drawn during element
  capture in supervised mode. This value should be tuned so that the color contrasts as much as possible with the average UI element color.
* `element.retrieval.min.target.score` (Env: `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score are treated as target element candidates. Default: `0.8`.
* `element.retrieval.min.general.score` (Env: `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval for potential matches. Default: `0.5`.
* `element.retrieval.min.page.relevance.score` (Env: `ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE`): Minimum page relevance score for vector
  DB UI element retrieval. Default: `0.5`.
* `element.locator.visual.similarity.threshold` (Env: `VISUAL_SIMILARITY_THRESHOLD`): OpenCV template matching threshold. Default: `0.8`.
* `element.locator.top.visual.matches` (Env: `TOP_VISUAL_MATCHES_TO_FIND`): Maximum number of visual matches to pass to the AI model.
  Default: `6`.
* `element.locator.found.matches.dimension.deviation.ratio` (Env: `FOUND_MATCHES_DIMENSION_DEVIATION_RATIO`): Maximum allowed deviation
  ratio for the dimensions of a found visual match. Default: `0.3`.
* `element.locator.visual.grounding.model.vote.count` (Env: `VISUAL_GROUNDING_MODEL_VOTE_COUNT`): Number of visual grounding votes. Default:
  `1`.
* `element.locator.validation.model.vote.count` (Env: `VALIDATION_MODEL_VOTE_COUNT`): Number of validation model votes. Default: `1`.
* `element.locator.bbox.clustering.min.intersection.ratio` (Env: `BBOX_CLUSTERING_MIN_INTERSECTION_RATIO`): Minimum IoU ratio for
  clustering bounding boxes. Default: `0.9`.
* `element.locator.zoom.scale.factor` (Env: `ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR`): Zoom scale factor for element location. Default: `1`.
* `element.locator.algorithmic.search.enabled` (Env: `ALGORITHMIC_SEARCH_ENABLED`): Enable/disable OpenCV algorithmic search. Default:
  `false`.
* `element.locator.skip.model.selection.vision.only` (Env: `SKIP_UI_ELEMENT_SELECTION_FOR_VISION`): When enabled, skip the model
  selection step when only visual grounding results are available (no algorithmic matches). In this case, the first identified element
  from the visual grounding results is returned directly without additional model validation. This can speed up element location when
  algorithmic search is disabled. Default: `false`.
* `bounding.box.already.normalized` (Env: `BOUNDING_BOX_ALREADY_NORMALIZED`): Whether bounding boxes are pre-normalized. Default: `false`.
* `bbox.screenshot.longest.allowed.dimension.pixels` (Env: `BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS`): Maximum screenshot
  dimension. Default: `1568`.
* `bbox.screenshot.max.size.megapixels` (Env: `BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS`): Maximum screenshot size in megapixels. Default:
  `1.15`.
* `hdr.color.correction.enabled` (Env: `HDR_COLOR_CORRECTION_ENABLED`): Apply sRGB gamma correction to screenshots captured on HDR-enabled
  monitors so saved screenshots and model inputs do not look washed out. Default: `false`.

**Agent-Specific Model Configuration:**

Each specialized agent can be configured with its own model and prompt version using the following pattern:

* `<agent>.model.name`: Model name for the agent
* `<agent>.model.provider`: Model provider (`google`, `openai`, `groq`, or `anthropic`)
* `<agent>.prompt.version`: System prompt version

Available agents and their configuration prefixes:

* `precondition.agent.*`: Precondition Action Agent
* `precondition.verification.agent.*`: Precondition Verification Agent
* `test.step.action.agent.*`: Test Step Action Agent
* `test.step.verification.agent.*`: Test Step Verification Agent
* `test.case.extraction.agent.*`: Test Case Extraction Agent
* `ui.element.description.agent.*`: UI Element Description Agent
* `ui.state.check.agent.*`: UI State Check Agent
* `element.bounding.box.agent.*`: Element Bounding Box Agent
* `element.selection.agent.*`: Element Selection Agent
* `element.candidate.selection.agent.*`: Element Candidate Selection Agent (uses same model as Element Selection Agent)
* `page.description.agent.*`: Page Description Agent
* `knowledge.suggestion.agent.*`: Knowledge Suggestion Agent

**Example agent configuration:**

```properties
precondition.agent.model.name=gemini-3-flash-preview
precondition.agent.model.provider=google
precondition.agent.prompt.version=v1.0.0
```

**User UI Dialog Settings:**

* `dialog.default.horizontal.gap`, `dialog.default.vertical.gap`, `dialog.default.font.type`,
  `dialog.user.interaction.check.interval.millis`, `dialog.default.font.size`, `dialog.hover.as.click`: Cosmetic and timing settings for
  interactive dialogs.

## How to Run

1. Ensure the project is built.
2. Run the `Server` class using Maven Exec Plugin:
   ```bash
   mvn exec:java -Dexec.mainClass="org.tarik.ta.Server"
   ```
   Or run the packaged JAR:
   ```bash
   java -jar target/<your-jar-name.jar>
   ```
3. The server will start listening on the configured port (default `8005`).
4. Send a `POST` request to the root endpoint (`/`) with the correct A2A message. Use `message/stream` to receive incremental progress
   and logs as a Server-Sent Events stream, or `message/send` for a single aggregated response.
5. The server starts the test case execution if it accepts the request (i.e., not already running a test case) or returns
   `429 Too Many Requests` if it's busy. Streaming clients receive step results and log lines as they are produced; in both cases the
   execution concludes by emitting a single consolidated `TestExecutionResult` (full result JSON + logs file + screenshots), so a
   `message/send` caller gets one ready-to-consume result object.

### Generating the Knowledge Graph Health Report

A standalone CLI tool generates a self-contained HTML health report without requiring the agent to be running. It connects directly to Neo4j
and writes the report to the configured (or specified) output path.

**Using the provided scripts (recommended):**

```bash
# Unix/macOS — generate report to default path (reports/graph-health-report.html)
scripts/generate-health-report.sh

# Unix/macOS — generate report to a custom path
scripts/generate-health-report.sh --output /tmp/my-report.html

# Windows — generate report to default path
scripts\generate-health-report.bat

# Windows — generate report to a custom path
scripts\generate-health-report.bat --output C:\reports\my-report.html
```

Both scripts require `JAVA_HOME` to be set and the Neo4j connection configured via `config.properties` or environment variables (`VECTOR_DB_URL`,
`NEO4J_USERNAME`, `NEO4J_DATABASE`, `NEO4J_PASSWORD`).

**Using Maven directly:**

```bash
mvn exec:java -pl ui_test_execution_agent \
    -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli \
    -Dexec.args="--output path/to/report.html"
```

The HTML report includes a color-coded summary card per health category (green=OK, yellow=WARNING, red=CRITICAL) and collapsible detail
sections listing individual findings. All finding text is HTML-escaped to prevent XSS when procedure names contain special characters.

## Deployment

This section provides detailed instructions for deploying the UI Test Execution Agent, both to Google Cloud Platform (GCP) and locally
using Docker.

### Cloud Deployment (Google Compute Engine)

The agent can be deployed as a containerized application on a Google Compute Engine (GCE) virtual machine, providing a robust and scalable
environment for automated UI testing. Because the agent needs at least 2 ports to be exposed (one for communicating with other agents
and one for noVNC connection), using Google Cloud Run as a financially more efficient alternative is not possible. However, using Spot
VMs is also a formidable option.

#### Prerequisites for Cloud Deployment

* **Google Cloud Project:** An active GCP project with billing enabled.
* **gcloud CLI:** The Google Cloud SDK `gcloud` command-line tool installed and configured.
* **Secrets in Google Secret Manager:** The following secrets must be created in Google Secret Manager within your GCP project. These are
  crucial for the agent's operation and should be stored securely. The list of secrets depends heavily on the provider of the models
  which are used for analyzing execution instructions and for performing visual tasks. The exemplary list is valid for using Groq as the
  platform.
    * `GROQ_API_KEY`: Your API key for Groq platform.
    * `GROQ_ENDPOINT`: The endpoint URL for Groq platform.
    * `VECTOR_DB_URL`: The URL of your vector DB instance (see deployment instructions below).
    * `VNC_PW`: The password for accessing the noVNC session using browser.

  You can create these secrets using GCP Console.

#### Deploying Chroma DB (Vector Database)

The agent relies on a vector database, Chroma DB is currently the only supported option. You can deploy Chroma DB to Google Cloud Run
or use a managed Chroma DB service. Refer to the [Chroma DB documentation](https://docs.trychroma.com/) for deployment options.

After deployment, note the URL of the deployed Chroma DB service; this will be your `VECTOR_DB_URL` which you need to set as a secret.

#### Building and Deploying the Agent on GCE

1. **Navigate to the project root:**
   ```bash
   cd <project_root_directory>
   ```

2. **Configure deployment substitutions:**

   The deployment is configured via Cloud Build substitutions in `deployment/cloud/cloudbuild.yaml`. This file contains all configurable
   parameters as substitutions that can be overridden when running the build.

   **Key configuration categories:**

    * **GCP Configuration:** Region, zone, instance name, network settings, machine type
    * **Port Configuration:** noVNC, VNC, and agent server ports
    * **Application Settings:** VNC resolution, log level, unattended/debug mode
    * **Screenshot and Bounding Box Settings:** Image dimension limits and normalization settings
    * **Agent Model Configuration:** Model names, providers, and prompt versions for each agent
    * **API Endpoints:** Groq, Google Cloud location and project settings
    * **Additional GCP Configuration:** Firewall rules, disk settings, VM provisioning model, etc.
    * **Base Image configuration:** `_BUILD_BASE_IMAGE` (default `false`) controls whether to rebuild the base image or use the cached one.

   **Important notes:**
    * **Empty values use defaults:** If a substitution value is empty (e.g., `_ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME: ''`),
      the application will use defaults from `config.properties`.
    * **Override substitutions:** Pass custom values when running `gcloud builds submit`.

3. **Deploy using Cloud Build:**
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml .
   ```

   To override specific substitutions:
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
     --substitutions=_MACHINE_TYPE=e2-standard-4,_LOG_LEVEL=DEBUG .
   ```

   The build will:
    * Build the Maven project.
    * Build or pull the Docker base image (conditional on `_BUILD_BASE_IMAGE`).
    * Build the Docker application image.
    * Push the Docker image to Google Container Registry.
    * Enable necessary GCP services.
    * Set up VPC network and firewall rules (if they don't exist).
    * Create a GCE Spot VM instance.
    * Start the agent container inside the created VM.

   If you want to use the agent as part of an already existing network (e.g., together
   with [Agentic QA Framework](https://github.com/partarstu/agentic-qa-framework)), you must carefully update the substitutions
   in the YAML file to avoid destroying existing settings.

#### Accessing the Deployed Agent

* **Agent Server:** The agent will be running on the port configured by `AGENT_SERVER_PORT` (default `443`). The internal hostname can be
  retrieved by executing `curl "http://metadata.google.internal/computeMetadata/v1/instance/hostname" -H "Metadata-Flavor: Google"` inside
  the VM. This hostname can later be used for communication inside the network with other agents of the framework.
* **noVNC Access:** You can access the agent's desktop environment via noVNC in your web browser. The URL will be
  `https://<EXTERNAL_IP>:<NO_VNC_PORT>`, where `<EXTERNAL_IP>` is the external IP of your GCE instance and `<NO_VNC_PORT>` is the noVNC
  port (default `6901`). The VNC password is set via the `VNC_PW` secret. The SSL/TLS certificate is self-signed, so you'll have to
  confirm visiting the page for the first time.

### Local Docker Deployment

For local development and testing, you can run the agent within a Docker container on your machine.

#### Docker Image Architecture

The agent uses a two-layer Docker image architecture:

1. **Base Image (`ui-testing-agent-base`)**: Built from `deployment/Dockerfile.base`, provides:
    - Ubuntu 24.04 LTS
    - Xfce desktop environment
    - TigerVNC server
    - noVNC (web-based VNC access)
    - **Google Chrome Stable** (latest version)
    - Common utilities (wget, curl, git, zip, unzip, jq, etc.)

2. **Application Image (`ui-test-execution-agent`)**: Built from `deployment/local/Dockerfile`, adds:
    - Java 25 runtime
    - Agent application JAR
    - Application-specific configuration

#### Prerequisites for Local Docker Deployment

* **Docker Desktop:** Ensure Docker Desktop is installed and running on your system.

#### Building and Running the Docker Image

The `build_and_run_docker.bat` script (for Windows) simplifies the process of building the application and Docker image, and running the
container.

1. **Adapt `deployment/local/Dockerfile`:**
    * **IMPORTANT:** Before running the script, open `deployment/local/Dockerfile` and replace the placeholder `VNC_PW` environment variable
      with a strong password of your choice. For example:
      ```dockerfile
      ENV VNC_PW="your_strong_vnc_password"
      ```
      (Note: The `build_and_run_docker.bat` script also sets `VNC_PW` to `123456` for convenience, but it's recommended to set it directly
      in the Dockerfile for consistency and security.)

2. **Execute the batch script:**
   ```bash
   deployment\local\build_and_run_docker.bat
   ```
   This script will:
    * Build the `ui_test_execution_agent` module (and its dependencies) using Maven.
    * Build the base Docker image `ui-testing-agent-base` from `deployment/Dockerfile.base` (Ubuntu 24.04 + VNC + Chrome).
    * Build the application Docker image `ui-test-execution-agent` using `deployment/local/Dockerfile`.
    * Stop and remove any existing container named `ui-agent`.
    * Run a new Docker container, mapping ports `5901` (VNC), `6901` (noVNC), and `8005` (agent server) to your local machine.

#### Accessing the Local Agent

* **VNC Client:** You can connect to the VNC session using a VNC client at `localhost:5901`.
* **noVNC (Web Browser):** Access the agent's desktop environment via your web browser at `http://localhost:6901/vnc.html`.
* **Agent Server:** The agent's server will be accessible at `http://localhost:8005`.

Remember to use the VNC password you set in the Dockerfile when prompted.

## TODOs

* ~~Add public method comments and unit tests.~~ (Partially completed - unit tests added for many components)
* Add public unit tests for at least 80% coverage.

## Final Notes

* **Project Scope:** This project is developed as a prototype of an agent, a minimum working example, and thus a basis for further
  extensions and enhancements. It's not a production-ready instance or a product developed according to all the requirements/standards
  of an SDLC (however many of them have been taken into account during development).
* **Modular Architecture:** The agent now uses a modular architecture with specialized AI agents (e.g., `UiPreconditionActionAgent`,
  `UiTestStepVerificationAgent`, `ElementBoundingBoxAgent`). Each agent can be independently configured with its own AI model and prompt
  version, allowing for fine-tuned performance optimization. The `GenericAiAgent<T>` interface provides common retry and execution logic.
  UI-specific configuration is managed by [UiTestAgentConfig](src/main/java/org/tarik/ta/UiTestAgentConfig.java).
* **Budget Management:** The `BudgetManager` provides guardrails for execution in unattended mode, preventing runaway costs by limiting
  time, tokens, and tool calls. This is particularly important for CI/CD integration.
* **Enhanced Error Handling:** The new `ErrorCategory` enum and `RetryPolicy` record provide structured error handling with configurable
  retry strategies, making the agent more robust and easier to debug.
* **Environment:** The agent has been manually tested on the Windows 11 platform. There are issues with OpenCV and OpenBLAS libraries
  running on Linux, but there is no solution to those issues yet.
* **Standalone Executable Size:** The standalone JAR file can be quite large (at least ~330 MB). This is primarily due to the automatic
  inclusion of the ONNX embedding model (`all-MiniLM-L6-v2`) as a dependency of LangChain4j, and the native OpenCV libraries required for
  visual element location.
* **Unit Tests:** The project now includes unit tests for many components including agents, DTOs, managers, and tools. All future
  contributions and pull requests to the `main` branch **should** include relevant unit tests. Contributing by adding new unit tests
  to existing code is, as always, welcome.
 Contributing by adding new unit tests
  to existing code is, as always, welcome.
  visual element location.
* **Unit Tests:** The project now includes unit tests for many components including agents, DTOs, managers, and tools. All future
  contributions and pull requests to the `main` branch **should** include relevant unit tests. Contributing by adding new unit tests
  to existing code is, as always, welcome.
