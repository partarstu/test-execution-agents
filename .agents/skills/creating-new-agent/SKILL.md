---
name: creating-new-agent
description: Creates a new A2A test execution agent module or a new LangChain4j sub-agent in the Test Execution Agents project - Maven module, configuration, result records, agent interfaces, tools, Avaje Inject wiring, A2A executor and agent card, server, deployment, CALM architecture node, unit tests and smoke-suite coverage. Use when the user asks to add a new agent or sub-agent with its own prompts and tools.
---

# Creating a New Agent

An agent module is an A2A server built on `agent_core`. `api_test_execution_agent` is the reference implementation: read the file named in each step and mirror it, instead of copying code from memory.

For a new **sub-agent inside an existing module**, do steps 4, 5, 7 and 11 only.

Copy this checklist and track progress:

```
- [ ] 1. CALM model (architecture first)
- [ ] 2. Maven module
- [ ] 3. Configuration
- [ ] 4. Result record
- [ ] 5. Agent interface and system prompt
- [ ] 6. Tools
- [ ] 7. Agent wiring (bean factory and request scope)
- [ ] 8. Orchestration agent
- [ ] 9. A2A executor, agent card and server
- [ ] 10. Deployment
- [ ] 11. Unit tests
- [ ] 12. Smoke suite
- [ ] 13. Documentation
- [ ] 14. Build and start locally
```

Target layout, mirroring `api_test_execution_agent`:

```
<agent_module>/
├── pom.xml
├── README.md
├── deployment/
│   ├── Dockerfile.cloud
│   └── cloud/cloudbuild.yaml
└── src/
    ├── main/java/org/tarik/ta/
    │   ├── Server.java, <Name>TestAgent.java, <Name>TestAgentConfig.java
    │   ├── <Name>AgentsBeanFactory.java, <Name>AgentCardFactory.java
    │   ├── <Name>AgentRequestScope.java, <Name>AgentRequestScopeFactory.java, package-info.java
    │   ├── a2a/        (<Name>AgentExecutor, AgentCardProducer)
    │   ├── agents/     (LangChain4j agent interfaces)
    │   └── tools/      (tool classes)
    ├── main/resources/
    │   ├── config.properties, logback.xml
    │   └── prompt_templates/system/agents/<agent path>/<version>/<prompt>.md
    └── test/java/org/tarik/ta/   (unit tests, smoke/)
```

## 1. CALM model (architecture first)

A new agent is a new architecture node, so *Architecture first (CALM)* in `AGENTS.md` applies before anything below is written: model the node and its relationships (to the orchestrator, the LLM provider, the system under test, its runtime), validate, render and get the user's approval.

## 2. Maven module

- Add the module to `<modules>` of the parent `pom.xml`.
- Model `pom.xml` on `api_test_execution_agent/pom.xml`: parent reference, `agent-core` dependency (plus its `test-jar` for smoke tests), Avaje Inject with its generator, the shade plugin with `<main.class>org.tarik.ta.Server</main.class>`. Declare the dependencies as `JAVA_GUIDELINES.md` § 15 requires.

## 3. Configuration

- `<Name>TestAgentConfig` extends `org.tarik.ta.core.AgentConfig`, is a `@Singleton`, and loads every setting in its constructor with `loadProperty(...)`/`loadPropertyAsInteger(...)`, giving a property key, an environment variable and a default. Model it on `ApiTestAgentConfig`.
- Every sub-agent gets its model name, model provider and prompt version as settings.
- Default the values in `src/main/resources/config.properties`, starting from the core section of `api_test_execution_agent/src/main/resources/config.properties`. Pick a `port` no other agent uses and set `agent.version=1.0.0`.

## 4. Result record

The reusable types in `org.tarik.ta.core.dto` are `VerificationExecutionResult`, `EmptyExecutionResult` and `PreconditionExecutionActionResult`. A new record is modelled on `VerificationExecutionResult`, including its static `@Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE_IF_LAST)` method with the `@P(FINAL_RESULT_PARAM_DESCRIPTION)` parameter, which the LLM calls to end the execution.

## 5. Agent interface and system prompt

- Model the interface on `agents/ApiTestStepActionAgent`: one method annotated with `@UserMessage`, using `{{variable}}` placeholders bound by `@V("variable")` parameters. `getAgentTaskDescription()` is used in logs.
- Put the system prompt in `src/main/resources/prompt_templates/system/agents/<agent path>/v1.0.0/<prompt>.md`: the task as a numbered sequence, the tools described by purpose, and what to return when a tool fails or information is missing.

## 6. Tools

- Model tool classes on `tools/ApiRequestTools`; mark optional parameters with `@P(required = false)`.
- Wrap unexpected exceptions with `rethrowAsToolException(e, "<operation>")`.
- Reuse the shared tools from `agent_core` (`TestContextDataTools`) instead of re-implementing context access.

## 7. Agent wiring

- Sub-agents are created per request. Model the wiring on `ApiAgentsBeanFactory`: a package-private `@Factory` annotated with the module's request scope, one `@Bean` method per agent that builds it with `AiServices.builder(...)`, `chatModel(modelFactory.getModel(...).chatModel())`, `systemMessageProvider(_ -> prompt)` with `PromptUtils.loadSystemPrompt(...)`, `toolProvider(new InheritanceAwareToolProvider<>(List.of(tools...), ResultType.class))`, `toolExecutionErrorHandler(new DefaultToolErrorHandler(config.getActionRetryPolicy()))` and `maxToolCallingRoundTrips(config.getAgentToolCallsBudget())`.
- For a new module, create the request scope annotation, the request scope factory and `package-info.java` as in `ApiAgentRequestScope`, `ApiAgentRequestScopeFactory` and `api_test_execution_agent/src/main/java/org/tarik/ta/package-info.java`. The `<Name>AgentRequestModule` is generated by the Avaje Inject annotation processor.

## 8. Orchestration agent

Model `<Name>TestAgent` on `ApiTestAgent`: request-scoped, constructor injection of the configuration, `TestCaseExtractor`, `BudgetManager`, `TestExecutionContext`, `LogCapture` and a `Provider<...>` per sub-agent. It resets and activates the budgets, extracts the test case, runs preconditions and steps, emits progress through the context's event emitter and returns a `TestExecutionResult` that keeps the shared status contract.

## 9. A2A executor, agent card and server

- `a2a/<Name>AgentExecutor` extends `AbstractAgentExecutor` and implements `executeTestCase(String, StreamingEventEmitter)` by opening a request scope in try-with-resources and delegating to the orchestration agent, as `ApiAgentExecutor` does.
- `a2a/AgentCardProducer` and `<Name>AgentCardFactory` build the agent card (version from `agent.version`, name, description with the sub-agent models, skill with a stable `id`, JSONRPC interface), as in the API agent.
- `Server` starts the application `BeanScope` in try-with-resources and calls `AbstractServer.start()`.

## 10. Deployment

If the agent is deployed to Cloud Run, mirror the API agent: `deployment/Dockerfile.cloud`, `deployment/cloud/cloudbuild.yaml`, and a `deploy-<name>-agent` step plus its substitutions in the root `cloudbuild.yaml`.

## 11. Unit tests

Write the tests with the `writing-unit-tests` skill. Mirror the API agent's tests: configuration (`ApiTestAgentConfigTest`), orchestration (`ApiTestAgentTest`), executor (`a2a/ApiAgentExecutorTest`), agent card (`a2a/AgentCardProducerTest`), agent interfaces (`agents/AgentsInterfacesTest`) and every tool class (`tools/ApiRequestToolsTest`).

## 12. Smoke suite

A new agent is a new flow, so the smoke suite covers it in the same change (*Smoke suite* in `AGENTS.md`): add `smoke/<Name>AgentSmokeTest`, tagged `@Tag("smoke")`, that drives the real orchestration agent with a scripted chat model (see `api_test_execution_agent/src/test/java/org/tarik/ta/smoke/ScriptedChatModel.java`) and mocks only the external boundaries, plus `src/test/resources/junit-platform.properties` disabling parallel execution as in the other modules. Add an A2A transport test modelled on `ApiAgentA2aSmokeTest` when the agent exposes new A2A behaviour.

## 13. Documentation

- Write the module's `README.md` (overview, key classes, configuration, agents, tools, smoke tests, deployment) and update the root `README.md` (project structure, module overview, smoke tests, deployment).

## 14. Build and start locally

```bash
mvn clean install -pl <agent_module> -am -DskipTests
java -jar <agent_module>/target/<artifact>-<version>.jar
```

The agent card must be served at `http://localhost:<port>/.well-known/agent-card.json`.
