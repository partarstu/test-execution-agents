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

*   **`agent_core`**: A shared library module containing the core framework logic, data transfer objects (DTOs), base agent classes, budget management, and generic utilities. This module provides:
    *   **`AbstractServer`**: Base class for agent servers providing common HTTP server initialization and A2A endpoint configuration.
    *   **`AbstractAgentExecutor`**: Base class for agent executors handling test case execution lifecycle and artifact management.
    *   **`GenericAiAgent`**: Core interface for all AI agents with retry logic and budget management.
    *   **`TestCaseExtractor`**: Utility class that provides shared test case extraction functionality using an AI model.
    *   **`TestContextDataTools`**: Shared tools for loading and managing test data (JSON, CSV).
    *   **`DefaultToolErrorHandler`**: Centralized tool error handling with configurable retry policies.
    *   **`InheritanceAwareToolProvider`**: Enhanced tool provider that supports tool inheritance and discovery.
    *   **`LogCapture`**: Utility for capturing execution logs to include in test results.
    *   **`SystemInfo`**: DTO for capturing device/OS/browser information.

*   **`ui_test_execution_agent`**: The executable application module that implements the specific logic for UI testing. It includes:
    *   **`UiTestAgent`**: Main entry point for UI test execution.
    *   **`UiTestAgentConfig`**: UI-specific configuration (element locator, dialogs, video recording, vision agents).
    *   **`UiTestExecutionContext`**: Extended context with visual state management.
    *   UI-specific agents for visual grounding, element interaction, and verification.
    *   Computer vision capabilities using OpenCV.
    *   Tools for mouse/keyboard control.
    *   RAG integration with ChromaDB for element retrieval.
    *   Deployed as a **GCE VM** with VNC access.

*   **`api_test_execution_agent`**: The executable application module that implements the specific logic for API testing. It includes:
    *   **`ApiTestAgent`**: Main entry point for API test execution.
    *   **`ApiTestAgentConfig`**: API-specific configuration (HTTP client, proxy, timeouts, authentication).
    *   **`ApiPreconditionActionAgent`**: Executes and verifies API preconditions (auth setup, data creation).
    *   **`ApiTestStepActionAgent`**: Executes and verifies individual API test steps.
    *   **`ApiRequestTools`**: HTTP request execution with multiple authentication types (Basic, Bearer, API Key).
    *   **`ApiAssertionTools`**: Response validation (JSON Schema, OpenAPI spec, status codes, JSON paths).
    *   **`ApiContext`**: Session management (cookies, variables, configuration).
    *   Deployed as a **Cloud Run** service.

### Core Architecture

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
│ • Core DTOs         │ │ • RAG Integration   │ │ • Schema Validation     │
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
- **A2A Protocol Support**: Both agents implement the Agent-to-Agent (A2A) protocol for inter-agent communication.
- **Test Case Extraction**: AI-powered parsing of natural language test cases into structured format.
- **Budget Management**: Token and time budget controls to prevent runaway executions.
- **Structured Logging**: Execution logs captured and included in test results.
- **System Info Capture**: Device, OS, browser, and environment information in results.

### UI Test Agent Specific
- **Visual Grounding**: AI-powered element location using screenshots and descriptions.
- **Screen Recording**: Captures video of test execution for debugging.
- **Element RAG**: Vector database integration for efficient element retrieval.
- **Attended/Semi-Attended/Unattended Modes**: Interactive, semi-interactive, or fully automated execution.

### API Test Agent Specific
- **Multiple Auth Types**: Basic, Bearer Token, and API Key authentication.
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

## Configuration

### Core Configuration

The following configuration properties are shared across agents (defined in `AgentConfig`):

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `port` | `PORT` | `8005` | Server port |
| `host` | `AGENT_HOST` | (required) | Server host |
| `external.url` | `EXTERNAL_URL` | `http://localhost:{port}` | External URL for A2A card |
| `vector.db.provider` | `VECTOR_DB_PROVIDER` | `chroma` | RAG Vector DB provider (chroma, qdrant) |
| `vector.db.url` | `VECTOR_DB_URL` | (required) | URL for the vector database |
| `vector.db.key` | `VECTOR_DB_KEY` | | API Key/Token for the vector database |
| `model.provider` | `MODEL_PROVIDER` | `google` | AI model provider (google, openai, groq, anthropic) |
| `model.name` | `MODEL_NAME` | `gemini-3-flash-preview` | Default model name |
| `gemini.thinking.level` | `GEMINI_THINKING_LEVEL` | `MINIMAL` | Gemini thinking configuration level |
| `model.max.retries` | `MAX_RETRIES` | `10` | Maximum model API retries |

### Agent-Specific Configuration

Each agent has its own configuration file template with agent-specific settings:

| Agent | Configuration Template | Purpose |
|-------|----------------------|---------|
| UI Test Execution Agent | `ui_test_execution_agent/config.properties.example` | UI-specific settings (dialogs, element locator, video recording, vision agents) |
| API Test Execution Agent | `api_test_execution_agent/config.properties.example` | API-specific settings (HTTP client, proxy, timeouts, schema validation, authentication) |

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
   - `VNC_PW` (for UI agent)

3. Create a VPC connector named `agent-network-connector` for Cloud Run to access internal resources.

### Deploying with Cloud Build

#### Deploy All Agents (Default)

To deploy both UI and API agents:

```bash
gcloud builds submit --config=cloudbuild.yaml
```

#### Deploy Agents Separately

You can deploy agents individually using the `_DEPLOY_TARGET` substitution:

**Deploy only the UI agent:**
```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=ui
```

**Deploy only the API agent:**
```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=api
```

#### Standalone Deployment (Module-Level)

Each agent also has its own `cloudbuild.yaml` for fully independent deployment:

**UI Agent standalone deployment:**
```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml
```

**API Agent standalone deployment:**
```bash
gcloud builds submit --config=api_test_execution_agent/deployment/cloud/cloudbuild.yaml
```

#### Customizing Deployment Parameters

To customize deployment parameters, you can override substitutions:

```bash
gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=_DEPLOY_TARGET=api,_IMAGE_TAG=v1.0.0,_API_AGENT_EXTERNAL_URL=https://your-actual-cloudrun-url.run.app
```

> **Note:** After the first deployment, update `_API_AGENT_EXTERNAL_URL` in `cloudbuild.yaml` with the actual Cloud Run service URL. This URL is displayed in the Cloud Run console after deployment and is required for the A2A agent card to advertise the correct service endpoint.

### Deployment Details

| Agent | Deployment Target | Port | Access |
|-------|------------------|------|--------|
| UI Test Execution Agent | GCE VM | 8005 | noVNC (HTTPS) + Agent Server |
| API Test Execution Agent | Cloud Run | 8005 | HTTP (internal by default) |

## Test Execution Results

Both agents return structured `TestExecutionResult` objects containing:

| Field | Description |
|-------|-------------|
| `testCaseName` | Name of the executed test case |
| `testExecutionStatus` | PASSED, FAILED, or ERROR |
| `preconditionResults` | Results for each precondition |
| `stepResults` | Results for each test step |
| `executionStartTimestamp` | When execution started |
| `executionEndTimestamp` | When execution completed |
| `generalErrorMessage` | Overall error message if any |
| `systemInfo` | Device, OS, browser, environment info |
| `logs` | Captured execution logs |

UI agent results additionally include screenshots and video recordings.

## Documentation

*   For detailed documentation on the UI Test Execution Agent, see **[UI Agent README](ui_test_execution_agent/README.md)**.
*   For detailed documentation on the API Test Execution Agent, see **[API Agent README](api_test_execution_agent/README.md)**.
*   For development guidelines, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.