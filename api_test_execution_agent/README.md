# API Test Execution Agent

## Overview

This module provides the API Test Execution Agent capabilities, enabling automated API testing through LLM-driven orchestration.
It is implemented as a standalone Cloud Run-deployable service that communicates via the A2A (Agent-to-Agent) protocol.


## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   API Test Execution Agent                        │
├──────────────────────────────────────────────────────────────────┤
│  Server (extends AbstractServer)                                  │
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

| Class | Description |
|-------|-------------|
| `Server` | HTTP server entry point, extends `AbstractServer` from core |
| `ApiAgentExecutor` | Handles A2A task execution, extends `AbstractAgentExecutor` |
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
- **Assertions:** Validates Status Codes, JSON Paths, JSON Schemas, and OpenAPI Specifications via `ApiAssertionTools`.
- **Variable Substitution:** Dynamically replaces `${variableName}` in URLs, Headers, and Bodies.
- **A2A Protocol:** Full support for Agent-to-Agent communication protocol.
- **Execution Logging:** Captures and returns execution logs with test results.
- **System Info:** Includes device/OS/environment information in test results.

## Configuration

The API Test Execution Agent is configured via `config.properties` file or environment variables. Below is a comprehensive list of all
configuration properties:

### HTTP Client Configuration

| Property                       | Environment Variable           | Default | Description                                 |
|--------------------------------|--------------------------------|---------|---------------------------------------------|
| `api.base.uri`                 | `API_BASE_URI`                 | (empty) | Base URI for API requests                   |
| `api.proxy.host`               | `API_PROXY_HOST`               | (empty) | Proxy server hostname                       |
| `api.proxy.port`               | `API_PROXY_PORT`               | `8080`  | Proxy server port                           |
| `api.relaxed.https.validation` | `API_RELAXED_HTTPS_VALIDATION` | `true`  | Disable strict HTTPS certificate validation |

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

### Data Loading Configuration

| Property               | Environment Variable   | Default     | Description                                 |
|------------------------|------------------------|-------------|---------------------------------------------|
| `api.test.data.folder` | `API_TEST_DATA_FOLDER` | `test-data` | Folder path for test data files (JSON, CSV) |

### Schema Validation Configuration

| Property                | Environment Variable    | Default   | Description                        |
|-------------------------|-------------------------|-----------|------------------------------------|
| `api.schema.folder`     | `API_SCHEMA_FOLDER`     | `schemas` | Folder path for JSON Schema files  |
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
| `precondition.agent.model.name`     | `PRECONDITION_AGENT_MODEL_NAME`     | `gemini-3-flash-preview` | Model name     |
| `precondition.agent.model.provider` | `PRECONDITION_AGENT_MODEL_PROVIDER` | `google`                 | Model provider |
| `precondition.agent.prompt.version` | `PRECONDITION_AGENT_PROMPT_VERSION` | `v1.0.0`                 | Prompt version |

#### Test Step Action Agent

Executes individual API test steps and verifies expected results.

| Property                                | Environment Variable                    | Default                  | Description    |
|-----------------------------------------|-----------------------------------------|--------------------------|----------------|
| `test.step.action.agent.model.name`     | `TEST_STEP_ACTION_AGENT_MODEL_NAME`     | `gemini-3-flash-preview` | Model name     |
| `test.step.action.agent.model.provider` | `TEST_STEP_ACTION_AGENT_MODEL_PROVIDER` | `google`                 | Model provider |
| `test.step.action.agent.prompt.version` | `TEST_STEP_ACTION_AGENT_PROMPT_VERSION` | `v1.0.0`                 | Prompt version |

### Content Type Configuration

| Property                   | Environment Variable       | Default            | Description                 |
|----------------------------|----------------------------|--------------------|-----------------------------|
| `api.default.content.type` | `API_DEFAULT_CONTENT_TYPE` | `application/json` | Default Content-Type header |
| `api.default.accept`       | `API_DEFAULT_ACCEPT`       | `application/json` | Default Accept header       |

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
  configured default authentication type (`api.default.auth.type`) is used.
- `getLastApiResponse()` - Retrieves the last API response information (status code, body, headers). Use when a test step explicitly
  requires access to the previous API response data.
- `uploadFile(url, filePath, multipartName, headers, authType)` - Uploads files using multipart/form-data. The `authType` parameter is
  optional; if not specified, the configured default authentication type is used.

### ApiAssertionTools

- `assertStatusCode(expectedCode)` - Validates response status code
- `assertJsonPath(jsonPath, expectedValue)` - Validates JSON path values
- `extractValue(jsonPath, variableName)` - Extracts values from responses
- `validateSchema(schemaPath)` - Validates response against JSON Schema
- `validateOpenApi(specPath)` - Validates response against OpenAPI specification

### TestContextDataTools

- `loadJsonData(filePath, variableName)` - Loads JSON data into context
- `loadCsvData(filePath, variableName)` - Loads CSV data into context

## Deployment

### Cloud Run Deployment

The API agent is designed to be deployed on Google Cloud Run. Use the provided Cloud Build configuration:

```bash
# Standalone deployment
gcloud builds submit --config=api_test_execution_agent/deployment/cloud/cloudbuild.yaml

# Or via parent project
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=api
```

### Configuration for Deployment

Key environment variables for Cloud Run:

| Variable | Description |
|----------|-------------|
| `PORT` | Server port (default: 8005) |
| `EXTERNAL_URL` | Public URL for A2A agent card |
| `GOOGLE_API_KEY` | API key for Google AI models |
| `MODEL_PROVIDER` | AI model provider (google, openai, etc.) |

### Local Development

Run the agent locally:

```bash
# Build the module
mvn clean package -pl api_test_execution_agent -am -DskipTests

# Run the server
java -jar api_test_execution_agent/target/api-test-execution-agent-*-shaded.jar
```

## Usage

The `ApiTestAgent.executeTestCase(String message)` method is the main entry point. It:

1. Parses the user message using `TestCaseExtractor` to extract a structured `TestCase`
2. Initializes `ApiContext` from `ApiTestAgentConfig`
3. Creates the necessary tools (`ApiRequestTools`, `ApiAssertionTools`, `TestContextDataTools`)
4. Executes preconditions using `ApiPreconditionActionAgent`
5. Executes test steps using `ApiTestStepActionAgent`
6. Returns a `TestExecutionResult` with logs and system info