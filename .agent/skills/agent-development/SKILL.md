---
name: Agent Development
description: Guide for creating new AI agents following the project's architecture patterns
---

# Agent Development Skill

This skill provides comprehensive instructions for creating new AI agents in the Test Execution Agents project.

## When to use this skill

- Use this when creating a new agent module.
- Use this when implementing agent components (tools, executors, servers).
- Use this when wiring up LangChain4j services.

## Project Architecture Overview

The project follows a modular architecture with these key components:

```
agent_core/                  (Shared Library)
├── AbstractServer           → Base class for HTTP servers
├── AbstractAgentExecutor    → Base class for A2A task execution
├── GenericAiAgent          → Core interface for all AI agents
├── FinalResult             → Marker interface for agent result types
└── InheritanceAwareToolProvider → Enhanced tool provider for @Tool discovery

ui_test_execution_agent/     (Example: UI Testing Agent)
api_test_execution_agent/    (Example: API Testing Agent)
```

## Step-by-Step: Creating a New Agent Module

### Step 1: Create Module Structure

Create a new Maven module. See `examples/` for reference implementation of core classes.

Structure:
```
new_agent/
├── pom.xml
├── deployment/
│   └── cloud/
│       └── cloudbuild.yaml     (if deploying to GCP)
└── src/
    ├── main/
    │   ├── java/org/tarik/ta/
    │   │   ├── NewTestAgent.java           (Main entry point)
    │   │   ├── NewTestAgentConfig.java     (Agent-specific config)
    │   │   ├── a2a/
    │   │   │   ├── NewAgentExecutor.java   (Extends AbstractAgentExecutor)
    │   │   │   └── AgentCardProducer.java  (A2A card definition)
    │   │   ├── agents/
    │   │   │   └── ...Agent.java           (Specialized agents)
    │   │   └── tools/
    │   │       └── ...Tools.java           (Agent tools)
    │   └── resources/
    │       ├── config.properties
    │       └── prompts/                    (Prompt templates)
    └── test/
        └── java/org/tarik/ta/
            └── ...Test.java
```

### Step 2: Create the Maven POM

Add dependency to `agent_core` in your `pom.xml`.

### 3. Implement Core Components

Refer to the files in `examples/` for implementation details:

#### 3.1 Create FinalResult Implementation
Every agent result type must implement `FinalResult`. Use Java records for DTOs.
- See: [`examples/NewActionResult.java`](examples/NewActionResult.java)

#### 3.2 Create the Agent Interface
Extend `GenericAiAgent<T>` where `T` is your result type.
- See: [`examples/NewActionAgent.java`](examples/NewActionAgent.java)

#### 3.3 Create Tools Classes
Tools provide capabilities to the agent. Extend `AbstractTools` if shared functionality is needed.
- See: [`examples/NewAgentTools.java`](examples/NewAgentTools.java)

#### 3.4 Create Agent Executor
Extend `AbstractAgentExecutor` to handle A2A task execution.
- See: [`examples/NewAgentExecutor.java`](examples/NewAgentExecutor.java)

#### 3.5 Create Server Implementation
Extend `AbstractServer` for the HTTP server.
- See: [`examples/NewTestAgentServer.java`](examples/NewTestAgentServer.java)

### 4. Wire Up the Agent with LangChain4j
Create the agent using AI Services with `InheritanceAwareToolProvider`.
- See: [`examples/NewTestAgent.java`](examples/NewTestAgent.java)

### 5. Create Configuration

#### Agent Configuration Class
- See: [`examples/NewTestAgentConfig.java`](examples/NewTestAgentConfig.java)

#### Properties File Template
- See: [`resources/config.properties.example`](resources/config.properties.example)

## Best Practices

### 1. Result Types
- Always use Java records for result DTOs
- Implement `FinalResult` interface
- Add static `@Tool` method named `submitResult` for agent to return results

### 2. Tool Methods
- Use `@Tool` annotation with clear descriptions
- Use `@P` annotation for parameter descriptions
- Keep tools focused and single-purpose
- Handle exceptions using `DefaultToolErrorHandler`

### 3. Agent Interfaces
- Use `@UserMessage` with template variables `{{varName}}`
- Use `@V("varName")` for method parameters
- Return `Result<String>` from execution methods
- Implement `getAgentTaskDescription()` for logging

### 4. Error Handling
- Use `ToolExecutionException` for tool errors
- Leverage `DefaultToolErrorHandler` for retry logic
- Return meaningful error messages in result types

### 5. Testing
- Follow the Unit Testing Skill for test patterns
- Test tool methods independently
- Use mocks for external dependencies
