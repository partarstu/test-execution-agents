# API Test Execution Agent

## Overview

This module provides the API Test Execution Agent capabilities, enabling automated API testing through LLM-driven orchestration.
It is implemented as a standalone Cloud Run-deployable service that communicates via the A2A (Agent-to-Agent) protocol.


## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   API Test Execution Agent                        │
├──────────────────────────────────────────────────────────────────┤
│  Server (resolves and starts the injected AbstractServer)         │
│    └─► ApiAgentExecutor (extends AbstractAgentExecutor)           │
│          └─► ApiTestAgent (main entry point)                      │
│                ├─► ApiPreconditionActionAgent                     │
│                └─► ApiTestStepActionAgent                         │
├──────────────────────────────────────────────────────────────────┤
│  Context & Tools                                                  │
│    ├─► ApiContext (session/cookie/config management)             │
│    ├─► ApiRequestTools (HTTP requests, auth handling)            │
│    ├─► ApiAssertionTools (schema/OpenAPI validation)             │
│    └─► TestContextDataTools (JSON/CSV data loading)              │
└──────────────────────────────────────────────────────────────────┘
```

## Key Classes

The runtime bootstrap now uses Avaje Inject modules. `Server` creates the root `BeanScope` and keeps it open while the server runs (closing it would shut down the beans it owns, such as the OpenTelemetry SDK; its shutdown hook closes it when the JVM exits), resolves `AbstractServer` from `agent_core`,
and `ApiAgentExecutor` opens a child request scope through `ApiAgentRequestScopeFactory` for each test case execution. The request scope
explicitly reuses app-scope singletons such as `ApiTestAgentConfig`, `ModelFactory`, `TestCaseExtractor`, and `BudgetManager` so manual
or nested scope creation resolves all required dependencies consistently.

| Class | Description |
|-------|-------------|
| `Server` | Thin bootstrap entry point that creates the root `BeanScope` and starts the injected `AbstractServer` |
| `ApiAgentCardFactory` | Produces the singleton A2A `AgentCard` bean from injected configuration |
| `AgentCardProducer` | Builds the A2A `AgentCard`: advertises the version from `agent.version`, the test-execution skill in the `skills` list and renders an HTML-friendly description listing each sub-agent and its configured model |
| `ApiAgentExecutor` | Handles A2A task execution, extends `AbstractAgentExecutor` |
| `ApiAgentRequestScopeFactory` | Centralizes creation of API request-scoped child `BeanScope` instances |
| `ApiTestAgent` | Main orchestrator for API test execution |
| `ApiTestAgentConfig` | API-specific configuration properties |
| `ApiContext` | Session state management (cookies, variables, last response) |
| `ApiPreconditionActionAgent` | Executes and verifies test preconditions |
| `ApiTestStepActionAgent` | Executes and verifies individual test steps |

## Features

- **HTTP Request Execution:** Supports GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, etc.
- **Authentication:** Supports Basic (Preemptive), Bearer Token, and API Key (Header/Query).
- **Context Management:** Maintains cookies, session variables, and base configuration across steps via `ApiContext`.
- **Data Driven Testing:** Loads test data from JSON and CSV files using `TestContextDataTools`.
- **Assertions:** Validates response bodies against JSON Schemas and OpenAPI Specifications via `ApiAssertionTools`.
- **Variable Substitution:** Dynamically replaces `${variableName}` in URLs, Headers, and Bodies.
- **A2A Protocol:** Full support for Agent-to-Agent communication protocol, including streaming `message/stream` over Server-Sent Events
  (the agent card advertises `capabilities.streaming = true`).
- **Live Streaming:** Before each step/precondition runs, a `working` status update with an `ExecutionActivity` payload announces what is
  about to execute (for live dashboards); step and precondition results are then streamed as artifacts as they are recorded; and log lines
  are streamed live as appended chunks of a single growing `execution_log.log` artifact. The execution always concludes by emitting a single
  consolidated `TestExecutionResult` (result JSON + logs file), so non-streaming `message/send` callers receive one ready-to-consume result object.
- **System Info:** Includes device/OS/environment information in test results.
- **Diagnosable Failures:** An unexpected exception during a run no longer leaves the caller without a result: `ApiTestAgent` turns it into
  an `ERROR` `TestExecutionResult` carrying the logs captured up to that point, and the A2A executor flushes the buffered log lines before
  the terminal completion *or* failure event, since a task accepts no further artifact once it is terminal.

## Configuration

The API Test Execution Agent is configured via `config.properties` file or environment variables. Below is a comprehensive list of all
configuration properties:

### HTTP Client Configuration

| Property                       | Environment Variable           | Default | Description                                 |
|--------------------------------|--------------------------------|---------|---------------------------------------------|
| `api.base.uri`                 | `API_BASE_URI`                 | (empty) | Base URI for API requests                   |
| `api.proxy.host`               | `API_PROXY_HOST`               | (empty) | Proxy server hostname                       |
| `api.proxy.port`               | `API_PROXY_PORT`               | `8080`  | Proxy server port                           |
| `api.relaxed.https.validation` | `API_RELAXED_HTTPS_VALIDATION` | `false` | Relax strict HTTPS certificate validation (opt in per trusted target only) |
| `api.outbound.host.allowlist`  | `API_OUTBOUND_HOST_ALLOWLIST`  | (empty) | Optional CSV allow-list of hosts permitted for outbound requests; empty ⇒ only SSRF range-blocking applies |
| `api.upload.base.dir`          | `API_UPLOAD_BASE_DIR`          | (empty) | Base directory `uploadFile` is confined to (canonicalized); empty ⇒ uploads refused |

### Timeout Configuration

| Property                        | Environment Variable            | Default | Description                        |
|---------------------------------|---------------------------------|---------|------------------------------------|
| `api.request.timeout.millis`    | `API_REQUEST_TIMEOUT_MILLIS`    | `30000` | Request timeout in milliseconds    |
| `api.response.timeout.millis`   | `API_RESPONSE_TIMEOUT_MILLIS`   | `30000` | Response timeout in milliseconds   |
| `api.connection.timeout.millis` | `API_CONNECTION_TIMEOUT_MILLIS` | `10000` | Connection timeout in milliseconds |

### Logging and Debugging

| Property                       | Environment Variable           | Default | Description             |
|--------------------------------|--------------------------------|---------|-------------------------|
| `api.request.logging.enabled`  | `API_REQUEST_LOGGING_ENABLED`  | `false` | Enable request logging  |
| `api.response.logging.enabled` | `API_RESPONSE_LOGGING_ENABLED` | `false` | Enable response logging |
| `LOG_LEVEL` | `LOG_LEVEL` | `INFO` | Global log level for the agent and key dependencies like LangChain4j and A2A SDK |

### Data Loading Configuration

| Property                     | Environment Variable         | Default     | Description                                                    |
|------------------------------|------------------------------|-------------|----------------------------------------------------------------|
| `test.data.folder`           | `TEST_DATA_FOLDER`           | `test-data` | The only folder test data files (JSON, CSV) may be loaded from |
| `sensitive.data.key.pattern` | `SENSITIVE_DATA_KEY_PATTERN` | `(?i).*(password\|passwd\|pwd\|pin\|token\|secret\|api[_-]?key\|credential).*` | Regex matched against test data keys; the values of matching keys are masked as `{{secret:<key>}}` in prompts, logs and artifacts and resolved back only in the outgoing HTTP request |

### Schema Validation Configuration

| Property                | Environment Variable    | Default   | Description                        |
|-------------------------|-------------------------|-----------|------------------------------------|
| `api.schema.folder`     | `API_SCHEMA_FOLDER`     | `schemas` | Folder the local JSON Schema and OpenAPI spec files of `validateSchema` and `validateOpenApi` are confined to |
| `api.openapi.spec.path` | `API_OPENAPI_SPEC_PATH` | (empty)   | Path to OpenAPI specification file |

### Retry and Resilience Configuration

| Property                 | Environment Variable     | Default | Description                      |
|--------------------------|--------------------------|---------|----------------------------------|
| `api.max.retry.attempts` | `API_MAX_RETRY_ATTEMPTS` | `3`     | Maximum number of retry attempts |
| `api.retry.delay.millis` | `API_RETRY_DELAY_MILLIS` | `1000`  | Delay between retry attempts     |

### AI Agent Configuration

The API Test Execution Agent uses two specialized AI agents, each configurable independently:

#### Precondition Action Agent

Executes precondition setup operations and verifies their success.

| Property                            | Environment Variable                | Default                  | Description    |
|-------------------------------------|-------------------------------------|--------------------------|----------------|
| `precondition.agent.model.name`     | `PRECONDITION_AGENT_MODEL_NAME`     | `gemini-3.5-flash` | Model name     |
| `precondition.agent.model.provider` | `PRECONDITION_AGENT_MODEL_PROVIDER` | `google`                 | Model provider |
| `precondition.agent.prompt.version` | `PRECONDITION_AGENT_PROMPT_VERSION` | `v1.1.0`                 | Prompt version |

#### Test Step Action Agent

Executes individual API test steps and verifies expected results.

| Property                                | Environment Variable                    | Default                  | Description    |
|-----------------------------------------|-----------------------------------------|--------------------------|----------------|
| `test.step.action.agent.model.name`     | `TEST_STEP_ACTION_AGENT_MODEL_NAME`     | `gemini-3.5-flash` | Model name     |
| `test.step.action.agent.model.provider` | `TEST_STEP_ACTION_AGENT_MODEL_PROVIDER` | `google`                 | Model provider |
| `test.step.action.agent.prompt.version` | `TEST_STEP_ACTION_AGENT_PROMPT_VERSION` | `v1.1.0`                 | Prompt version |

### Content Type Configuration

| Property                   | Environment Variable       | Default            | Description                                                                           |
|----------------------------|----------------------------|--------------------|---------------------------------------------------------------------------------------|
| `api.default.content.type` | `API_DEFAULT_CONTENT_TYPE` | `application/json` | Content-Type header sent when the request sets none (header names ignore letter case) |
| `api.default.accept`       | `API_DEFAULT_ACCEPT`       | `application/json` | Default Accept header                                                                 |

### Cookie and Response Configuration

| Property                        | Environment Variable            | Default | Description                      |
|---------------------------------|---------------------------------|---------|----------------------------------|
| `api.cookies.enabled`           | `API_COOKIES_ENABLED`           | `true`  | Enable automatic cookie handling |
| `api.max.response.body.size.kb` | `API_MAX_RESPONSE_BODY_SIZE_KB` | `10240` | Maximum response body size in KB |

### Default Authentication Configuration

| Property                      | Environment Variable          | Default         | Description                                                                                                                                                                                |
|-------------------------------|-------------------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api.default.auth.type`       | `API_DEFAULT_AUTH_TYPE`       | `NONE`          | Default authentication type for API requests. Valid values: `NONE`, `BASIC`, `BEARER`, `API_TOKEN`. This value is used when no explicit authentication type is specified in the tool call. |
| `api.auth.basic.username.env` | `API_AUTH_BASIC_USERNAME_ENV` | `API_USERNAME`  | Environment variable name for Basic auth username                                                                                                                                          |
| `api.auth.basic.password.env` | `API_AUTH_BASIC_PASSWORD_ENV` | `API_PASSWORD`  | Environment variable name for Basic auth password                                                                                                                                          |
| `api.auth.bearer.token.env`   | `API_AUTH_BEARER_TOKEN_ENV`   | `API_TOKEN`     | Environment variable name for Bearer token                                                                                                                                                 |
| `api.auth.apikey.name.env`    | `API_AUTH_APIKEY_NAME_ENV`    | `API_KEY_NAME`  | Environment variable name for API key header name                                                                                                                                          |
| `api.auth.apikey.value.env`   | `API_AUTH_APIKEY_VALUE_ENV`   | `API_KEY_VALUE` | Environment variable name for API key value                                                                                                                                                |

## Agents

The API Test Execution Agent uses two specialized AI agents that extend `GenericAiAgent` from the core module:

### ApiPreconditionActionAgent

Responsible for executing **and verifying** test case preconditions in a single operation. Handles setup operations such as:

- Creating test data via API calls
- Setting up authentication tokens
- Initializing session state
- Creating required resources before test execution

After execution, it also verifies:
- API response status codes and bodies
- Expected resources were created
- Authentication tokens are valid
- Data state matches expectations

**Location:** `org.tarik.ta.agents.ApiPreconditionActionAgent`

### ApiTestStepActionAgent

Responsible for executing **and verifying** individual API test steps in a single operation:

- Sending HTTP requests with various methods and authentication
- Processing request/response data
- Storing extracted values in context for later use
- Handling data-driven test scenarios

After execution, it also verifies:
- API response status codes match expectations
- Response body content against expected values
- JSON path values and structure
- Response schema compliance

**Location:** `org.tarik.ta.agents.ApiTestStepActionAgent`

## Tools

The API Test Execution Agent uses the following tools:

### ApiRequestTools

- `sendRequest(method, url, headers, body, authType)` - Sends HTTP requests. The `authType` parameter is optional; if not specified, the
  configured default authentication type (`api.default.auth.type`) is used. The target host is enforced against the outbound guard
  (SSRF range-blocking plus the optional `api.outbound.host.allowlist`) regardless of model instructions.
- In both request tools every redirect is followed only after its target host passes the same outbound guard, so an allowed host cannot redirect the agent to an internal or metadata address. A `POST` is redirected only by a `303`, as Apache HttpClient does by default.
- In both request tools `url` is an absolute URL or a path starting with `/`, which is resolved against `api.base.uri`, and `headers` is a list of `{name, value}` objects (a name given twice keeps its last value). `{{secret:<key>}}` placeholders in the URL and the headers are resolved only for the outgoing request, after `${var}` substitution and before the outbound guard checks the host, so the guard sees the real target while the context and the log keep the placeholder.
- `getLastApiResponse()` - Retrieves the last API response information (status code, body, headers). Use when a test step explicitly
  requires access to the previous API response data.
- `uploadFile(url, filePath, multipartName, headers, authType)` - Uploads files using multipart/form-data. The `authType` parameter is
  optional; if not specified, the configured default authentication type is used. The target host is enforced against the outbound guard,
  and the file path must resolve inside `api.upload.base.dir` (uploads are refused when that directory is unset).

### ApiAssertionTools

- `validateSchema(schemaPath)` - Validates response against JSON Schema. The path must resolve inside `api.schema.folder`.
- `validateOpenApi(specPath, method, path)` - Validates response against OpenAPI specification. A local spec path must resolve inside `api.schema.folder`; an `http(s)` spec URL must pass the same outbound guard as a request, since the validator fetches it itself. The spec must be self-contained: a `$ref` to another file or URL is refused instead of being fetched outside the guard, while references inside the spec (`#/components/...`) resolve as usual.

Status code and JSON path assertions are not separate tools; the model reads the response via `getLastApiResponse()` and reasons
about it directly.

### TestContextDataTools

- `loadJsonData(filePath, variableName)` - Loads JSON data into context
- `loadCsvData(filePath, variableName)` - Loads CSV data into context
- `storeVariableIntoContext(variableName, value)` - Stores an extracted or computed value into context for later use (e.g. cross-step data flow)

## Smoke Tests

`src/test/java/org/tarik/ta/smoke/ApiAgentSmokeTest` is a hermetic, end-to-end smoke suite that drives the
**real** agent flow (`ApiTestAgent` -> step / precondition agents -> real `ApiRequestTools` /
`ApiAssertionTools`) and mocks only the external boundaries:

- the LLM is replaced by `smoke/ScriptedChatModel`, a deterministic `ChatModel` that returns a scripted
  sequence of real tool calls (so the real tool-calling loop, tools and result extraction all run unchanged);
  it implements `doChat()` + `listeners()` and reports a fixed `TokenUsage` per response, so the production
  `ChatModelEventListener` can be attached and real token accounting runs against the `BudgetManager`;
- the target API is a local **WireMock** server, which records the requests that actually left the agent;
- the config, `TestCaseExtractor` and `LogCapture` are Mockito mocks.

Coverage: happy path, precondition + step (full `TestExecutionResult` contract), failed verification,
verification retry recovery (fail -> retry -> pass), POST with a JSON body (header merging, default
Content-Type injection, body `${var}` resolution), a 5xx response read back through `getLastApiResponse`,
a network fault recovered through the tool-error retry path, malformed final-result arguments handed back to the model and corrected, Basic / Bearer / API-Key auth headers,
`${var}` substitution, cross-step data flow through the real `storeVariableIntoContext` tool, cookie
propagation, JSON-schema validation (matching and mismatching bodies), OpenAPI validation via
`validateOpenApi` (conformant and spec-violating bodies), base-URI resolution of relative URLs, a data-table secret sent through the URL and a header while the model and the logs see only its placeholder, loading
file data through the real `loadJsonData` / `loadCsvData` tools, an unextractable test case (-> `ERROR`
without any model interaction), the SSRF guard (including a redirect to a host outside the allow-list), `uploadFile` confinement, time-, token- and
tool-call-budget exhaustion, the three precondition failure paths (action error, no result from the model
and failed verification — each -> `ERROR`, per the shared status contract), a step answered with plain text
instead of a tool call (-> `ERROR`), and HTTPS against WireMock's self-signed certificate (rejected without
`relaxedHttpsValidation`, accepted with it).

`src/test/java/org/tarik/ta/smoke/ApiAgentA2aSmokeTest` additionally covers the full production stack in one
test: an HTTP `message/stream` request travels `AgentExecutionResource` -> executor -> the real
`ApiTestAgent` with real tools -> WireMock, and the step + final-result events are asserted over the SSE
stream (using `A2aTestClient` from `agent_core`'s test-jar).

The tests are tagged `@Tag("smoke")` and are **excluded from the default build** (`mvn package` /
`mvn test`) via the parent POM's `excludedGroups`. Run them explicitly:

```bash
# Run from the reactor root so agent_core is compiled with -parameters and used fresh.
mvn -B test -P linux -Dtest.excluded.groups= -Dgroups=smoke
```

Notes:
- Auth credentials are injected as **system properties** (the agent reads them through
  `CommonUtils.getEnvironmentVariable`, which falls back to system properties), not real environment variables.
- The suite shares process-global state (the `BudgetManager` singleton and a static WireMock server), so it
  runs **single-threaded** (`src/test/resources/junit-platform.properties`).

## Deployment

### Cloud Run Deployment

The API agent is designed to be deployed on Google Cloud Run. Use the provided Cloud Build configuration:

```bash
# Standalone deployment
gcloud builds submit --config=api_test_execution_agent/deployment/cloud/cloudbuild.yaml

# Or via parent project
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=api
```

The container starts the agent with `--sun-misc-unsafe-memory-access=allow`, because dependencies still call the deprecated `sun.misc.Unsafe` memory-access methods, which JDK 27 otherwise warns about on every start (JEP 498). The option stops working once a JDK makes `deny` the default.

### Configuration for Deployment

Key environment variables for Cloud Run:

| Variable | Description |
|----------|-------------|
| `PORT` | Server port (default: 8005) |
| `EXTERNAL_URL` | Public URL for A2A agent card |
| `GOOGLE_API_KEY` | API key for Google AI models |
| `AGENT_AUTH_TOKEN` | Shared secret required as a `Bearer` token on the main A2A endpoint; unset ⇒ endpoint is unauthenticated |
| `PRECONDITION_AGENT_MODEL_PROVIDER` | Model provider for the Precondition Action Agent (google, openai, etc.) |
| `TEST_STEP_ACTION_AGENT_MODEL_PROVIDER` | Model provider for the Test Step Action Agent (google, openai, etc.) |

### Local Development

Run the agent locally:

```bash
# Build the module
mvn clean package -pl api_test_execution_agent -am -DskipTests

# Run the server
java -jar api_test_execution_agent/target/api-test-execution-agent-*.jar
```

## Usage

The `ApiTestAgent.executeTestCase(String message)` method is the main entry point. It:

1. Parses the user message using `TestCaseExtractor` to extract a structured `TestCase`
2. Stores the parsed `TestCase` in the request-scoped `TestExecutionContext`
3. Uses the already-open request scope to initialize `ApiContext` and request-scoped tools
4. Executes preconditions using `ApiPreconditionActionAgent`
5. Executes test steps using `ApiTestStepActionAgent`
6. Returns a `TestExecutionResult` with logs and system info

### User message format

The precondition and test step messages are built in Java with `MarkdownSections` (agent_core): every piece of data sits in its own `## <label>` section, a section without data is left out, heading lines inside the data are escaped (`## x` becomes `\## x`), the data items are a Markdown list and the test execution context data is a compact JSON array of its entries in execution order, grouped per step. Every precondition and test step becomes the current step when it starts, gets the next execution number of the run and stores each non-blank line of its data as an entry of that step (a `key=value` line under its key, any other line without a name); a value a tool stores during a step is an entry of that step, and the data table entries belong to no step. Each group carries its `step` and `belongsToCurrentStep` once (both absent for the data table) and its `entries`, each with its `name`, `value` and `source`, and the action prompts include the shared rules `common/execution_context/v1.1.0/execution_context_data.md` (agent_core), so the model uses the data of an earlier step only when the current step explicitly refers to it, never resolves an indirect reference such as "the specified order" with another step's data, and reports a failure naming the missing data when neither the current step nor the test case provides it. Tools that need a value itself (e.g. `{{name}}` resolution) read the latest value of every named entry.

```
## Test step action
Fetch the created order

## Expected results to verify
Status is 200

## Data related to the test step
- orderId={{orderId}}

## Test execution context data
[{"step":{"number":1,"type":"test step","description":"Create an order"},"belongsToCurrentStep":false,"entries":[{"name":"createOrderResponse","value":"{\n  \"id\": 4711\n}","source":"stored during execution"}]},{"step":{"number":2,"type":"test step","description":"Fetch the created order"},"belongsToCurrentStep":true,"entries":[{"name":"orderId","value":"{{orderId}}","source":"test step data"}]}]
```

A precondition message has the sections `## Precondition`, `## Data relevant to this precondition` and `## Test execution context data`. The text reaches the agent as a `@UserMessage List<Content>` parameter, never as a `String`, so data containing `{{…}}` (API variables, secret placeholders) is sent verbatim instead of being parsed as a LangChain4j prompt template.
