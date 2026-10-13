---
name: writing-unit-tests
description: Writes JUnit 6 unit tests with AssertJ and Mockito for the Test Execution Agents modules (agent_core, UI agent, API agent) - agents, tools, A2A executors, configuration, DTOs and knowledge-graph services - following the project's existing test patterns. Use when adding or updating tests for new or changed code.
---

# Writing Unit Tests

Tests follow `JAVA_GUIDELINES.md` § 13 and *Unit tests* in `AGENTS.md`. This skill adds the setup and conventions for writing new tests.

## Setup facts

- The versions of JUnit Jupiter, Mockito and AssertJ are properties of the parent `pom.xml`; use only APIs of those versions.
- Surefire runs with Mockito as a `-javaagent` (parent `pom.xml`), so final classes and static methods can be mocked without extra setup.
- The tests that need real infrastructure or a GUI (`ManualTest`, `CleanDbTest`, the model comparison tests) are `@Disabled`.
- `agent_core/src/test/resources/config.properties` provides dummy configuration for core tests. `agent_core` publishes its test classes as a test-jar (e.g. `A2aTestClient`), which the agent modules reuse.

## Follow the existing tests

Read the closest existing test before writing a new one, and reuse its helpers and setup instead of copying them:

| Testing                                          | Model on                                                                     |
|--------------------------------------------------|------------------------------------------------------------------------------|
| Records and DTOs                                 | `agent_core/.../core/dto/DtoTest.java`                                       |
| Configuration classes                            | `agent_core/.../core/AgentConfigTest.java`, `api_test_execution_agent/.../ApiTestAgentConfigTest.java` |
| Agent retry and result extraction                | `agent_core/.../core/agents/BaseAiAgentRetryTest.java`                       |
| Tool classes                                     | `api_test_execution_agent/.../tools/ApiRequestToolsTest.java`                |
| Orchestration agents                             | `api_test_execution_agent/.../ApiTestAgentTest.java`                         |
| A2A executors                                    | `agent_core/.../core/a2a/AbstractAgentExecutorTest.java`, `api_test_execution_agent/.../a2a/ApiAgentExecutorTest.java` |
| Agent cards                                      | `api_test_execution_agent/.../a2a/AgentCardProducerTest.java`                |
| Static mocking (`MockedStatic`)                  | `agent_core/.../core/utils/TestCaseExtractorTest.java`                       |
| UI knowledge-based execution                     | `ui_test_execution_agent/.../knowledge_graph/StepExecutionOrchestratorTest.java` |
| Avaje Inject bean factories                      | `ui_test_execution_agent/.../UiAgentsBeanFactoryTest.java`                   |

`...` stands for `src/test/java/org/tarik/ta`.

## Project conventions

1. **Tool classes** are tested directly, without an LLM.
2. **Operator prompts**: mock `OperatorPrompts` for every class that asks the operator, and test the prompts themselves on the panel: `WebOperatorPanel.ask` on a separate thread with the answer completed in the test (`WebOperatorPanelTest`), or over HTTP against `PanelTestFixture` (`PromptRoutesTest`). Pass short durations through the constructors instead of waiting for real countdowns.
3. **Configuration**: mock the `*Config` class or use the test `config.properties`; do not set environment variables.

## What to cover

Cover what `JAVA_GUIDELINES.md` § 13 requires. The failure paths this project's code handles explicitly are usually a `ToolExecutionException` with its `ErrorCategory`, an `OperationExecutionResult` with an error status, a `TestExecutionResult` with status `ERROR` or `FAILED`, retries through `RetryPolicy`, and logged-and-continued errors.

## Verify

Run the test class, then its module (*Commands* in `AGENTS.md`); both must pass without new warnings. If anything fails, continue with the `running-unit-tests` skill.
