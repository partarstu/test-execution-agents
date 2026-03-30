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
    * **`AbstractServer`**: Base class for agent servers providing common HTTP server initialization and A2A endpoint configuration.
    * **`AbstractAgentExecutor`**: Base class for agent executors handling test case execution lifecycle and artifact management.
    * **`GenericAiAgent`**: Core interface for all AI agents with retry logic and budget management.
    * **`TestCaseExtractor`**: Utility class that provides shared test case extraction functionality using an AI model.
    * **`TestContextDataTools`**: Shared tools for loading and managing test data (JSON, CSV).
    * **`TestExecutionContext`**: Shared request-scoped execution state for step history, precondition history, and shared data; test case data is passed explicitly by the agents instead of being stored in the context.
    * **`DefaultToolErrorHandler`**: Centralized tool error handling with configurable retry policies.
    * **`InheritanceAwareToolProvider`**: Enhanced tool provider that supports tool inheritance and discovery.
    * **`LogCapture`**: Utility for capturing execution logs to include in test results.
    * **`SystemInfo`**: DTO for capturing device/OS/browser information.

* **`ui_test_execution_agent`**: The executable application module that implements the specific logic for UI testing. It includes:
    * **`UiTestAgent`**: Main entry point for UI test execution.
    * **`UiTestAgentConfig`**: UI-specific configuration (element locator, dialogs, video recording, vision agents).
    * **`UiTestExecutionContext`**: Extended context with visual state management.
    * UI-specific agents for visual grounding, element interaction, and verification.
    * Computer vision capabilities using OpenCV.
    * Tools for mouse/keyboard control.
    * Knowledge graph integration with Neo4j for persistent element and procedure storage.
    * Knowledge collection dialogs: `ProcedureKnowledgeCollectionDialog` (top-level and recursive child procedure editor with bidirectional navigation, dirty state tracking, and element creation/refinement popup integration; includes a "Remove Element" button to detach a target UI element from an atomic procedure) and `ChildStepEditDialog` (lightweight per-step editor for quick "Add" operations).
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

- **A2A Protocol Support**: Both agents implement the Agent-to-Agent (A2A) protocol for inter-agent communication.
- **Test Case Extraction**: AI-powered parsing of natural language test cases into structured format.
- **Budget Management**: Token and time budget controls to prevent runaway executions.
- **Structured Logging**: Execution logs captured and included in test results.
- **System Info Capture**: Device, OS, browser, and environment information in results.

### UI Test Agent Specific

- **Visual Grounding**: AI-powered element location using screenshots and descriptions.
- **Screen Recording**: Captures video of test execution for debugging.
- **Knowledge Graph**: Neo4j-backed persistent storage for UI elements and reusable procedures with vector search.
- **Attended/Supervised/Unattended Modes**: Interactive, semi-interactive, or fully automated execution.

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

| Property                | Environment Variable    | Default                   | Description                                         |
|-------------------------|-------------------------|---------------------------|-----------------------------------------------------|
| `port`                  | `PORT`                  | `8005`                    | Server port                                         |
| `host`                  | `AGENT_HOST`            | (required)                | Server host                                         |
| `external.url`          | `EXTERNAL_URL`          | `http://localhost:{port}` | External URL for A2A card                           |
| `vector.db.provider`    | `VECTOR_DB_PROVIDER`    | `neo4j`                   | Knowledge DB provider (`neo4j`)                     |
| `vector.db.url`         | `VECTOR_DB_URL`         | (required)                | URL for the vector database                         |
| `vector.db.key`         | `VECTOR_DB_KEY`         |                           | API Key/Token for the vector database               |
| `model.provider`        | `MODEL_PROVIDER`        | `google`                  | AI model provider (google, openai, groq, anthropic) |
| `model.name`            | `MODEL_NAME`            | `gemini-3-flash-preview`  | Default model name                                  |
| `gemini.thinking.level` | `GEMINI_THINKING_LEVEL` | `MINIMAL`                 | Gemini thinking configuration level                 |
| `model.max.retries`     | `MAX_RETRIES`           | `10`                      | Maximum model API retries                           |
| `LOG_LEVEL`             | `LOG_LEVEL`             | `INFO`                    | Global log level (INFO, DEBUG, WARN, ERROR)         |
| `TZ`                    | `TZ`                    | `Europe/Vienna`           | Container timezone                                  |

### Agent-Specific Configuration

Each agent has its own configuration file template with agent-specific settings:

| Agent                    | Configuration Template                               | Purpose                                                                                 |
|--------------------------|------------------------------------------------------|-----------------------------------------------------------------------------------------|
| UI Test Execution Agent  | `ui_test_execution_agent/config.properties.example`  | UI-specific settings (dialogs, element locator, video recording, vision agents)         |
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
    - `VECTOR_DB_KEY` (auto-created by Neo4j deploy script with generated password)
    - `VNC_PW` (for UI agent)
    - `NEO4J_USERNAME` (auto-created by Neo4j deploy script, defaults to `neo4j`)

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

**Deploy only the Neo4j knowledge persistence VM:**

```bash
gcloud builds submit --config=cloudbuild.yaml --substitutions=_DEPLOY_TARGET=neo4j
```

#### Standalone Deployment (Module-Level)

Each agent and Neo4j also have their own `cloudbuild.yaml` for fully independent deployment:

**UI Agent standalone deployment:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml
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
| UI Test Execution Agent  | GCE VM (SPOT)     | 8005       | noVNC (HTTPS) + Agent Server        |
| API Test Execution Agent | Cloud Run         | 8005       | HTTP (internal by default)          |
| Neo4j Knowledge DB       | GCE VM (SPOT)     | 7687, 7474 | Bolt (VPC + external), HTTP Browser |

## Test Execution Results

Both agents return structured `TestExecutionResult` objects containing:

| Field                     | Description                           |
|---------------------------|---------------------------------------|
| `testCaseName`            | Name of the executed test case        |
| `testExecutionStatus`     | PASSED, FAILED, or ERROR              |
| `preconditionResults`     | Results for each precondition         |
| `stepResults`             | Results for each test step            |
| `executionStartTimestamp` | When execution started                |
| `executionEndTimestamp`   | When execution completed              |
| `generalErrorMessage`     | Overall error message if any          |
| `systemInfo`              | Device, OS, browser, environment info |
| `logs`                    | Captured execution logs               |

UI agent results additionally include screenshots and video recordings.

## Knowledge Persistence (Neo4j)

The UI Test Execution Agent supports an optional **knowledge persistence layer** backed by Neo4j 5.x that enables the agent to learn and
remember procedures (reusable test action sequences) across sessions.

### Features

- **Procedure Graph**: Stores hierarchical procedures (composite and atomic) as a Neo4j graph with CONTAINS (parent-child) and
  TARGETS (step-to-UI-element) relationships.
- **PDDL-Lite Planning**: Prerequisite/effect state tracking enables automatic prerequisite resolution during test execution.
- **Queue-Based Execution**: Replaces the sequential for-loop with a dynamic execution queue that injects prerequisite steps when
  prerequisites are unmet.
- **Human-in-the-Loop Collecting knowledge**: In ATTENDED and SUPERVISED modes, the agent triggers a Swing dialog for operators to collect knowledge new
  procedures when an unknown action is encountered. AI suggests prerequisites, effects, and decomposition.
- **Unified Vector Store**: UI element storage migrated from Chroma/Qdrant to Neo4j using `langchain4j-community-neo4j`, providing both
  graph relationships and vector search in a single database.

### Neo4j Setup

#### Local Development

1. Start a Neo4j Community Edition container locally:
   ```bash
   docker run -d --name neo4j-knowledge \
     -p 7687:7687 -p 7474:7474 \
     -e NEO4J_AUTH="neo4j/your-secure-password" \
     -v neo4j-data:/data \
     neo4j:5-community
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
│   ├── Docker: neo4j:5-community
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

**Then deploy the UI agent with the Neo4j host:**

```bash
gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
  --substitutions=_NEO4J_HOST=<neo4j-internal-ip>
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
| `knowledge.embedding.model`       | `KNOWLEDGE_EMBEDDING_MODEL`       | `bge-small-en-v15`      | Embedding model for semantic search                               |
| `knowledge.max.depth`             | `KNOWLEDGE_MAX_DEPTH`             | `3`                     | Maximum procedure decomposition depth                         |
| `knowledge.embedding.batch.size`  | `KNOWLEDGE_EMBEDDING_BATCH_SIZE`  | `10`                    | Batch size for embedding generation                               |
| `knowledge.match.confidence.high` | `KNOWLEDGE_MATCH_CONFIDENCE_HIGH` | `0.85`                  | High-confidence match threshold                                   |
| `knowledge.match.confidence.low`  | `KNOWLEDGE_MATCH_CONFIDENCE_LOW`  | `0.5`                   | Low-confidence match threshold                                    |
| `knowledge.query.timeout.seconds` | `KNOWLEDGE_QUERY_TIMEOUT_SECONDS` | `60`                    | Neo4j query timeout in seconds                                    |

Knowledge persistence is automatically enabled when `vector.db.key` (or `VECTOR_DB_KEY`) is set to a non-blank value. Both
`NEO4J_USERNAME` and `VECTOR_DB_KEY` are stored as secrets in Secret Manager for cloud deployments.

**Resilient DB connection:** The Neo4j driver does not verify connectivity on startup. Schema migration runs in a background virtual thread
at startup (the server waits for it to finish but is not blocked from starting if the DB is unreachable). If migration fails due to
connection issues, the error is logged and the server starts normally. Migration is automatically retried on the first incoming DB
operation. If migration still fails during request processing, the entire request fails with a `DatabaseConnectionException`. The driver
is configured with `withMaxTransactionRetryTime(120s)` so managed-transaction retries (~10 × 10s connection-timeout cycles) are handled
transparently by the driver without any custom retry loop.

### UiElementCache

The UI Test Execution Agent uses a session-scoped `UiElementCache` singleton to avoid redundant Neo4j fetches for the same `UiElement`
across tools, dialogs, and orchestrators.

#### Cache Operations

| Operation | Triggered By |
|-----------|--------------|
| **put** (populate) | `UiElementDbTools.searchElementInDb()`, `UiElementDbTools.createElementInDb()` |
| **update** | `UiElementRefinementHelper.updateElementInfo()`, `UiElementRefinementHelper.updateElementScreenshot()`, `UiElementDialogHelper` inline screenshot update |
| **remove** | `UiElementRefinementHelper.deleteElement()` |
| **get** | `UiElementRefinementHelper.findElementById()` (also used by `UiElementDialogHelper`) |

#### Element Details in Agent Messages

When the execution orchestrator resolves a target UI element for an atomic step, it retrieves the full `UiElement` from the cache and
passes its screenshot and description to the action/precondition agents alongside the current screen screenshot. The user message includes:

```
Target UI element details:
  Name: {element.name}
  Description: {element.description}
  Location details: {element.locationDetails}
  Parent element context: {element.parentElementSummary}
The element screenshot is attached as the last image.
```

This allows the agent to see both the current screen and the reference screenshot of the target element for more accurate interaction.

### Collecting knowledge Workflow

1. During test execution, the agent encounters an unknown action (no matching procedure in the knowledge graph).
2. The AI suggestion agent analyzes the action and proposes preconditions, effects, and child steps.
3. A Swing collecting knowledge dialog (`ProcedureKnowledgeCollectionDialog`) presents the suggestions for the operator to review, modify, or accept. Child steps are listed with screenshot thumbnails; double-clicking or clicking the ✏ affordance on any row opens a recursive `ProcedureKnowledgeCollectionDialog` for that child (bidirectional navigation with "Edit Parent" button). The dialog tracks unsaved changes and warns before discarding them.
4. For atomic steps, the operator can: (a) run the agent-driven element search ("Locate UI Element..."), or (b) open `UiElementLookupDialog` ("Select UI element") to search for an existing element by description and link it, or create a new one directly (agent skips DB search and creates the element, then captures its screenshot).
5. The completed procedure tree is persisted to Neo4j with all relationships and embeddings.
6. On subsequent executions, the agent recognizes the action and executes the learned procedure automatically.

## Development Skills

The project includes AI assistant skills in `.agents/skills/` to help with common development tasks:

| Skill                  | Description                                                                                                                                                          |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Agent Development**  | Guide for creating new AI agents following the project's architecture patterns                                                                                       |
| **Software Architect** | Expert guidance for implementing or modifying features with detailed planning, modern design patterns (Hexagonal, DDD), Java 25 best practices, and ADR requirements |
| **Unit Testing**       | Guide for writing and maintaining unit tests using JUnit 5, AssertJ, and Mockito                                                                                     |
| **Prepare PR**         | Prepares code for a pull request by running Maven build, tests, license checks, and dependency analysis                                                              |
| **PR Review**          | Reviews an open GitHub PR for the current branch, applying project-specific and Java best practice criteria                                                          |
| **Commit and Push**    | Allows to commit all files and push them to the remote repository                                                                                                    |

## Documentation

* For detailed documentation on the UI Test Execution Agent, see **[UI Agent README](ui_test_execution_agent/README.md)**.
* For detailed documentation on the API Test Execution Agent, see **[API Agent README](api_test_execution_agent/README.md)**.
* For development guidelines, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.
