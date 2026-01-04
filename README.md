# AI-Powered Test Execution Agents (Parent Project)

This repository contains a multi-module Maven project for AI-powered test execution agents. It is designed to be modular and scalable, 
separating core agent logic from specific testing agent implementations.

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

*   **`agent_core`**: A shared library module containing the core framework logic, data transfer objects (DTOs), base agent classes, budget management, and generic utilities. This module is designed to be reused by different types of test execution agents.
*   **`ui_test_execution_agent`**: The executable application module that implements the specific logic for UI testing. It includes the server, UI-specific agents (e.g., for visual grounding, element interaction), computer vision capabilities (using OpenCV), and tools for mouse/keyboard control. Deployed as a **GCE VM** with VNC access.
*   **`api_test_execution_agent`**: The executable application module that implements the specific logic for API testing. It includes REST request execution, authentication handling, assertions, and data-driven testing capabilities. Deployed as a **Cloud Run** service.

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
│ • BaseAiAgent       │ │                     │ │                         │
│ • BudgetManager     │◄│ • Server            │ │ • Server                │
│ • Core DTOs         │ │ • UI Agents         │ │ • API Agents            │
│ • Error Handling    │ │ • OpenCV Tools      │ │ • REST Tools            │
└─────────────────────┘ └─────────────────────┘ └─────────────────────────┘
                              │                       │
                              ▼                       ▼
                        ┌───────────┐           ┌───────────┐
                        │  GCE VM   │           │ Cloud Run │
                        │  (VNC)    │           │           │
                        └───────────┘           └───────────┘
```

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

Each agent has its own configuration file template with agent-specific settings:

| Agent | Configuration Template | Purpose |
|-------|----------------------|---------|
| UI Test Execution Agent | `ui_test_execution_agent/config.properties.example` | UI-specific settings (dialogs, element locator, video recording, vision agents) |
| API Test Execution Agent | `api_test_execution_agent/config.properties.example` | API-specific settings (HTTP client, proxy, timeouts, schema validation) |

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

To deploy both agents:

```bash
gcloud builds submit --config=cloudbuild.yaml
```

To customize deployment parameters, you can override substitutions:

```bash
gcloud builds submit --config=cloudbuild.yaml \
  --substitutions=_IMAGE_TAG=v1.0.0,_API_AGENT_EXTERNAL_URL=https://your-actual-cloudrun-url.run.app
```

> **Note:** After the first deployment, update `_API_AGENT_EXTERNAL_URL` in `cloudbuild.yaml` with the actual Cloud Run service URL. This URL is displayed in the Cloud Run console after deployment and is required for the A2A agent card to advertise the correct service endpoint.

### Deployment Details

| Agent | Deployment Target | Access |
|-------|------------------|--------|
| UI Test Execution Agent | GCE VM | noVNC (HTTPS) + Agent Server |
| API Test Execution Agent | Cloud Run | HTTP (internal by default) |

## Documentation

*   For detailed documentation on the UI Test Execution Agent, see **[UI Agent README](ui_test_execution_agent/README.md)**.
*   For detailed documentation on the API Test Execution Agent, see **[API Agent README](api_test_execution_agent/README.md)**.
*   For development guidelines, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.