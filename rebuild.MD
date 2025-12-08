# Rebuild Plan: Multi-Module Refactoring

## Objective

Refactor the current single-module project into a multi-module Maven project to support multiple test execution agents. Create a shared
`agent_core` module for common functionality and a `ui_test_execution_agent` module for the existing UI test execution logic.

> **Note**: All actionable checklist items are consolidated in **Section 15: Master Checklist**. Individual sections provide context and
> details only.

## Module Dependency Diagram

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
│                         │     │ • UI-specific Agents        │
│ • BudgetManager         │     │ • CommonUtils (AWT/OpenCV)  │
│ • Core DTOs             │     │ • Tools (Mouse, Keyboard)   │
│ • Error Handling        │     │ • User Dialogs (Swing)      │
└─────────────────────────┘     └─────────────────────────────┘
```

## 1. Project Structure Overview

**New Structure:**

```text
D:\Projects\ui-test-execution-agent\
├── pom.xml                  (Parent POM)
├── agent_core\              (New Module: Shared Logic)
│   ├── pom.xml
│   └── src\main\java\...
└── ui_test_execution_agent\           (New Module: Existing UI Agent)
    ├── pom.xml
    └── src\main\java\...
```

**Package Naming Convention:**

- `agent_core`: `org.tarik.ta.core.*` (new namespace for core functionality)
- `ui_test_execution_agent`: `org.tarik.ta.*` (keep existing namespace for minimal refactoring)

## 2. Phase 1: Infrastructure Setup

### 2.1 Parent POM Creation

**Parent POM Coordinates:**

```xml

<groupId>org.tarik.ta</groupId>
<artifactId>test-execution-agent-parent</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

**Contents to include:**

- Move `<properties>` and `<developers>` from the current `pom.xml` to this parent POM
- Add version property: `<jetbrains.annotations.version>24.1.0</jetbrains.annotations.version>`
- Define `<modules>`: `agent_core`, `ui_test_execution_agent`
- Define `<dependencyManagement>` to control versions of dependencies (LangChain4j, Jackson, etc.) across modules
- Define `<pluginManagement>` in parent for shared plugin versions (`maven-compiler-plugin`, `maven-surefire-plugin`,
  `license-maven-plugin`)

**Surefire plugin with Mockito agent (in `<pluginManagement>`):**

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>${maven.surefire.plugin.version}</version>
    <configuration>
        <argLine>
            -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar
        </argLine>
    </configuration>
</plugin>
```

**Test dependencies (in `<dependencyManagement>` with `<scope>test</scope>`):**

- `junit-jupiter-engine`
- `mockito-junit-jupiter`
- `assertj-core`

> **Note**: Child modules inherit these test dependencies by declaring them without `<version>` tags.

**Important Clarifications:**

- **OS-specific `<profiles>`** (windows, linux) with native dependencies (OpenCV, FFmpeg, OpenBLAS) → **Stay
  in `ui_test_execution_agent/pom.xml`**
- **`maven-shade-plugin`** configuration → **Stay in `ui_test_execution_agent/pom.xml`** (only `ui_test_execution_agent` produces executable
  JAR)

### 2.2 Module Directories

See **Section 8.2** for the exact git commands to create directories and preserve history.

## 3. Phase 2: `agent_core` Module Implementation

### 3.1 Module Setup

**Module Coordinates:**

```xml

<parent>
    <groupId>org.tarik.ta</groupId>
    <artifactId>test-execution-agent-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>agent_core</artifactId>
```

**Dependencies:**

| Category                | Dependencies                                                                                                                                                                   |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Core Framework**      | `langchain4j`, `langchain4j-core`                                                                                                                                              |
| **LLM Model Providers** | `langchain4j-azure-open-ai`, `langchain4j-open-ai`, `langchain4j-vertex-ai-gemini`, `langchain4j-vertex-ai-anthropic`, `langchain4j-google-ai-gemini`, `langchain4j-anthropic` |
| **RAG/Retrieval**       | `langchain4j-chroma`, `langchain4j-embeddings-all-minilm-l6-v2`                                                                                                                |
| **Serialization**       | `jackson-databind`, `jackson-module-jsonSchema`, `jackson-datatype-jsr310`                                                                                                     |
| **Logging**             | `slf4j-api`, `logback-classic`                                                                                                                                                 |
| **Utilities**           | `guava`, `commons-math3`                                                                                                                                                       |
| **Annotations**         | `org.jetbrains:annotations:${jetbrains.annotations.version}`                                                                                                                   |
| **A2A Protocol**        | `a2a-java-sdk-server-common`                                                                                                                                                   |
| **HTTP Server**         | `io.javalin:javalin`                                                                                                                                                           |
| **Test**                | Inherited from parent (no `<version>` tags)                                                                                                                                    |

### 3.2 Code Migration (Move from `ui_test_execution_agent` to `agent_core`)

The following classes/packages should be moved to `agent_core` with namespace `org.tarik.ta.core.*`.

#### 3.2.1 Configuration

| Current Location           | New Location                    | Notes                                                                                 |
|----------------------------|---------------------------------|---------------------------------------------------------------------------------------|
| `org.tarik.ta.AgentConfig` | `org.tarik.ta.core.AgentConfig` | Contains mixed generic and UI config. Keeping it in core avoids splitting complexity. |

#### 3.2.2 Core Models

| Current Location                            | New Location                                     | Notes                                                                        |
|---------------------------------------------|--------------------------------------------------|------------------------------------------------------------------------------|
| `org.tarik.ta.model.GenAiModel`             | `org.tarik.ta.core.model.GenAiModel`             | Generic model enum                                                           |
| `org.tarik.ta.model.ModelFactory`           | `org.tarik.ta.core.model.ModelFactory`           | Model creation logic                                                         |
| `org.tarik.ta.model.ChatModelEventListener` | `org.tarik.ta.core.model.ChatModelEventListener` | Generic LLM listener                                                         |
| `org.tarik.ta.model.TestExecutionContext`   | `org.tarik.ta.core.model.TestExecutionContext`   | Generic context holder. **VERIFY**: Check for UI dependencies before moving. |
| `org.tarik.ta.model.VisualState`            | **STAY in ui_test_execution_agent**              | UI-specific (screenshot state)                                               |

#### 3.2.3 DTOs (Data Transfer Objects)

| Current Location                        | New Location                                 | Notes                                                                                                          |
|-----------------------------------------|----------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `org.tarik.ta.dto.FinalResult`          | `org.tarik.ta.core.dto.FinalResult`          | Base result interface                                                                                          |
| `org.tarik.ta.dto.TestCase`             | `org.tarik.ta.core.dto.TestCase`             | Generic test case definition                                                                                   |
| `org.tarik.ta.dto.TestStep`             | `org.tarik.ta.core.dto.TestStep`             | Generic test step definition                                                                                   |
| `org.tarik.ta.dto.TestExecutionResult`  | `org.tarik.ta.core.dto.TestExecutionResult`  | Execution result record                                                                                        |
| `org.tarik.ta.dto.TestStepResult`       | `org.tarik.ta.core.dto.TestStepResult`       | Step result record                                                                                             |
| `org.tarik.ta.dto.EmptyExecutionResult` | `org.tarik.ta.core.dto.EmptyExecutionResult` | Empty result placeholder                                                                                       |
| All other DTOs                          | **STAY in ui_test_execution_agent**          | UI-specific DTOs                                                                                               |

#### 3.2.4 Tools

| Current Location                                                             | New Location                                   | Notes                         |
|------------------------------------------------------------------------------|------------------------------------------------|-------------------------------|
| `org.tarik.ta.tools.AgentExecutionResult`                                    | `org.tarik.ta.core.dto.AgentExecutionResult` | Generic execution result      |
| `org.tarik.ta.tools.AbstractTools`                                           | **STAY in ui_test_execution_agent**            | See Section 4.5 for rationale |
| `org.tarik.ta.tools.CommonTools`                                             | **STAY in ui_test_execution_agent**            | See Section 4.5 for rationale |
| `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UserInteractionTools` | **STAY in ui_test_execution_agent**            | UI-specific tools             |

#### 3.2.5 Base Agent Logic

| Current Location                                | New Location                                         | Notes                           |
|-------------------------------------------------|------------------------------------------------------|---------------------------------|
| `org.tarik.ta.agents.BaseAiAgent`               | `org.tarik.ta.core.agents.BaseAiAgent`               | See Section 4.2 for refactoring |

#### 3.2.6 Utilities

| Current Location                       | New Location                          | Notes                         |
|----------------------------------------|---------------------------------------|-------------------------------|
| `org.tarik.ta.utils.CommonUtils`       | **SPLIT**                             | See Section 4.4               |
| `org.tarik.ta.utils.PromptUtils`       | `org.tarik.ta.core.utils.PromptUtils` | Prompt utilities              |
| `org.tarik.ta.utils.ImageUtils`        | **STAY in ui_test_execution_agent**   | Heavy OpenCV/image processing |
| `org.tarik.ta.utils.ImageMatchingUtil` | **STAY in ui_test_execution_agent**   | Image matching algorithms     |
| `org.tarik.ta.utils.BoundingBoxUtil`   | **STAY in ui_test_execution_agent**   | Bounding box calculations     |
| `org.tarik.ta.utils.ScreenRecorder`    | **STAY in ui_test_execution_agent**   | Screen recording (FFmpeg)     |

#### 3.2.7 Error Handling

| Current Location                                   | New Location                                          | Notes                         |
|----------------------------------------------------|-------------------------------------------------------|-------------------------------|
| `org.tarik.ta.error.ErrorCategory`                 | `org.tarik.ta.core.error.ErrorCategory`               | Error taxonomy                |
| `org.tarik.ta.error.RetryPolicy`                   | `org.tarik.ta.core.error.RetryPolicy`                 | Retry configuration           |
| `org.tarik.ta.error.RetryState`                    | `org.tarik.ta.core.error.RetryState`                  | Retry state tracking          |
| `org.tarik.ta.exceptions.ToolExecutionException`   | `org.tarik.ta.core.exceptions.ToolExecutionException` | Generic tool exception        |
| `org.tarik.ta.exceptions.ElementLocationException` | **STAY in ui_test_execution_agent**                   | UI-specific element exception |

#### 3.2.8 Managers

| Current Location                           | New Location                              | Notes                            |
|--------------------------------------------|-------------------------------------------|----------------------------------|
| `org.tarik.ta.manager.BudgetManager`       | `org.tarik.ta.core.manager.BudgetManager` | Budget enforcement (generic)     |
| `org.tarik.ta.manager.VerificationManager` | **STAY in ui_test_execution_agent**       | UI verification state management |

#### 3.2.9 RAG / Retrieval

| Current Location                      | New Location                             | Notes                       |
|---------------------------------------|------------------------------------------|-----------------------------|
| `org.tarik.ta.rag.ChromaRetriever`    | `org.tarik.ta.core.rag.ChromaRetriever`  | Generic retriever interface |
| `org.tarik.ta.rag.RetrieverFactory`   | `org.tarik.ta.core.rag.RetrieverFactory` | Factory for retrievers      |
| `org.tarik.ta.rag.UiElementRetriever` | **STAY in ui_test_execution_agent**      | UI-specific retriever       |
| `org.tarik.ta.rag.model.UiElement`    | **STAY in ui_test_execution_agent**      | UI-specific element model   |

#### 3.2.10 A2A (Agent-to-Agent) Protocol

| Current Location                           | New Location                                    | Notes                                  |
|--------------------------------------------|-------------------------------------------------|----------------------------------------|
| `org.tarik.ta.a2a.JsonRpcTransportService` | `org.tarik.ta.core.a2a.JsonRpcTransportService` | Generic JSON-RPC transport             |
| `org.tarik.ta.a2a.AgentExecutionResource`  | `org.tarik.ta.core.a2a.AgentExecutionResource`  | See Section 4.3 (constructor refactor) |
| `org.tarik.ta.a2a.AgentCardProducer`       | **STAY in ui_test_execution_agent**             | UI agent-specific card                 |
| `org.tarik.ta.a2a.UiAgentExecutor`         | **STAY in ui_test_execution_agent**             | UI agent execution logic               |

#### 3.2.11 User Dialogs

| Current Location                         | Decision                                | Notes                         |
|------------------------------------------|-----------------------------------------|-------------------------------|
| `org.tarik.ta.user_dialogs.*` (12 files) | **ALL STAY in ui_test_execution_agent** | UI-specific Swing/AWT dialogs |

## 4. Phase 3: Code Refactoring for Decoupling

### 4.1 `AgentConfig` Handling

- **Action**: Move `AgentConfig.java` to `agent_core`.
- **Reason**: It contains mixed generic and UI config. Splitting it now is high-effort.

**Resource Loading Note**:
`AgentConfig` will load `config.properties` from the classpath. When `ui_test_execution_agent` includes `agent_core` as a dependency, both
JARs'
resources are on the classpath. Ensure `AgentConfig` uses absolute classpath-relative loading (`/config.properties`).

**Verification**: Confirm classpath-relative loading (starts with `/`). Test after migration with:
`mvn exec:java -pl ui_test_execution_agent`

### 4.2 `BaseAiAgent` Decoupling (Crucial)

**Issue**: `BaseAiAgent` interface default methods call `CommonUtils.captureScreen()` (static binding to UI/AWT).

**Solution**: Extract the UI-specific behavior into a new interface `BaseAiUiAgent` which extends from `BaseAiAgent`. 


### 4.3 `AgentExecutionResource` Refactoring

**Issue**: Hardcodes `new UiAgentExecutor()`.

**Solution**:

1. Create `org.tarik.ta.core.a2a.AgentExecutor` interface:
   ```java
   package org.tarik.ta.core.a2a;
   
   import io.a2a.server.agentexecution.AgentExecution;
   
   public interface AgentExecutor extends AgentExecution {
       // Inherits all methods from AgentExecution
   }
   ```

2. Refactor `AgentExecutionResource` constructor to accept `AgentExecutor`:
   ```java
   public AgentExecutionResource(AgentExecutor executor, AgentCard agentCard) {
       // ... initialization using injected executor
   }
   ```

3. Update `UiAgentExecutor` to implement `AgentExecutor`:
   ```java
   public class UiAgentExecutor implements AgentExecutor { ... }
   ```

4. In `Server.java`, instantiate and inject:
   ```java
   new AgentExecutionResource(new UiAgentExecutor(), AgentCardProducer.createCard());
   ```

### 4.4 `CommonUtils` Split Strategy

**Issue**: `CommonUtils` contains both generic utilities and UI-specific AWT methods.

**Decision**: Split into two classes.

**Methods to move to `agent_core` (`org.tarik.ta.core.utils.CoreUtils`):**

- `getObjectPrettyPrinted()`
- `parseStringAsInteger()` / `parseStringAsDouble()`
- `isBlank()` / `isNotBlank()`
- `sleepSeconds()` / `sleepMillis()`
- `deleteFile()` / `deleteFolderContents()`
- `waitUntil()`
- `getFutureResult()`

**Methods staying in `ui_test_execution_agent` (`CommonUtils`):**

- `captureScreen()` / `captureScreenPart()`
- `getScreenSize()`
- `getRobot()` / `getMouseLocation()`
- `getScaledScreenLocationCoordinates()` / `getPhysicalScreenLocationCoordinates()`
- `getScaledBoundingBox()` / `getPhysicalBoundingBox()`
- `getColorByName()` / `getColorName()` / `getStaticFieldValue()`
- `getCommonArea()`

**Action Items**: Create `org.tarik.ta.core.utils.CoreUtils` with generic methods, update imports across codebase, keep `CommonUtils` in
`ui_test_execution_agent` with UI-specific methods only.

### 4.5 Tools Migration Decisions (`AbstractTools` and `CommonTools`)

**Issue**: Both `AbstractTools.java` and `CommonTools.java` have UI dependencies:

| Class           | UI Dependencies                                                                       |
|-----------------|---------------------------------------------------------------------------------------|
| `AbstractTools` | `UiStateCheckAgent`, `UiStateCheckResult`, creates `UiStateCheckAgent` in constructor |
| `CommonTools`   | `UiStateCheckAgent`, `VerificationStatus`, `VerificationManager`                      |

**Decision**: **Keep both in `ui_test_execution_agent`**.

### 4.6 `PromptUtils` Resource Loading

**Issue**: `PromptUtils.java` is moving to `agent_core`, but prompt templates stay in
`ui_test_execution_agent/src/main/resources/prompt_templates/`.

**Expected Behavior**: Resources from `ui_test_execution_agent` will be on classpath at runtime. `PromptUtils` will find them via
classpath-relative loading.

**Verification**: Confirm `PromptUtils` uses classpath-relative loading. Test prompt loading after migration.

## 5. Phase 4: `ui_test_execution_agent` Module Implementation

### 5.1 Module Setup

**Module Coordinates:**

```xml

<parent>
    <groupId>org.tarik.ta</groupId>
    <artifactId>test-execution-agent-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>ui_test_execution_agent</artifactId>
```

**Dependencies:**

- `agent_core` (Project dependency)
- `opencv`, `ffmpeg`, `javacv` (UI/Video specific)
- `a2a-java-sdk-transport-jsonrpc`
- Test dependencies (inherited from parent, no `<version>` tags)

### 5.2 Remaining Code in `ui_test_execution_agent`

1. **Entry Points**: `Server`, `Agent` (Orchestrator)
2. **UI Executors**: `UiAgentExecutor`, `AgentCardProducer`
3. **UI Agents**: `ElementSelectionAgent`, `Precondition*Agent`, `TestStep*Agent`, `ElementBoundingBoxAgent`, `PageDescriptionAgent`,
   `TestCaseExtractionAgent`, `UiElementDescriptionAgent`, `UiStateCheckAgent`
4. **Tools**: `AbstractTools`, `CommonTools`, `MouseTools`, `KeyboardTools`, `ElementLocatorTools`, `UserInteractionTools`
5. **UI Utils**: `CommonUtils`, `ImageUtils`, `ImageMatchingUtil`, `BoundingBoxUtil`, `ScreenRecorder`
6. **UI DTOs**: All bounding box, element, location, page, precondition, UI element, UI state, verification, and new element DTOs
7. **Managers**: `VerificationManager`
8. **User Dialogs**: All 12 dialog classes
9. **Model**: `VisualState`
10. **RAG**: `UiElementRetriever`, `UiElement`
11. **Exceptions**: `ElementLocationException`

## 6. Phase 5: Resource File Migration

### 6.1 Resource File Strategy

| Resource                                            | Location                                      | Notes                               |
|-----------------------------------------------------|-----------------------------------------------|-------------------------------------|
| `logback.xml`                                       | `ui_test_execution_agent/src/main/resources/` | Application-level config            |
| `config.properties`                                 | `ui_test_execution_agent/src/main/resources/` | Agent configuration                 |
| `config.properties.example`                         | **Root directory**                            | Developer documentation             |
| `META-INF/services/io.a2a.server.TransportMetadata` | See below                                     | Service file handling               |

**Service File Handling:**

The `META-INF/services/io.a2a.server.TransportMetadata` file requires careful handling:

1. **Current content** (verify before migration):
   ```
   org.tarik.ta.a2a.JsonRpcTransportService
   ```

2. **After migration**:
    - Move file to `agent_core/src/main/resources/META-INF/services/`
    - Update content to: `org.tarik.ta.core.a2a.JsonRpcTransportService`

3. **Important**: Only `agent_core` should have this service file. If a second service file exists in `ui_test_execution_agent`, classloader
   behavior
   is undefined. Do NOT create duplicate service files.

**Existing prompt templates**

Prompt templates in `prompt_templates/**` must be split.  Prompts which relate to test case extraction must be moved into the agent_core 
module, the rest remains where it is.

### 6.2 `logback.xml` Strategy

**Decision**: Keep `logback.xml` in `ui_test_execution_agent` only.

- `agent_core` is a library module, not a standalone application
- Logging configuration should be controlled by the application module

**Optional**: Create `agent_core/src/test/resources/logback-test.xml` for core module tests.

## 7. Phase 6: Test File Migration

### 7.1 Test File Mapping

| Test File                     | Target Module             | Reason                       |
|-------------------------------|---------------------------|------------------------------|
| `BaseAiAgentRetryTest.java`   | `agent_core`              | Tests core agent retry logic |
| `BudgetManagerTest.java`      | `agent_core`              | Tests core budget management |
| `CommonUtilsTest.java`        | **SPLIT**                 | See detailed split below     |
| `AgentTest.java`              | `ui_test_execution_agent` | Tests UI agent orchestrator  |
| `ManualTest.java`             | `ui_test_execution_agent` | Manual UI testing            |
| `Precondition*AgentTest.java` | `ui_test_execution_agent` | UI agent tests               |
| `TestStep*AgentTest.java`     | `ui_test_execution_agent` | UI agent tests               |
| `*ResultTest.java` (DTOs)     | `ui_test_execution_agent` | UI DTO tests                 |
| `KeyboardToolsTest.java`      | `ui_test_execution_agent` | UI tool test                 |
| `MouseToolsTest.java`         | `ui_test_execution_agent` | UI tool test                 |
| `BoundingBoxUtilTest.java`    | `ui_test_execution_agent` | UI utility test              |

#### `CommonUtilsTest` Split Details

| Test Method                                     | Target                                | Reason                           |
|-------------------------------------------------|---------------------------------------|----------------------------------|
| `testGetObjectPrettyPrinted*`                   | `agent_core` (→ `CoreUtilsTest`)      | Tests generic JSON serialization |
| `testParseStringAsInteger*`                     | `agent_core` (→ `CoreUtilsTest`)      | Tests generic parsing            |
| `testParseStringAsDouble*`                      | `agent_core` (→ `CoreUtilsTest`)      | Tests generic parsing            |
| `testIsBlank*` / `testIsNotBlank*`              | `agent_core` (→ `CoreUtilsTest`)      | Tests generic string utils       |
| `testSleepSeconds*` / `testSleepMillis*`        | `agent_core` (→ `CoreUtilsTest`)      | Tests generic timing             |
| `testDeleteFile*` / `testDeleteFolderContents*` | `agent_core` (→ `CoreUtilsTest`)      | Tests generic file operations    |
| `testWaitUntil*`                                | `agent_core` (→ `CoreUtilsTest`)      | Tests generic waiting            |
| `testGetFutureResult*`                          | `agent_core` (→ `CoreUtilsTest`)      | Tests generic concurrency        |
| `testCaptureScreen*`                            | **STAY** in `ui_test_execution_agent` | Tests AWT screen capture         |
| `testGetScreenSize*`                            | **STAY** in `ui_test_execution_agent` | Tests AWT screen dimensions      |
| `testGetRobot*`                                 | **STAY** in `ui_test_execution_agent` | Tests AWT Robot                  |
| `testGetMouseLocation*`                         | **STAY** in `ui_test_execution_agent` | Tests AWT mouse                  |
| `testGetScaled*` / `testGetPhysical*`           | **STAY** in `ui_test_execution_agent` | Tests coordinate scaling         |
| `testGetColor*`                                 | **STAY** in `ui_test_execution_agent` | Tests AWT Color                  |

### 7.2 Test Resources

The `src/test/resources/` directory is currently empty. Create directories for future use: `agent_core/src/test/resources/` and
`ui_test_execution_agent/src/test/resources/`.

**Logging Configuration for Tests:**

- Create `agent_core/src/test/resources/logback-test.xml` with debug-level logging for core tests
- `ui_test_execution_agent` tests can inherit logback configuration from main resources

## 8. Implementation Steps (Execution Order)

### 8.1 Execution Checklist

1. **Root POM**: Create `pom.xml` in root.
2. **Create Folders**: Execute mkdir commands from Section 8.2.
3. **Move Source**: Execute git mv commands from Section 8.2.
4. **Setup Core**:
    - Create `agent_core/pom.xml`.
    - Use IntelliJ **Refactor → Move** to move identified core files (this updates imports automatically).
5. **Create New Files**:
    - `AgentExecutor.java` (executor interface)
    - `CoreUtils.java` (generic utilities extracted from CommonUtils)
6. **Refactor Core**:
    - Update `AgentExecutionResource` constructor.
7. **Setup UI Agent**:
    - Edit `ui_test_execution_agent/pom.xml` to depend on `agent_core`.    
    - Update `Server.java` to inject `UiAgentExecutor` into `AgentExecutionResource`.
    - Update `UiAgentExecutor` to implement `AgentExecutor` interface.
8. **Verify**: Run `mvn clean install` from root. Fix any remaining issues.

## 9. CI/CD and Build File Updates

### 9.1 Files Requiring Updates

| File                                | Action                            |
|-------------------------------------|-----------------------------------|
| `cloudbuild.yaml`                   | Update for multi-module build     |
| `.github/workflows/package.yml`     | Update for multi-module structure |
| `.run/*` (IntelliJ)                 | Update run configurations         |
| `deployment/cloud/Dockerfile.cloud` | Update JAR source path            |

### 9.2 Maven Commands

```bash
# Build all modules
mvn clean install

# Build specific module with dependencies
mvn clean install -pl ui_test_execution_agent -am

# Run tests for specific module
mvn test -pl agent_core
mvn test -pl ui_test_execution_agent
```

### 9.3 `cloudbuild.yaml` Updates

```yaml
# Updated command
args: [ 'mvn', 'clean', 'install', '-DskipTests', '-P', 'linux', '-pl', 'ui_test_execution_agent', '-am' ]
```

### 9.4 GitHub Actions Updates (`.github/workflows/package.yml`)

```yaml
- name: Build with Maven and Profiles
  run: mvn -B package --file pom.xml -pl ui_test_execution_agent -am -P linux

- name: Upload artifact
  uses: actions/upload-artifact@v4
  with:
    name: packaged-application
    path: ui_test_execution_agent/target/*.jar
    if-no-files-found: error
```

### 9.5 IntelliJ Run Configuration Updates

Update in `.run/` directory:

- [ ] `ManualTest.testServer.run.xml`: Change `<module name="ui_test_java" />` to `<module name="ui_test_execution_agent" />`
- [ ] `Server Groq.run.xml`: Same module name change

### 9.6 Dockerfile.cloud Updates

```dockerfile
# Updated path
ARG SOURCE_JAR_PATH=ui_test_execution_agent/target/ui_test_execution_agent-1.0.0-SNAPSHOT.jar
```

## 10. Documentation Updates

- [ ] Update `README.md` to reflect multi-module structure
- [ ] Update build instructions for developers
- [ ] Archive or delete `IMP_PLAN.MD` and `REFACTORING_PLAN.md` after migration
- [ ] Update `flow_diagram.puml` if it contains package references

## 11. Post-Migration Cleanup

- Remove unused imports
- Verify all license headers are correct
- Delete root-level `dependency-reduced-pom.xml`
- Add to `.gitignore`:
  ```gitignore
  ui_test_execution_agent/dependency-reduced-pom.xml
  agent_core/target/
  ui_test_execution_agent/target/
  ```
- Run code formatter on affected files
- Delete empty package directories after file moves

---