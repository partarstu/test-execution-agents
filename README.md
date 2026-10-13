# AI-Powered Test Execution Agents (Parent Project)

This repository contains a multi-module Maven project for AI-powered test execution agents. It is designed to be modular and scalable,
separating orchestration logic from specific testing agent implementations. All agents in this project use A2A protocol for
communication and have already been integrated and show-cased as a part of QuAIA™ (Quality Assurance with Intelligent Agents) Framework.

## Demo

Watch a demo of both UI and API test execution agents running as a part of QuAIA™ in action:

[QuAIA™ Framework Demo](https://youtu.be/LUf6ydlKfIU)

## Project Structure

The project is organized into the following modules:

```
D:\Projects\test-execution-agents\
├── pom.xml                      (Parent POM)
├── cloudbuild.yaml              (Cloud Build configuration)
├── agent_core\                  (Shared Library)
│   ├── pom.xml
│   └── src\main\java\...
├── ui_test_execution_agent\     (Executable UI Agent)
│   ├── pom.xml
│   ├── deployment\              (Docker & deployment scripts)
│   └── src\main\java\...
└── api_test_execution_agent\    (Executable API Agent)
    ├── pom.xml
    ├── deployment\              (Docker & deployment scripts)
    └── src\main\java\...
```

### Module Overview

* **`agent_core`**: A shared library module containing the core framework logic, data transfer objects (DTOs), base agent classes, budget
  management, and generic utilities. This module provides:
    * **`AbstractServer`**: Base class for agent servers providing common HTTP server initialization and A2A endpoint configuration,
      routing streaming methods to a Server-Sent Events (SSE) response and all other methods to a single JSON-RPC response. Request bodies
      are parsed with the A2A SDK's JSON-RPC parser; incoming standard A2A method names (`message/send`, `message/stream`, `tasks/get`,
      `tasks/cancel`, `tasks/resubscribe`) are normalized to the SDK's request types before parsing, the A2A 0.3 field `params.configuration.pushNotificationConfig`, which the SDK's v1 schema rejects, is dropped so that older clients are served (push notifications are not supported), and failures are answered with the
      corresponding spec-compliant JSON-RPC error (method not found, invalid params, invalid request, parse error, or internal error). The SDK denies every task operation unless a task authorization provider allows it; the agents register one that allows all operations, because access to the endpoint is already guarded by the bearer token (`AGENT_AUTH_TOKEN`). `tasks/get` and `tasks/cancel` return the task itself as the JSON-RPC `result`, as the A2A specification defines, while `message/send` and the streamed events wrap it in a `task` field. Push notifications are not supported and are ignored in requests.
    * **`AbstractAgentExecutor`**: Base class for agent executors handling the test case execution lifecycle. It streams incremental
      progress (step/precondition results, log lines) to the caller via a `StreamingEventEmitter` and emits a final consolidated
      `TestExecutionResult` artifact on completion. It also supports the A2A `tasks/cancel` method: a cancel request interrupts the
      running execution thread and transitions the task to the `canceled` state, suppressing any terminal completion/failure event from
      the aborted run. Cancelling when no task is running yields a `TaskNotFoundError`, and cancelling an already-terminal task yields a
      `TaskNotCancelableError`, as required by the A2A specification.
    * **`StreamingEventEmitter`**: Abstraction that bridges live execution progress (step results, precondition results, log lines) to the
      A2A streaming channel without coupling execution code to the transport layer.
    * **`GenericAiAgent`**: Core interface for all AI agents with retry logic and budget management.
    * **`TestCaseExtractor`**: Utility class that provides shared test case extraction functionality using an AI model.
    * **`TestContextDataTools`**: Shared tools for loading and managing test data (JSON, CSV).
    * **`TestExecutionContext`**: Shared request-scoped execution state for step history, precondition history, and shared data; test case data is passed explicitly by the agents instead of being stored in the context.
    * **`DefaultToolErrorHandler`**: Centralized tool error handling with configurable retry policies. Its `forToolArguments` handler is registered on every agent: tool-call arguments the framework cannot parse (e.g. an object parameter sent as an unbalanced JSON string, which a vLLM tool parser can pass through as-is) are returned to the model with the parse error so it can call the tool again, until the retry policy is exhausted, also in supervised mode.
    * **`InheritanceAwareToolProvider`**: Enhanced tool provider that supports tool inheritance and discovery. It registers every tool with the `returnBehavior` of its own `@Tool` annotation, so the final-result tool (`IMMEDIATE_IF_LAST`) ends the agent loop whenever it is the last call of a model response, also when the model batches it after an action (e.g. `[leftMouseClick, endExecutionAndGetFinalResult]`), without another model turn.
    * **`LogCapture`**: Utility for capturing execution logs to include in test results.
    * **`SystemInfo`**: DTO for capturing device/OS/browser information.

* **`ui_test_execution_agent`**: The executable application module that implements the specific logic for UI testing. It includes:
    * **`UiTestAgent`**: Main entry point for UI test execution.
    * **`UiTestAgentConfig`**: UI-specific configuration (element locator, operator panel and prompts, video recording, vision agents).
    * **`VisualStateHolder`**: The screen state of the running test case, kept next to the shared `TestExecutionContext` of `agent_core` so that the whole request scope - the UI flow and the context tools alike - works on one context.
    * UI-specific agents for visual grounding, element interaction, and verification.
    * Computer vision capabilities using OpenCV.
    * Tools for mouse/keyboard control.
    * Knowledge graph integration with Neo4j for persistent element and procedure storage.
    * Operator prompts: every operator interaction goes through the `OperatorPrompts` interface, implemented by the operator panel's `WebOperatorPanel` as prompts in the browser; the procedure editor is one multi-view prompt (`ProcedureEditorPrompt` on the server) whose actions run on `UiElementResolutionService`, `SuggestionService`, `SimilarChildrenService`, `ProcedureEditorRules` and `ProcedureSelectionRules`. The agent has no Swing UI.
    * **Orphan cleanup**: `KnowledgeIngestionService` collects all UI element IDs reachable from a procedure before updating it. After removing `TARGETS`/`CONTAINS` relationships, `ProcedureRepository.deleteUiElementsIfOrphaned` deletes any UI element no longer referenced by any atomic procedure. `deleteDescendants` is orphan-aware: it removes `CONTAINS` edges and only deletes a child procedure node when it has no remaining parents, recursively.
    * Deployed as a **GCE VM** with VNC access.

* **`api_test_execution_agent`**: The executable application module that implements the specific logic for API testing. It includes:
    * **`ApiTestAgent`**: Main entry point for API test execution.
    * **`ApiTestAgentConfig`**: API-specific configuration (HTTP client, proxy, timeouts, authentication).
    * **`ApiPreconditionActionAgent`**: Executes and verifies API preconditions (auth setup, data creation).
    * **`ApiTestStepActionAgent`**: Executes and verifies individual API test steps.
    * **`ApiRequestTools`**: HTTP request execution with multiple authentication types (Basic, Bearer, API Key).
    * **`ApiAssertionTools`**: Response validation (JSON Schema, OpenAPI spec, status codes, JSON paths).
    * **`ApiContext`**: Session management (cookies, variables, configuration).
    * Deployed as a **Cloud Run** service.

### Core Architecture

The project uses **[Avaje Inject](https://avaje.io/inject/)** for dependency injection across all modules.
The core module provides shared abstractions that both UI and API agents extend:

```
┌──────────────────────────────────────────────────────────────────┐
│                         agent_core                               │
├──────────────────────────────────────────────────────────────────┤
│  AbstractServer ◄──────────── UI Server, API Server              │
│  AbstractAgentExecutor ◄───── UiAgentExecutor, ApiAgentExecutor  │
│  GenericAiAgent ◄──────────── All specialized agents             │
│  OperationExecutionResult ─── Unified execution results          │
│  TestExecutionContext ──────► Shared execution context           │
│  DefaultToolErrorHandler ──── Centralized error handling         │
└──────────────────────────────────────────────────────────────────┘
```

### Module Dependency Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    test-execution-agent-parent              │
│                         (Parent POM)                        │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────────┐
│    agent_core       │ │ui_test_execution_   │ │api_test_execution_      │
│  (Shared Library)   │ │       agent         │ │       agent             │
│                     │ │  (UI Testing)       │ │  (API Testing)          │
│ • AbstractServer    │ │                     │ │                         │
│ • AbstractExecutor  │◄│ • UiTestAgent       │ │ • ApiTestAgent          │
│ • GenericAiAgent    │ │ • UI Agents         │ │ • API Agents            │
│ • BudgetManager     │ │ • OpenCV Tools      │ │ • REST Tools            │
│ • Core DTOs         │ │ • Knowledge Graph   │ │ • Schema Validation     │
│ • Error Handling    │ │ • Visual Grounding  │ │ • Auth Handling         │
└─────────────────────┘ └─────────────────────┘ └─────────────────────────┘
                              │                       │
                              ▼                       ▼
                        ┌───────────┐           ┌───────────┐
                        │  GCE VM   │           │ Cloud Run │
                        │  (VNC)    │           │           │
                        └───────────┘           └───────────┘
```

## Key Features

### Shared Across Agents

- **A2A Protocol Support**: Both agents implement the Agent-to-Agent (A2A) protocol for inter-agent communication, including the
  streaming `message/stream` (and `tasks/resubscribe`) methods served over Server-Sent Events (SSE). The agent cards advertise
  `capabilities.streaming = true`.
- **Task Cancellation**: The A2A `tasks/cancel` method interrupts the currently running test execution and transitions the task to the
  `canceled` state, suppressing the terminal completion/failure event of the aborted run. A cancel request for a task that is not running
  returns `TaskNotFoundError`, and one for an already-terminal task returns `TaskNotCancelableError`, per the A2A specification.
- **Live Streaming of Progress and Logs**: Streaming clients receive incremental events as execution advances — before each test step or
  precondition runs, a `working` status update carrying an `ExecutionActivity` payload announces what is *about to execute* (so a live
  dashboard can show the current activity); each precondition and test step result is then streamed as an artifact the moment it is
  recorded; and captured log lines are streamed live as appended chunks of a single growing `execution_log.log` artifact. UI step
  screenshots are streamed as part of the corresponding step events. Regardless of streaming, the run always concludes by emitting a single
  consolidated `TestExecutionResult` (full result JSON + logs file + end-of-test general screenshot); per-step screenshots are not
  re-bundled there since they already arrived with their step events, so non-streaming `message/send` callers still receive one
  ready-to-consume result object without having to reassemble the individual events.
- **Endpoint Authentication**: When `agent.auth.token` / `AGENT_AUTH_TOKEN` is set, every request to the main A2A endpoint must
  carry a matching `Authorization: Bearer <token>` header (compared in constant time); otherwise it is rejected with `401`. The
  `GET /.well-known/agent-card.json` discovery endpoint stays public. If no token is configured the endpoint is unauthenticated and a
  startup `WARN` is logged — set a token for any non-local deployment.
- **Secret Masking**: At the start of every run, each test data value whose key matches `sensitive.data.key.pattern` (passwords, PINs, tokens, API keys, credentials by default) is replaced in the test context by a `{{secret:<key>}}` placeholder. Prompts, dialogs, logs (the application's own log output included) and the result artifacts carry only the placeholder or the redacted value; the real value is resolved back solely by the tool that consumes it — the keyboard tools of the UI agent and the outgoing HTTP request (URL, headers and body) of the API agent. Logged stack traces are redacted too (the `%redactedEx` conversion word of the text appenders and the throwable converter of the JSON appender), and a tool error message returned to the model has its registered secrets replaced before the model sees it. The test case extraction call runs before the secrets are known, so its request and response content is never logged.
- **Test Case Extraction**: AI-powered parsing of natural language test cases into structured format.
- **Budget Management**: Token budget controls for all executions, with time and tool-call budgets activated for unattended executions to prevent runaway automation without limiting supervised operator workflows. The run-wide time and token budgets are checked before every tool call, and an exceeded budget (`BudgetExceededException`) reaches the run unwrapped by the tool error handlers, so a long sub-agent loop stops at its next tool call. The tool-call budget stays per step, as it is reset after each step.
- **Token Accounting per Sub-Agent Operation**: Every model call is attributed to the sub-agent operation that made it (e.g.
  `test_step_action`, `element_grounding`), including the prompt tokens the provider served from its cache. The counts reach the
  caller three ways: a debug log line per call, the `token_usage` artifact of the finished task (one entry per operation, emitted on
  the successful *and* the failing path), and OpenTelemetry metrics — see [Telemetry](#telemetry-opentelemetry). Releasing a
  finished task's usage is atomic with respect to model calls still being recorded, so no call is lost between the report and the
  cleanup.
- **Structured Logging**: Execution logs captured and streamed live during execution. Only the application's own logger
  (`org.tarik.ta`) is streamed to clients, so framework/third-party lines never enter the client stream. The optional
  `model.logging.enabled`, `api.request.logging.enabled`, and `api.response.logging.enabled` toggles default to `false` because
  enabling them streams sensitive data (prompts, response bodies, auth headers).
- **System Info Capture**: Device, OS, browser, and environment information in results.
- **Diagnosable Failures**: A run that breaks unexpectedly still reports what it had captured. Both agents' outer failure paths
  return the captured logs (and, for the UI agent, the recording path) instead of an empty result, and the A2A executor flushes the
  buffered log lines before it completes *or* fails the task — after the terminal event the task accepts no further artifact, so logs
  flushed later would never reach the caller.

### UI Test Agent Specific

- **Visual Grounding**: AI-powered element location using screenshots and descriptions. The shape in which the model is asked to report a location is configurable via `element.locator.grounding.output.format`: `BOUNDING_BOX` (default) asks for two opposite corners and suits general-purpose vision models such as Gemini and Claude, while `CENTER_AND_SIZE` centres the result on a single element's click position, which is what dedicated GUI grounding models such as Holo3.1 natively predict. Only the click position of such models should be relied on - any size reported alongside is not a measurement of the element.
- **Grounding request transport**: a self-hosted grounding model (`element.bounding.box.agent.model.provider=hcompany` or `qwen`, with `CENTER_AND_SIZE`) is asked the way its vendor documents it - no system message, the image ahead of the instruction, and the answer constrained by a JSON schema - instead of through the agent framework's final-result tool. Every other model keeps the tool-calling path. This is not cosmetic: asked through a tool call, the same model reports the visually salient element rather than the one the test data names, selecting the highlighted dropdown entry or the wrong calendar day while the test data sits verbatim in the prompt. Both families are asked for a click position and nothing else, exactly as their vendors document localization, but they need different schemas - Holo3.1 answers the plain `{x, y}` of H Company's `VisualLocalizerOutput`, while Qwen3.8 reads those same keys as a box's top-left corner and must be asked for `point_2d` instead - so each has its own `GroundingProtocol`. Neither model is asked how large the element is, so the rectangle handed back to the locator is a fixed 50x50 placeholder centred on the predicted point.
- **HDR Color Correction**: Optional sRGB gamma correction for screenshots captured on HDR-enabled monitors (enabled via `hdr.color.correction.enabled=true`).
- **Operator Panel**: A password-protected browser UI on its own port (`panel.port`, default `8443`, TLS in the cloud) with the run's timeline, status and log tail, the live view of the agent's screen relayed to Xvnc's Unix socket, the knowledge graph health report on its *Health* tab, and on its *Knowledge* tab a read-only exploration of the graph (a searchable procedure map, an element gallery, a 3D overview that lights up the running procedures, weekly trends, failure timelines and why each procedure was chosen, from the run history the agent records); one session and one tab at a time, the newest taking over (see [its README](ui_test_execution_agent/README.md)).
- **Screen Recording**: Captures video of test execution for debugging.
- **Knowledge Graph**: Neo4j-backed persistent storage for UI elements and reusable procedures with vector search.
- **Supervised/Unattended Modes**: Interactive or fully automated execution. In supervised mode, after a failure or halt the
  next-action prompt of the operator panel offers an explicit "Rerun the test step" (or "Rerun the precondition") and — once an
  atomic procedure has executed — "Rerun the last action"; after a halt it first offers to go on: "Proceed with this step" before
  the step runs, "Go on with the next step" after it succeeded. The reruns and "Proceed with this step" always refetch the procedure
  from the knowledge DB before running it, and a procedure created from that prompt is rerun instead of the original one. Edits and creations made from that
  prompt are persisted immediately and reopen it; only an explicit rerun or terminate leaves the loop.
- **Operator Panel Prompts**: Every operator question is a prompt in the browser panel, never a window on the agent's display. The server
  holds the deadline, the default and the validity of each answer: a countdown applies its default at its end, pauses while the
  operator's pointer is on the prompt and can be held up to `panel.prompt.max.wait.minutes`, a prompt without a countdown applies its default there or fails the run, and a cancelled task closes
  its prompt. A supervised agent therefore refuses to start without `panel.password` or without the built panel frontend.
- **Supervised Tool-Call Round Trips**: The unattended tool-call budget (`agent.tool.calls.budget.unattended`) also caps the
  agents' LangChain4j `maxToolCallingRoundTrips` — but only in fully unattended runs. In supervised mode the framework default
  of 100 applies instead, since an operator can always intervene and a halted-and-resumed step can legitimately need more
  round trips.
- **Ancestor Chain for Decomposed Steps**: When a composite procedure is decomposed, every atomic step it produces carries the whole chain of composites it came from. The chain goes into the action and verification prompts innermost first (the direct parent, then the parent of that level, and so on), each level stating only the data, expected results and notes it declares, and the outermost level additionally the data of the whole test step (or test case precondition) — context the agent must not execute or verify on its own. The confirmation prompt shows it as a breadcrumb, so "Click 'Save'" is no longer judged without knowing which flow it serves. A decomposed atomic step gets only its own stored data as the data relevant to it, so a step like "Click on the search field" never mistakes the test step's data for its own; an atomic procedure matched to the test step or precondition directly gets that item's data. Every prompt section without content (the data, the test execution context data, a detail of a level) is left out instead of being rendered as `none`.
- **Interactive Element Resolution Budget**: An element resolution started from the procedure editor is bounded by
  `element.resolution.max.duration.millis` (default 5 minutes) instead of the much shorter automated-action timeout. When the
  budget expires, the worker is interrupted and the operator is told that the location *timed out after N ms*, distinctly from a
  resolution that actually failed. Cancellation is best effort: the agent only stops at its next interruptible point.
- **Self-Closing Failure-Context Prompt**: The failure-context prompt counts down
  (`failure.context.dialog.countdown.seconds`, default 30) and closes itself without capturing anything if nobody reacts, so an
  unattended-in-practice run is not blocked by it. The countdown is held as soon as the operator starts filling the form in.
- **Forgiving Phrase-Property Repair**: The startup backfill of missing phrase nodes still aborts the start when it fails, but the
  backward repair of stale `prerequisites`/`effects` node properties only logs a `WARN` per procedure and a `WARN` summary and lets
  the agent start: the phrase nodes remain authoritative, and the repair is retried on the next start.
- **Parallel Procedure Prefetch**: Overlaps the current atomic procedure's execution with prefetching of the next atomic procedure context in unattended mode. The prefetched prerequisite check of the next procedure runs against the state predicted for when the current one has succeeded: its effects are added, and every composite procedure it is the last child of is closed the way the live execution closes it (its children's effects are dropped and its own effects are promoted).
- **Non-Blocking State Recording**: Asynchronous persistence of non-critical state data in unattended mode.
- **Configurable Verification Granularity**: Option to verify expected results per test-step instead of per-atomic-procedure.
- **Type-Faithful Procedure Editor**: An existing procedure is always rendered with the field set of its own type, not of the item it
  was opened from — a precondition shows *Relevant Data*, a test-step procedure shows *Test Data* and *Expected Results*. Saving keeps
  the loaded procedure's type and its stored Test Data even when that field was not rendered, so a hidden field is never wiped. The
  procedure searches label every row as precondition or test step, show the current item's type by default (the type filter
  reveals the rest), and picking a procedure of the other type for execution asks for confirmation first.

### API Test Agent Specific

- **Multiple Auth Types**: Basic, Bearer Token, and API Key authentication.
- **Outbound Request Guards (SSRF / file-exfiltration)**: Tool-layer enforcement independent of the model. Requests to SSRF-sensitive
  ranges (loopback, link-local, site-local, multicast, wildcard, `0.0.0.0/8`, carrier-grade NAT `100.64.0.0/10`, IPv6 unique-local `fc00::/7`) and the cloud metadata address `169.254.169.254` are blocked; an
  optional `api.outbound.host.allowlist` (CSV) restricts requests to trusted hosts (an allow-listed host bypasses the range check).
  `uploadFile` is confined to `api.upload.base.dir` (canonicalized) and is refused entirely when that directory is unset.
- **TLS Validation On By Default**: `api.relaxed.https.validation` defaults to `false`; relax it only per trusted target, since
  relaxing it exposes credentialed requests to man-in-the-middle interception.
- **Schema Validation**: JSON Schema and OpenAPI specification validation.
- **Variable Substitution**: Dynamic `${variableName}` replacement in requests.
- **Cookie Management**: Automatic session handling across requests.

## Building the Project

To build the entire project, run the following command from the root directory:

```bash
mvn clean install
```

This will build all modules and run the tests.

### Building Individual Agents

To build specifically the UI agent executable (skipping tests for speed):

```bash
mvn clean package -pl ui_test_execution_agent -am -DskipTests
```

To build specifically the API agent executable:

```bash
mvn clean package -pl api_test_execution_agent -am -DskipTests
```

### Building the Operator Panel Frontend

The UI agent's operator panel is a React + TypeScript app in `ui_test_execution_agent/src/main/frontend`, built by Vite into `target/classes/panel` and packaged into the agent's jar. The Maven build of the UI module runs it through `frontend-maven-plugin`, so no local Node installation is needed: the plugin downloads the Node version pinned in the parent POM (`node.version`) into `src/main/frontend/node` (git-ignored) and then runs `npm ci`, `npm audit signatures` (verifies the registry signatures of the installed packages and fails on a verification error), `npm run build` (`tsc --noEmit`, ESLint with the React Compiler rules, the accessibility rules of `eslint-plugin-jsx-a11y-x` (a fork of `eslint-plugin-jsx-a11y` that supports ESLint 10) and a ban on `dangerouslySetInnerHTML`, then the Vite build, which fails unless the built `index.html` links `public/layers.css`, the cascade-layer order, before the stylesheets of the split chunks) and, in the `test` phase, `npm test` (Vitest; the component tests render into jsdom with Testing Library and fail on any axe-core violation, and a token test keeps the colour contrast of both themes at WCAG 2.2 AA).

- The build is skipped when `target/classes/panel/index.html` is newer than every frontend source and build file (`build-helper-maven-plugin`'s `uptodate-property`), so a backend-only change does not rebuild the bundle; `mvn clean` or any change under `src/main/frontend` rebuilds it. `npm test` still runs on every build.
- `-Dskip.installnodenpm -Dskip.npm` skips the frontend entirely, for a backend-only build; `-DskipTests` skips `npm test`.
- `src/main/frontend/.npmrc` sets `ignore-scripts=true` (no dependency lifecycle scripts run) and `min-release-age=3` (an `npm install` or `npm update` that writes the lockfile skips versions published less than three days ago); Dependabot (`.github/dependabot.yml`) uses the same three-day cooldown. The Maven build installs the committed `package-lock.json` as pinned.
- The Cloud Build Maven steps that build the UI module (`cloudbuild.yaml`, `ui_test_execution_agent/deployment/cloud/cloudbuild.yaml`) run on `amazoncorretto:27-al2023` with `libatomic` installed, because the Node binaries the plugin downloads need glibc and `libatomic` and do not run on Alpine; the API agent's standalone build builds only its own module and stays on Alpine.
- The bundled third-party licences are written to `panel/licenses.md` next to the bundle.
- noVNC (`@novnc/novnc`) is a runtime dependency loaded through a dynamic `import()`, so it lands in its own chunk and the panel downloads it only when it shows a live view.
- The fonts (Atkinson Hyperlegible Next and Mono, from `@fontsource-variable`) and the icons (`lucide-react`, tree-shaken to the icons used) are bundled, so the panel loads nothing from another origin and its CSP stays `default-src 'self'`. The styles are one stylesheet per component, next to it, in CSS cascade layers; the colour tokens of both themes are in `src/styles/tokens.css`.

### Smoke Tests

A hermetic, end-to-end smoke suite drives the real agent flow while mocking only the external boundaries
(LLM, target HTTP API, screen/OS, Neo4j). The tests are tagged `@Tag("smoke")` and are **excluded from the
default build**; they run in a dedicated GitHub Actions workflow (`.github/workflows/smoke.yml`) and can be
run locally with:

```bash
mvn -B test -P linux -Dtest.excluded.groups= -Dgroups=smoke
```

Both agents share the same result-status contract: `FAILED` is reserved for failed step verifications, while any
execution error (tool exception, budget exhaustion, aborted step, a model response without a tool call) and any
precondition failure (the test never got a fair run) yields `ERROR`.

Five suites are implemented:

- **API agent** (`api_test_execution_agent`) — drives the real `ApiTestAgent` flow against a local WireMock
  server with a scripted `ChatModel` standing in for the LLM (see
  [its README](api_test_execution_agent/README.md#smoke-tests)).
- **UI agent** (`ui_test_execution_agent`) — drives the real `UiTestAgent` knowledge-based execution flow
  (`UiTestAgent` -> `KnowledgeBasedExecutionOrchestrator` -> `StepExecutionOrchestrator` ->
  `VerificationTools`) with the LLM agents, `KnowledgeService` (Neo4j) and the screen mocked, so it runs
  headless (see [its README](ui_test_execution_agent/README.md#smoke-tests)).
- **UI agent operator panel** (`ui_test_execution_agent`) — `org.tarik.ta.smoke.PanelSmokeTest` drives the real operator panel server with a Unix-domain-socket stub for Xvnc and stubbed health and knowledge repositories for Neo4j: login and request checks, security headers, the event stream, displacement, the live view relay, the health route and the knowledge routes.
- **Shared A2A surface** (`agent_core`) — `org.tarik.ta.core.smoke.CoreA2aSmokeTest` starts the real
  `AbstractServer` over HTTP and exercises the JSON-RPC + Server-Sent-Events transport end to end through a
  small SSE-parsing test client (`A2aTestClient`): public agent-card discovery (advertising `streaming`),
  bearer-token auth, `message/send`, `message/stream`, failure propagation (a throwing executor fails the
  task on both `message/send` and the stream), JSON-RPC error mapping for malformed bodies (-32700) and
  unknown methods (-32601), the `tasks/get`, `tasks/cancel` and `tasks/resubscribe` task lifecycle (including
  their unknown-task and terminal-state error responses), and completion of a task whose domain-level result
  is `FAILED` (a failed test is a completed task carrying `FAILED` in its `final_result` artifact, not a
  failed task). Only the agent's test-execution body is a recording stand-in.
- **Full-stack transport-to-agent** (`api_test_execution_agent`) —
  `org.tarik.ta.smoke.ApiAgentA2aSmokeTest` closes the seam the other suites leave open: one `message/stream`
  request travels HTTP → `AgentExecutionResource` → executor → the real `ApiTestAgent` with real tools →
  WireMock, asserting the streamed step and final-result events over SSE. It reuses `A2aTestClient` via
  `agent_core`'s test-jar.

The smoke workflow is hardened with a 15-minute job timeout, a per-ref `concurrency` group that cancels
superseded runs, `-Djacoco.skip=true` (nothing consumes the coverage data there) and `paths-ignore` for
docs-only (`**/*.md`) changes.

## Configuration

### Core Configuration

The following configuration properties are shared across agents (defined in `AgentConfig`):

| Property                | Environment Variable    | Default                   | Description                                         |
|-------------------------|-------------------------|---------------------------|-----------------------------------------------------|
| `port`                  | `PORT`                  | `8005`                    | Server port                                         |
| `host`                  | `AGENT_HOST`            | (required)                | Server host                                         |
| `agent.version`         | `AGENT_VERSION`         | (required)                | Version advertised in the A2A agent card (`MAJOR.MINOR.PATCH`) |
| `agent.auth.token`      | `AGENT_AUTH_TOKEN`      | (empty ⇒ auth disabled)   | Shared secret required as `Bearer` token on the main endpoint |
| `external.url`          | `EXTERNAL_URL`          | `http://localhost:{port}` | External URL for A2A card                           |
| `vector.db.provider`    | `VECTOR_DB_PROVIDER`    | `neo4j`                   | Knowledge DB provider (`neo4j`)                     |
| `vector.db.url`         | `VECTOR_DB_URL`         | (required)                | URL for the vector database                         |
| `vector.db.key`         | `VECTOR_DB_KEY`         |                           | API Key/Token for the vector database               |
| `gemini.thinking.level` | `GEMINI_THINKING_LEVEL` | `MINIMAL`                 | Gemini thinking configuration level                 |
| `model.max.retries`     | `MAX_RETRIES`           | `10`                      | Maximum model API retries                           |
| `test.data.folder`      | `TEST_DATA_FOLDER`      | `test-data`               | The only folder the test context data tools may read files from; relative paths resolve against the agent's working directory |
| `sensitive.data.key.pattern` | `SENSITIVE_DATA_KEY_PATTERN` | `(?i).*(password\|passwd\|pwd\|pin\|token\|secret\|api[_-]?key\|credential).*` | Regex matched against test data keys; matching values are masked (see *Secret Masking*) |
| `LOG_LEVEL`             | `LOG_LEVEL`             | `INFO`                    | Global log level (INFO, DEBUG, WARN, ERROR)         |
| `LOG_FORMAT`            | `LOG_FORMAT`            | `text`                    | `json` writes one JSON object per log event (the cloud deployments set it); anything else keeps the readable pattern |
| `TZ`                    | `TZ`                    | `Europe/Vienna`           | Container timezone                                  |

There is no single global model provider/name; each specialized sub-agent has its own `<agent>.model.name` / `<agent>.model.provider`
properties (e.g. `precondition.agent.model.name`), documented per module below.

### Agent-Specific Configuration

Each agent has its own configuration file template with agent-specific settings:

| Agent                    | Configuration Template                               | Purpose                                                                                 |
|--------------------------|------------------------------------------------------|-----------------------------------------------------------------------------------------|
| UI Test Execution Agent  | `ui_test_execution_agent/config.properties.example`  | UI-specific settings (operator panel, element locator, video recording, vision agents) |
| API Test Execution Agent | `api_test_execution_agent/config.properties.example` | API-specific settings (HTTP client, proxy, timeouts, schema validation, authentication) |

### Telemetry (OpenTelemetry)

Both agents record the GenAI client metrics of the OpenTelemetry semantic conventions, so a collector can aggregate them together
with those of any other service:

| Instrument                        | Type      | Unit       | Recorded per                                                              |
|-----------------------------------|-----------|------------|---------------------------------------------------------------------------|
| `gen_ai.client.token.usage`       | histogram | `{token}`  | model call and `gen_ai.token.type` (`input`, `output`, `cache_read`)      |
| `gen_ai.client.operation.duration`| histogram | `s`        | model call                                                                |

Both carry the attributes `gen_ai.operation.name` (the sub-agent operation, e.g. `test_step_action`), `gen_ai.provider.name`
(`gcp.gen_ai`, `gcp.vertex_ai`, `openai`, `anthropic`, `groq`, and the self-hosted `hcompany` / `qwen`), `gen_ai.request.model`,
`gen_ai.response.model` and, for a provider addressed through a configured URL, `server.address`. The task id is deliberately *not*
an attribute — it is unbounded and would explode the metric cardinality; a single run is correlated through the logs and the
`token_usage` artifact instead. A token type the provider does not report produces no data point at all, so a model without prompt
caching contributes no `cache_read` series.

Configuration is the standard OpenTelemetry one, through environment variables only:

| Environment Variable           | Default                     | Description                                                            |
|--------------------------------|-----------------------------|------------------------------------------------------------------------|
| `OTEL_METRICS_EXPORTER`        | `none`                      | Set to `otlp` to export; `none` keeps the instruments in memory        |
| `OTEL_EXPORTER_OTLP_ENDPOINT`  | (empty)                     | Collector endpoint, e.g. `http://collector:4317`                       |
| `OTEL_SERVICE_NAME`            | `test-execution-agent`      | Service name the metrics are attributed to                             |
| `OTEL_METRIC_EXPORT_INTERVAL`  | `60000`                     | Export interval in milliseconds                                        |

All exporters (metrics, traces, logs) default to `none`, so an agent that is not told where to send its telemetry costs nothing and
emits nothing. **This repository provisions no collector**; the deployment scripts only pass the variables through
(`vm_startup_script.sh` and `deploy_vm.sh` for the UI agent's VM, the Cloud Run `--set-env-vars` for the API agent, and
`build_and_run_docker.bat` from the calling shell locally).

### Setup

1. Copy the appropriate `config.properties.example` to `src/main/resources/config.properties`:
   ```bash
   # For UI Agent
   cp ui_test_execution_agent/config.properties.example ui_test_execution_agent/src/main/resources/config.properties
   
   # For API Agent
   cp api_test_execution_agent/config.properties.example api_test_execution_agent/src/main/resources/config.properties
   ```

2. Update the configuration values with your API keys, endpoints, and desired settings.

3. Properties can be overridden using environment variables in deployment environments.

### Self-Hosted Models Served by Cloud Run

Two providers serve models the project hosts itself, each with its own endpoint:

| Provider   | Endpoint property   | Serves                                                                                       |
|------------|---------------------|-----------------------------------------------------------------------------------------------|
| `hcompany` | `hcompany.endpoint` | Holo3.1, supported for grounding and for any sub-agent — see `ui_test_execution_agent/deployment/holo` |
| `qwen`     | `qwen.endpoint`     | A Qwen model on any OpenAI-compatible server such as vLLM, supported for any sub-agent     |

Either provider can serve any sub-agent, chosen per agent through its `*.model.provider`. The shipped configuration runs all of them on `google` with `gemini-3.8-flash`; moving one to Holo3.1 means setting its `*.model.provider` to `hcompany` and its `*.model.name` to `Hcompany/Holo-3.1-35B-A3B-NVFP4` (for the grounding agent also `element.locator.grounding.output.format=CENTER_AND_SIZE`), moving one to Qwen setting them to `qwen` and the name of the served model, and the providers can be mixed freely.

`config.properties` ships only placeholders for both endpoints: set the service URLs through the `HCOMPANY_ENDPOINT` and `QWEN_ENDPOINT` environment variables, which the VM deployment reads from the optional Secret Manager secrets of the same names.

When either endpoint points at a `*.run.app` host, the agent ignores the matching API key property and authenticates with a Google
identity token minted from the application default credentials, because Cloud Run authorizes through IAM. Grant `roles/run.invoker`
on the service to a dedicated service account and make the credentials resolve to it once:

```bash
gcloud auth application-default login --impersonate-service-account=<invoker service account>
```

Alternatively, point `GOOGLE_APPLICATION_CREDENTIALS` at that service account's key file. Any other endpoint keeps using its
configured API key unchanged.

A plain `http://` endpoint of any OpenAI-compatible provider (`openai`, `groq`, `hcompany`, `qwen`), such as a vLLM server on a VM or behind a tunnel, is called over HTTP/1.1: the JDK HTTP client would otherwise try an `h2c` upgrade, and uvicorn-based servers like vLLM then drop the request body and answer `400` with `body: Field required`. `https://` endpoints keep negotiating HTTP/2 through TLS. The manual comparison suites build their clients the same way (`OpenAiCompatibleHttpClients` in `agent_core`).

How the two providers are asked depends on the job rather than on the provider:

| Request                     | Thinking                              | Temperature                  | Tool choice |
|-----------------------------|---------------------------------------|------------------------------|-------------|
| Grounding, either provider  | off                                   | `model.temperature`          | none |
| Sub-agent on `qwen`         | `qwen.agent.thinking.enabled`         | `model.temperature`          | `required` |
| Sub-agent on `hcompany`     | `hcompany.agent.thinking.enabled`     | `hcompany.agent.temperature` | none |

* **Grounding keeps thinking off, always.** The answer is schema-constrained, and with thinking on the chat template opens a
  think block the constrained answer never leaves: the whole valid answer arrives as reasoning and the content is empty.
* **A Holo sub-agent needs thinking on.** It is the only thing that makes its tool-calling loop finish. Measured over two
  baselines at three samples each, the loop reached the final-result tool 6/6 with
  thinking on and 0/6 with it off - the model instead announcing in prose that the work was done, which ends the loop with no
  typed result. A forced tool choice did not recover it, nor did a prompt forbidding prose.
* **A Holo sub-agent runs off the global temperature.** The same loop measured 3263 ms at 0.8 against 6694 ms at 0.0: greedy
  decoding makes this model reason longer and more repetitively for no gain in accuracy, and its own `generation_config.json`
  samples at 1.0. Grounding is unaffected and keeps `model.temperature`, which is what its benchmark was measured at.
* **The H Company service ignores the tool choice.** Asked with `tool_choice=required` that deployment answers with prose in the content, an empty tool call array and a finish reason of `tool_calls`, and naming the tool outright behaves the same way.

Element IDs shown to a model are short aliases (`E1`, `E2`, ...) rather than UUIDs, because an ID travels to the model as text and comes back as a tool argument - a 36-character UUID has to be reproduced character by character, and self-hosted models get one wrong often enough to break the database lookup and the step with it. DB search and creation tools return these aliases, and the alias registry resolves them back to persistent UUIDs both during screen location and when processing the agent's final result.

## Cloud Deployment

Both agents can be deployed to Google Cloud using Cloud Build. The `cloudbuild.yaml` file defines the deployment pipeline.

### Prerequisites

1. Enable the required GCP services:
    - Cloud Build API
    - Cloud Run API
    - Compute Engine API
    - Container Registry API
    - Secret Manager API

2. Configure secrets in Secret Manager:
    - `GOOGLE_API_KEY`
    - `GROQ_API_KEY`
    - `GROQ_ENDPOINT`
    - `VECTOR_DB_URL`
    - `VECTOR_DB_KEY` (auto-created by Neo4j deploy script with generated password)
    - `PANEL_PASSWORD`, `PANEL_TLS_CERT`, `PANEL_TLS_KEY` (for UI agent: the operator panel login, at least 16 characters, and its PEM certificate chain and private key)
    - `HCOMPANY_ENDPOINT` (optional, for UI agent: URL of the self-hosted Holo3.1 service)
    - `QWEN_ENDPOINT` (optional, for UI agent: URL of a self-hosted Qwen service)
    - `NEO4J_USERNAME` (auto-created by Neo4j deploy script, defaults to `neo4j`)
    - `AGENT_AUTH_TOKEN` (shared bearer token required on the main A2A endpoint; consumed by both agents)

3. Create a VPC connector named `agent-network-connector` for Cloud Run to access internal resources.

### Deploying with Cloud Build

#### Deploy All Agents (Default)

To deploy both UI and API agents (`_FIREWALL_PANEL_SOURCE_RANGES` is required whenever the UI agent is deployed: the CIDR ranges allowed to reach its operator panel; several ranges need gcloud's alternate delimiter, e.g. `--substitutions=^;^_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24,198.51.100.7/32`):

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24
```

#### Deploy Agents Separately

You can deploy agents individually using the `_DEPLOY_TARGET` substitution:

**Deploy only the UI agent:**

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=ui,_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24
```

**Deploy only the API agent:**

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=api
```

**Deploy only the Neo4j knowledge persistence VM:**

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=neo4j
```

#### Standalone Deployment (Module-Level)

Each agent and Neo4j also have their own `cloudbuild.yaml` for fully independent deployment:

**UI Agent standalone deployment:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml --substitutions=_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24
```

**API Agent standalone deployment:**

```bash
gcloud builds submit --config=api_test_execution_agent/deployment/cloud/cloudbuild.yaml
```

**Neo4j standalone deployment:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/neo4j/cloudbuild.yaml
```

#### Customizing Deployment Parameters

To customize deployment parameters, you can override substitutions:

```bash
gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=_DEPLOY_TARGET=api,_IMAGE_TAG=v1.0.0,_API_AGENT_EXTERNAL_URL=https://your-actual-cloudrun-url.run.app
```

> **Note:** After the first deployment, update `_API_AGENT_EXTERNAL_URL` in `cloudbuild.yaml` with the actual Cloud Run service URL. This
> URL is displayed in the Cloud Run console after deployment and is required for the A2A agent card to advertise the correct service endpoint.

### Deployment Details

| Component                | Deployment Target | Port       | Access                              |
|--------------------------|-------------------|------------|-------------------------------------|
| UI Test Execution Agent  | GCE VM (SPOT)     | 443, 8443  | A2A (VPC only) + Operator panel (HTTPS) |
| API Test Execution Agent | Cloud Run         | 8005       | HTTP (internal by default)          |
| Neo4j Knowledge DB       | GCE VM (SPOT)     | 7687, 7474 | Bolt (VPC + external), HTTP Browser |

## Test Execution Results

During execution, a `working` status update announces each test step / precondition right before it runs (an `ExecutionActivity` payload
with `activityType` and `description`, for live "currently executing" dashboards); each precondition and test step result is then streamed
as an artifact as soon as it is recorded; and log lines are streamed as appended chunks of a single growing `execution_log.log` artifact
(UI step artifacts additionally include the step screenshot). The task then **completes with a single consolidated
`TestExecutionResult`** — emitted both as a final artifact (full result JSON + logs file + end-of-test general screenshot) and as the
completion message. Per-step screenshots are not re-bundled into the final artifact since they were already streamed with their step events,
so a non-streaming `message/send` caller receives one ready-to-consume result object, while a streaming `message/stream` caller additionally
sees the live per-step progress.

The consolidated `TestExecutionResult` contains:

| Field                     | Description                           |
|---------------------------|---------------------------------------|
| `testCaseName`            | Name of the executed test case        |
| `testExecutionStatus`     | PASSED, FAILED (a verification failed), or ERROR (an execution error occurred) |
| `preconditionResults`     | Results for each precondition         |
| `stepResults`             | Results for each test step            |
| `executionStartTimestamp` | When execution started                |
| `executionEndTimestamp`   | When execution completed              |
| `generalErrorMessage`     | Overall error message if any          |
| `systemInfo`              | Device, OS, browser, environment info |
| `logs`                    | Captured execution logs               |

UI agent results additionally include screenshots and video recordings.

## Knowledge Persistence (Neo4j)

The UI Test Execution Agent supports an optional **knowledge persistence layer** backed by Neo4j 2026.01 or newer that enables the agent to learn and
remember procedures (reusable test action sequences) across sessions.

### Features

- **Procedure Graph**: Stores hierarchical procedures (composite and atomic) as a Neo4j graph with CONTAINS (parent-child) and TARGETS (step-to-UI-element) relationships. To ensure data integrity, each procedure is restricted to target at most/exactly one `UiElement` by automatically deleting any previous target relationship before linking a new one.
- **PDDL-Lite Planning**: Prerequisite/effect state tracking enables automatic prerequisite resolution during test execution.
- **Queue-Based Execution**: Replaces the sequential for-loop with a dynamic execution queue that injects prerequisite steps when
  prerequisites are unmet.
- **Start From A Later Step**: When the operator starts the run from a later test step in the start-step prompt, the preconditions and the earlier test steps are treated as already executed: the effects of the procedure that matches each of them with high confidence are seeded into the execution state before the first selected step, so its prerequisites are satisfied. A skipped item without a high-confidence match is logged and seeds nothing.
- **Unsatisfied Prerequisites**: When a mandatory atomic step's prerequisites are not satisfied, UNATTENDED mode fails the item and stops. In SUPERVISED mode, the "Prerequisites Not Satisfied" warning is followed by the standard next-action prompt: browse or create procedures, rerun the whole test step, rerun the atomic procedure (reloaded from the DB and checked against its prerequisites again, so an edit of its prerequisites takes effect), or terminate.
- **Human-in-the-Loop Collecting knowledge**: In SUPERVISED mode, the agent opens the procedure editor prompt of the operator panel for operators to collect new
  procedures when an unknown action is encountered. AI suggests all info which a new procedure must contain.
- **Unified Vector Store**: UI element storage migrated from Chroma/Qdrant to Neo4j, accessed through the Neo4j Java driver, providing both
  graph relationships and vector search in a single database.
- **Atomic Phrase-Node Writes**: Ingest, supervised update, and delete each update both the `Procedure` node properties
  (`prerequisites`/`effects`) and the linked `PhraseEmbedding` nodes in a single Neo4j transaction, eliminating partial-write
  inconsistencies.
- **Phrase-Aware Reads**: `ProcedureRepository.findByIdWithPhrases` rebuilds the returned `Procedure`'s prerequisites/effects lists from
  the ordered `PhraseEmbedding` nodes whenever they exist, so callers always see the authoritative phrase order rather than
  potentially-stale node properties.
- **Truthful Element Staleness**: `ProcedureRepository.updateElementStability` advances a UI element's `lastLocatedAt` only when the
  element was actually located. A failed location attempt updates the stability score but leaves the timestamp at the moment the
  element was last really seen, so staleness stays meaningful.
- **Bidirectional Startup Sync**: `PhraseNodeMigrationService` runs two passes at startup — a forward pass that creates missing phrase nodes from node-property lists (legacy backfill), and a backward pass that repairs stale node-property lists from the authoritative phrase nodes (`ProcedureRepository.findWithPhrasePropertyMismatches` + `updatePhraseProperties`). Any forward migration or backward repair failure during this startup sequence triggers an `IllegalStateException` that immediately aborts startup with clear diagnostic logs.

### Neo4j Setup

#### Local Development

1. Start a Neo4j Community Edition container locally:
   ```bash
   docker run -d --name neo4j-knowledge \
     -p 7687:7687 -p 7474:7474 \
     -e NEO4J_AUTH="neo4j/your-secure-password" \
     -e NEO4J_db_query_default__language=CYPHER_25 \
     -v neo4j-data:/data \
     neo4j:2026.09.0-community
   ```

2. Configure the agent by setting the following in `config.properties` or via environment variables:
   ```properties
   vector.db.url=bolt://localhost:7687
   neo4j.username=neo4j
   vector.db.key=your-secure-password
   neo4j.database=neo4j
   ```

#### Cloud Deployment (Dedicated GCE VM)

Neo4j is deployed on its own dedicated GCE VM with a persistent data disk, separate from the UI agent VM. Both VMs share the same
`agent-network` VPC for internal Bolt connectivity.

```
agent-network VPC
├── neo4j-knowledge-vm (e2-medium, SPOT)
│   ├── Persistent Disk: neo4j-data-disk (20GB)
│   ├── Docker: neo4j:2026.09.0-community (Cypher 25 default)
│   └── Ports: 7687 (Bolt), 7474 (HTTP)
│
└── ui-test-execution-agent-vm (SPOT)
    └── VECTOR_DB_URL=bolt://<neo4j-internal-ip>:7687
```

**Deploy Neo4j VM:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/neo4j/cloudbuild.yaml
```

The deploy script automatically:

1. Creates `NEO4J_USERNAME` and `VECTOR_DB_KEY` secrets in Secret Manager if they don't exist (username defaults to `neo4j`, key is
   auto-generated)
2. Creates a persistent data disk that survives VM recreations
3. Provisions the VM with authentication enabled and verifies credentials on startup

The startup script locates the data disk by its configured `DATA_DISK_NAME` (passed via VM metadata) and aborts if that disk is not
attached, rather than silently starting Neo4j against an empty boot-disk directory. An existing data disk is never reformatted; only a
genuinely empty disk is formatted on first boot. When attaching a cloned/restored disk, its `device-name` must equal `DATA_DISK_NAME`.

The startup script reads the secrets with the gcloud CLI image from Google's own registry (`gcr.io/google.com/cloudsdktool/google-cloud-cli:alpine`, which avoids Docker Hub's rate limit on anonymous pulls) and retries every image pull (that one and the Neo4j image) up to 10 times, 15 seconds apart, before the boot fails. Both deploy scripts (`deploy_neo4j_vm.sh` and the UI agent's `deploy_vm.sh`) enable only the GCP services of `_GCP_SERVICES` that are not enabled yet (`deployment/gcp_services.sh`), because enabling a service needs a permission the Cloud Build account may lack.

Neo4j runs with Cypher 25 as the default query language (`NEO4J_db_query_default__language=CYPHER_25`), and every query of the agent carries a `CYPHER 25` prefix anyway. A `neo4j` database created by an older Neo4j keeps its Cypher 5 default, and Community Edition cannot change it (`ALTER DATABASE` is Enterprise only), so the startup script converts it once: it recreates only the system database, which registers the existing `neo4j` store with the Cypher 25 default and restores the credentials, and it verifies the default language and the node count before Neo4j is served again. The conversion runs in a container without published ports, keeps the original system database in `cypher25-migration/` on the data disk until it is verified, and is resumed on the next boot if the VM stops in the middle; later boots skip it. Snapshot the data disk before deploying a newer Neo4j image: the first start upgrades the store in place, and Neo4j cannot downgrade a store.

**Then deploy the UI agent with the Neo4j host:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
  --substitutions=_NEO4J_HOST=<neo4j-internal-ip>,_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24
```

The Neo4j VM's internal IP is printed in the deploy output. The UI agent VM fetches `VECTOR_DB_KEY` and `VECTOR_DB_URL` from Secret
Manager at startup and passes them to the agent container.

To retrieve credentials for local development against the cloud Neo4j instance:

```bash
gcloud secrets versions access latest --secret=NEO4J_USERNAME
gcloud secrets versions access latest --secret=VECTOR_DB_KEY
```

### Knowledge Configuration Properties

| Property                          | Environment Variable              | Default                 | Description                                                       |
|-----------------------------------|-----------------------------------|-------------------------|-------------------------------------------------------------------|
| `neo4j.username`                  | `NEO4J_USERNAME`                  | `neo4j`                 | Neo4j username (from Secret Manager in cloud)                     |
| `neo4j.database`                  | `NEO4J_DATABASE`                  | `neo4j`                 | Neo4j database name                                               |
| `knowledge.embedding.model`       | `KNOWLEDGE_EMBEDDING_MODEL`       | `multilingual-e5-small` | Embedding model for semantic search                               |
| `knowledge.max.depth`             | `KNOWLEDGE_MAX_DEPTH`             | `3`                     | Maximum procedure decomposition depth                         |
| `knowledge.embedding.batch.size`  | `KNOWLEDGE_EMBEDDING_BATCH_SIZE`  | `10`                    | Batch size for embedding generation                               |
| `knowledge.match.confidence.high` | `KNOWLEDGE_MATCH_CONFIDENCE_HIGH` | `0.95`                  | Auto-execute floor (raw cosine 0.90): a match at or above it is executed directly              |
| `knowledge.match.confidence.low`  | `KNOWLEDGE_MATCH_CONFIDENCE_LOW`  | `0.88`                  | Candidate floor (raw cosine 0.76): below it a procedure is not a candidate at all |
| `satisfies.similarity.threshold`  | `SATISFIES_SIMILARITY_THRESHOLD`  | `0.90`                  | Candidate floor for creating a `SATISFIES` edge (raw cosine 0.80)  |
| `element.retrieval.min.target.score` | `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE` | `0.93`           | Candidate floor for the targeted UI element                       |
| `element.retrieval.min.general.score` | `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE` | `0.87`         | Candidate floor for general UI element retrieval                  |
| `neo4j.max.connection.pool.size`  | `NEO4J_MAX_CONNECTION_POOL_SIZE`  | `20`                    | Maximum Neo4j driver connection pool size                         |
| `neo4j.connection.acquisition.timeout.seconds` | `NEO4J_CONNECTION_ACQUISITION_TIMEOUT_SECONDS` | `30` | Timeout for acquiring a connection from the pool |
| `neo4j.connection.timeout.seconds` | `NEO4J_CONNECTION_TIMEOUT_SECONDS` | `10`                   | Neo4j connection timeout in seconds                                |
| `neo4j.max.transaction.retry.time.seconds` | `NEO4J_MAX_TRANSACTION_RETRY_TIME_SECONDS` | `120`      | Maximum time the driver retries a managed transaction              |
| `element.appearance.history.size` | `ELEMENT_APPEARANCE_HISTORY_SIZE` | `3`                     | Expired appearances (earlier screenshots and descriptions) kept per UI element |
| `failure.hints.stale.days`        | `FAILURE_HINTS_STALE_DAYS`        | `90`                    | Days without recurrence after which a failure hint expires        |
| `failure.hints.max.count`         | `FAILURE_HINTS_MAX_COUNT`         | `5`                     | Most failure hints of a procedure passed to the prompts and the hints tool |
| `anomaly.recording.enabled`       | `ANOMALY_RECORDING_ENABLED`       | `true`                  | Records failed element locations, failed steps and timing outliers as `ExecutionAnomaly` nodes |
| `anomaly.history.size`            | `ANOMALY_HISTORY_SIZE`            | `20`                    | Execution anomalies kept per procedure or UI element              |
| `anomaly.timing.outlier.factor`   | `ANOMALY_TIMING_OUTLIER_FACTOR`   | `3.0`                   | A step taking longer than this factor times its average is recorded as a timing outlier |
| `health.recent.window.days`       | `HEALTH_RECENT_WINDOW_DAYS`       | `30`                    | Window of the recent-activity categories of the knowledge graph health report |

Knowledge persistence is automatically enabled when `vector.db.key` (or `VECTOR_DB_KEY`) is set to a non-blank value. Both
`NEO4J_USERNAME` and `VECTOR_DB_KEY` are stored as secrets in Secret Manager for cloud deployments.

**Similarity scale and the matching bands:** every threshold above is on Neo4j's normalised scale, where a vector index
returns `score = (1 + cosine) / 2`; the raw cosine of a configured value is therefore `2 · score − 1`. The code defaults and the
values in `config.properties` are identical, so one value is never silently overridden by the other. A semantic score alone cannot
tell a paraphrase from a swapped value — with the bundled BGE model, "Select 'Germany'" against "Select 'France'" scores 0.966 and
"logged in" against "logged out" scores 0.973, both above any threshold that still accepts genuine paraphrases. The auto-execute
floor is therefore set high enough that only a near-verbatim match runs on its own, and matches are resolved in three bands:

- **below `knowledge.match.confidence.low`** — not a candidate; the procedure is treated as unknown.
- **between the two thresholds** — too close to a value or element swap to execute unattended: the candidate goes to the operator
  in supervised mode and fails the run in unattended mode.
- **at or above `knowledge.match.confidence.high`** — a near-verbatim match, executed directly.

The same limit applies to negated effects, which are matched against the current state at `satisfies.similarity.threshold`: a negated effect also removes a current effect describing the opposite state. This is harmless when the procedure adds that opposite state as its own effect, as a closing or logging-out step usually does; otherwise the removed state is missing for the prerequisite checks that follow.

**Non-English descriptions:** the default `bge-small-en-v15` model is English-only and scores unrelated German phrases far too high
("Speichern" against "Abbrechen" scores 0.985). Set `knowledge.embedding.model=multilingual-e5-small` when the procedures are not
written in English.

**Resilient DB connection:** The Neo4j driver does not verify connectivity on startup. Schema migration runs in a background virtual thread
at startup (the server waits for it to finish but is not blocked from starting if the DB is unreachable). If migration fails due to
connection issues, the error is logged and the server starts normally. Migration is automatically retried on the first incoming DB
operation. If migration still fails during request processing, the entire request fails with a `DatabaseConnectionException`. The driver
is configured with `withMaxTransactionRetryTime(120s)` so managed-transaction retries (~10 × 10s connection-timeout cycles) are handled
transparently by the driver without any custom retry loop.

### UiElementCache

The UI Test Execution Agent uses a session-scoped `UiElementCache` singleton to avoid redundant Neo4j fetches for the same `UiElement`
across tools, prompt actions, and orchestrators.

#### Cache Operations

| Operation | Triggered By |
|-----------|--------------|
| **put** (populate) | `UiElementDbTools.searchElementInDb()`, `UiElementDbTools.createElementInDb()` |
| **update** | `UiElementRefinementHelper.updateElementInfo()`, `UiElementRefinementHelper.replaceElementScreenshot()`, `UiElementResolutionService` inline screenshot update |
| **remove** | `UiElementRefinementHelper.deleteElement()` |
| **get** | `UiElementRefinementHelper.findElementById()` |

#### Element Details in Agent Messages

When the execution orchestrator resolves a target UI element for an atomic step, it retrieves the full `UiElement` from the cache and
passes its screenshot and description to the action/precondition agents alongside the current screen screenshot. The user message includes:

```
## Target UI element details
- Name: {element.name}
- Description: {element.description}
- Location details: {element.locationDetails}
- Enclosing component (the component containing this UI element, e.g. a page, dialog, form, panel or pop-up): {element.parentElementSummary}
- Depends on data: {yes|no}
```

Every piece of data in a user message sits in its own Markdown section; see *User message format* in the [UI agent README](ui_test_execution_agent/README.md).

This allows the agent to see both the current screen and the reference screenshot of the target element for more accurate interaction. Only for an element which depends on data, the agent reads its description and location details to find out which specific data identifies it, extracts that data from everything it was given (the data of the action or precondition, the data of the whole test step or test case precondition, the procedure hierarchy and the test execution context data) and passes it to the location tool; it fails the action when that data is missing, and passes no data for any other element.

### Collecting knowledge Workflow

1. During test execution, the agent encounters an unknown action (no matching procedure in the knowledge graph).
2. The AI suggestion agent analyzes the action and proposes preconditions, effects, negated effects (states the action ends), expected results, and child steps. Expected results use an array in the tool contract; legacy model responses containing a single string are accepted and normalized to the same list.
3. The procedure editor prompt of the operator panel presents the suggestions for the operator to review, modify, or accept. Child steps are listed with screenshot thumbnails — including persisted child steps loaded from the knowledge DB, which show their target UI element's screenshot (atomic steps only); "Edit" (or "Define" for a suggested step not saved yet) opens that child in the same prompt, at any depth, and "Back to the parent" or the breadcrumb returns to the parent it was opened from. A child is saved into the knowledge DB when the operator saves it in its own view. The editor tracks unsaved changes and asks before discarding them.
4. For atomic steps, the operator can: (a) run the agent-driven element search ("Locate on screen"), or (b) open the element lookup ("Choose a known element") to search for an existing element by the element description extracted from the procedure and link it, or create the described element directly (the agent skips the DB search and creates the element, then captures its screenshot). When the creation fails (e.g. the element is not found on the screen or several matching elements are), the lookup stays open with the reason, so the operator can refine the description and create again, choose an existing element, or go back.
5. "Pick from earlier effects" in the Prerequisites list opens an effect picker with exactly the effects the prerequisite check will see when the edited procedure runs, newest first and filterable: the effects currently in the execution state (only effects produced before the current test step when a new procedure is created, because it replaces that step on a rerun), then the own effects of the preceding sibling steps at every nesting level. A finished composite contributes only its own effects, never those of its child steps, because the runtime drops them when the composite's scope closes. Picking one inserts the phrase verbatim so it matches the effect exactly, and "Type a prerequisite instead" adds an empty entry; "Add" always types one directly.
6. The completed procedure tree is persisted to Neo4j with all relationships and embeddings.
7. On subsequent executions, the agent recognizes the action and executes the learned procedure automatically.

## Development Skills

The project includes AI assistant skills in `.agents/skills/` to help with common development tasks. All Java rules live in a single document, [JAVA_GUIDELINES.md](JAVA_GUIDELINES.md) (Java 27, no preview features); the general working rules are in [AGENTS.md](AGENTS.md). The skills do not restate these rules: they reference the relevant guideline sections and add only project-specific conventions.

| Skill                       | Description                                                                                                                                                                              |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **architecting-features**   | Designs a new feature or change and produces a compact implementation plan for approval: code reuse, library research, the architecture-first CALM update (modelled, validated, rendered and approved before any code), Java design, versions, smoke-suite impact, verification steps |
| **creating-new-agent**      | Creates a new A2A agent module or LangChain4j sub-agent modelled on the API agent: configuration, result records, agents, tools, wiring, executor, deployment, tests, smoke suite and CALM |
| **implementing-changes**    | Implements a plan or change request completely in one run, never in batches: when the request names the parts to implement (phases, steps, findings), the user only confirms them, otherwise picks *Implement everything* or *Implement specific parts*; the lead agent implements and fixes in its own context, while per round an independent headless reviewer and tester (separate `claude -p` processes fed from handover files in the run directory) verify it in parallel, until no CRITICAL/HIGH/MEDIUM finding with confidence ≥ 40 remains and the tests pass with 80% coverage of the changed lines; the user picks model and effort for the reviewer and the tester; the smoke suite is left for a manual run |
| **writing-unit-tests**      | Writes JUnit 6, AssertJ and Mockito unit tests following the project's reference tests and pitfalls                                                                                      |
| **running-unit-tests**      | Runs the unit tests with Maven, diagnoses failures and fixes their root cause                                                                                                            |
| **preparing-pull-requests** | Runs the CI checks locally (build, tests, license headers, dependencies, CALM), checks guideline compliance, documentation and smoke coverage, then commits and opens the PR after approval |
| **reviewing-pull-requests** | Reviews a GitHub PR against `AGENTS.md`, `JAVA_GUIDELINES.md` and the review criteria, shows the numbered findings, and posts only the ones the user names (`numbers`, `all` or `none`) as one review with inline comments |
| **commit-and-push**         | Commits all changed and untracked files with user-chosen timestamps and pushes them to the remote repository                                                                             |

## Architecture model (CALM)

The repository's own architecture is modeled as [FINOS CALM](https://calm.finos.org/) (Common Architecture Language Model) documents under `calm/`. The model is self-contained: it covers only what this repository owns or directly integrates with (the two deployable agents, Neo4j, the LLM provider, the system under test, the hosting runtimes and the human operator who uses the UI agent's operator panel, modeled as an `actor` node). The orchestrator that calls the agents over A2A is modeled only as an external `system` node — the model has no reference to any file or identifier of the orchestrator repository, and validation runs fully offline against the meta-schemas bundled with `calm-cli`. The endpoints each agent serves are modeled as node interfaces: `ui-agent-a2a` (internal) and `ui-agent-panel` (public) on the UI agent, `api-agent-a2a` (internal) on the API agent.

```
calm/
├── architecture/
│   └── execution-agents.arch.json    (the architecture instance: nodes, relationships, descriptions)
└── patterns/
    └── execution-agents.pattern.json (governance pattern requiring every node/relationship listed above)
```

### Validating locally

```shell
npx -y @finos/calm-cli@1.60.1 validate -p calm/patterns/execution-agents.pattern.json -a calm/architecture/execution-agents.arch.json --strict -f pretty
```

This is the exact command run by the blocking `Architecture (CALM)` CI job (`.github/workflows/architecture.yml`) on every push/PR to
`main`.

### Changing the model

* Add or change a node/relationship in `calm/architecture/execution-agents.arch.json`, then add the matching required entry (fixed
  `unique-id`/`node-type`/`relationship-type`, free-form `name`/`description`) to `calm/patterns/execution-agents.pattern.json` and bump
  the `minItems`/`maxItems` counts — the pattern requires every node and relationship listed in the architecture, so the two files must
  stay in lockstep.
* Run the validation command above before committing; it must exit `0` with `No issues found.`.

### Design decisions

* **Controls deferred**: the bearer-token authentication enforced by `AbstractServer.requireValidToken`
  (`Authorization: Bearer <AGENT_AUTH_TOKEN>` on the main A2A endpoint, 401 on mismatch, open with a logged warning when the token is unset
  for local runs) is documented in the `ui-test-execution-agent`/`api-test-execution-agent` node descriptions instead of formal CALM
  controls. Formal controls need their own requirement schemas, a repo-owned URL namespace, and a URL-mapping file for offline resolution —
  three files of scaffolding for a single control. Introduce them once a second control appears or machine-checkable governance is
  actually needed.
* **Endpoints as free-form interfaces**: each interface is a CALM `interface-type` (a `unique-id` plus free properties `description`, `protocol`, `port` with the local default, `port-env`, `authentication` and `exposure`); the pattern requires the interfaces of each agent by `unique-id`. The deployed port, when it differs from the default (443 for the UI agent's A2A port on the GCE VM), is stated in the interface description. The orchestrator and operator edges stay `interacts` relationships, which cannot name an interface, so the interface they use is stated in their descriptions.
* **The OpenTelemetry collector is a node although this repository does not provision it**: the agents integrate with it over OTLP,
  and CALM models the integration edges of the system regardless of who operates the other end - exactly as for the orchestrator and
  the LLM provider. The edge exists in the model even when no endpoint is configured at runtime and nothing is exported.
* **No duplicate Cloud Build validation stage**: `cloudbuild.yaml` does not run this validation. It would run the identical command,
  restructure the existing stage dependency graph (`deploy-neo4j` starts immediately today), and needlessly block `_DEPLOY_TARGET=neo4j`
  deployments. CI (`.github/workflows/architecture.yml`) is the only gate; bypassing it via a manual `gcloud builds submit` is an accepted
  risk for a single-maintainer repo.

## Documentation

* For detailed documentation on the UI Test Execution Agent, see **[UI Agent README](ui_test_execution_agent/README.md)**.
* For detailed documentation on the API Test Execution Agent, see **[API Agent README](api_test_execution_agent/README.md)**.
* For development guidelines, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.
See the [LICENSE](LICENSE) file for the full license text.

The third-party components it uses, bundles or installs, and their licenses, are listed in the [NOTICE](NOTICE) file.

Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
