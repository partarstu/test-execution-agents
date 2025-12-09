# AI-Powered UI Test Execution Agent (Parent Project)

This repository contains a multi-module Maven project for AI-powered test execution agents. It is designed to be modular and scalable, 
separating core agent logic from specific testing agent implementations.

## Project Structure

The project is organized into the following modules:

```
D:\Projects\ui-test-execution-agent\
├── pom.xml                  (Parent POM)
├── agent_core\              (Shared Library)
│   ├── pom.xml
│   └── src\main\java\...
└── ui_test_execution_agent\ (Executable UI Agent)
    ├── pom.xml
    └── src\main\java\...
```

### Module Overview

*   **`agent_core`**: A shared library module containing the core framework logic, data transfer objects (DTOs), base agent classes, budget management, and generic utilities. This module is designed to be reused by different types of test execution agents.
*   **`ui_test_execution_agent`**: The executable application module that implements the specific logic for UI testing. It includes the server, UI-specific agents (e.g., for visual grounding, element interaction), computer vision capabilities (using OpenCV), and tools for mouse/keyboard control.

### Module Dependency Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    test-execution-agent-parent              │
│                         (Parent POM)                        │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────────┐
│      agent_core         │     │  ui_test_execution_agent    │
│   (Shared Library)      │◄────│   (Executable Application)  │
│                         │     │                             │
│ • BaseAiAgent           │     │ • Server (Entry Point)      │
│ • BudgetManager         │     │ • UI-specific Agents        │
│ • Core DTOs             │     │ • CommonUtils (AWT/OpenCV)  │
│ • Error Handling        │     │ • Tools (Mouse, Keyboard)   │
└─────────────────────────┘     └─────────────────────────────┘
```

## Building the Project

To build the entire project, run the following command from the root directory:

```bash
mvn clean install
```

This will build both modules and run the tests.

To build specifically the UI agent executable (skipping tests for speed):

```bash
mvn clean package -pl ui_test_execution_agent -am -DskipTests
```

## Documentation

*   For detailed documentation on the UI Test Execution Agent, features, configuration, and usage, please refer to the **[UI Agent README](ui_test_execution_agent/README.md)**.
*   For development guidelines, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.