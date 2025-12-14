# API Test Execution Agent

## Overview
This module provides the API Test Execution Agent capabilities, enabling automated API testing through LLM-driven orchestration.

## Features
- **HTTP Request Execution:** Supports GET, POST, PUT, DELETE, etc.
- **Authentication:** Supports Basic (Preemptive), Bearer Token, and API Key (Header/Query).
- **Context Management:** Maintains cookies, session variables, and base configuration across steps.
- **Data Driven Testing:** Loads test data from JSON and CSV files.
- **Assertions:** Validates Status Codes, JSON Paths, JSON Schemas, and OpenAPI Specifications.
- **Variable Substitution:** Dynamically replaces `${variableName}` in URLs, Headers, and Bodies.

## Configuration

The API Test Execution Agent is configured via `config.properties` file or environment variables. Below is a comprehensive list of all configuration properties:

### HTTP Client Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.base.uri` | `API_BASE_URI` | (empty) | Base URI for API requests |
| `api.proxy.host` | `API_PROXY_HOST` | (empty) | Proxy server hostname |
| `api.proxy.port` | `API_PROXY_PORT` | `8080` | Proxy server port |
| `api.relaxed.https.validation` | `API_RELAXED_HTTPS_VALIDATION` | `true` | Disable strict HTTPS certificate validation |

### Timeout Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.request.timeout.millis` | `API_REQUEST_TIMEOUT_MILLIS` | `30000` | Request timeout in milliseconds |
| `api.response.timeout.millis` | `API_RESPONSE_TIMEOUT_MILLIS` | `30000` | Response timeout in milliseconds |
| `api.connection.timeout.millis` | `API_CONNECTION_TIMEOUT_MILLIS` | `10000` | Connection timeout in milliseconds |

### Logging and Debugging

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.request.logging.enabled` | `API_REQUEST_LOGGING_ENABLED` | `false` | Enable request logging |
| `api.response.logging.enabled` | `API_RESPONSE_LOGGING_ENABLED` | `false` | Enable response logging |

### Data Loading Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.test.data.folder` | `API_TEST_DATA_FOLDER` | `test-data` | Folder path for test data files (JSON, CSV) |

### Schema Validation Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.schema.folder` | `API_SCHEMA_FOLDER` | `schemas` | Folder path for JSON Schema files |
| `api.openapi.spec.path` | `API_OPENAPI_SPEC_PATH` | (empty) | Path to OpenAPI specification file |

### Retry and Resilience Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.max.retry.attempts` | `API_MAX_RETRY_ATTEMPTS` | `3` | Maximum number of retry attempts |
| `api.retry.delay.millis` | `API_RETRY_DELAY_MILLIS` | `1000` | Delay between retry attempts |

### AI Agent Configuration

The API Test Execution Agent uses four specialized AI agents, each configurable independently:

#### Precondition Action Agent
Executes precondition setup operations like creating test data, obtaining tokens, and initializing state.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.precondition.action.agent.model.name` | `API_PRECONDITION_ACTION_AGENT_MODEL_NAME` | `gemini-2.5-flash` | Model name |
| `api.precondition.action.agent.model.provider` | `API_PRECONDITION_ACTION_AGENT_MODEL_PROVIDER` | `google` | Model provider |
| `api.precondition.action.agent.prompt.version` | `API_PRECONDITION_ACTION_AGENT_PROMPT_VERSION` | `v1.0.0` | Prompt version |

#### Precondition Verification Agent
Verifies that precondition setup operations completed successfully.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.precondition.verification.agent.model.name` | `API_PRECONDITION_VERIFICATION_AGENT_MODEL_NAME` | `gemini-2.5-flash` | Model name |
| `api.precondition.verification.agent.model.provider` | `API_PRECONDITION_VERIFICATION_AGENT_MODEL_PROVIDER` | `google` | Model provider |
| `api.precondition.verification.agent.prompt.version` | `API_PRECONDITION_VERIFICATION_AGENT_PROMPT_VERSION` | `v1.0.0` | Prompt version |

#### Test Step Action Agent
Executes individual API test steps including HTTP requests and data handling.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.test.step.action.agent.model.name` | `API_TEST_STEP_ACTION_AGENT_MODEL_NAME` | `gemini-2.5-flash` | Model name |
| `api.test.step.action.agent.model.provider` | `API_TEST_STEP_ACTION_AGENT_MODEL_PROVIDER` | `google` | Model provider |
| `api.test.step.action.agent.prompt.version` | `API_TEST_STEP_ACTION_AGENT_PROMPT_VERSION` | `v1.0.0` | Prompt version |

#### Test Step Verification Agent
Verifies test step expected results against actual API responses.

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.test.step.verification.agent.model.name` | `API_TEST_STEP_VERIFICATION_AGENT_MODEL_NAME` | `gemini-2.5-flash` | Model name |
| `api.test.step.verification.agent.model.provider` | `API_TEST_STEP_VERIFICATION_AGENT_MODEL_PROVIDER` | `google` | Model provider |
| `api.test.step.verification.agent.prompt.version` | `API_TEST_STEP_VERIFICATION_AGENT_PROMPT_VERSION` | `v1.0.0` | Prompt version |

### Content Type Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.default.content.type` | `API_DEFAULT_CONTENT_TYPE` | `application/json` | Default Content-Type header |
| `api.default.accept` | `API_DEFAULT_ACCEPT` | `application/json` | Default Accept header |

### Cookie and Response Configuration

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `api.cookies.enabled` | `API_COOKIES_ENABLED` | `true` | Enable automatic cookie handling |
| `api.max.response.body.size.kb` | `API_MAX_RESPONSE_BODY_SIZE_KB` | `10240` | Maximum response body size in KB |

## Agents

The API Test Execution Agent uses four specialized AI agents:

### ApiPreconditionActionAgent
Responsible for executing test case preconditions. Handles setup operations such as:
- Creating test data via API calls
- Setting up authentication tokens
- Initializing session state
- Creating required resources before test execution

### ApiPreconditionVerificationAgent
Responsible for verifying precondition execution by:
- Checking API response status codes and bodies
- Validating that expected resources were created
- Confirming authentication tokens are valid
- Verifying data state matches expectations

### ApiTestStepActionAgent
Responsible for executing individual API test steps:
- Sending HTTP requests with various methods and authentication
- Processing request/response data
- Storing extracted values in context for later use
- Handling data-driven test scenarios

### ApiTestStepVerificationAgent
Responsible for verifying test step expected results:
- Validating API response status codes match expectations
- Checking response body content against expected values
- Verifying JSON path values and structure
- Validating response schema compliance

## Tools

The API Test Execution Agent uses the following tools:

### ApiRequestTools
- `sendRequest(method, url, headers, body, authType, authValue, authLocation)` - Sends HTTP requests with various authentication methods
- `uploadFile(url, filePath, multipartName, headers)` - Uploads files using multipart/form-data

### ApiAssertionTools
- `assertStatusCode(expectedCode)` - Validates response status code
- `assertJsonPath(jsonPath, expectedValue)` - Validates JSON path values
- `extractValue(jsonPath, variableName)` - Extracts values from responses
- `validateSchema(schemaPath)` - Validates response against JSON Schema
- `validateOpenApi(specPath)` - Validates response against OpenAPI specification

### TestContextDataTools
- `loadJsonData(filePath, variableName)` - Loads JSON data into context
- `loadCsvData(filePath, className, variableName)` - Loads CSV data into context

## Implementation Status
- [x] Module Structure
- [x] Dependencies
- [x] Core Components
    - [x] ApiContext
    - [x] ApiRequestTools
    - [x] TestContextDataTools
    - [x] ApiAssertionTools
    - [x] ApiTestAgentConfig
- [x] Agent Interfaces
    - [x] ApiPreconditionActionAgent
    - [x] ApiPreconditionVerificationAgent
    - [x] ApiTestStepActionAgent
    - [x] ApiTestStepVerificationAgent
- [x] Agent Integration
- [x] Testing (Unit tests for Request Tools)
- [x] Configuration system
- [x] Prompt templates for all agents

## Usage
The `ApiTestAgent.executeTestCase(String message)` method is the entry point. It parses the user message to extract a Test Case, initializes the `ApiContext` from configuration, and executes the steps using the specialized agents.