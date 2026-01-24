# AI-Powered UI Test Execution Agent

This project is a Java-based agent that leverages Generative AI models and Retrieval-Augmented Generation (RAG) to execute test
cases written in a natural language form at the graphical user interface (GUI) level. It understands explicit test case instructions
(both actions and verifications), performs corresponding actions using its tools (like the mouse and keyboard), locates the required UI
elements on the screen (if needed), and verifies whether actual results correspond to the expected ones using computer vision capabilities.

[![Package Project](https://github.com/partarstu/ui-test-execution-agent/actions/workflows/package.yml/badge.svg)](https://github.com/partarstu/ui-test-execution-agent/actions/workflows/package.yml)

Here the corresponding article on
Medium: [AI Agent That's Rethinking UI Test Automation](https://medium.com/@partarstu/meet-the-ai-agent-thats-rethinking-ui-test-automation-d8ef9742c6d5)

This agent can be a part of any distributed testing framework which uses A2A protocol for communication between agents. An example of
such a framework is [Agentic QA Framework](https://github.com/partarstu/agentic-qa-framework). This agent has been tested as
a part of this framework for executing a sample test case inside Google Cloud.

## Key Features

* **Modular Agent Architecture:**
    * The agent itself is built around a modular sub-agent architecture with specialized AI sub-agents:
        * **[PreconditionActionAgent](src/main/java/org/tarik/ta/agents/PreconditionActionAgent.java):** Handles the execution of
          precondition actions before test case execution.
        * **[PreconditionVerificationAgent](src/main/java/org/tarik/ta/agents/PreconditionVerificationAgent.java):** Verifies that
          preconditions are fully met.
        * **[TestStepActionAgent](src/main/java/org/tarik/ta/agents/TestStepActionAgent.java):** Executes individual test step actions.
        * **[TestStepVerificationAgent](src/main/java/org/tarik/ta/agents/TestStepVerificationAgent.java):** Verifies the expected results
          after each test step.
        * **[TestCaseExtractionAgent](src/main/java/org/tarik/ta/agents/TestCaseExtractionAgent.java):** Extracts and parses test case
          from received task content.
        * **[ElementBoundingBoxAgent](src/main/java/org/tarik/ta/agents/ElementBoundingBoxAgent.java):** Identifies UI element bounding
          boxes on screen (visual grounding).
        * **[ElementSelectionAgent](src/main/java/org/tarik/ta/agents/ElementSelectionAgent.java):** Selects the best and correct
          element from multiple candidates (visual grounding).
        * **[UiElementDescriptionAgent](src/main/java/org/tarik/ta/agents/UiElementDescriptionAgent.java):** Generates new UI element info
          suggestions in order accelerate the execution in attended mode.
        * **[UiStateCheckAgent](src/main/java/org/tarik/ta/agents/UiStateCheckAgent.java):** Checks the current state of the UI against
          an expected one.
        * **[UiElementFromCandidatesSelectionAgent](src/main/java/org/tarik/ta/agents/UiElementFromCandidatesSelectionAgent.java):** When multiple UI 
          elements in the database match the description (same or similar names), this agent analyzes the current screenshot and selects the best
          matching element based on all element information (name, description, location details/parent context, and parent element info).
        * **[PageDescriptionAgent](src/main/java/org/tarik/ta/agents/PageDescriptionAgent.java):** Describes the current page context. This can be
          used for various purposes such as understanding the current UI state.
    * Each agent can be independently configured with its own AI model (name and provider) and system prompt version via
      `config.properties`.

* **Budget Management:**
    * The [BudgetManager](../agent_core/src/main/java/org/tarik/ta/core/manager/BudgetManager.java) provides comprehensive execution
      control:
        * **Time Budget:** Configurable maximum execution time for the test case execution(`agent.execution.time.budget.seconds`).
        * **Token Budget:** Limits total token consumption across all models during test case execution (`agent.token.budget`).
        * **Tool Call Budget:** Limits max tool calls for each agent (`agent.tool.calls.budget`).
        * Tracks token usage per model (input, output, cached, total).
        * Automatically interrupts execution in unattended mode if budget is exceeded.

* **Enhanced Error Handling:**
    * Structured error handling with [ErrorCategory](../agent_core/src/main/java/org/tarik/ta/core/error/ErrorCategory.java) enum:
        * `TERMINATION_BY_USER`: User-initiated interruption (no retry).
        * `VERIFICATION_FAILED`: Verification failures (retryable).
        * `TRANSIENT_TOOL_ERROR`: Temporary failures like network issues (retryable).
        * `NON_RETRYABLE_ERROR`: Fatal errors (no retry).
        * `TIMEOUT`: Execution timeouts (bounded retry if budget allows).
    * [RetryPolicy](../agent_core/src/main/java/org/tarik/ta/core/error/RetryPolicy.java) for configurable retry behavior:
        * Maximum retries, delay between retries, and total timeout.
    * [DefaultToolErrorHandler](../agent_core/src/main/java/org/tarik/ta/core/model/DefaultToolErrorHandler.java) provides centralized
      error handling with retry logic.
    * [RetryState](../agent_core/src/main/java/org/tarik/ta/core/error/RetryState.java) for tracking retry attempts and elapsed time.

* **Element Location Prefetching:**
    * Configurable UI element location prefetching (`prefetching.enabled`) for improved performance in unattended mode.
    * When enabled, the UI element from the next test step (if applicable) will be located on the screen without waiting for the test
      step verification of the previous step to complete. This allows to reduce test execution time, especially if the used LLM is slow
      in visual grounding tasks.

* **Screen Video Recording:**
    * Built-in screen video recording capability for debugging and documentation:
        * `screen.recording.active`: Enable/disable recording.
        * `screen.recording.output.dir`: Output directory for recordings.
        * `recording.bit.rate`: Video bitrate configuration.
        * `recording.file.format`: Output format (default: mp4).
        * `recording.fps`: Frames per second for recording.

* **AI Model Integration:**
    * Utilizes the [LangChain4j](https://github.com/langchain4j/langchain4j) library to seamlessly interact with various Generative AI
      models.
    * Supports all major LLMs, provides explicit configuration for models from Google (via AI Studio or Vertex AI), Azure OpenAI,
      Groq and Anthropic. Configuration is managed through `config.properties` and `AgentConfig.java`, allowing specification of providers,
      model names, API keys/tokens, endpoints, and generation parameters (temperature, topP, max output tokens, retries).
    * Each specialized agent can use a different AI model, configured independently:
        * Model name: `<agent>.model.name` (e.g., `precondition.agent.model.name`)
        * Model provider: `<agent>.model.provider` (e.g., `precondition.agent.model.provider`)
        * Prompt version: `<agent>.prompt.version` (e.g., `precondition.agent.prompt.version`)
    * Uses structured prompts stored in versioned directories under `src/main/resources/prompt_templates/system/agents/` and
      `../agent_core/src/main/resources/prompt_templates/system/agents/`.
    * Includes options for model logging (`model.logging.enabled`) and outputting the model's thinking process (`thinking.output.enabled`).

* **RAG:**
    * Employs a Retrieval-Augmented Generation (RAG) approach to manage information about UI elements.
    * Uses a vector database to store and retrieve UI element details (name, element description, location description, parent element description,
      and screenshot). It currently supports only Chroma DB, configured via `vector.db.url` in `config.properties`.
    * RAG components are located in the UI module: [RetrieverFactory](src/main/java/org/tarik/ta/rag/RetrieverFactory.java),
      [ChromaRetriever](src/main/java/org/tarik/ta/rag/ChromaRetriever.java), and [UiElementRetriever](src/main/java/org/tarik/ta/rag/UiElementRetriever.java).
    * Stores UI element information as `UiElement` records, which include a name, self-description, description of surrounding
      elements (anchors), a parent element description, and a screenshot (`UiElement.Screenshot`).
    * Retrieves the top N (`retriever.top.n` in config) most relevant UI elements based on semantic similarity between the query (derived
      from the test step action) and based on the stored element names. Minimum similarity scores (`element.retrieval.min.target.score`,
      `element.retrieval.min.general.score`, `element.retrieval.min.page.relevance.score` in config) are used to filter results for target
      element identification and potential refinement suggestions.

* **Computer Vision:**
    * Employs a hybrid approach combining large vision models with traditional computer vision algorithms (OpenCV's ORB and Template
      Matching) for robust UI element location.
    * Leverages a vision-capable AI model to:
        * Identify potential bounding boxes for UI elements on the screen.
        * Disambiguate when multiple visual matches are found or to confirm that a single visual match, if found, corresponds to the target
          element's description and surrounding element information.
    * Uses OpenCV (via `org.bytedeco.opencv`) for visual pattern matching (ORB and Template Matching) to find occurrences of an element's
      stored screenshot on the current screen.
    * Intelligent logic in `ElementLocator` combines results from the vision model and algorithmic matching, considering intersections and
      relevance, to determine the best match.
    * Configurable zoom scaling for element location (`element.locator.zoom.scale.factor`) in case the LLM can't efficiently work with
      high resolutions or the focus on a specific part of the screen is needed in order to avoid too much surrounding noise.
    * Algorithmic search can be enabled/disabled (`element.locator.algorithmic.search.enabled`).
    * Screenshot size conversion logic in case the LLM requires specific dimensions or size (e.g. Claude Sonnet 4.5):
        * `bbox.screenshot.longest.allowed.dimension.pixels`: Maximum dimension for screenshots.
        * `bbox.screenshot.max.size.megapixels`: Maximum screenshot size in megapixels.

* **GUI Interaction Tools:**
    * Provides a set of [tools](src/main/java/org/tarik/ta/tools) for interacting with the GUI using Java's `Robot` class.
    * [MouseTools](src/main/java/org/tarik/ta/tools/MouseTools.java) offer actions for working with the mouse (clicks, hover,
      click-and-drag, etc.).
    * [KeyboardTools](src/main/java/org/tarik/ta/tools/KeyboardTools.java) provide actions for working with the keyboard (typing text into
      specific elements, clearing data from input fields, pressing single keys or key combinations, etc.).
    * [CommonTools](src/main/java/org/tarik/ta/tools/CommonTools.java) include common actions like waiting for a specified duration and
      opening the Chrome browser.
    * [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java ) provides the whole logic for locating a specific
      UI element on the screen based on its description.
    * [UserInteractionTools](src/main/java/org/tarik/ta/tools/UserInteractionTools.java) facilitates user interactions via dialogs for 
      element creation, refinement, and verification. **Note:** These tools are only available when running in attended or semi-attended modes 
      (`execution.mode=ATTENDED` or `execution.mode=SEMI_ATTENDED`).

* **Execution Modes:**
    * Supports three execution modes controlled by the `execution.mode` property in `config.properties`.
    * **Attended ("Trainee") Mode (`execution.mode=ATTENDED`):** Designed for initial test case runs or when execution in unattended mode
      fails for debugging/fixing purposes. In this mode the agent behaves as a trainee, who needs assistance from the human tutor/mentor
      in order to identify all the information which is required for the unattended (without supervision) execution of the test case. Key features:
        * Agent asks for confirmation after locating elements.
        * User can create new elements or refine existing ones.
        * User can manually select the next action at any point.
        * **Verification Failure Notification:** When a verification fails, the user is notified with details about the failure and the retry timeout, and can choose to continue or terminate.
        * Tool call limits are significantly relaxed.
    * **Semi-Attended Mode (`execution.mode=SEMI_ATTENDED`):** The agent operates autonomously but allows the operator to intervene.
        * **Countdown Halt:** Displays a countdown popup (configurable duration) after test step actions, allowing the operator to click "Halt".
        * **Verification Failure Notification:** When a verification fails, the operator is notified with details about the failure and the retry timeout. The operator can choose to:
            * **OK:** Continue execution and let the system retry the verification within the configured timeout.
            * **Terminate:** Immediately stop the test execution.
        * **Element Selection Confirmation:** Displays a popup with a countdown when an element is automatically selected. The operator can see the selected element and intended action, and choose to "Proceed" (default), "Create new element", or take "Other action" (prompting the agent).
        * **Operator Intervention:** On halt, error, or verification failure, the operator can choose the next action (Retry, Refine, Terminate, etc.).
        * Suitable for monitoring execution without constant clicking, while retaining control to fix issues on the fly.
    * **Unattended Mode (`execution.mode=UNATTENDED`):** The agent executes the test case without any human assistance. It relies entirely on the
        information stored in the RAG database and the AI models' ability to interpret instructions and locate elements based on stored data.
        Errors during element location or verification will cause the execution to fail. This mode is suitable for integration into CI/CD
        pipelines. Budget checks are automatically enforced in this mode.

* **Server mode:**
    * The [Server](src/main/java/org/tarik/ta/Server.java) class extends [AbstractServer](../agent_core/src/main/java/org/tarik/ta/core/AbstractServer.java)
      and is the entry point where a Javalin web server is started.
      The agent registers its capabilities and listens for A2A JSON-RPC requests on the root endpoint (`/`) (port configured via `port`
      in `config.properties`). The server accepts only one test case execution at a time (the agent has been designed as a static utility
      for simplicity purposes). Upon receiving a valid request when idle, it returns `200 OK` and starts the test case execution. If busy,
      it returns `429 Too Many Requests`.

## Test Case Execution Workflow

The test execution process, orchestrated by the [UiTestAgent](src/main/java/org/tarik/ta/UiTestAgent.java) class, follows these steps:

1. **Test Case Processing:** The agent parses the received message (task) using [TestCaseExtractor](../agent_core/src/main/java/org/tarik/ta/core/utils/TestCaseExtractor.java),
   extracts the required information and converts it into a test case object. This file contains the overall test case name, optional
   `preconditions` (natural language description of the required state before execution), and a list of `TestStep`s. Each `TestStep`
   includes a `stepDescription` (natural language instruction), optional `testData` (inputs for the step), and `expectedResults`
   (natural language description of the expected state after the step).
2. **Precondition Execution and Verification:** If preconditions are defined, the agent executes and verifies them against the current UI
   state using a vision model. If preconditions are not met, the test case execution fails.
3. **Step Iteration:** The agent iterates through each `TestStep` sequentially, executing each test step.
4. **Test Step Action:**
    * **Element Location (if required by the tool):** If the requested tool needs to interact with a specific UI element (e.g., clicking an
      element), the element is located using the [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java) class
      based on the element's description (provided as a parameter for the tool). (See "UI Element Location Workflow" below for details).
    * **Tool Execution:** The appropriate sequence of tools with the arguments provided by the agent is invoked.
    * **Retry/Rerun Logic:** If a tool execution reports that retrying makes sense (e.g., an element was not found on the screen), the
      agent retries the execution after a short delay, up to a configured timeout (`test.step.execution.retry.timeout.millis`). If the
      error persists after the deadline, the test case execution is marked as `ERROR`.
5. **Test Step Expected Results Verification:**
    * **Delay:** A short delay (`action.verification.delay.millis`) is introduced to allow the UI state to change after the preceding
      action.
    * **Screenshot:** A screenshot of the current screen is taken.
    * **Vision Model Interaction:** A verification prompt containing the expected results description and the current screenshot is sent to
      the configured vision AI model. The model analyzes the screenshot and compares it against the expected results description.
    * **Result Parsing:** The model's response contains information indicating whether the verification passed, and extended information
      with the justification for the result.
    * **Retry Logic:** If the verification fails, the agent retries the verification process after a short interval (
      `test.step.execution.retry.interval.millis`) until a timeout (`verification.retry.timeout.millis`) is reached. If it still fails after
      the deadline, the test case execution is marked as `FAILED`.
6. **Completion/Termination:** Execution continues until all steps are processed successfully or an interruption (error, verification
   failure, user termination) occurs. The final `TestExecutionResult` (including `TestExecutionStatus` and detailed `TestStepResult` for
   each step) is returned.

### UI Element Location Workflow

The [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java ) class is responsible for finding the coordinates
of a target UI element based on its natural language description provided by the instruction model during an action step. This involves a
combination of RAG, computer vision, analysis, and potentially user interaction (if run in attended mode):

1. **RAG Retrieval:** The provided UI element's description is used to query the vector database, where the top N (`retriever.top.n`) most
   semantically similar `UiElement` records are retrieved based on their stored names, using embeddings generated by
   the `all-MiniLM-L6-v2` model. Results are filtered based on configured minimum similarity scores (`element.retrieval.min.target.score`
   for high confidence, `element.retrieval.min.general.score` for potential matches) and `element.retrieval.min.page.relevance.score` for
   relevance to the current page.
2. **Handling Retrieval Results:**
    * **High-Confidence Match(es) Found:** If one or more elements exceed the `MIN_TARGET_RETRIEVAL_SCORE` and/or
      `MIN_PAGE_RELEVANCE_SCORE`:
        * **Hybrid Visual Matching:**
            * A vision model is used to identify potential bounding boxes for UI elements that visually resemble the target element on the
              current screen.
            * Concurrently, OpenCV's ORB and Template Matching algorithms are used to find additional visual matches of the element's stored
              screenshot on the current screen.
            * The results from both the vision model and algorithmic matching are combined and analyzed to find common or best-fitting
              bounding boxes.
        * **Disambiguation (if needed):** If multiple candidate bounding boxes are found, the vision model is employed to select the single
          best match that corresponds to the target element's description and the description of surrounding elements (anchors), based on a
          screenshot showing all candidate bounding boxes highlighted with specific color and having unique ID labels.
    * **Low-Confidence/No Match(es) Found:** If no elements meet the `MIN_TARGET_RETRIEVAL_SCORE` or `MIN_PAGE_RELEVANCE_SCORE`, but some
      meet the
      `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Attended Mode:** The agent displays a popup showing a list of the low-scoring potential UI element candidates. The user can
          choose to:
            * **Update** one of the candidates by refining its name, description, anchors, or parent element info and save the updated information to the vector DB.
            * **Delete** a deprecated element from the vector DB.
            * **Create New Element** (see below).
            * **Retry Search** (useful if elements were manually updated).
            * **Terminate** the test execution (e.g., due to an AUT bug).
        * **Unattended Mode:** The location process fails.
    * **No Matches Found:** If no elements meet even the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Attended Mode:** The user is guided through the new element creation flow:
            1. The user draws a bounding box around the target element on a full-screen capture.
            2. The captured element screenshot with its description are sent to the vision model to generate a suggested detailed name,
               self-description, surrounding elements (anchors) description, and parent element info.
            3. The user reviews and confirms/edits the information suggested by the model.
            4. The new `UiElement` record (with UUID, name, descriptions, parent element info, screenshot) is stored into the vector DB.
        * **Unattended Mode:** The location process fails.

## Setup Instructions

### Prerequisites

* Java Development Kit (JDK) - Version 25 or later recommended.
* Apache Maven - For building the project.
* Chroma vector database (the only one supported for now).
* Subscription to an AI model provider (Google Cloud/AI Studio, Azure OpenAI, or Groq).

### Maven Setup

This project uses Maven for dependency management and building.

1. **Clone the Repository:**
   ```bash
   git clone <repository_url>
   cd <project_directory>
   ```

2. **Build the Project:**
   ```bash
   mvn clean package
   ```
   This command downloads dependencies, compiles the code, runs tests (if any), and packages the application into a standalone JAR file in
   the `target/` directory.

### Vector DB Setup

Instructions for setting up the currently only one supported vector database Chroma DB could be found on its official website.

### Configuration

Configure the agent by editing the [config.properties](src/main/resources/config.properties) file or by setting environment variables. *
*Environment variables
override properties file settings.**

**Key Configuration Properties:**

**Basic Agent Configuration:**

* `execution.mode` (Env: `EXECUTION_MODE`): Mode of execution (`ATTENDED`, `SEMI_ATTENDED`, `UNATTENDED`). Default: `UNATTENDED`.
* `semi.attended.countdown.seconds` (Env: `SEMI_ATTENDED_COUNTDOWN_SECONDS`): Duration in seconds for the countdown popup in semi-attended mode. Default: `5`.
* `debug.mode` (Env: `DEBUG_MODE`): `true` enables debug mode, which saves intermediate screenshots (e.g., with bounding boxes drawn)
  during element location for debugging purposes. `false` disables this. Default: `false`.
* `port` (Env: `PORT`): Port for the server mode. Default: `8005`.
* `host` (Env: `AGENT_HOST`): Host address for the server mode. Default: `localhost`.

**RAG Configuration:**

* `vector.db.provider` (Env: `VECTOR_DB_PROVIDER`): Vector database provider. Default: `chroma`.
* `vector.db.url` (Env: `VECTOR_DB_URL`): Required URL for the vector database connection. Default: `http://localhost:8020`.
* `retriever.top.n` (Env: `RETRIEVER_TOP_N`): Number of top similar elements to retrieve from the vector DB based on semantic element name
  similarity. Default: `5`.

**Model Configuration:**

* `model.max.output.tokens` (Env: `MAX_OUTPUT_TOKENS`): Maximum amount of tokens for model responses. Default: `8192`.
* `model.temperature` (Env: `TEMPERATURE`): Sampling temperature for model responses. Default: `0.0`.
* `model.top.p` (Env: `TOP_P`): Top-P sampling parameter. Default: `1.0`.
* `model.max.retries` (Env: `MAX_RETRIES`): Max retries for model API calls. Default: `10`.
* `model.logging.enabled` (Env: `LOG_MODEL_OUTPUT`): Enable/disable model logging. Default: `false`.
* `thinking.output.enabled` (Env: `OUTPUT_THINKING`): Enable/disable thinking process output. Default: `false`.
* `gemini.thinking.budget` (Env: `GEMINI_THINKING_BUDGET`): Budget for Gemini thinking process. Default: `0`.
* `verification.model.max.retries` (Env: `VERIFICATION_MODEL_MAX_RETRIES`): Retries for verification models. Default: `3`.

**Google API Configuration:**

* `google.api.provider` (Env: `GOOGLE_API_PROVIDER`): Google API provider (`studio_ai` or `vertex_ai`). Default: `studio_ai`.
* `google.api.token` (Env: `GOOGLE_API_KEY`): API Key for Google AI Studio. Required if using AI Studio.
* `google.project` (Env: `GOOGLE_PROJECT`): Google Cloud Project ID. Required if using Vertex AI.
* `google.location` (Env: `GOOGLE_LOCATION`): Google Cloud location (region). Required if using Vertex AI.

**Azure OpenAI API Configuration:**

* `azure.openai.api.key` (Env: `OPENAI_API_KEY`): API Key for Azure OpenAI. Required if using OpenAI.
* `azure.openai.endpoint` (Env: `OPENAI_API_ENDPOINT`): Endpoint URL for Azure OpenAI. Required if using OpenAI.

**Groq API Configuration:**

* `groq.api.key` (Env: `GROQ_API_KEY`): API Key for Groq. Required if using Groq.
* `groq.endpoint` (Env: `GROQ_ENDPOINT`): Endpoint URL for Groq. Required if using Groq.

**Anthropic API Configuration:**

* `anthropic.api.provider` (Env: `ANTHROPIC_API_PROVIDER`): Anthropic API provider (`anthropic_api` or `vertex_ai`). Default:
  `anthropic_api`.
* `anthropic.api.key` (Env: `ANTHROPIC_API_KEY`): API Key for Anthropic. Required if using Anthropic.
* `anthropic.endpoint` (Env: `ANTHROPIC_ENDPOINT`): Endpoint URL for Anthropic. Default: `https://api.anthropic.com/v1/`.

**Timeout and Retry Configuration:**

* `test.step.execution.retry.timeout.millis` (Env: `TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed test case
  actions. Default: `5000 ms`.
* `test.step.execution.retry.interval.millis` (Env: `TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS`): Delay between test case action retries.
  Default: `1000 ms`.
* `verification.retry.timeout.millis` (Env: `VERIFICATION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed verifications. Default:
  `10000 ms`.
* `action.verification.delay.millis` (Env: `ACTION_VERIFICATION_DELAY_MILLIS`): Delay after executing a test case action before performing
  the corresponding verification. Default: `500 ms`.
* `max.action.execution.duration.millis` (Env: `MAX_ACTION_EXECUTION_DURATION_MILLIS`): Maximum duration for a single action execution.
  Default: `30000 ms`.

**Budget Management Configuration:**

* `agent.token.budget` (Env: `AGENT_TOKEN_BUDGET`): Maximum total tokens that can be consumed across all models. Default: `1000000`.

* `agent.tool.calls.budget` (Env: `AGENT_TOOL_CALLS_BUDGET`): Maximum tool calls per agent. Default: `10`.
* `agent.execution.time.budget.seconds` (Env: `AGENT_EXECUTION_TIME_BUDGET_SECONDS`): Maximum execution time in seconds. Default: `3000`.

**Screen Recording Configuration:**

* `screen.recording.active` (Env: `SCREEN_RECORDING_ENABLED`): Enable/disable screen recording. Default: `false`.
* `screen.recording.output.dir` (Env: `SCREEN_RECORDING_FOLDER`): Output directory for recordings. Default: `./videos`.
* `recording.bit.rate` (Env: `VIDEO_BITRATE`): Video bitrate. Default: `2000000`.
* `recording.file.format` (Env: `SCREEN_RECORDING_FORMAT`): Recording file format. Default: `mp4`.
* `recording.fps` (Env: `SCREEN_RECORDING_FRAME_RATE`): Frames per second for recording. Default: `10`.

**Prefetching Configuration:**

* `prefetching.enabled` (Env: `PREFETCHING_ENABLED`): Enable/disable element location prefetching in unattended mode. Default: `false`.

**Element Location Configuration:**

* `element.bounding.box.color` (Env: `BOUNDING_BOX_COLOR`): Required color name (e.g., `green`) for the bounding box drawn during element
  capture in attended mode. This value should be tuned so that the color contrasts as much as possible with the average UI element color.
* `element.retrieval.min.target.score` (Env: `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score are treated as target element candidates. Default: `0.8`.
* `element.retrieval.min.general.score` (Env: `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval for potential matches. Default: `0.5`.
* `element.retrieval.min.page.relevance.score` (Env: `ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE`): Minimum page relevance score for vector
  DB UI element retrieval. Default: `0.5`.
* `element.locator.visual.similarity.threshold` (Env: `VISUAL_SIMILARITY_THRESHOLD`): OpenCV template matching threshold. Default: `0.8`.
* `element.locator.top.visual.matches` (Env: `TOP_VISUAL_MATCHES_TO_FIND`): Maximum number of visual matches to pass to the AI model.
  Default: `6`.
* `element.locator.found.matches.dimension.deviation.ratio` (Env: `FOUND_MATCHES_DIMENSION_DEVIATION_RATIO`): Maximum allowed deviation
  ratio for the dimensions of a found visual match. Default: `0.3`.
* `element.locator.visual.grounding.model.vote.count` (Env: `VISUAL_GROUNDING_MODEL_VOTE_COUNT`): Number of visual grounding votes. Default:
  `1`.
* `element.locator.validation.model.vote.count` (Env: `VALIDATION_MODEL_VOTE_COUNT`): Number of validation model votes. Default: `1`.
* `element.locator.bbox.clustering.min.intersection.ratio` (Env: `BBOX_CLUSTERING_MIN_INTERSECTION_RATIO`): Minimum IoU ratio for
  clustering bounding boxes. Default: `0.9`.
* `element.locator.zoom.scale.factor` (Env: `ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR`): Zoom scale factor for element location. Default: `1`.
* `element.locator.algorithmic.search.enabled` (Env: `ALGORITHMIC_SEARCH_ENABLED`): Enable/disable OpenCV algorithmic search. Default:
  `false`.
* `element.locator.skip.model.selection.vision.only` (Env: `SKIP_UI_ELEMENT_SELECTION_FOR_VISION`): When enabled, skip the model 
  selection step when only visual grounding results are available (no algorithmic matches). In this case, the first identified element 
  from the visual grounding results is returned directly without additional model validation. This can speed up element location when 
  algorithmic search is disabled. Default: `false`.
* `bounding.box.already.normalized` (Env: `BOUNDING_BOX_ALREADY_NORMALIZED`): Whether bounding boxes are pre-normalized. Default: `false`.
* `bbox.screenshot.longest.allowed.dimension.pixels` (Env: `BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS`): Maximum screenshot
  dimension. Default: `1568`.
* `bbox.screenshot.max.size.megapixels` (Env: `BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS`): Maximum screenshot size in megapixels. Default:
  `1.15`.

**Agent-Specific Model Configuration:**

Each specialized agent can be configured with its own model and prompt version using the following pattern:

* `<agent>.model.name`: Model name for the agent
* `<agent>.model.provider`: Model provider (`google`, `openai`, `groq`, or `anthropic`)
* `<agent>.prompt.version`: System prompt version

Available agents and their configuration prefixes:

* `precondition.agent.*`: Precondition Action Agent
* `precondition.verification.agent.*`: Precondition Verification Agent
* `test.step.action.agent.*`: Test Step Action Agent
* `test.step.verification.agent.*`: Test Step Verification Agent
* `test.case.extraction.agent.*`: Test Case Extraction Agent
* `ui.element.description.agent.*`: UI Element Description Agent
* `ui.state.check.agent.*`: UI State Check Agent
* `element.bounding.box.agent.*`: Element Bounding Box Agent
* `element.selection.agent.*`: Element Selection Agent
* `element.candidate.selection.agent.*`: Element Candidate Selection Agent (uses same model as Element Selection Agent)
* `page.description.agent.*`: Page Description Agent

**Example agent configuration:**

```properties
precondition.agent.model.name=gemini-3-flash-preview
precondition.agent.model.provider=google
precondition.agent.prompt.version=v1.0.0
```

**User UI Dialog Settings:**

* `dialog.default.horizontal.gap`, `dialog.default.vertical.gap`, `dialog.default.font.type`,
  `dialog.user.interaction.check.interval.millis`, `dialog.default.font.size`, `dialog.hover.as.click`: Cosmetic and timing settings for
  interactive dialogs.

## How to Run

1. Ensure the project is built.
2. Run the `Server` class using Maven Exec Plugin:
   ```bash
   mvn exec:java -Dexec.mainClass="org.tarik.ta.Server"
   ```
   Or run the packaged JAR:
   ```bash
   java -jar target/<your-jar-name.jar>
   ```
3. The server will start listening on the configured port (default `8005`).
4. Send a `POST` request to the root endpoint (`/`) with the correct A2A message.
5. The server will respond with execution results after it's done processing if it accepts the request (i.e., not already running a
   test case) or with `429 Too Many Requests` if it's busy. The test case execution synchronously.

## Deployment

This section provides detailed instructions for deploying the UI Test Execution Agent, both to Google Cloud Platform (GCP) and locally
using Docker.

### Cloud Deployment (Google Compute Engine)

The agent can be deployed as a containerized application on a Google Compute Engine (GCE) virtual machine, providing a robust and scalable
environment for automated UI testing. Because the agent needs at least 2 ports to be exposed (one for communicating with other agents
and one for noVNC connection), using Google Cloud Run as a financially more efficient alternative is not possible. However, using Spot
VMs is also a formidable option.

#### Prerequisites for Cloud Deployment

* **Google Cloud Project:** An active GCP project with billing enabled.
* **gcloud CLI:** The Google Cloud SDK `gcloud` command-line tool installed and configured.
* **Secrets in Google Secret Manager:** The following secrets must be created in Google Secret Manager within your GCP project. These are
  crucial for the agent's operation and should be stored securely. The list of secrets depends heavily on the provider of the models
  which are used for analyzing execution instructions and for performing visual tasks. The exemplary list is valid for using Groq as the
  platform.
    * `GROQ_API_KEY`: Your API key for Groq platform.
    * `GROQ_ENDPOINT`: The endpoint URL for Groq platform.
    * `VECTOR_DB_URL`: The URL of your vector DB instance (see deployment instructions below).
    * `VNC_PW`: The password for accessing the noVNC session using browser.

  You can create these secrets using GCP Console.

#### Deploying Chroma DB (Vector Database)

The agent relies on a vector database, Chroma DB is currently the only supported option. You can deploy Chroma DB to Google Cloud Run
using the provided `cloudbuild_chroma.yaml` configuration.

1. **Configure `cloudbuild_chroma.yaml`:**
    * Update `_CHROMA_BUCKET` with the name of a Google Cloud Storage bucket where Chroma DB will store its data.
    * Update `_CHROMA_DATA_PATH` if you want a specific path within the bucket.
    * Update `_PORT` if you want Chroma DB to run on a different port (default is `8000`).

2. **Deploy using Cloud Build:**
   ```bash
   gcloud builds submit . --config deployment/cloudbuild_chroma.yaml --substitutions=_CHROMA_BUCKET=<your-chroma-bucket-name>,_CHROMA_DATA_PATH=chroma,_PORT=8000 --project=<your-gcp-project-id>
   ```
   After deployment, note the URL of the deployed Chroma DB service; this will be your `VECTOR_DB_URL` which you need to set as a secret.

#### Building and Deploying the Agent on GCE

1. **Navigate to the project root:**
   ```bash
   cd <project_root_directory>
   ```

2. **Configure deployment substitutions:**

   The deployment is configured via Cloud Build substitutions in `deployment/cloud/cloudbuild.yaml`. This file contains all configurable 
   parameters as substitutions that can be overridden when running the build.

   **Key configuration categories:**
   
   * **GCP Configuration:** Region, zone, instance name, network settings, machine type
   * **Port Configuration:** noVNC, VNC, and agent server ports
   * **Application Settings:** VNC resolution, log level, unattended/debug mode
   * **Screenshot and Bounding Box Settings:** Image dimension limits and normalization settings
   * **Agent Model Configuration:** Model names, providers, and prompt versions for each agent
   * **API Endpoints:** Groq, Google Cloud location and project settings
   * **Additional GCP Configuration:** Firewall rules, disk settings, VM provisioning model, etc.
   * **Base Image configuration:** `_BUILD_BASE_IMAGE` (default `false`) controls whether to rebuild the base image or use the cached one.

   **Important notes:**
   * **Empty values use defaults:** If a substitution value is empty (e.g., `_ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME: ''`), 
     the application will use defaults from `config.properties`.
   * **Override substitutions:** Pass custom values when running `gcloud builds submit`.

3. **Deploy using Cloud Build:**
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml .
   ```
   
   To override specific substitutions:
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
     --substitutions=_MACHINE_TYPE=e2-standard-4,_LOG_LEVEL=DEBUG .
   ```

   The build will:
    * Build the Maven project.
    * Build or pull the Docker base image (conditional on `_BUILD_BASE_IMAGE`).
    * Build the Docker application image.
    * Push the Docker image to Google Container Registry.
    * Enable necessary GCP services.
    * Set up VPC network and firewall rules (if they don't exist).
    * Create a GCE Spot VM instance.
    * Start the agent container inside the created VM.

   If you want to use the agent as part of an already existing network (e.g., together
   with [Agentic QA Framework](https://github.com/partarstu/agentic-qa-framework)), you must carefully update the substitutions 
   in the YAML file to avoid destroying existing settings.

#### Accessing the Deployed Agent

* **Agent Server:** The agent will be running on the port configured by `AGENT_SERVER_PORT` (default `443`). The internal hostname can be
  retrieved by executing `curl "http://metadata.google.internal/computeMetadata/v1/instance/hostname" -H "Metadata-Flavor: Google"` inside
  the VM. This hostname can later be used for communication inside the network with other agents of the framework.
* **noVNC Access:** You can access the agent's desktop environment via noVNC in your web browser. The URL will be
  `https://<EXTERNAL_IP>:<NO_VNC_PORT>`, where `<EXTERNAL_IP>` is the external IP of your GCE instance and `<NO_VNC_PORT>` is the noVNC
  port (default `6901`). The VNC password is set via the `VNC_PW` secret. The SSL/TLS certificate is self-signed, so you'll have to
  confirm visiting the page for the first time.

### Local Docker Deployment

For local development and testing, you can run the agent within a Docker container on your machine.

#### Docker Image Architecture

The agent uses a two-layer Docker image architecture:

1. **Base Image (`ui-testing-agent-base`)**: Built from `deployment/Dockerfile.base`, provides:
   - Ubuntu 24.04 LTS
   - Xfce desktop environment
   - TigerVNC server
   - noVNC (web-based VNC access)
   - **Google Chrome Stable** (latest version)
   - Common utilities (wget, curl, git, zip, unzip, jq, etc.)

2. **Application Image (`ui-test-execution-agent`)**: Built from `deployment/local/Dockerfile`, adds:
   - Java 25 runtime
   - Agent application JAR
   - Application-specific configuration

#### Prerequisites for Local Docker Deployment

* **Docker Desktop:** Ensure Docker Desktop is installed and running on your system.

#### Building and Running the Docker Image

The `build_and_run_docker.bat` script (for Windows) simplifies the process of building the application and Docker image, and running the container.

1. **Adapt `deployment/local/Dockerfile`:**
    * **IMPORTANT:** Before running the script, open `deployment/local/Dockerfile` and replace the placeholder `VNC_PW` environment variable
      with a strong password of your choice. For example:
      ```dockerfile
      ENV VNC_PW="your_strong_vnc_password"
      ```
      (Note: The `build_and_run_docker.bat` script also sets `VNC_PW` to `123456` for convenience, but it's recommended to set it directly
      in the Dockerfile for consistency and security.)

2. **Execute the batch script:**
   ```bash
   deployment\local\build_and_run_docker.bat
   ```
   This script will:
    * Build the `ui_test_execution_agent` module (and its dependencies) using Maven.
    * Build the base Docker image `ui-testing-agent-base` from `deployment/Dockerfile.base` (Ubuntu 24.04 + VNC + Chrome).
    * Build the application Docker image `ui-test-execution-agent` using `deployment/local/Dockerfile`.
    * Stop and remove any existing container named `ui-agent`.
    * Run a new Docker container, mapping ports `5901` (VNC), `6901` (noVNC), and `8005` (agent server) to your local machine.

#### Accessing the Local Agent

* **VNC Client:** You can connect to the VNC session using a VNC client at `localhost:5901`.
* **noVNC (Web Browser):** Access the agent's desktop environment via your web browser at `http://localhost:6901/vnc.html`.
* **Agent Server:** The agent's server will be accessible at `http://localhost:8005`.

Remember to use the VNC password you set in the Dockerfile when prompted.


## TODOs

* ~~Add public method comments and unit tests.~~ (Partially completed - unit tests added for many components)
* Add public unit tests for at least 80% coverage.

## Final Notes

* **Project Scope:** This project is developed as a prototype of an agent, a minimum working example, and thus a basis for further
  extensions and enhancements. It's not a production-ready instance or a product developed according to all the requirements/standards
  of an SDLC (however many of them have been taken into account during development).
* **Modular Architecture:** The agent now uses a modular architecture with specialized AI agents (e.g., `UiPreconditionActionAgent`,
  `UiTestStepVerificationAgent`, `ElementBoundingBoxAgent`). Each agent can be independently configured with its own AI model and prompt
  version, allowing for fine-tuned performance optimization. The `GenericAiAgent<T>` interface provides common retry and execution logic.
  UI-specific configuration is managed by [UiTestAgentConfig](src/main/java/org/tarik/ta/UiTestAgentConfig.java).
* **Budget Management:** The `BudgetManager` provides guardrails for execution in unattended mode, preventing runaway costs by limiting
  time, tokens, and tool calls. This is particularly important for CI/CD integration.
* **Enhanced Error Handling:** The new `ErrorCategory` enum and `RetryPolicy` record provide structured error handling with configurable
  retry strategies, making the agent more robust and easier to debug.
* **Environment:** The agent has been manually tested on the Windows 11 platform. There are issues with OpenCV and OpenBLAS libraries
  running on Linux, but there is no solution to those issues yet.
* **Standalone Executable Size:** The standalone JAR file can be quite large (at least ~330 MB). This is primarily due to the automatic
  inclusion of the ONNX embedding model (`all-MiniLM-L6-v2`) as a dependency of LangChain4j, and the native OpenCV libraries required for
  visual element location.
* **Unit Tests:** The project now includes unit tests for many components including agents, DTOs, managers, and tools. All future
  contributions and pull requests to the `main` branch **should** include relevant unit tests. Contributing by adding new unit tests
  to existing code is, as always, welcome.