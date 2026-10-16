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
        * **[UiPreconditionActionAgent](src/main/java/org/tarik/ta/agents/UiPreconditionActionAgent.java):** Handles the execution of
          precondition actions before test case execution. Receives a screenshot of the current screen state to provide visual context for
          tool selection.
        * **[UiPreconditionVerificationAgent](src/main/java/org/tarik/ta/agents/UiPreconditionVerificationAgent.java):** Verifies that
          preconditions are fully met.
        * **[UiTestStepActionAgent](src/main/java/org/tarik/ta/agents/UiTestStepActionAgent.java):** Executes individual test step actions.
          Receives a screenshot of the current screen state to provide visual context for tool selection.
        * **[UiTestStepVerificationAgent](src/main/java/org/tarik/ta/agents/UiTestStepVerificationAgent.java):** Verifies the expected results
          after each test step.
        * **[TestCaseExtractionAgent](../agent_core/src/main/java/org/tarik/ta/core/agents/TestCaseExtractionAgent.java):** Extracts and parses test case
          from received task content.
        * **[UiElementGroundingAgent](src/main/java/org/tarik/ta/agents/UiElementGroundingAgent.java):** Locates a UI element on a
          screenshot (visual grounding), reporting it in the shape set by `element.locator.grounding.output.format`.
        * **[BestUiElementMatchSelectionAgent](src/main/java/org/tarik/ta/agents/BestUiElementMatchSelectionAgent.java):** Selects the best and correct
          element from multiple candidates (visual grounding).
        * **[UiElementDescriptionExtractionAgent](src/main/java/org/tarik/ta/agents/UiElementDescriptionExtractionAgent.java):** Extracts
          the UI element description from a test action/procedure description.
        * **[UiStateCheckAgent](src/main/java/org/tarik/ta/agents/UiStateCheckAgent.java):** Checks the current state of the UI against
          an expected one.
        * **[DbUiElementSelectionAgent](src/main/java/org/tarik/ta/agents/DbUiElementSelectionAgent.java):** When
          multiple UI elements in the database match the description (same or similar names), this agent analyzes the current screenshot and selects the
          best matching element based on all element information (name, description, location details/parent context, and parent element info).
        * **[UiElementExtendedDescriptionAgent](src/main/java/org/tarik/ta/agents/UiElementExtendedDescriptionAgent.java):** Generates
          extended descriptions for UI elements based on screenshots and initial descriptions.
        * **[UiElementResolutionAgent](src/main/java/org/tarik/ta/agents/UiElementResolutionAgent.java):** One-shot sub-agent that
          resolves (locates and, if needed, creates in DB) a UI element for an atomic collecting-knowledge procedure.
        * **[KnowledgeSuggestionAgent](src/main/java/org/tarik/ta/agents/KnowledgeSuggestionAgent.java):** AI agent that suggests
          prerequisites, effects, and child steps for procedures. When creating a new procedure, it receives the test case context and
          accumulated execution effects to ground its suggestions in the current state. When editing an existing procedure, it receives the parent chain, the
          procedure's established prerequisites (which must be reused), and the test case context.
    * Each agent can be independently configured with its own AI model (name and provider) and system prompt version via
      `config.properties`.

* **Budget Management:**
    * The [BudgetManager](../agent_core/src/main/java/org/tarik/ta/core/manager/BudgetManager.java) provides comprehensive execution
      control:
        * **Time Budget:** Configurable maximum execution time for the test case execution(`agent.execution.time.budget.seconds`).
        * **Token Budget:** Limits total token consumption across all models during test case execution (`agent.token.budget`).
        * **Tool Call Budget:** Limits max tool calls per agent in unattended mode (`agent.tool.calls.budget.unattended`).
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

* **HDR Screenshot Correction:**
    * Optional sRGB gamma correction for screenshots captured on HDR-enabled monitors.
    * Controlled by `hdr.color.correction.enabled` or `HDR_COLOR_CORRECTION_ENABLED`.
    * Keeps Windows HDR enabled while avoiding washed-out screenshot colors in model inputs and saved captures.

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
    * Uses structured prompts stored as Markdown files in versioned directories under `src/main/resources/prompt_templates/system/agents/` and
      `../agent_core/src/main/resources/prompt_templates/system/agents/`.
    * A prompt line of the form `{{> <agent path>/<version>/<file name>}}` is replaced with the content of that file (relative to `prompt_templates/system/agents/`, resolved recursively; an include cycle fails the load with the chain of files that forms it) when the prompt is loaded, so text shared by several prompts has a single source. The precondition and test step action agents take their shared rules from `common/executor/v1.0.0/` this way, and the action and verification agents take the rules for the test execution context data from `common/execution_context/v1.1.0/`, which agent_core ships on the classpath of both agents.
    * Includes options for model logging (`model.logging.enabled`) and outputting the model's thinking process (`thinking.output.enabled`).

* **RAG:**
    * Employs a Retrieval-Augmented Generation (RAG) approach to manage information about UI elements.
    * Uses Neo4j (through the Neo4j Java driver) both as a graph store and as a vector database to store and retrieve UI element
      details (name, element description, location description, parent element description, and screenshot), configured via
      `vector.db.provider` and `vector.db.url` in `config.properties`. `RagDbProvider` still defines `CHROMA`/`QDRANT` values, but only
      `NEO4J` is wired up in this module.
    * RAG/vector-search components live under [knowledge_graph](src/main/java/org/tarik/ta/knowledge_graph):
      [UiElementRepository](src/main/java/org/tarik/ta/knowledge_graph/repository/UiElementRepository.java) (vector search over stored
      elements) and [EmbeddingService](src/main/java/org/tarik/ta/knowledge_graph/service/EmbeddingService.java) (embedding generation).
      See the "Knowledge Persistence (Neo4j)" feature below for the full procedure/element graph model.
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
    * Intelligent logic in `ElementLocatorTools` combines results from the vision model and algorithmic matching, considering intersections
      and relevance, to determine the best match.
    * Configurable zoom scaling for element location (`element.locator.zoom.scale.factor`) in case the LLM can't efficiently work with
      high resolutions or the focus on a specific part of the screen is needed in order to avoid too much surrounding noise.
    * Algorithmic search can be enabled/disabled (`element.locator.algorithmic.search.enabled`).
    * Screenshot size conversion logic in case the LLM requires specific dimensions or size (e.g. Claude Sonnet 4.5):
        * `bbox.screenshot.longest.allowed.dimension.pixels`: Maximum dimension for screenshots.
        * `bbox.screenshot.max.size.megapixels`: Maximum screenshot size in megapixels.

* **GUI Interaction Tools:**
    * Provides a set of [tools](src/main/java/org/tarik/ta/tools) for interacting with the GUI using Java's `Robot` class.
    * [MouseTools](src/main/java/org/tarik/ta/tools/MouseTools.java) offer actions for working with the mouse (clicks, hover, click-and-drag, clicking until a state is reached, and scrolling the mouse wheel up or down at the current mouse position until a state is reached, which needs no UI element and no coordinates). Both state-driven tools return a confirmation naming the reached state, so the model knows the action is complete instead of repeating it. Every coordinate and drag offset is an integer in `[0, 1000]` (offsets in `[-1000, 1000]`), normalized to the screen width and height, which is the format the Holo, Qwen and Gemini models are trained on; the tools convert it to logical screen coordinates and reject values outside that range, e.g. screen pixels, with an error the model can correct.
    * [KeyboardTools](src/main/java/org/tarik/ta/tools/KeyboardTools.java) provide actions for working with the keyboard (typing text into
      specific elements, clearing data from input fields, pressing single keys or key combinations, etc.).
    * [CommonTools](src/main/java/org/tarik/ta/tools/CommonTools.java) include common actions like waiting for a specified duration and
      opening the Chrome browser.
    * [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java ) provides the whole logic for locating a specific
      UI element on the screen based on its description.
    * [VerificationTools](src/main/java/org/tarik/ta/tools/VerificationTools.java) verifies precondition/test-step expected results using
      the `UiPreconditionVerificationAgent`/`UiTestStepVerificationAgent` against a fresh screenshot, which a test step's verifier compares
      with the screenshot captured before the action.
    * [SpinnerTools](src/main/java/org/tarik/ta/tools/SpinnerTools.java) shows and clears the agent's current activity on the status line of the operator panel during long-running tool calls (e.g. element creation in supervised mode); it never opens a prompt.
    * [KnowledgeElementTools](src/main/java/org/tarik/ta/tools/KnowledgeElementTools.java) provides mode-aware UI element handling for
      the knowledge persistence feature. Supports collecting knowledge flow (searches for or creates elements by description) and execution flow
      (locates known elements directly by UUID, bypassing vector DB search).

* **Execution Modes:**
    * Supports three execution modes controlled by the `execution.mode` property in `config.properties`.    
    * **Supervised Mode (`execution.mode=SUPERVISED`):** The agent operates autonomously but allows the operator to intervene. Every question is a prompt of the operator panel (see *Operator Panel*), so a supervised agent refuses to start without `panel.password` or without the panel frontend on its classpath (`target/classes/panel`, built by the Maven build of this module unless `-Dskip.npm` is set).
        * **Countdown Halt:** Before and after an atomic step runs, a confirmation prompt counts down (`supervised.countdown.seconds`) and then proceeds, unless the operator chooses "Halt"; the countdown pauses while the pointer is on the prompt and is held once the operator presses Tab in it.
        * **Verification Failure Notification:** When a verification fails (after all automatic retries), the operator is shown the failure with its reason and the screen it was judged on before the test execution goes on.
        * **Operator Intervention:** On halt or failure, the next-action prompt — the same one used for ambiguous match resolution — gives the operator the full set of actions: after a halt first "Proceed with this step" (halted before the step ran; it refetches and executes it) or "Go on with the next step" (halted after it succeeded), then Rerun the whole Test Step (or Precondition), Rerun the Atomic Procedure (once an atomic procedure has executed, so not after a halt before the step ran; a decomposition failure offers only the whole-step rerun), Browse existing procedures, Create a new procedure (after which the prompt reopens, and "Rerun Test Step" runs the newly created procedure), or Terminate. Every rerun refetches the procedure from the knowledge DB before rerunning it, and a whole-step rerun first restores the execution state from before the failed attempt: the effects the attempt produced are dropped, the ones it negated are back and its executed atomic procedures are discarded.
        * Suitable for monitoring execution without constant clicking, while retaining control to fix issues on the fly.
    * **Unattended Mode (`execution.mode=UNATTENDED`):** The agent executes the test case without any human assistance. It relies entirely
      on the information stored in the RAG database and the AI models' ability to interpret instructions and locate elements based on stored
      data. Errors during element location or verification will cause the execution to fail. This mode is suitable for integration into
      CI/CD pipelines. Budget checks are automatically enforced in this mode.

* **Operator Panel:** A browser UI that the agent serves on its own port (`panel.port`, default `8443`), next to the A2A port, in both execution modes and also between runs. It is built with React and bundled into the agent's jar ([`src/main/frontend`](src/main/frontend)); the server side lives in the [`panel`](src/main/java/org/tarik/ta/panel) package.
    * **Login:** One machine-generated password (`panel.password`), distinct from the A2A bearer token, opens one session. The page shell is public; every API call, the event stream and the live view need the session cookie.
    * **App bar and attention:** One bar of constant height carries the views (*Run*, *Health*, *Knowledge*; on a phone they move to a bottom bar), a run chip with the status and how many items have ended (on the other tabs; it brings the *Run* tab forward), "Search and jump", a *Preferences* menu with the theme (same as the system, light or dark) and an opt-in desktop notification when the run starts waiting while the page is not in front (it needs the browser's permission and a secure context; both choices are remembered by the browser) and the log-out. While a prompt is open, the *Run* view is marked as waiting, the browser tab title leads with the question, the favicon shows a waiting mark and screen readers hear the new prompt announced; they also hear each item start and end and the run end, but not the agent's moment-to-moment activity. The panel uses Atkinson Hyperlegible Next and Mono, served from the agent with the bundle, and is checked against WCAG 2.2 AA (text contrast of at least 4.5:1 and field boundaries of at least 3:1 in both themes and with `prefers-contrast: more`, keyboard paths for every control, a skip link, focus never hidden under the bar, every status indicator kept visible in Windows forced-colours mode). Icon-only buttons and screenshots show their name or what a click does as a tooltip on hover and on keyboard focus; it stays while the pointer is on it and Escape hides it. A view that fails to render shows "This view failed" with "Show it again", while the other views and an open prompt keep working. The views cross-fade as the tabs switch and an answered prompt sinks away, both off under `prefers-reduced-motion`; on a very wide screen *Health* and *Knowledge* keep a maximum width, while *Run* uses the whole width for the live view.
    * **Keyboard:** "Search and jump" (Ctrl+K, ⌘K on a Mac) lists every command of the panel: go to a view or a *Knowledge* view, answer the waiting question, show or hide the logs, open the live view large or go back to the step, copy the log (confirmed in a short message at the bottom of the page), change the theme, show the shortcuts, log out. G then R, H or K goes to a view, W to the waiting question, L shows or hides the logs, V opens the live view large or goes back to the step (both bring the *Run* tab forward), the keys 1 to 9 choose a prompt's answers (only with the focus in the prompt or nowhere in particular, never from a field or from a confirmation over the prompt), Shift+Esc gives the keyboard back from the live view and ? lists the shortcuts. The keys stay out of fields, the live view and open dialogs.
    * **Run tab:** On a desk-sized window the tab fills the window under the app bar and every column scrolls on its own, so nothing pushes the current step or the live view out of sight; on a narrow one it is a single column that scrolls as a page. A run header spans the top in one row on a wide window (two on a narrow one), dark in both themes like the live view's stage and lit along its top edge in the run's status colour (the test case, cut short with its full name on hover, the execution mode, the run's status, one segment per item in its status colour, an error hatched so it differs from a failure without its colour, with how many items are done, and the counts per status, errors apart from failures, on hover and for screen readers, what the agent is doing while no step is running or the error of a failed run, and the "Logs" toggle). The side column holds the timeline of the current test case (its preconditions and steps with their status, duration, error message and "Why this procedure"; its heading stays in place while it scrolls; an error message, here and in the run header, shows its first four lines with "Show the whole error" for the rest) and, when `vnc.socket.path` is set, the live view of the agent's screen as a view-only tile. The middle column shows the current step: "Test step n of m" or "Precondition n of m", its description, its test data and its status (Finding the procedure, Executing, Waiting for you, Passed with its duration, Failed), a bar with one segment per atomic action, grouped by the top-level parts of its procedure, with the counts as text ("5 of 9 actions done · action 6 executing · 3 to go"), and the tree of its procedures. The tree lists the decomposition of the step in execution order: composites fold and unfold (finished ones start folded) and show how many of their actions are done and, once finished, their duration; actions show their duration, "Skipped – not needed", "Failed" or "Next"; the guides are lit along the route to the current action, travelled ones green-grey and the ones still ahead dashed, and the markers take the timeline's shapes. The current action opens in place with its phase (Executing, Waiting for your go, Executed – waiting for your go, Waiting for you, Failed), "Action n of m", what the agent is doing, the expected result before it runs and the verified one after, and scrolls into view when it changes; the compact one beside the large live view also scrolls back into full view, to the middle of its pane, when it grows. The heading and the progress bar stay in place while the tree scrolls. "Open large" on the tile, a click on it or V opens the live view large in the middle, where the operator may control it, and the step moves under the timeline in a compact form with only the path to the current action unfolded; "Back to the step" or V returns. The live view stays one connection while it moves, and the panel always opens on the step view. "Logs" (or L) opens the agent logs as a side panel next to the middle column, or under it on a narrower window: the newest 500 log lines, newest first (time, level and logger set apart, warnings and errors highlighted, filtered by level with the count of each or by text with the matches marked, long lines wrapped or not, keeping the newest line in view at the top until the operator scrolls down, with "Jump to the newest" and "Copy" of the lines shown, oldest first, which says "Copied" or, where the browser blocks the clipboard (outside a secure context or without its permission), to copy the lines by hand; screen readers do not hear each new line). The logs start closed, the browser remembers whether they were left open, and while they are closed the toggle counts the warnings and errors that arrived since. The step's outline reaches the panel once per decomposition (`item-outlined`, with every text redacted) and each procedure's start and end name its node in it, so the tree is complete before the actions run. Every connect starts with a snapshot of the whole run state followed by its changes, so a reloaded tab or a reconnect after a server restart shows the same state. The run events reach the panel through a `RunObserver` that wraps the A2A emitter, so the A2A stream is unchanged; a cancelled task is shown as aborted. Known limits: an action the operator replaces through "Rerun the last action" keeps its old description in the tree until the step is decomposed again, and an action's duration is the wall time from its start to its end, so in supervised mode it includes the time the confirmation waited.
    * **Live view:** noVNC in the browser, loaded only when the tab shows a live view, relayed by the agent to Xvnc's Unix socket. The view is as wide as the tab at a 16:9 aspect ratio and at most half the window high, and can fill the screen; when `panel.live.control.enabled` is `true`, "Control the screen" enlarges it and lets the operator use mouse and keyboard on the agent's screen until it is pressed again or Shift+Esc gives the keyboard back (the screen otherwise takes every key, Tab included); otherwise "Watching only" says so and expands into why. A lost connection offers "Reconnect" and shrinks the stage, which has no screen to show. No viewer can resize the display, because the agent's coordinates depend on its geometry. Nothing of the panel is on the agent's display, so answering prompts needs no live control.
    * **Prompts:** Every question of a supervised run is a prompt on the *Run* tab: the starting test step, the confirmation of an action, the next action after a failure, halt or missing procedure, informational messages and verification failures, the failure note, and the procedure editor. A prompt shows what kind of question it is, since when the run waits, how long it still waits (a draining ring and fuse while a countdown runs) and what then happens; its answers are numbered cards that say what each one does; a new prompt brings the *Run* tab forward unless its countdown applies a default on its own (the confirmation, the failure note), and the tab label marks a waiting run. The run blocks until the prompt is answered or its deadline passes: a countdown (the confirmation, the failure note) applies its default at its end, the pointer on the prompt pauses the countdown, which resumes with exactly the time it had left once the pointer leaves, pressing Tab inside the prompt holds it by moving that end to the prompt upper bound (`panel.prompt.max.wait.minutes`), and a prompt without a countdown applies its default there or, without one, fails the run. The server is the authority on deadlines, defaults and the validity of every answer, so neither a closed tab nor the operator machine's clock changes what the agent does; one prompt is open at a time, and a second answer, an answer after the deadline or an action of a closed prompt is refused. While a prompt is open the A2A task reports "Waiting for operator: <prompt>"; a cancelled task closes its prompt, interrupts a running element search or screen capture and waits for it to stop. The confirmation of an action ("Run this action?" before it, "Action executed. Go on?" after it) is answered in the current action of the step's tree, and the pointer anywhere on that action pauses its countdown. Proceeding is the countdown's default, so Halt is its only answer, unlike the other prompts without a number key and never taking the focus, so a stray key cannot halt the run; W moves the focus to the question and Tab on to Halt. While the live view is large, nothing appears over it: the compact current action under the timeline shows a small countdown ring, the time left and Halt. The failure note is a modal dialog whose Escape gives its default, docked to the bottom of a wide window; every other prompt is answered in place above the step or the large live view. While such a prompt is open, it shows the agent's activity, and a modal prompt dims the page behind it only lightly; the other tabs show the question under the app bar with the time its countdown leaves and "Answer", which brings the *Run* tab forward and moves the focus to the prompt. A confirmation, which goes on by itself, shows there the action instead: "Up next" with "Runs in N s" before it, "Just executed" with "The run goes on in N s" after it, "Waits for your go" while its countdown is paused, held or off, and "Go to the run" in place of "Answer". The starting test step's Start and Abort buttons stay pinned above its list of steps, which opens at its first step, and the procedure editor's Save and Cancel stay in view while it scrolls. A prompt view that replaces another (the procedure search, a view of the procedure editor) takes the focus, and leaving it returns the focus to the control that opened it.
    * **Procedure editor:** One prompt with a stack of views: the procedure, a child step at any depth, the prerequisite picker (the effects that hold before the procedure, newest first), the element lookup (search the known elements, or create the described one and locate it on the screen, with the reason when that fails), the element details, a new element screenshot (capture the agent's screen and drag a rectangle around the element; only the latest capture of the prompt keeps its full image, so only it can be cropped) and the parent chooser. Back, the breadcrumb and "Edit a parent instead" move within the prompt; only Save, Delete and Cancel of the edited procedure end it. A child step is saved into the knowledge when the operator saves it in its own view, so the parent's save references persisted steps only; element details and screenshots are saved when they are edited. Opening, saving, deleting or moving up from an existing procedure is accepted only for a procedure the editor has shown (the edited one, its child rows, its parents and the search hits), as the editor's answer accepts only its own roots. Leaving a view with unsaved changes, deleting and replacing values with AI suggestions are confirmed first.
    * **Health tab:** The knowledge graph health report (see *Knowledge Graph Health Dashboard*), checked by the running agent when the tab is first shown and on every Refresh, never in the background; a request while a check runs joins it. A verdict states the worst severity found with the counts of critical checks, checks with warnings, passed checks and findings, and tiles with the count per severity filter the list; the categories are listed critical first and passed last, and a report without findings shows an all-clear instead of the list. Each category shows its severity as text and colour, its finding count and description, and expands into its findings. A finding that names a procedure or a UI element links to it on the *Knowledge* tab.
    * **Knowledge tab:** What the knowledge graph holds, read-only (knowledge is still edited only through the procedure editor prompt) and available in both modes and between runs. Summary tiles (procedures, UI elements, test cases, the share of atomic steps with a target and with effects as meters toned by their share with their counts, schema version; on a phone one row that scrolls sideways) sit above five views, switched by tabs that scroll sideways on a narrow screen and each loaded only when first opened, so the *Run* tab downloads none of their libraries:
        * **Map:** starts from a question, never from the whole graph: a search over procedure descriptions, element names and test case names (at most 50 hits), a finding of the *Health* tab, a test case (its lens; the empty map offers the running test case's) or a node of another view. It shows one procedure in focus with only its direct relations, so it stays readable however densely the graph is linked: the composites it is part of above it, its steps in order, its target elements and the test cases using it in the centre, on the left the procedures whose effects satisfy its prerequisites (*Enabled by*) and on the right the procedures whose prerequisites its effects satisfy (*Enables*), each grouped by the matched prerequisite or effect phrase, the closest match first, with the first five of a group shown and the rest on demand. A click on any card puts it in focus and *Back* returns to the previous one; every card spells out its kind, the test cases using it and its health flags, an amber edge marks a missing target, missing effects or low location stability and a red edge a recent anomaly, and the procedures running now glow. The details panel lists everything about the selected procedure (description, flags, successful runs and last success, timing, prerequisites, effects, negated effects, test data, expected results, parents, children, target element, test cases, the newest anomalies and the step resolutions that chose it, see *Run history*). A test case in focus (its lens) shows the procedures it uses and, once runs are recorded, lists the procedures its latest run chose, in order, and marks them with a dotted outline.
        * **Elements:** the UI elements as a grid of screenshots with stability (its bar green, amber or red by its share), average location time, location retries and whether they are located by their data, ordered by least stable, recently changed or most retries, 60 at a time; a card opens the element's descriptions, the procedures acting on it and its appearance history, newest first.
        * **Brain:** a 3D overview of every procedure and element (at most 5000) with three.js and three-forcegraph; the camera frames the settled graph, then zooms onto the running chain and follows it to every new procedure; the running chain glows and is listed above the view with links to the map. Once the operator zooms, their zoom level is kept and new procedures only re-centre the view, until the view is rebuilt (Refresh, a theme switch or re-opening it). Rotation and the camera's easing are off under `prefers-reduced-motion`, a browser without WebGL gets a notice, and hiding the view releases its WebGL context. A click opens the procedure on the map.
        * **Trends:** weekly bars over 4 to 52 ISO weeks in UTC: procedures created, anomalies by kind, how steps were resolved with the knowledge hit rate, and step outcomes, two charts to a row on a wide screen, each with a table view. UI elements carry no creation time, so their growth is not shown.
        * **Failures:** the failure hints of the procedure selected on the map, or found with the view's own procedure search, on a time axis, current and expired, with their occurrences and resolutions and the unresolved ones marked. Hints carry no first-seen time, and hints that expired more than `failure.hints.stale.days` ago are deleted at startup, so the history reaches back only that far past an expiry.
      Every text the views show passes the secret redaction, a screenshot is served only as `image/png` or `image/jpeg`, and invalid input (a malformed id, an over-long search) is refused before any query.
    * **Why this procedure:** Once an item of the run ends, its timeline entry on the *Run* tab explains how it was resolved: from the knowledge graph or by the operator, with which confidence, whether the best semantic match was passed over, and every candidate with its similarity, prerequisites met, shared parents and stability.
    * **One operator, one tab:** At most one session, one event stream and one live view exist, and the newest always wins: a login replaces the current session, and a second tab of the same browser replaces the first. The replaced tab stops updating and says when, from which IP address and from which browser it was replaced (a tab of the same browser can take the panel back with "Use here"); after a takeover it also says "If this wasn't you, change the panel password."

* **Server mode:**
    * The [Server](src/main/java/org/tarik/ta/Server.java) class
      extends [AbstractServer](../agent_core/src/main/java/org/tarik/ta/core/AbstractServer.java) and is the entry point where a
      Javalin web server is started. The agent registers its capabilities and listens for A2A JSON-RPC requests on the root endpoint (`/`) (
      port configured via `port` in `config.properties`). The endpoint serves the streaming `message/stream` (and `tasks/resubscribe`)
      methods as a Server-Sent Events (SSE) stream and all other methods as a single JSON-RPC response; the agent card advertises
      `capabilities.streaming = true`, exposes a test-execution skill in its `skills` list, and uses an HTML-friendly description that
      lists each sub-agent together with its configured model. The advertised name embeds the active `execution.mode` (e.g.
      `UI Test Execution Agent (supervised)` or `UI Test Execution Agent (unattended)`). The advertised skill depends on `execution.mode`:
      an unattended agent exposes the `ui_test_execution_unattended` skill (tag `unattended`) while a supervised agent exposes
      `ui_test_execution_supervised` (tag `supervised`), so CI/CD selectors can pick only unattended agents for automatic execution. The server accepts only one test case
      execution at a time (the agent has been
      designed as a static utility for simplicity purposes). Upon receiving a valid request when idle, it returns `200 OK` and starts the
      test case execution. If busy, it returns `429 Too Many Requests`.
    * The runtime request lifecycle now mirrors the API agent: [UiAgentExecutor](src/main/java/org/tarik/ta/a2a/UiAgentExecutor.java)
      opens a child request `BeanScope` through [UiAgentRequestScopeFactory](src/main/java/org/tarik/ta/UiAgentRequestScopeFactory.java)
      and resolves a request-scoped [UiTestAgent](src/main/java/org/tarik/ta/UiTestAgent.java) from that scope.
    * The UI request scope now explicitly depends on the shared base request scope, so `LogCapture`, shared test-context tooling, and the
      UI-only runtime `VisualState` are all assembled in the same child `BeanScope` without duplicate default `@InjectModule`
      declarations.
    * Every sub-agent and everything that holds one lives in that child `BeanScope`: the agents themselves, the mouse, keyboard, common, element locator, element database and knowledge element tools, the element resolution, suggestion and similar-children services, the knowledge, satisfies-edge and asynchronous persistence services, and both execution orchestrators. That is what lets any of them take the per-run `TestExecutionContext` and its `TestContextDataTools`, which exist only for the duration of one test case. Configuration, the model factory, the Neo4j repositories, the graph health service and the A2A server side stay in the root scope and are shared across runs.

* **Knowledge Persistence (Neo4j):**
    * Optional knowledge persistence layer backed by Neo4j 2026.01 or newer that enables the agent to learn and remember procedures (reusable
      test action sequences) across sessions.
    * **Procedure Graph:** Stores hierarchical procedures (composite and atomic) as a Neo4j graph with CONTAINS (parent-child) and
      TARGETS (step-to-UI-element) relationships. To ensure data integrity, each procedure is restricted to target at most/exactly one `UiElement`; linking a procedure to a new element automatically detaches and deletes any existing `TARGETS` relationship.
    * **PDDL-Lite Planning:** Prerequisite/effect state tracking enables automatic prerequisite resolution and procedure branching during test execution.
    * **Negated Effects (PDDL Delete List):** A procedure can declare negated effects: states that were true before it runs and stop being true after it (e.g. "the login dialog is open" for "Close the login dialog"), phrased like effects. They are stored in the `negatedEffects` node property and as `PhraseEmbedding` nodes of type `NEGATED_EFFECT` linked by `HAS_NEGATED_EFFECT {sequence}`, and are edited in the "Negated effects" list of the procedure editor, which the AI suggestion pre-fills. Before an atomic procedure runs, `SatisfiesEdgeService.findNegatedEffectNodeIds()` matches its negated effects semantically (`satisfies.similarity.threshold`, scored with `vector.similarity.cosine` against the current effect nodes only; no query runs for a procedure without negated effects). On success the matched effects are removed from every open scope frame of `ExecutionStateTracker` before the procedure's effects are added. A composite applies its negations when its scope closes, and the next-atomic prefetch predicts the state with the same removals. Seeding the effects of skipped items when starting from a later step applies the negations of each skipped procedure too. Existing procedures without the property read it as an empty list, so no data migration is needed. Known limit: the match is semantic, and opposite states can score above the threshold ("logged in" against "logged out" scores 0.973, see *Similarity scale* in the root README), so a negated effect also removes a current effect describing the opposite state. This is harmless when the procedure then adds that opposite state as its own effect, as a closing or logging-out step usually does; otherwise the removed state is missing for the prerequisite checks that follow.
    * **Procedure Branching:** When multiple semantically similar procedures exist with different prerequisites, the execution engine automatically selects the one whose prerequisites are satisfied by the current execution state. This allows defining alternative procedure variants (branches) that execute conditionally based on accumulated effects.
    * **State-Aware Candidate Re-Ranking:** Before selecting the best match, candidates are re-ranked by state compatibility using semantic matching between each procedure's prerequisites and the accumulated execution effects. Prerequisite satisfaction is checked in Cypher by scoring each prerequisite against the current effect nodes with `vector.similarity.cosine` (native `PhraseEmbedding` nodes, see below), replacing the previous O(N×M) in-memory cosine loop. Procedures with no prerequisites always score `1.0` (universally applicable). Among prerequisite-bearing procedures, the score is the proportion of prerequisites semantically met (similarity ≥ high-confidence threshold). Within equal proportion scores, semantic similarity breaks ties; ancestry affinity is the final tiebreaker. When the top semantic match is demoted by re-ranking, a DEBUG log entry is emitted showing the original semantic score, how many prerequisites were met vs. total, which prerequisites were unmet, whether `effectNodeIds` was empty, and which procedure was selected instead — enabling diagnosis of re-ranking decisions.
    * **Native Phrase Embedding Nodes:** Prerequisites and effects are stored as first-class `PhraseEmbeddingNode` Neo4j nodes (`label: PhraseEmbedding`, properties: `id`, `phrase`, `embedding: float[384]`, `type: PREREQUISITE|EFFECT|NEGATED_EFFECT`) connected to their parent `Procedure` via `HAS_PREREQUISITE {sequence}`, `HAS_EFFECT {sequence}` and `HAS_NEGATED_EFFECT {sequence}` relationships. A dedicated vector index (`phrase_embedding_vector_index`, cosine similarity, dimension 384) and a phrase-text index enable efficient Cypher-native similarity queries without any Java-side vector arithmetic. Embeddings are computed once at ingestion time by `KnowledgeIngestionService` and batch-created via `PhraseEmbeddingRepository`. `ExecutionStateTracker` tracks accumulated effects as `Map<String, UUID>` (phrase → node ID); precondition satisfaction is resolved directly in Cypher rather than loading float arrays into memory: the candidate re-ranking and the unsatisfied-prerequisite check score the current effect nodes with `vector.similarity.cosine` (same implementation and 0–1 score range as the index) instead of querying the vector index, so a matching effect can never be crowded out by nearer phrases elsewhere in the graph. If a procedure's `HAS_EFFECT` phrase nodes are missing at execution time (legacy data), a WARN log is emitted and state tracking falls back to string-only mode, which degrades prerequisite semantic matching for subsequent steps.
    * **Startup Phrase Node Migration:** On startup, `PhraseNodeMigrationService` scans for procedures that have non-empty `effects`, `prerequisites` or `negatedEffects` node properties but are missing the corresponding `HAS_EFFECT`, `HAS_PREREQUISITE` or `HAS_NEGATED_EFFECT` phrase embedding edges (a symptom of data ingested before phrase-node creation was part of the pipeline). For each such procedure it deletes any partial phrase nodes and recreates them atomically via `KnowledgeIngestionService`. This ensures `SATISFIES` edge computation and prerequisite semantic matching work correctly for all procedures on first use. Any forward migration or backward repair failure during this startup sequence triggers an `IllegalStateException`, immediately aborting startup with clear diagnostic logs. If Neo4j is unreachable at startup (`DatabaseConnectionException`), this migration and the startup cleanup of `GraphHealthService` are skipped with a `WARN` instead of stopping the agent, which then connects on its first database operation; both run again on the next start.
    * **Async Persistence Reliability:** All asynchronous background state persistence operations (such as saving steps, test cases, execution nodes, and run timings) are run on a virtual-thread executor owned by the request scope and wrapped in try-catch blocks with robust error logging, ensuring background database operations do not silently fail or impact execution flow. When the request scope closes, writes still in flight get up to 10 seconds to land before they are interrupted.
    * **Composite Traversal Reuse:** Executing a composite procedure no longer re-queries the graph for a tree that was just walked. `DecompositionService` caches the ordered children it fetches during `decompose()` and serves them back through `getChildrenOrdered()`, which `KnowledgeService.getChildrenForExecution()` exposes to the orchestrator. The children cache shares the lifecycle of the decomposition cache, so it is cleared by the same `invalidateCache()` calls on session end and after every knowledge ingestion, and can never serve a tree that has since been edited. `KnowledgeService.getChildren()` remains a direct repository read for callers outside execution, such as knowledge collection.
    * **Composite Effects Prefetch:** When the orchestrator enters a composite scope it starts loading that composite's effects on a virtual-thread executor owned by the orchestrator (shut down with a 5-second grace period when the request scope closes) and joins the result when the scope closes; in unattended mode the next-atomic prefetch may join it earlier on its own background thread, because the state it predicts after the composite's last child includes the effects the closing composite promotes. The result is loaded once and shared by both. Because the children take seconds to execute, the effects read — a full round trip to a remote graph — is warm by the time it is needed. If the prefetch fails, a WARN is logged and the effects are read synchronously, so the promoted effect set is unchanged either way.
    * **Jackson JSON Node Storage:** Each `Procedure` Neo4j node stores its scalar properties (name, description, prerequisites/effects as plain phrase strings, timing/stability metadata, etc.) as a single Jackson-serialized JSON string in a `data` property. The `id`, `description`, and `embedding` properties are kept as dedicated Neo4j properties for vector index use. Phrase embedding vectors are stored separately as `PhraseEmbeddingNode` nodes — not inside the Procedure JSON — keeping the Procedure payload lightweight and enabling selective loading.
    * **Queue-Based Execution:** Replaces the sequential for-loop with a dynamic execution queue that injects prerequisite steps when
      prerequisites are unmet.
    * **Human-in-the-Loop Collecting knowledge:** In SUPERVISED mode, the agent opens the procedure editor prompt of the operator panel
      for the operator to collect a new procedure when an unknown action is encountered. The agent first loads AI suggestions for the
      item (its status line says so meanwhile) and opens the editor pre-filled with them; the suggested child steps are listed as not
      saved yet, and opening one loads suggestions for it in the same way.
        * **Ambiguous Match Resolution:** If the agent cannot automatically resolve the procedure — due to low confidence, unmet
          prerequisites, or no match at all — it opens the next-action prompt, where the operator retries the search, creates a new
          procedure, terminates the run, or picks an existing procedure from a scored search of all semantically matching procedures
          (showing how many of each procedure's prerequisites the current state meets). The search is always offered, even if zero
          semantic matches are found, and the server only accepts a procedure its search returned. A picked procedure opens for editing;
          in the editor, "Edit a parent instead" moves up to the parent, or to a chooser when the procedure has several, within the same prompt.
          The three previous `ProcedureLookup` variants (`LowConfidenceMatch`, `NoProcedureWithFulfilledPrerequisites`, `NoMatchFound`)
          are collapsed into a single `NeedsUserResolution` record carrying an optional `MatchResult` and missing-prerequisite list,
          from which a precise reason message is derived.
        * **Procedure Type in the Editor:** An existing procedure is always rendered with the field set of its own type rather than of
          the item it was opened from — a precondition shows *Relevant data*, a test-step procedure shows *Test data* and *Expected
          results* — and saving keeps the loaded procedure's `isPrecondition` and its stored Test Data even when that field was not
          rendered, so a hidden field is never wiped. Every procedure search labels each row as precondition or test step and as atomic
          or composite, shows only procedures of the current item's type by default (the type filter reveals the rest), and picking a
          procedure of the other type asks for confirmation first.
        * **Element Selection During Collecting knowledge:** The target element of an atomic procedure is located on the screen by the
          resolution agent ("Locate on screen", bounded by `element.resolution.max.duration.millis`), or chosen in the element lookup,
          which searches the known elements with the element description extracted from the procedure description and can create the
          described element and locate it; a failed attempt shows its reason.
        * **Confirmation Scoping:** The post-execution confirmation is only shown for **pre-existing** procedures. Newly collected
          procedures skip it because the operator just worked on them in the editor and there is nothing additional to confirm.
        * **Prerequisite Failure Handling:** If no procedure branch has its prerequisites satisfied by the current execution state, the test execution fails with a descriptive error listing which prerequisites are missing. In SUPERVISED mode the operator is prompted with the standard EDIT/CREATE/RETRY selection; in UNATTENDED mode the test terminates immediately.
        * **Procedure Usage Tracking:** The agent automatically tracks which test cases use which procedures using `USES_PROCEDURE` edges in the graph. The edge to a root procedure is merged when that procedure starts executing, so a procedure that fails stays linked to the test cases it breaks. When an operator edits a procedure that is used by multiple test cases, a warning is shown to the operator to highlight the potential impact across the test suite. Stale usage edges are automatically cleaned up with dedicated error handling only upon successful test execution runs to avoid premature cleanup on aborted or failed executions. In unattended mode the per-procedure `USES_PROCEDURE` merges are written on virtual threads via `AsyncExecutionPersistenceService.mergeUsesProcedure()` so the round trip stays off the execution path; nothing in the execution path reads these edges. `AsyncExecutionPersistenceService.awaitPendingUsageWrites()` drains the in-flight writes on every exit path of the run — including an early stop or a failure — so no write is lost to the graph driver closing. In supervised mode the merge stays synchronous.
    * **SATISFIES Edges — Pre-Computed Precondition Satisfaction:** Persists `SATISFIES` relationships directly between Procedure nodes to record that an effect of one procedure satisfies a precondition of another. Each edge carries a cosine similarity `score`, matched `effectPhrase`/`precondPhrase` texts, and lifecycle timestamps (`createdAt`, `lastVerifiedAt`). Computed asynchronously by `SatisfiesEdgeService` after each successful step: a virtual thread fires N parallel similarity comparisons (one per effect embedding), deduplicates by maximum score per consumer procedure, filters by `satisfies.similarity.threshold` (default `0.90`), and batch-persists the results. This replaces the previous O(N×M) per-step cosine loop with a single graph traversal during re-ranking and enables cross-run caching — a match is never recomputed until the procedure is edited. Edges are deleted transactionally when a procedure is modified. Stale edges (not verified within `satisfies.stale.days`) are flagged during health checks and cleaned up via `GraphHealthService.runStaleSatisfiesEdgeCleanup()`. In unattended mode, persistence happens asynchronously before execution starts. In supervised mode, SATISFIES persistence is fully synchronous to ensure consistency when a user edits or retries procedures.
    * **Ordering Conflict Detection:** At the start of execution in supervised mode only, the engine queries the `SATISFIES` graph to detect when a test step B appears before step A but B's preconditions require an effect that A produces — a test authoring error. Conflicts are displayed as a warning `InformationalPopup` and do not block execution. Skipped entirely when no `SATISFIES` edges exist yet (cold graph).
    * **Knowledge-Driven Failure Recovery:** The system learns from past failures via `FailureContext` nodes linked to procedures via `HAS_FAILURE_CONTEXT` edges. Each node stores a failure `symptom`, `category` (using `ErrorCategory`), user-provided `resolution`, `occurrences` count, a `mode` (`SUPERVISED`, `SUPERVISED_TIMEOUT`, or `UNATTENDED`), and timestamps. Before executing a procedure, the agent retrieves failure hints via `FailureContextService.findFailureHints()`: only current hints (no `expiredAt`) that carry a resolution, the most frequent and then the most recent first, capped at `failure.hints.max.count`; hints without a resolution (e.g. auto-captured in unattended mode) never reach the prompts and are listed in the health report instead. Non-empty hints are injected into the agent's prompt as a "Known issues" section by `StepExecutionOrchestrator`. After retry exhaustion: in supervised mode the failure-note prompt of the operator panel asks the operator (countdown `failure.context.dialog.countdown.seconds`, default 30 s) with error category pre-selected and symptom pre-filled; in unattended mode context is auto-captured from the error category. A repeat failure with the same `(procedureId, category, symptomNormalized)` increments `occurrences` of the current hint (and fills in its resolution if it had none); a failure whose resolution differs from the current non-empty one sets `expiredAt` on the current hint and starts a new one, so the history of resolutions is kept instead of overwritten. Concurrent captures for one procedure are serialised by a write lock on the procedure node, so they never create two current hints for the same symptom. On startup, `GraphHealthService` expires current hints that have not recurred within `failure.hints.stale.days` and deletes the hints that have been expired for longer than that. Orphaned `FailureContext` nodes are cleaned up by `FailureContextService.cleanupOrphanedFailureContexts()` after procedure deletion.
    * **Execution Anomalies:** Unexpected events of a run are recorded as `ExecutionAnomaly` nodes (`kind`, `subjectId`, `occurredAt`, `testCaseName`, `details` capped at 500 characters, `mode`) linked by `OBSERVED_ON` to the UI element or procedure they happened on: `ELEMENT_LOCATION_FAILED` for every failed element location attempt, `STEP_FAILED` when an atomic step terminates the run (after its retries, or because its prerequisites are not satisfied) or, in unattended mode, on the best-matching procedure when no procedure could be selected for an item, and `TIMING_OUTLIER` when a successful step took more than `anomaly.timing.outlier.factor` times its previous average execution time. Recording is controlled by `anomaly.recording.enabled` (default `true`) and is independent of `location.history.and.failure.hints.collection.enabled`, which still gates the stability metrics. The test case name is bound when the run starts and stored as a property, because the `USES_PROCEDURE` edges of the test case are written asynchronously. Only the newest `anomaly.history.size` anomalies are kept per subject (schema migration 10 indexes `ExecutionAnomaly.subjectId` for this trim). Anomalies are written like the other execution data: on a virtual thread in unattended mode, synchronously otherwise; a failure to store one is logged and never changes the outcome of the step it was recorded for. The health report summarises them per subject in the "Recent Execution Anomalies" category. On startup, `GraphHealthService` deletes the anomalies whose UI element or procedure no longer exists, whichever way it was removed.
    * **Run history:** Every run is recorded as an `ExecutionRun` node (`id`, `testCaseName`, `startedAt` and `finishedAt` in epoch milliseconds, `mode`, `status` `PASSED`/`FAILED`/`ERROR`/`ABORTED`) linked by `HAS_RUN` from its `TestCase` (merged by name, as for `USES_PROCEDURE`), and every precondition or test step that reached the resolution of its procedure as a `StepResolution` node linked by `HAS_RESOLUTION` from the run: `sequence`, `itemType`, `itemDescription` (capped at 500 characters), `source` (`KNOWLEDGE` for a match of the graph, directly or prefetched; `OPERATOR_SELECTED` for a procedure the operator found through retry or browse; `OPERATOR_CREATED` for one the operator created; `UNRESOLVED` when unattended mode found no executable procedure or the operator ended the run), `confidence`, `candidates` (a JSON list of the first `knowledge.match.top.n` candidates of the match, each with its semantic score, prerequisites met, shared parents, low stability and unmet prerequisites, the values the re-ranking computes anyway, so no query is added), `wasDemoted`, `demotedDueToPrerequisites`, `outcome` (`SUCCEEDED`/`FAILED`/`NOT_EXECUTED`, from the item's merged result) and `durationMs`, with a `CHOSE` edge to the chosen procedure. `ExecutionRunService` writes the start and the resolutions like the other execution data (in order on a virtual thread in unattended mode, synchronously otherwise, drained before the run ends) and the end synchronously, once the queued start and resolutions have landed, so the end always finds its run node; in the same transaction as the end, the runs beyond the newest `run.history.size` are deleted with their resolutions (schema migration 11 indexes `ExecutionRun.startedAt`). A failure to store any of it is logged and never changes the run. The operator panel shows the resolution of each item ("Why this procedure"), the resolutions that chose a procedure in its details, the resolutions and outcomes per week on *Trends* and the latest run's path in a test case's lens.
    * **Element Stability Index:** Reliability metrics stored on `UiElement` nodes: `stabilityScore` (EWMA α=0.3, initialised optimistically at `1.0`; first failure drops to `0.70`), `avgLocationTimeMs`, `locationStrategy`, `failedLocationCount`, and `lastLocatedAt`. `StabilityRecorder` is a `@FunctionalInterface` (`record(UUID elementId, boolean located, long locationTimeMs, String strategy)`) wired as a lambda delegating to `ProcedureRepository.updateElementStability()`. `ElementLocatorTools` calls it after every location attempt. `lastLocatedAt` is advanced only when the element was actually located — a failed attempt updates the score but leaves the timestamp at the moment the element was last really seen (it is only seeded when the element has no timestamp at all yet), so staleness stays meaningful. Elements with `stabilityScore < stability.penalty.threshold` are flagged as low-stability: a warning is logged in all execution modes, and in supervised mode a warning popup is shown right before the procedure starts executing. Unstable elements use a try-first, wait-and-retry strategy instead of a proactive pre-sleep.
    * **UI Element Appearance History:** Refining a UI element no longer overwrites its previous appearance. When `UiElementRepository.update()` changes the own description, anchors description, parent element summary or screenshot of an element whose stored appearance is not empty, it first copies the stored appearance into a `UiElementAppearance` node linked by `HAD_APPEARANCE` (`validFrom` = the element's `updatedAt`, which is set when the element is created and moves only when its appearance changes, so a rename or a re-save leaves it untouched; `validTo` = now) and trims the history to the newest `element.appearance.history.size` entries, all in one transaction; the current appearance stays on the `UiElement` node, so every reader is unchanged. When neither vision grounding nor algorithmic matching finds a candidate for the current appearance and algorithmic search is allowed for the element (`element.locator.algorithmic.search.enabled` and not data-dependent), `ElementLocatorTools` runs ORB and template matching with the screenshots of the expired appearances, newest first, and lets the model validate the merged candidates as for any algorithmic match (an INFO log names the matched appearance's `validTo`); no extra vision call is made. Removing an element deletes its expired appearances with it.
    * **Procedure Execution Timing Profiles:** Per-procedure timing stored as rolling averages (EWMA α=0.2) on Procedure nodes: `avgExecutionMs`, `avgVerificationDelayMs`, `maxVerificationDelayMs` (with 0.95 decay factor), and `lastTimingUpdate`. `TimingRecorder` is a `@FunctionalInterface` (`record(UUID procedureId, long executionMs, long verificationDelayMs)`) wired via `ProcedureRepository::updateTimingProfile`. The post-action verification delay adapts per-procedure: `Math.max(timing.verification.min.delay.ms, avgVerificationDelayMs)`, falling back to the global `action.verification.delay.millis` when no profile exists yet. `maxVerificationDelayMs` decays gradually so one-off spikes don't permanently inflate wait times. The recorder fires only on the success paths of a step (precondition met, step without verification, verification passed), so the same write also increments `successCount`, and `lastTimingUpdate` is the time of the last successful execution; the health report uses both as execution evidence ("Unproven Procedures").
    * **Ancestry Context for Matching:** A bounded sliding window (`ancestry.window.size`, default 5) of recently-matched parent procedure IDs is tracked in `ExecutionStateTracker`. During candidate re-ranking, procedures that share a parent (via `CONTAINS` relationship) with recently-executed procedures receive an ancestry affinity boost, helping disambiguate procedures with similar semantic scores that belong to different workflow contexts.
    * **`KnowledgeServices` Facade:** A single `KnowledgeServices` record bundles the five core knowledge components: `KnowledgeService`, `KnowledgeIngestionService`, `SatisfiesEdgeService`, `ProcedureRepository` (write instance), and `FailureContextService`. Created by `KnowledgeServiceFactory.createKnowledgeServices()` and passed as a single object to `KnowledgeBasedExecutionOrchestrator` and `StepExecutionOrchestrator`, replacing the previous pattern of passing individual services.
    * **`AtomicStepExecutionContext`:** A record that bundles execution-scoped parameters flowing through the atomic step call chain: `Optional<TimingProfile> timingProfile`, `TimingRecorder timingRecorder`, `List<String> failureHints`, `String elementId`, and `String effectiveExpectedResults`. Replaces individual parameters in `StepExecutionOrchestrator` methods, enabling failure hint injection without adding service dependencies in the orchestrator.
    * **Knowledge Graph Health Dashboard:** `GraphHealthService` runs sixteen read-only health-check queries and returns a `GraphHealthReport` (record with `List<HealthCheckCategory>` and `generatedAt` timestamp), shown on the *Health* tab of the operator panel. Each finding is a `HealthFinding` with its text and typed references (`NodeRef`: label and id) to the graph nodes it names, two for a duplicate pair or a stale `SATISFIES` edge. Each `HealthCheckCategory` carries a `Severity` (`OK` / `WARNING` / `CRITICAL`) computed from finding count against configurable thresholds (`health.warning.threshold`, `health.critical.threshold`). Checks covered:
        * Structure: orphaned UI elements, leaf procedures without a target element, deep hierarchies (above `knowledge.max.depth`), unused procedures (procedures that no test case uses, directly or through a parent procedure), procedures with missing effects, stale `SATISFIES` edges, orphaned `FailureContext` nodes and orphaned phrase embeddings.
        * Recent activity (within `health.recent.window.days`): volatile UI elements (at least two appearance changes), recent execution anomalies per subject, and unresolved failure hints (current hints without a resolution, which never reach the prompts).
        * Blast radius: "Low-Stability Elements by Blast Radius" lists UI elements whose stability is below `stability.penalty.threshold` with the number of atomic procedures targeting them and of test cases using any procedure that contains those atomics (`UiElement[id] 'name': stability=0.42, atomic procedures=12, test cases=40`); "Failing Procedures by Blast Radius" lists procedures with `STEP_FAILED` anomalies in the window, counted the same way (`Procedure[id] 'description': 3 failures in 30 days, test cases=7`). Both are ordered by the affected test cases first, so the fix with the widest effect comes first.
        * Execution evidence: "Unproven Procedures" lists atomic procedures created before the window without a successful execution inside it (`Procedure[id] 'description': successes=4, last success=2026-05-01T10:00:00Z`, or `successes=0, last success=never`). Only successes are shown, because failure occurrences are recorded only while `location.history.and.failure.hints.collection.enabled` is on.
        * Knowledge gaps: "Unsatisfiable Prerequisites" lists prerequisite phrases that no effect of another procedure matches with at least `satisfies.similarity.threshold` (scored with `vector.similarity.cosine` against every effect, with the best near miss), so a procedure that would always end in the unmet-prerequisites branch is visible before a run hits it; the scan is quadratic, which is acceptable for an on-demand report. "Duplicate Atomic Procedures" lists pairs of atomic procedures on the same UI element whose description embeddings reach `knowledge.match.confidence.high`; merging them stays manual. `SATISFIES` cycles are not reported, because toggles such as opening and closing a dialog form legitimate cycles. At startup, the service automatically runs stale edge cleanup to delete `SATISFIES` edges not verified within `satisfies.stale.days`. Key API:
        * `runFullHealthCheck()` — runs the check and returns the report; the operator panel serves it as JSON at `GET /api/health/graph`, with every text passed through the secret redaction and a `503` with a generic body when Neo4j cannot be reached.
        * `logHealthReport()` — logs a structured report at INFO level.
        * `generateHtmlReport(Path outputPath)` — writes a self-contained HTML file with color-coded summary cards (green=OK, yellow=WARNING, red=CRITICAL) and collapsible detail sections. No external template dependencies. Defaults to the `health.report.output.path` config path.
        * `runStaleSatisfiesEdgeCleanup()` — deletes `SATISFIES` edges not verified within `satisfies.stale.days`.
    * **Unified Vector Store:** UI element storage can use Neo4j (through the Neo4j Java driver), providing both graph relationships
      and vector search in a single database.
    * `vector.db.key` (or `VECTOR_DB_KEY`) must be set to a non-blank value.
    * `location.history.and.failure.hints.collection.enabled` (or `LOCATION_HISTORY_AND_FAILURE_HINTS_COLLECTION_ENABLED`) - disable location history and failure hints collection by default (recommended for local or supervised mode). Set to `true` to enable collection (recommended for CI/CD pipelines). Retrieval of existing hints and history remains enabled regardless of this setting.

## Test Case Execution Workflow

The test execution process, orchestrated by the [UiTestAgent](src/main/java/org/tarik/ta/UiTestAgent.java) class using the `KnowledgeBasedExecutionOrchestrator`, follows these steps:

1. **Test Case Processing:** The agent parses the received message (task)
   using [TestCaseExtractor](../agent_core/src/main/java/org/tarik/ta/core/utils/TestCaseExtractor.java),
   extracts the required information and converts it into a test case object. This file contains the overall test case name, optional
   `preconditions` (natural language description of the required state before execution), and a list of `TestStep`s. Each `TestStep`
   includes a `stepDescription` (natural language instruction), optional `testData` (inputs for the step), and `expectedResults`
   (natural language description of the expected state after the step).
2. **Starting Step Selection:** In Supervised mode, the operator can choose to start execution from a specific test step.
   In Unattended mode, execution always starts from the first step.
3. **Queue Creation:** Both test case preconditions (if any) and test steps are combined into a single unified `ExecutionQueue`. The `ExecutionStateTracker` is initialized to track accumulated effects throughout the test run.
4. **Knowledge-Based Iteration:** For each item in the queue (precondition or test step):
    * **Procedure Matching:** The agent queries the knowledge base for a matching procedure based on the item's description.
    * **State-Aware Resolution:** If candidates are found, the agent evaluates their prerequisites against the current execution state to select a feasible procedure.
    * **Human-in-the-Loop Fallback:** If no match is found, or if no candidate's prerequisites are met, the agent triggers a knowledge collection flow (in Supervised mode) for the operator to create or refine a procedure. In Unattended mode, the execution stops with result `ERROR`: the item gets an unsuccessful precondition result or a test step result with status `ERROR` carrying the selection reason (without the operator question "What do you want to do next ?", which only the supervised dialog asks), and a candidate procedure that matched with too low a confidence or with unsatisfied prerequisites gets a `STEP_FAILED` anomaly and a `USES_PROCEDURE` edge from the test case, so that the health report shows it as failing and in the blast radius of the test case.
    * **Decomposition:** Composite procedures are decomposed into their constituent atomic child steps. Each atomic step keeps the chain
      of composites it came from — see *Ancestor chain for decomposed steps* below.
5. **Atomic Step Execution:** For each resolved atomic step:
    * **Screenshot Capture:** A screenshot of the current screen is captured to provide visual context.
    * **Target Resolution:** If the atomic step is linked to a known UI element, the agent attempts to locate it directly, bypassing full semantic searches. Otherwise, standard element location logic applies.
    * **Action Execution:** The configured action agent (e.g., `UiTestStepActionAgent` or `UiPreconditionActionAgent`) interacts with the system using appropriate tools.
    * **Verification:** Following a short delay (`action.verification.delay.millis`), the verification agent confirms the step's expected results against a new screenshot using a vision model. The expected results, which may span several lines, are enclosed between two triple backtick lines in the verifier's message, so the verifier treats exactly that block as the verification scope and everything else as context. A test step's verifier also gets the screenshot captured right before the action, so it can confirm that the change the action should cause (e.g. a zoomed-in map) actually happened; differences unrelated to the action and the expected result (clock, notifications, other windows) are ignored. A precondition's verifier gets the new screenshot only.
    * **State Tracking:** Upon successful verification, the effects the atomic step negates are removed from the `ExecutionStateTracker` and its effects are added, influencing subsequent prerequisite resolution.
    * **Retry Logic:** If a tool execution or verification reports that retrying makes sense, the agent retries the execution/verification after a short delay up to a configured timeout.
6. **Completion/Termination:** Execution continues until the queue is exhausted or an interruption (error, unrecoverable verification failure, or user termination) occurs. The final `UiTestExecutionResult` is returned.

### Ancestor chain for decomposed steps

An atomic step taken out of its composites often reads as ambiguous: "Click 'Save'" says nothing about which screen or which record it
applies to. Every atomic step produced by a decomposition therefore carries the whole chain of composites it came from — outermost
first — assembled by `KnowledgeBasedExecutionOrchestrator` while it walks the hierarchy and passed along in
`AtomicStepExecutionContext.ancestorChain()`.
[AncestorChainRenderer](src/main/java/org/tarik/ta/knowledge_graph/execution/AncestorChainRenderer.java) turns the chain into the text
that goes into the prompts as the `## Procedure hierarchy` section, innermost first: level 1 is the `### Level 1: Direct parent (the procedure this step is a part of)` section, every further level `### Level N: Parent of level N-1`, and each level has the procedure description followed by only the `- Data`, `- Expected results` and `- Notes` list items it declares. The procedure the semantic search matched to the test step or precondition is followed by that item's data as `### Data of the whole test step` or `### Data of the whole test case precondition`. The headings (*Data relevant to this action*, *Data relevant to this precondition*, *Test execution context data*) are the exact terms the system prompts use for the same data, and [UserMessageSections](src/main/java/org/tarik/ta/utils/UserMessageSections.java) holds the ones the action and verification messages share.

* **Action prompts** (`UiTestStepActionAgent`, `UiPreconditionActionAgent`) get the chain as context. Their system prompts state
  explicitly that the levels are context and must never be executed — only the action the agent was given is executed.
* **Verification prompts** (`UiTestStepVerificationAgent`, `UiPreconditionVerificationAgent`) get the chain plus the executed step's own
  `additionalInfo`, and are likewise told not to verify the levels as separate expectations.
* **The confirmation prompt** in supervised mode shows the chain as a breadcrumb (`Place an order → Fill in the address`) instead of the
  immediate parent alone.

The data relevant to an atomic step depends on whether it has ancestors. An atomic procedure matched to the test step or precondition directly gets that item's data, or its own stored data when the item has none. A decomposed atomic step gets only its own stored data (the test data of a test-step procedure, the relevant data of a precondition procedure), never the item's data: that data belongs to the whole item and appears only after the outermost level of the chain, and each ancestor's data appears on its own level. The action system prompts state that the data of the whole test step (or test case precondition) only supplies values the executed action itself needs and never adds an interaction the action does not name, so an atomic "Click on the search field" does not type the dashboard name the test step carries.

### User message format

Every user message is built in Java with [MarkdownSections](../agent_core/src/main/java/org/tarik/ta/core/utils/MarkdownSections.java), so the model sees exactly where each piece of data starts and ends:

- A message may open with one instruction line (`Execute the action from the "Action" section.`); everything after it is sections.
- Every top-level piece of data is a `## <label>` section with the data on the lines below. There are at most two heading levels: `###` is used only for repeated items with several attributes (the procedure hierarchy levels, the DB candidates) and never deeper, so the structure stays flat and cheap to read.
- The attributes of one item are `- Label: value` list items (e.g. the target UI element details); a multi-line value continues on the lines below its item, indented by two spaces. Lists of short entries (known issues, bounding box IDs, test data, context entries) are Markdown lists, and a list inside an item is a nested list.
- A section without data is left out entirely, so an empty value costs no tokens and leaves no empty heading the model could fill with the next section.
- Heading lines inside the data are escaped (`## x` becomes `\## x`), so data can never open a section of its own.
- Each image gets its own heading directly before it: the action and precondition messages end with `## Screenshot`, the test step verification sends `## Screenshot before the action` and `## Screenshot after the action` right before the two images.
- The test execution context is rendered under `## Test execution context data` as a compact JSON array of its entries in execution order, grouped per step (see *Test execution context data* below).

#### Test execution context data

The test execution context (`TestExecutionContext`, agent_core) keeps an ordered list of entries, one `ContextEntry` per value, and never overwrites one:

- The data table of the test case is seeded at the start of the run; its entries belong to no step and apply to every precondition and test step.
- When a precondition or test step starts, it becomes the current step, gets the next execution number of the run (1, 2, 3 … over preconditions, test steps and injected prerequisites) and every non-blank line of its data is stored as an entry of that step: a `key=value` line under its key, any other line (e.g. `Dashboard name is 'Sales'`) without a name.
- A value a tool stores during a step (e.g. with the test context data tools) is an entry of the current step.
- The entries are rendered grouped per step, so a step is described once: every group has its `step` (`number`, `type`, `description`; absent for the data table), `belongsToCurrentStep` (absent for the data table) and `entries`, each with its `name` (absent for an unnamed line), `value` and `source` (`test case data table`, `test step data` or `stored during execution`), so the model can tell the data of the current step from the data of earlier ones. The action and verification prompts include the shared rules `common/execution_context/v1.1.0/execution_context_data.md` (agent_core): an entry of an earlier step is used only when the current step explicitly refers to it, an indirect reference such as "the specified panel" is never resolved with another step's data, even when exactly one would fit, and when neither the current step nor the test case provides the referenced data the agent performs nothing that depends on it and reports a failure naming the missing data.
- Tools that need a value itself (e.g. placeholder resolution) read the latest value of every named entry.

A test step action message looks like this:

```
Execute the action from the "Action" section.

## Action
Click 'Save'

## Data relevant to this action
- username=john

## Procedure hierarchy
Innermost first; every level is the parent of the previous one.

### Level 1: Direct parent (the procedure this step is a part of)
Create an order
- Data:
  - customer=ACME Corp
- Expected results: The order form is closed

## Test execution context data
[{"step":{"number":1,"type":"test step","description":"Create a draft order"},"belongsToCurrentStep":false,"entries":[{"name":"orderId","value":"17","source":"stored during execution"}]},{"step":{"number":2,"type":"test step","description":"Create an order"},"belongsToCurrentStep":true,"entries":[{"name":"username","value":"john","source":"test step data"}]}]

## Target UI element ID
E3

## Target UI element details
- Name: Save button
- Description: Blue 'Save' button with a disk icon
- Location details: Bottom right corner of the form
- Enclosing component (the component containing this UI element, e.g. a page, dialog, form, panel or pop-up): Order form dialog
- Depends on data: no

## Screenshot
```

The text reaches the agent as a `@UserMessage List<Content>` parameter (built with `MarkdownSections.userMessageContents`), never as a `String`: LangChain4j parses a `String` user message as a prompt template and fails on data containing `{{…}}` (secret placeholders, API variables), and it sends a lone `@UserMessage TextContent` argument twice. `@V` variables are used only for the system prompt placeholders (`execution_workflow`, `bounding_box_color`).

### UI Element Location Workflow

The [ElementLocatorTools](src/main/java/org/tarik/ta/tools/ElementLocatorTools.java) class is responsible for finding the coordinates of a target UI element based on its natural language description or known element ID provided during an action step. When locating a known UI element via `locateKnownElementById`, the action agent supplies the element ID and, only for an element which depends on data, the specific data which identifies it (`dataRelevantForLocatingTheElement`, e.g. `30` for "the last day of the displayed month"), which the action agent resolves itself from the element's description and location details and the data it was given, since the grounding model does not reason: never an instruction such as "select the last day". The tool returns the element's centre and region normalized to `[0, 1000]` of the screen, whatever format the grounding model answered in (see `bounding.box.already.normalized`). A box whose centre lies outside the screenshot, as a model answering in another coordinate space than configured produces, is discarded, so the location fails and can be retried, and a box reaching past an edge is clipped to the screenshot. The mouse tools take exactly these values and convert them to the logical screen coordinates of the `Robot` ([NormalizedCoordinates](src/main/java/org/tarik/ta/utils/NormalizedCoordinates.java)). An ID which is neither a known alias nor a UUID (e.g. one the model invented) does not fail the step: the tool's error message tells the model to use only the ID given in the task, and the tool error handler hands that message back to the model in both execution modes, up to the action retry policy's limit, after which the step fails. The retry budget of every tool error handler is scoped to one agent invocation (one step), so errors of earlier steps never shorten it. This involves a combination of RAG, computer vision, analysis, and potentially user interaction (if run in a supervised mode):

1. **RAG Retrieval:** The provided UI element's description is used to query the vector database, where the top N (`retriever.top.n`) most
   semantically similar `UiElement` records are retrieved based on their stored names, using embeddings generated by the configured
   `knowledge.embedding.model` (default `multilingual-e5-small`, see "Knowledge Configuration" below). Results are
   filtered based on configured minimum similarity scores (`element.retrieval.min.target.score`
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
      meet the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Supervised Mode:** The agent displays a popup showing a list of the low-scoring potential UI element candidates. The user can
          choose to:
            * **Update** one of the candidates by refining its name, description, anchors, or parent element info and save the updated
              information to the vector DB.
            * **Delete** a deprecated element from the vector DB.
            * **Create New Element** (see below).
            * **Retry Search** (useful if elements were manually updated).
            * **Terminate** the test execution (e.g., due to an AUT bug).
        * **Unattended Mode:** The location process fails.
    * **No Matches Found:** If no elements meet even the `MIN_GENERAL_RETRIEVAL_SCORE`:
        * **Supervised Mode:** The user is guided through the new element creation flow:
            1. The user draws a bounding box around the target element on a full-screen capture.
            2. The captured element screenshot with its description are sent to the vision model to generate a suggested detailed name,
               self-description, surrounding elements (anchors) description, and parent element info.
            3. The user reviews and confirms/edits the information suggested by the model.
            4. The new `UiElement` record (with UUID, name, descriptions, parent element info, screenshot) is stored into the vector DB.
        * **Unattended Mode:** The location process fails.

## Setup Instructions

### Prerequisites

* Java Development Kit (JDK) - Version 27 or later recommended (Amazon Corretto).
* Apache Maven - For building the project.
* Neo4j vector database (`vector.db.provider` still accepts `CHROMA`/`QDRANT` values, but only `NEO4J` is currently wired up in this module).
* Subscription to an AI model provider (Google Cloud/AI Studio, Azure OpenAI, or Groq).
* (Optional) Neo4j 2026.01 or newer for knowledge persistence feature (the vector searches use the Cypher 25 `SEARCH` clause).

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

Neo4j is the only vector database backend currently wired up in this module; see the "Neo4j Setup" section below.

### Neo4j Setup (for Knowledge Persistence)

For local development, start a Neo4j Community Edition container:

```bash
docker run -d --name neo4j-knowledge \
  -p 7687:7687 -p 7474:7474 \
  -e NEO4J_AUTH="neo4j/your-secure-password" \
  -e NEO4J_db_query_default__language=CYPHER_25 \
  -v neo4j-data:/data \
  neo4j:2026.09.0-community
```

A `neo4j-data` volume created by an older Neo4j keeps its Cypher 5 default until it is recreated or converted as the cloud startup script does (see the root [README's Neo4j deployment section](../README.md#cloud-deployment-dedicated-gce-vm)); the agent runs its queries as Cypher 25 either way.

Configure the agent by setting the following in `config.properties` or via environment variables:

```properties
vector.db.url=bolt://localhost:7687
neo4j.username=neo4j
vector.db.key=your-secure-password
neo4j.database=neo4j
```

The Vector DB key is always required. The agent will throw an error at startup if the key is not configured.

### Configuration

Configure the agent by editing the [config.properties](src/main/resources/config.properties) file or by setting environment variables. *
*Environment variables
override properties file settings.**

**Key Configuration Properties:**

**Basic Agent Configuration:**

* `execution.mode` (Env: `EXECUTION_MODE`): Mode of execution (`SUPERVISED`, `UNATTENDED`). Default: `SUPERVISED`.
* `supervised.countdown.seconds` (Env: `SUPERVISED_COUNTDOWN_SECONDS`): Duration in seconds of the confirmation prompt's countdown in supervised
  mode, after which the step proceeds. Default: `5`.
* `debug.mode` (Env: `DEBUG_MODE`): `true` enables debug mode, which saves intermediate screenshots (e.g., with bounding boxes drawn)
  during element location for debugging purposes. `false` disables this. Default: `false`.
* `port` (Env: `PORT`): Port for the server mode. Default: `8005`.
* `host` (Env: `AGENT_HOST`): Host address for the server mode. (required, no default).
* `agent.version` (Env: `AGENT_VERSION`): Version advertised in the A2A agent card (`MAJOR.MINOR.PATCH`). (required, no default).
* `LOG_LEVEL` (Env: `LOG_LEVEL`): Global log level for the agent and key dependencies like LangChain4j and A2A SDK. Default: `INFO`.
* `test.data.folder` (Env: `TEST_DATA_FOLDER`): The only folder the test context data tools may read files from; relative paths resolve against the agent's working directory. Default: `test-data`.
* `sensitive.data.key.pattern` (Env: `SENSITIVE_DATA_KEY_PATTERN`): Regex matched against test data keys; the values of matching keys are masked as `{{secret:<key>}}` in prompts, dialogs, logs and artifacts and resolved back only by the keyboard tools when they type the text. Default: `(?i).*(password|passwd|pwd|pin|token|secret|api[_-]?key|credential).*`.

**RAG Configuration:**

* `vector.db.provider` (Env: `VECTOR_DB_PROVIDER`): Vector database provider. Default: `neo4j`.
* `vector.db.url` (Env: `VECTOR_DB_URL`): Required URL for the vector database connection (required, no default).
* `retriever.top.n` (Env: `RETRIEVER_TOP_N`): Number of top similar elements to retrieve from the vector DB based on semantic element name
  similarity. Default: `5`.

**Neo4j Configuration:**

* `neo4j.username` (Env: `NEO4J_USERNAME`): Neo4j username. Default: `neo4j`.
* `neo4j.database` (Env: `NEO4J_DATABASE`): Neo4j database name. Default: `neo4j`.
* `neo4j.max.connection.pool.size` (Env: `NEO4J_MAX_CONNECTION_POOL_SIZE`): Maximum Neo4j driver connection pool size. Default: `20`.
* `neo4j.connection.acquisition.timeout.seconds` (Env: `NEO4J_CONNECTION_ACQUISITION_TIMEOUT_SECONDS`): Timeout for acquiring a
  connection from the pool. Default: `30`.
* `neo4j.connection.timeout.seconds` (Env: `NEO4J_CONNECTION_TIMEOUT_SECONDS`): Neo4j connection timeout in seconds. Default: `10`.
* `neo4j.max.transaction.retry.time.seconds` (Env: `NEO4J_MAX_TRANSACTION_RETRY_TIME_SECONDS`): Maximum time the driver retries a
  managed transaction. Default: `120`.

**Knowledge Configuration:**

* `knowledge.embedding.model` (Env: `KNOWLEDGE_EMBEDDING_MODEL`): Embedding model used for knowledge-graph semantic search and procedure matching. Default: `multilingual-e5-small`. Supported values:
    * `multilingual-e5-small` (default) — 384-dimensional multilingual model. On first startup the ONNX model files (`model.onnx`, `tokenizer.json`) are automatically downloaded from Hugging Face (`intfloat/multilingual-e5-small`) to `~/.cache/test-execution-agents/models/multilingual-e5-small/`. Document embeddings (ingestion) use the `"passage: "` prefix; query embeddings (semantic search) use the `"query: "` prefix.
    * `bge-small-en-v15` — 384-dimensional English-only model bundled in the JAR; no download required. Suitable for English-only test suites.
    * **Rollout / Migration Note:** Switching active embedding models after data has been ingested requires re-embedding all stored procedures to avoid vector mismatch. The agent performs startup validation checks to ensure the resolved model's dimensions match the required Neo4j vector indexes (384-dimensional). Unsupported values or dimension mismatches will abort application startup.
* `knowledge.max.depth` (Env: `KNOWLEDGE_MAX_DEPTH`): Maximum procedure decomposition depth. Default: `3`.
* `knowledge.embedding.batch.size` (Env: `KNOWLEDGE_EMBEDDING_BATCH_SIZE`): Batch size for embedding generation. Default: `10`.
* `knowledge.match.confidence.high` (Env: `KNOWLEDGE_MATCH_CONFIDENCE_HIGH`): Auto-execute floor — a match at or above it is executed
  directly. Default: `0.95` (raw cosine `0.90`).
* `knowledge.match.confidence.low` (Env: `KNOWLEDGE_MATCH_CONFIDENCE_LOW`): Candidate floor — below it a procedure is not a candidate at
  all. Default: `0.88` (raw cosine `0.76`).

**Similarity scale and the matching bands:** every similarity threshold of this agent is on Neo4j's normalised scale, where a
vector index returns `score = (1 + cosine) / 2`; the raw cosine of a configured value is therefore `2 · score − 1`. The code defaults and
the values in `config.properties` are identical, so neither silently overrides the other. A semantic score alone cannot separate a
paraphrase from a swapped value — with the bundled BGE model, "Select 'Germany'" against "Select 'France'" scores `0.966` and "logged in"
against "logged out" scores `0.973`. The auto-execute floor is therefore set high enough that only a near-verbatim match runs on its own,
and matches are resolved in three bands: below `knowledge.match.confidence.low` the procedure is treated as unknown; between the two
thresholds the candidate is too close to a value or element swap to execute unattended, so it goes to the operator in supervised mode and
fails the run in unattended mode; at or above `knowledge.match.confidence.high` the match is near-verbatim and is executed directly. Note
that the default `bge-small-en-v15` model is English-only and scores unrelated German phrases far too high ("Speichern" against
"Abbrechen" scores `0.985`); set `knowledge.embedding.model=multilingual-e5-small` for non-English procedures.
* `knowledge.match.top.n` (Env: `KNOWLEDGE_MATCH_TOP_N`): Number of top candidate procedures retrieved during knowledge matching. Default: `5`.
* `procedure.lookup.delay.ms` (Env: `PROCEDURE_LOOKUP_DELAY_MS`): How long the searches of the operator prompts wait after the last keystroke before they run. Default: `2000`.

**Knowledge Graph Enhancement Configuration:**

* `satisfies.similarity.threshold` (Env: `SATISFIES_SIMILARITY_THRESHOLD`): Candidate floor for effect-to-prerequisite matching. Default: `0.90` (raw cosine `0.80`). It governs both the creation of a `SATISFIES` edge between procedures and the prerequisite-satisfaction check performed during candidate re-ranking.
* `satisfies.stale.days` (Env: `SATISFIES_STALE_DAYS`): Number of days after which a `SATISFIES` edge not verified is considered stale. Default: `30`.
* `ancestry.window.size` (Env: `ANCESTRY_WINDOW_SIZE`): Number of recent parent procedure IDs tracked for ancestry affinity boosting during re-ranking. Default: `5`.
* `timing.ewma.alpha` (Env: `TIMING_EWMA_ALPHA`): EWMA smoothing factor for procedure execution timing profiles. Default: `0.2`.
* `timing.verification.min.delay.ms` (Env: `TIMING_VERIFICATION_MIN_DELAY_MS`): Minimum verification delay (floor) applied even when the timing profile suggests a shorter wait. Default: `500`.
* `stability.ewma.alpha` (Env: `STABILITY_EWMA_ALPHA`): EWMA smoothing factor for element stability score updates. Default: `0.3`.
* `stability.penalty.threshold` (Env: `STABILITY_PENALTY_THRESHOLD`): Elements with a stability score below this value are flagged as low-stability. A warning is logged in all modes; in supervised mode a warning popup is shown right before the procedure starts executing. Default: `0.5`.
* `failure.capture.dialog.timeout.seconds` (Env: `FAILURE_CAPTURE_DIALOG_TIMEOUT_SECONDS`): Countdown timeout for the failure context capture dialog in supervised mode. Auto-captures as `SUPERVISED_TIMEOUT` on expiry. Default: `60`.
* `health.report.output.path` (Env: `HEALTH_REPORT_OUTPUT_PATH`): Output path for the generated HTML health report. Default: `reports/graph-health-report.html`.
* `health.warning.threshold` (Env: `HEALTH_WARNING_THRESHOLD`): Finding count at which a health check category escalates to WARNING severity. Default: `3`.
* `health.critical.threshold` (Env: `HEALTH_CRITICAL_THRESHOLD`): Finding count at which a health check category escalates to CRITICAL severity. Default: `10`.
* `health.recent.window.days` (Env: `HEALTH_RECENT_WINDOW_DAYS`): Window in days of the health report categories about recent activity (volatile UI elements, recent execution anomalies, failing procedures, unproven procedures). Must be at least `1`. Default: `30`.
* `element.appearance.history.size` (Env: `ELEMENT_APPEARANCE_HISTORY_SIZE`): Expired appearances (earlier screenshot and descriptions) kept per UI element; older ones are deleted, because screenshots make an unbounded history expensive. Must be at least `1`. Default: `3`.
* `failure.hints.stale.days` (Env: `FAILURE_HINTS_STALE_DAYS`): Days without recurrence after which a failure hint expires at startup; an expired hint is deleted at startup once it has been expired for the same number of days. Must be at least `1`. Default: `90`.
* `failure.hints.max.count` (Env: `FAILURE_HINTS_MAX_COUNT`): The most failure hints of a procedure passed to the step prompts and returned by the failure hints tool. Must be at least `1`. Default: `5`.
* `anomaly.recording.enabled` (Env: `ANOMALY_RECORDING_ENABLED`): Records failed element locations, failed steps and timing outliers as `ExecutionAnomaly` nodes; independent of `location.history.and.failure.hints.collection.enabled`. Default: `true`.
* `anomaly.history.size` (Env: `ANOMALY_HISTORY_SIZE`): Execution anomalies kept per procedure or UI element; older ones are deleted. Must be at least `1`. Default: `20`.
* `run.history.size` (Env: `RUN_HISTORY_SIZE`): Newest runs kept in the knowledge graph with their step resolutions (see *Run history*); older ones are deleted when a run finishes. Must be at least `1`. Default: `500`.
* `anomaly.timing.outlier.factor` (Env: `ANOMALY_TIMING_OUTLIER_FACTOR`): A successful step whose execution time exceeds this factor times its previous average execution time is recorded as a timing outlier. Must be greater than `1`. Default: `3.0`.

**Model Configuration:**

* `model.max.output.tokens` (Env: `MAX_OUTPUT_TOKENS`): Maximum amount of tokens for model responses. Default: `5000`.
* `model.temperature` (Env: `TEMPERATURE`): Sampling temperature for model responses. Default: `0.0`. Applies to every model
  except an `hcompany` one driving a sub-agent, which uses `hcompany.agent.temperature` instead, and an `anthropic` one, which is sent no temperature because newer Claude models such as Haiku 5.5 reject a non-default value.
* `hcompany.agent.thinking.enabled` (Env: `HCOMPANY_AGENT_THINKING_ENABLED`): Whether an `hcompany` model driving a sub-agent
  thinks before it acts. Default: `true`, and it is what makes such an agent's tool-calling loop finish at all - measured 6/6
  loops reaching the final-result tool with it on and 0/6 with it off, the model otherwise announcing completion in prose and
  ending the loop with no typed result. Grounding is never affected: it stays schema-constrained with thinking off, because a
  constrained answer emitted inside an open think block arrives as reasoning with the content empty.
* `hcompany.agent.temperature` (Env: `HCOMPANY_AGENT_TEMPERATURE`): Sampling temperature for an `hcompany` model driving a
  sub-agent. Default: `0.8`, deliberately not `model.temperature` - the same loop measured 3263 ms at 0.8 against 6694 ms at
  0.0, greedy decoding making this model reason longer and more repetitively for no gain in accuracy. The shipped `config.properties` sets `0.4`.
* `model.top.p` (Env: `TOP_P`): Top-P sampling parameter. Default: `1.0`.
* `model.max.retries` (Env: `MAX_RETRIES`): Max retries for model API calls. Default: `10`.
* `model.max.image.pixels` (Env: `MODEL_MAX_IMAGE_PIXELS`): Pixel budget a full-detail screenshot may occupy, which the `<agent>.image.detail.level` fractions are taken from for the providers that ignore the detail hint (`hcompany`, `qwen`). Keep it at or below the `max_pixels` the serving stack is deployed with; set it higher and the server silently downscales instead. Default: `2073600` (1920x1080).
* `model.logging.enabled` (Env: `LOG_MODEL_OUTPUT`): Enable/disable model logging. Default: `false`. For the self-hosted
  providers (`hcompany`, `qwen`) this logs the raw response body of every model call under the `model.http.responses`
  logger, including the `reasoning` field; requests are never logged, because they carry every screenshot as base64.
* `thinking.output.enabled` (Env: `OUTPUT_THINKING`): Enable/disable thinking process output. Default: `false`.
* `gemini.thinking.level` (Env: `GEMINI_THINKING_LEVEL`): Gemini thinking configuration level. Default: `MINIMAL`.
* `verification.model.max.retries` (Env: `VERIFICATION_MODEL_MAX_RETRIES`): Retries for verification models. Default: `3`.

**Google API Configuration:**

* `google.api.provider` (Env: `GOOGLE_API_PROVIDER`): Google API provider (`studio_ai` or `vertex_ai`). Default: `studio_ai`.
* `google.api.token` (Env: `GOOGLE_API_KEY`): API Key for Google AI Studio. Required if using AI Studio.
* `google.project` (Env: `GOOGLE_PROJECT`): Google Cloud Project ID. Required if using Vertex AI.
* `google.location` (Env: `GOOGLE_LOCATION`): Google Cloud location (region). Required if using Vertex AI.

**OpenAI / Azure OpenAI API Configuration:**

* `openai.api.key` (Env: `OPENAI_API_KEY`): API Key for (Azure) OpenAI. Required if using OpenAI.
* `openai.endpoint` (Env: `OPENAI_ENDPOINT`): Endpoint URL for (Azure) OpenAI. Default: `https://api.openai.com/v1/`.

**Groq API Configuration:**

* `groq.api.key` (Env: `GROQ_API_KEY`): API Key for Groq. Required if using Groq.
* `groq.endpoint` (Env: `GROQ_ENDPOINT`): Endpoint URL for Groq. Required if using Groq.

**Anthropic API Configuration:**

* `anthropic.api.provider` (Env: `ANTHROPIC_API_PROVIDER`): Anthropic API provider (`anthropic_api` or `vertex_ai`). Default:
  `anthropic_api`.
* `anthropic.api.key` (Env: `ANTHROPIC_API_KEY`): API Key for Anthropic. Required if using Anthropic.
* `anthropic.endpoint` (Env: `ANTHROPIC_ENDPOINT`): Endpoint URL for Anthropic. Default: `https://api.anthropic.com/v1/`.
* `anthropic.thinking.enabled` (Env: `ANTHROPIC_THINKING_ENABLED`): Whether a Claude model on `anthropic_api` thinks adaptively before it acts. Default: `false`, which sends thinking disabled with a forced tool choice and keeps the sub-agents fast; `true` sends adaptive thinking and leaves the tool choice to the model, because a forced tool choice makes Claude skip thinking. `model.max.output.tokens` caps the thinking and the answer together.
* `anthropic.thinking.effort` (Env: `ANTHROPIC_THINKING_EFFORT`): Effort of a thinking Claude model (`low`, `medium`, `high`, `xhigh` or `max`). Default: `medium`. Ignored while thinking is off.

**Self-Hosted Model Endpoints:**

* `hcompany.endpoint` (Env: `HCOMPANY_ENDPOINT`): Base URL (`https://<service-url>/v1/`) of the Holo3.1 service from `deployment/holo`. Required only for a sub-agent switched to the `hcompany` provider (the bundled `config.properties` runs every sub-agent on `google` with `gemini-3.8-flash` and ships only a placeholder).
* `qwen.endpoint` (Env: `QWEN_ENDPOINT`): Base URL (`https://<service-url>/v1/`) of an OpenAI-compatible server such as vLLM serving a Qwen model. Required only for an agent switched to the `qwen` provider.
* `qwen.agent.thinking.enabled` (Env: `QWEN_AGENT_THINKING_ENABLED`): Whether a `qwen` model driving a sub-agent thinks before it acts. Default: `true`. Grounding is never affected: it stays schema-constrained with thinking off.
* `hcompany.api.key` (Env: `HCOMPANY_API_KEY`) and `qwen.api.key` (Env: `QWEN_API_KEY`): API keys for the `hcompany` and `qwen` endpoints. Ignored for a Cloud Run endpoint (`*.run.app`), which is called with an identity token minted from application default credentials instead.

**Timeout and Retry Configuration:**

* `test.step.execution.retry.timeout.millis` (Env: `TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed test case
  actions. Default: `10000 ms`.
* `test.step.execution.retry.interval.millis` (Env: `TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS`): Delay between test case action retries.
  Default: `1000 ms`.
* `verification.retry.timeout.millis` (Env: `VERIFICATION_RETRY_TIMEOUT_MILLIS`): Timeout for retrying failed verifications. Default:
  `10000 ms`.
* `action.verification.delay.millis` (Env: `ACTION_VERIFICATION_DELAY_MILLIS`): Delay after executing a test case action before performing
  the corresponding verification. Default: `1000 ms`.
* `max.action.execution.duration.millis` (Env: `MAX_ACTION_EXECUTION_DURATION_MILLIS`): Maximum duration for a single action execution.
  Default: `15000 ms`.
* `mouse.scroll.notches.per.attempt` (Env: `MOUSE_SCROLL_NOTCHES_PER_ATTEMPT`): Mouse wheel notches the scroll-until-state tool turns per attempt before it checks the expected state again; the whole scrolling is bounded by `max.action.execution.duration.millis`. Must be positive. Default: `3`.
* `element.resolution.max.duration.millis` (Env: `ELEMENT_RESOLUTION_MAX_DURATION_MILLIS`): Budget for a single interactive UI element
  resolution started from the procedure editor, where an operator is waiting and the automated-action timeout is far too short. When it
  expires, the worker thread is interrupted and the operator is told the location *timed out after N ms*, distinctly from a resolution
  that failed; cancellation is best effort, since the agent only stops at its next interruptible point. When the resolution is interrupted instead (its prompt closed), the worker is interrupted too and awaited for the rest of this budget, so it no longer drives the agent once the prompt is gone. Default: `300000 ms`.
* `execution.verification.atomic.enabled` (Env: `EXECUTION_VERIFICATION_ATOMIC_ENABLED`): Whether every atomic procedure gets its own
  verification pass, instead of verifying only at coarser granularity. Default: `true`.

**Budget Management Configuration:**

* `agent.token.budget` (Env: `AGENT_TOKEN_BUDGET`): Maximum total tokens that can be consumed across all models. Default: `1000000`; the bundled `config.properties` sets `10000000`.
* `agent.tool.calls.budget.unattended` (Env: `AGENT_TOOL_CALLS_BUDGET_UNATTENDED`): Maximum tool calls per agent in unattended mode. Default: `5`.
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
  capture in supervised mode. This value should be tuned so that the color contrasts as much as possible with the average UI element color.
* `element.retrieval.min.target.score` (Env: `ELEMENT_RETRIEVAL_MIN_TARGET_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval. Elements reaching this score are treated as target element candidates. Default: `0.93`.
* `element.retrieval.min.general.score` (Env: `ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE`): Minimum semantic similarity score for vector DB UI
  element retrieval for potential matches. Default: `0.87`.
* `element.retrieval.min.page.relevance.score` (Env: `ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE`): Minimum page relevance score for vector
  DB UI element retrieval. Default: `0.5`.
* `element.locator.visual.similarity.threshold` (Env: `VISUAL_SIMILARITY_THRESHOLD`): OpenCV template matching threshold. Default: `0.8`.
* `element.locator.top.visual.matches` (Env: `TOP_VISUAL_MATCHES_TO_FIND`): Maximum number of visual matches to pass to the AI model.
  Default: `3`.
* `element.locator.found.matches.dimension.deviation.ratio` (Env: `FOUND_MATCHES_DIMENSION_DEVIATION_RATIO`): Maximum allowed deviation
  ratio for the dimensions of a found visual match. Default: `0.3`.
* `element.locator.visual.grounding.model.vote.count` (Env: `VISUAL_GROUNDING_MODEL_VOTE_COUNT`): Number of visual grounding votes. Default:
  `5`.
* `element.locator.validation.model.vote.count` (Env: `VALIDATION_MODEL_VOTE_COUNT`): Number of validation model votes. Default: `3`.
* `element.locator.bbox.clustering.min.intersection.ratio` (Env: `BBOX_CLUSTERING_MIN_INTERSECTION_RATIO`): Minimum IoU ratio for
  clustering bounding boxes. Default: `0.7`.
* `element.locator.zoom.scale.factor` (Env: `ELEMENT_LOCATOR_ZOOM_SCALE_FACTOR`): Zoom scale factor for element location. Default: `1`.
* `element.locator.algorithmic.search.enabled` (Env: `ALGORITHMIC_SEARCH_ENABLED`): Enable/disable OpenCV algorithmic search. Default:
  `true`.
* `element.locator.skip.model.selection.vision.only` (Env: `SKIP_UI_ELEMENT_SELECTION_FOR_VISION`): When enabled, skip the model
  selection step when only visual grounding results are available (no algorithmic matches). In this case, the first identified element
  from the visual grounding results is returned directly without additional model validation. This can speed up element location when
  algorithmic search is disabled. Default: `true`.
* `element.locator.grounding.output.format` (Env: `ELEMENT_LOCATOR_GROUNDING_OUTPUT_FORMAT`): Shape in which the grounding model reports an element's location: `BOUNDING_BOX` (two opposite corners, for general-purpose vision models such as Gemini and Claude) or `CENTER_AND_SIZE` (click position plus size, for GUI grounding models such as Holo3.1; with the `hcompany` and `qwen` providers the click position alone is requested through a JSON schema). Default: `BOUNDING_BOX`, which the bundled `config.properties` also sets for its Gemini sub-agents; set `CENTER_AND_SIZE` when the grounding agent runs on Holo3.1.
* `bounding.box.already.normalized` (Env: `BOUNDING_BOX_ALREADY_NORMALIZED`): Whether the grounding model answers in coordinates normalized to `[0, 1000]` (Holo, Qwen, Gemini) instead of in pixels of the screenshot it was sent (Anthropic models, whose screenshot is first downscaled to `bbox.screenshot.*` and whose answer is scaled back). Either way the action models receive coordinates normalized to `[0, 1000]`. Default: `false`.
* `bbox.screenshot.longest.allowed.dimension.pixels` (Env: `BBOX_SCREENSHOT_LONGEST_ALLOWED_DIMENSION_PIXELS`): Maximum screenshot
  dimension. Default: `1568`.
* `bbox.screenshot.max.size.megapixels` (Env: `BBOX_SCREENSHOT_MAX_SIZE_MEGAPIXELS`): Maximum screenshot size in megapixels. Default:
  `1.15`.
* `hdr.color.correction.enabled` (Env: `HDR_COLOR_CORRECTION_ENABLED`): Apply sRGB gamma correction to screenshots captured on HDR-enabled
  monitors so saved screenshots and model inputs do not look washed out. Default: `false`.

**Agent-Specific Model Configuration:**

Each specialized agent can be configured with its own model and prompt version using the following pattern:

* `<agent>.model.name`: Model name for the agent
* `<agent>.model.provider`: Model provider (`google`, `openai`, `groq`, `anthropic`, `hcompany`, or `qwen`)
* `<agent>.prompt.version`: System prompt version
* `<agent>.image.detail.level`: How closely this agent's model should look at the screenshot - `low`, `medium`, `high`,
  `ultra_high` or `auto`. How it is applied depends on the agent's model provider:
  * `google`, `openai`, `groq` and `anthropic` honour the detail hint, so the level is sent along with the screenshot
    and the model itself decides how much of it to process.
  * `hcompany` and `qwen` are served by vLLM, which accepts the hint and ignores it - measured as identical token
    counts and identical answers for `low`, `high` and `auto`. For those the level is spent on resolution instead: the
    screenshot is downscaled to a share of `model.max.image.pixels` before it is sent (`low` a quarter, `medium` a
    half, `high` / `ultra_high` / `auto` all of it). Images are never upscaled, and target dimensions are snapped to
    multiples of 32 so the server's own rounding becomes a no-op.

Available agents and their configuration prefixes:

* `precondition.agent.*`: Precondition Action Agent
* `precondition.verification.agent.*`: Precondition Verification Agent
* `test.step.action.agent.*`: Test Step Action Agent
* `test.step.verification.agent.*`: Test Step Verification Agent
* `test.case.extraction.agent.*`: Test Case Extraction Agent
* `ui.element.description.extraction.agent.*`: UI Element Description Extraction Agent
* `ui.element.description.matcher.agent.*`: UI Element Extended Description ("matcher") Agent
* `ui.state.check.agent.*`: UI State Check Agent
* `element.bounding.box.agent.*`: Element Bounding Box Agent
* `element.selection.agent.*`: Element Selection Agent
* `db.element.selection.agent.*`: DB Element Selection Agent (disambiguates multiple DB matches)
* `knowledge.suggestion.agent.*`: Knowledge Suggestion Agent
* `knowledge.collection.element.resolution.agent.*`: Knowledge Collection Element Resolution Agent

**Example agent configuration:**

```properties
precondition.agent.model.name=gemini-3.8-flash
precondition.agent.model.provider=google
precondition.agent.prompt.version=v1.1.0
```

All prompts ship at `v1.0.0` except those of the action and verification agents, which ship at `v1.1.0`: an indirect reference of the current step (e.g. "the specified order") is never resolved with the data of an earlier step, even when exactly one would fit, and when neither the step nor the test case provides that data the agent performs nothing that depends on it and reports a failure naming the missing data. The action and verification agents (`precondition.agent`, `test.step.action.agent`, `precondition.verification.agent`, `test.step.verification.agent`) follow the rules for the step-attributed test execution context data (see *Test execution context data*), and the test step verification agent never takes the data an expected result refers to (e.g. "the specified panel") from an earlier step. The verification agents (`precondition.verification.agent`, `test.step.verification.agent`) and the action agents (`precondition.agent`, `test.step.action.agent`) describe the step hierarchy as rendered innermost first with relation labels, each level stating only the details it declares and the outermost one also the data of the whole test step or test case precondition (see *Ancestor chain for decomposed steps*). The action agents fail an action which none of their tools can perform instead of substituting a tool of a different kind (e.g. clicking instead of scrolling), and without a target UI element ID they never call a tool that takes screen coordinates; they also never invent a UI element ID (the location tool takes only the ID given in the task), and a tool result stating that the expected state was reached ends that action instead of triggering a repeat.

**Operator Prompt Settings:**

* `failure.context.dialog.countdown.seconds` (Env: `FAILURE_CONTEXT_DIALOG_COUNTDOWN_SECONDS`): Countdown after which the
  failure-context prompt closes without capturing anything, so a run nobody is watching is not blocked by it. The prompt shows the
  remaining time and holds the countdown as soon as the operator starts filling the form in. Default: `30`.

**Operator Panel Settings:**

* `panel.port` (Env: `PANEL_PORT`): Listen port of the operator panel. Default: `8443`.
* `panel.host` (Env: `PANEL_HOST`): Bind address of the operator panel; the Docker deployments set `0.0.0.0`. Default: `127.0.0.1`.
* `panel.password` (Env: `PANEL_PASSWORD`): The login secret, machine-generated (e.g. `openssl rand -base64 32`) and at least 16 characters long. The agent refuses to start with a shorter one, and with none in supervised mode or when the panel binds to a non-loopback address; an unattended agent with none on a loopback address starts without the panel and logs a warning. Default: empty.
* `panel.tls.cert` / `panel.tls.key` (Env: `PANEL_TLS_CERT` / `PANEL_TLS_KEY`): The PEM certificate chain and private key of the panel, as contents, not paths; both or neither must be set. Without them the panel serves plain HTTP and logs a warning, which is meant for local runs only. Default: empty.
* `panel.external.url` (Env: `PANEL_EXTERNAL_URL`): The panel URL the agent card shows as plain text; empty shows none. Default: empty.
* `panel.session.idle.timeout.minutes` (Env: `PANEL_SESSION_IDLE_TIMEOUT_MINUTES`): Idle expiry of the session; an open event stream counts as activity, so a tab that only watches stays logged in. Default: `30`.
* `panel.session.absolute.timeout.hours` (Env: `PANEL_SESSION_ABSOLUTE_TIMEOUT_HOURS`): Absolute expiry of the session, which also ends a watching tab. Default: `8`.
* `panel.prompt.max.wait.minutes` (Env: `PANEL_PROMPT_MAX_WAIT_MINUTES`): Upper bound of a prompt without a countdown, or with a held one: when it elapses the prompt's default applies, and a prompt without a default fails the run with a timeout instead of holding the A2A task forever. Default: `120`.
* `panel.live.control.enabled` (Env: `PANEL_LIVE_CONTROL_ENABLED`): Lets the operator drive the agent's screen through the live view; `false` keeps every viewer watch-only. The container's VNC start script reads the same variable, because Xvnc enforces it. Prompts are answered in the panel itself, so supervised deployments need it only to work on the agent's screen by hand. Default: `false`.
* `vnc.socket.path` (Env: `VNC_SOCKET_PATH`): The Xvnc Unix domain socket the live view relays to (`/tmp/xvnc.sock` in the Docker images); empty means no live view. Default: empty.

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
4. Send a `POST` request to the root endpoint (`/`) with the correct A2A message. Use `message/stream` to receive incremental progress
   and logs as a Server-Sent Events stream, or `message/send` for a single aggregated response.
5. The server starts the test case execution if it accepts the request (i.e., not already running a test case) or returns
   `429 Too Many Requests` if it's busy. Streaming clients receive, before each step/precondition runs, a `working` status update
   announcing the item about to execute (an `ExecutionActivity` payload for live dashboards), followed by step results and log lines as they
   are produced; in both cases the execution concludes by emitting a single consolidated `TestExecutionResult` (full result JSON + logs file
   + screenshots), so a `message/send` caller gets one ready-to-consume result object. A run that breaks unexpectedly still reports what it
   had captured: the agent's outer failure path returns the captured logs and the recording path instead of an empty result, and the A2A
   executor flushes the buffered log lines before the terminal completion *or* failure event, since a task accepts no further artifact once
   it is terminal.
6. With `panel.password` set, the operator panel starts on `panel.port` before the A2A server; open `http://localhost:8443` and log in. The Maven build downloads Node and builds the panel's frontend (see *Building the Operator Panel Frontend* in the root README); a backend-only build with `-Dskip.installnodenpm -Dskip.npm` serves the panel's API without its pages. In a local supervised run the agent captures and drives the primary screen, so keep the panel's browser on another monitor, or run the agent in Docker.
7. The `@Disabled` manual tests (e.g. `ManualTest`, `GrafanaManualTest`) run a test case in-process on the current screen through `ManualAgentRunner`, which bootstraps the agent like the server, starts the operator panel and runs the test case through the A2A executor, so the panel shows the run and answers its prompts. They need the same environment as the server, `panel.password` included in supervised mode.

### Generating the Knowledge Graph Health Report

With the agent running, the *Health* tab of the operator panel shows the report: the agent runs the check against its own Neo4j when the tab is first shown and on every Refresh.

Without a running agent, a standalone CLI tool generates a self-contained HTML health report. It connects directly to Neo4j and writes the report to the configured (or specified) output path.

**Using the provided scripts (recommended):**

```bash
# Unix/macOS — generate report to default path (reports/graph-health-report.html)
scripts/generate-health-report.sh

# Unix/macOS — generate report to a custom path
scripts/generate-health-report.sh --output /tmp/my-report.html

# Windows — generate report to default path
scripts\generate-health-report.bat

# Windows — generate report to a custom path
scripts\generate-health-report.bat --output C:\reports\my-report.html
```

Both scripts require `JAVA_HOME` to be set and the Neo4j connection configured via `config.properties` or environment variables (`VECTOR_DB_URL`,
`NEO4J_USERNAME`, `NEO4J_DATABASE`, `VECTOR_DB_KEY`).

**Using Maven directly:**

```bash
mvn exec:java -pl ui_test_execution_agent \
    -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli \
    -Dexec.args="--output path/to/report.html"
```

The HTML report includes a color-coded summary card per health category (green=OK, yellow=WARNING, red=CRITICAL) and collapsible detail
sections listing individual findings. All finding text is HTML-escaped to prevent XSS when procedure names contain special characters.

## Smoke Tests

`src/test/java/org/tarik/ta/smoke/UiAgentSmokeTest` is a hermetic, end-to-end smoke suite that drives the **real** knowledge-based
execution flow (`UiTestAgent` -> `KnowledgeBasedExecutionOrchestrator` -> `StepExecutionOrchestrator` -> `VerificationTools`, plus the real
execution queue, state tracker and prefetch coordinator) and mocks only the external boundaries:

- the four LLM agents (test-step / precondition action + verification) are Mockito mocks, stubbed at `executeAndGetResult`, so the real
  orchestration, result-merging and `UiTestExecutionResult` contract all run unchanged;
- `KnowledgeService` (the Neo4j boundary) is a mock that returns in-memory `Procedure` matches;
- the screen is mocked via a `MockedStatic` of `UiCommonUtils.captureScreen()`, which sits above every AWT `Robot` call, so the suite is
  xvfb/headless-safe;
- the config, `ScreenRecorder`, `LogCapture` and the remaining knowledge-graph services are Mockito mocks.

The whole object graph is wired by hand (no Avaje DI bootstrap). Most tests run unattended; the supervised ones start a real operator panel on a random port and answer its prompts through its HTTP API with a scripted operator that follows the event stream (starting from a later step; unsatisfied prerequisites answered with a test step rerun from the next-action prompt; a run cancelled while the start-step prompt is open, which ends with no open prompt and the panel showing it aborted; a procedure created from the next-action prompt, recorded as `OPERATOR_CREATED`). Coverage: single-step and multi-step happy paths (asserting `SystemInfo`, logs and the recording path on the result), a composite A run over a nested composite reports every procedure node started and finished in nesting order to the run observer, and records the run, the item's `KNOWLEDGE` resolution with its candidate's semantic score and the chosen procedure, and the `PASSED` end through the mocked `ExecutionRunRepository`.
procedure decomposed into its atomic children (both executed), composite variants where a child's verification fails (-> `FAILED`) or a
child's action agent throws (-> `ERROR`), a step verification that fails once and recovers on its retry attempt, a step verification that
receives the screenshots captured before and after the action, each right after its own heading, a failing precondition
action or verification (-> `ERROR`), a failed step verification (-> `FAILED`, with the screenshot attached to the step result), an
unexpected action-agent exception (-> `ERROR`), a budget-exhaustion exception propagated with its message preserved (-> `ERROR`), a missing
procedure (-> `ERROR`, with an `ERROR` step result carrying the selection reason), a low-confidence match in unattended mode (-> `ERROR`, with a `STEP_FAILED` anomaly and a `USES_PROCEDURE` edge for the matched procedure and no action executed), and the streaming events emitted through the `StreamingEventEmitter` (started + result events for the precondition
and the step, in order — the HTTP + SSE transport itself is covered by the `agent_core` suite).

A click step runs the real `ElementLocatorTools` and `MouseTools` on a mocked 4K screen scaled by 150%: the grounding model answers in pixels of a screenshot downscaled by half, as the Anthropic models do, and the suite asserts the sections of the action message (the action, the target UI element ID and details, the closing screenshot heading), the resolved value handed to the grounder, the centre normalized to `[0, 1000]` which the locator returns and the logical screen position the `Robot` clicks.

The suite pins the unified result-status contract shared with the API agent: `FAILED` is reserved for failed verifications, while any
execution error yields `ERROR` — `mergeAtomicResults` preserves an atomic-level `ERROR` instead of flattening it into `FAILURE`.

The tests are tagged `@Tag("smoke")` and are **excluded from the default build** (`mvn package` / `mvn test`) via the parent POM's
`excludedGroups`. Run them explicitly:

```bash
mvn -B test -P linux -Dtest.excluded.groups= -Dgroups=smoke
```

The suite uses process-global test doubles (the `MockedStatic` screen stub plus background prefetch threads), so it runs **single-threaded**
(`src/test/resources/junit-platform.properties`).

`src/test/java/org/tarik/ta/smoke/PanelSmokeTest` drives the real operator panel server on a random local port over plain HTTP, serving the built frontend, with a Unix-domain-socket stub standing in for Xvnc and stubbed `GraphHealthRepository`, `KnowledgeViewRepository`, `ProcedureRepository` and `UiElementRepository` for Neo4j. It covers the session probe before and after the login, the refusal of cross-site and origin-less writes, the built `index.html` without inline scripts, the security headers on every response, the event stream starting with the snapshot and carrying a run as deltas with a registered secret redacted, a logout ending the session's event stream and relay connection, a second login displacing the first session (its stream receives the notice, its probe then answers with it), the relay passing bytes both ways, replacing an open connection and keeping a quiet one open past the WebSocket idle timeout, and the health route answering 401 without a session and, with one, the categories, severities and finding refs with a secret redacted. Every `/api/knowledge/*` route answers 401 without a session; with one, the summary, a neighbourhood slice with a secret in a procedure description redacted, an element screenshot with its bytes and MIME type and the trends come from a stubbed `KnowledgeViewRepository` (and stubbed procedure and element repositories).

## Deployment

This section provides detailed instructions for deploying the UI Test Execution Agent, both to Google Cloud Platform (GCP) and locally
using Docker.

### Cloud Deployment (Google Compute Engine)

The agent can be deployed as a containerized application on a Google Compute Engine (GCE) virtual machine, providing a robust and scalable
environment for automated UI testing. Because the agent needs 2 ports (the A2A port, reachable only from inside the VPC, and the public
operator panel port), using Google Cloud Run as a financially more efficient alternative is not possible. However, using Spot
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
    * `PANEL_PASSWORD`: The operator panel login, machine-generated (e.g. `openssl rand -base64 32`) and at least 16 characters long.
    * `PANEL_TLS_CERT` and `PANEL_TLS_KEY`: The PEM certificate chain and private key of the operator panel, self-signed or CA-signed. A self-signed pair for the VM's IP address can be created with `openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=<EXTERNAL_IP>" -addext "subjectAltName=IP:<EXTERNAL_IP>" -keyout panel.key -out panel.crt`; browsers then warn once. Let's Encrypt also issues short-lived (160-hour) certificates for IP addresses, which would remove the warning without DNS. To rotate the pair, add new secret versions and redeploy (or restart the VM, whose startup script reads the latest versions). The VM fails to boot when any of the three panel secrets is missing.
    * `HCOMPANY_ENDPOINT` (optional): The URL of the self-hosted Holo3.1 service (see `deployment/holo`).
    * `QWEN_ENDPOINT` (optional): The URL of a self-hosted Qwen service.

  You can create these secrets using GCP Console.

#### Deploying Neo4j (Vector Database)

The agent relies on Neo4j as its vector database. Neo4j can be deployed on its own dedicated GCE VM using
`deployment/neo4j/cloudbuild.yaml` (see the root [README's Neo4j deployment section](../README.md#cloud-deployment-dedicated-gce-vm) for
the full procedure), or run locally via Docker (see "Neo4j Setup" above).

After deployment, note the internal/external Bolt URL of the Neo4j instance; this will be your `VECTOR_DB_URL` which you need to set as a
secret (`VECTOR_DB_KEY` is auto-created by the Neo4j deploy script).

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
    * **Port Configuration:** the agent server port `_AGENT_SERVER_PORT` (default `443`) and the operator panel port `_PANEL_PORT` (default `8443`)
    * **Application Settings:** VNC resolution, log level, the execution mode `_EXECUTION_MODE` (`UNATTENDED` or `SUPERVISED`), live control of the panel's live view `_PANEL_LIVE_CONTROL_ENABLED` (default `false`; prompts never need it), debug mode, screen recording (`_SCREEN_RECORDING_ENABLED`, default `false`; videos go to `/var/log/ui-test-execution-agent/videos` on the VM)
    * **Screenshot and Bounding Box Settings:** Image dimension limits and normalization settings
    * **Agent Model Configuration:** Model names, providers, and prompt versions for each agent
    * **API Endpoints:** Groq, Google Cloud location and project settings
    * **Additional GCP Configuration:** Firewall rules (`allow-agent-server` limits the A2A port to `_FIREWALL_APP_INTERNAL_SOURCE_RANGES`, `allow-panel` opens the panel port to `_FIREWALL_PANEL_SOURCE_RANGES`, which has no default and must be set to the CIDR ranges the operators connect from, or the deployment stops before it provisions anything; several ranges need gcloud's alternate delimiter, e.g. `--substitutions=^;^_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24,198.51.100.7/32` (see `gcloud topic escaping`); both are updated on every deployment, and an old `allow-novnc` rule is deleted), disk settings, VM provisioning model, etc.
    * **Base Image configuration:** `_BUILD_BASE_IMAGE` (default `false`) controls whether to rebuild the base image or use the cached one. A change of `deployment/Dockerfile.base` reaches the cloud only when the next deployment runs with `--substitutions=_BUILD_BASE_IMAGE=true`. The first deployment with the operator panel needs it, because the cached base image still runs noVNC with a VNC password; tag the current base image first (`gcloud container images add-tag gcr.io/<project>/ui-testing-agent-base:latest gcr.io/<project>/ui-testing-agent-base:before-panel`) so it can be restored.

   **Important notes:**
    * **Empty values use defaults:** If a substitution value is empty (e.g., `_ELEMENT_BOUNDING_BOX_AGENT_MODEL_NAME: ''`),
      the application will use defaults from `config.properties`.
    * **Override substitutions:** Pass custom values when running `gcloud builds submit`.
    * **Agent model substitutions:** Every sub-agent has its `_<AGENT>_MODEL_NAME`, `_<AGENT>_MODEL_PROVIDER` and `_<AGENT>_PROMPT_VERSION` substitutions with the environment variable names of `UiTestAgentConfig`; the DB element selection agent's prompt version is `_ELEMENT_CANDIDATE_SELECTION_AGENT_PROMPT_VERSION`, and the execution mode is `_EXECUTION_MODE` (`UNATTENDED` by default).
    * **JVM options:** The container starts the agent with `--sun-misc-unsafe-memory-access=allow`, because dependencies still call the deprecated `sun.misc.Unsafe` memory-access methods, which JDK 27 otherwise warns about on every start (JEP 498). The option stops working once a JDK makes `deny` the default.
    * **Secrets by name:** The VM startup script reads every secret from Secret Manager, exports it and passes it to `docker run` as `-e NAME` without a value, so Docker takes it from the script's environment and no secret appears in the process arguments, on a disk or in the image (they stay readable through `docker inspect` by whoever holds the Docker socket).
    * **Boot resilience:** The VM startup script retries every image pull (the gcloud CLI image `gcr.io/google.com/cloudsdktool/google-cloud-cli:alpine` used to read the secrets, and the agent image) up to 10 times, 15 seconds apart, before the boot fails.

3. **Deploy using Cloud Build:**
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
     --substitutions=_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24 .
   ```

   To override further substitutions:
   ```bash
   gcloud builds submit --config=ui_test_execution_agent/deployment/cloud/cloudbuild.yaml \
     --substitutions=_FIREWALL_PANEL_SOURCE_RANGES=203.0.113.0/24,_MACHINE_TYPE=e2-standard-4,_LOG_LEVEL=DEBUG .
   ```

   The build will:
    * Build the Maven project.
    * Build or pull the Docker base image (conditional on `_BUILD_BASE_IMAGE`).
    * Build the Docker application image.
    * Push the Docker image to Google Container Registry.
    * Enable the necessary GCP services that are not enabled yet.
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
* **Operator panel:** Open `https://<EXTERNAL_IP>:<PANEL_PORT>` (default port `8443`; the deployment prints the URL, and the agent card shows it) and log in with the `PANEL_PASSWORD` secret. The live view of the agent's desktop is part of the panel; no VNC or noVNC port is published, and the A2A port is not reachable from outside the VPC.

### Local Docker Deployment

For local development and testing, you can run the agent within a Docker container on your machine.

#### Docker Image Architecture

The agent uses a two-layer Docker image architecture:

1. **Base Image (`ui-testing-agent-base`)**: Built from `deployment/Dockerfile.base`, provides:
    - Ubuntu 26.04 LTS
    - Xfce desktop environment
    - TigerVNC server, listening only on the Unix socket `/tmp/xvnc.sock` of the `ubuntu` user (mode `0600`) without a password and without a TCP listener; the operator panel relays it
    - **Google Chrome Stable** (latest version)
    - Common utilities (wget, curl, git, zip, unzip, jq, etc.)

2. **Application Image (`ui-test-execution-agent`)**: Built from `deployment/local/Dockerfile`, adds:
    - Java 27 runtime (Amazon Corretto)
    - Agent application JAR
    - Application-specific configuration

#### Prerequisites for Local Docker Deployment

* **Docker Desktop:** Ensure Docker Desktop is installed and running on your system.

#### Building and Running the Docker Image

The `build_and_run_docker.bat` script (for Windows) simplifies the process of building the application and Docker image, and running the
container.

1. **Set the panel password:** Set `PANEL_PASSWORD` in the shell that runs the script to a password of at least 16 characters; the script refuses to start without it and passes it to the container by name.

2. **Execute the batch script:**
   ```bash
   deployment\local\build_and_run_docker.bat
   ```
   This script will:
    * Build the `ui_test_execution_agent` module (and its dependencies) using Maven with the `linux` profile, so that the jar carries the Linux native libraries the container needs.
    * Build the base Docker image `ui-testing-agent-base` from `deployment/Dockerfile.base` (Ubuntu 26.04 + VNC + Chrome).
    * Build the application Docker image `ui-test-execution-agent` using `deployment/local/Dockerfile`.
    * Stop and remove any existing container named `ui-agent`.
    * Run a new Docker container, mapping port `8005` (agent server) and port `8443` (operator panel, plain HTTP, on the host's loopback address only) to your local machine.

#### Accessing the Local Agent

* **Operator panel:** Open `http://localhost:8443` and log in with `PANEL_PASSWORD`; the live view shows the container's desktop.
* **Agent Server:** The agent's server will be accessible at `http://localhost:8005`.

### Operator Panel Security

* **One credential for one person:** The panel has no user identities; its password belongs to one operator, and sharing it is that operator's responsibility. It is compared with `MessageDigest.isEqual`, whose time depends only on the length of the expected password, and the login is rate limited to five attempts per minute per client address (`429` above that); failed attempts are logged with their source address.
* **One session, one tab, newest wins:** A login replaces the current session, and a second event stream or live view replaces the open one, so a laptop that went to sleep never locks the operator out. The replaced tab is told when, from where and from which browser it was replaced, which is also the sign of a leaked password.
* **Sessions:** A 32-byte random session id in an `HttpOnly`, `SameSite=Strict` cookie (`__Host-panel_session` with `Secure` under TLS), an idle timeout of 30 minutes in which an open event stream counts as activity, an absolute timeout of 8 hours and a server-side logout that also clears the site data.
* **Cross-site requests:** Every write and the live view's WebSocket upgrade need `Sec-Fetch-Site: same-origin` or, when the browser sends no Fetch Metadata (plain HTTP on a non-loopback address), an `Origin` equal to the panel's own; JSON bodies need the JSON content type.
* **Headers:** A Content Security Policy of `default-src 'self'` without inline scripts or styles, Trusted Types for scripts (`require-trusted-types-for 'script'`), `frame-ancestors 'none'` and `X-Frame-Options: DENY`, same-origin opener and resource policies, `nosniff`, a strict referrer policy, a `Permissions-Policy` without geolocation, camera and microphone, `Strict-Transport-Security` under TLS, and `Cache-Control: no-store` on every API response.
* **Live view:** Xvnc asks for no password but listens only on a `0600` Unix socket of the container's `ubuntu` user and has no TCP listener, so the panel login is the only way in. Without live control, Xvnc drops every input event; no viewer may resize the display.
* **Secrets:** The panel password and TLS pair are environment variables, passed to `docker run` by name in the cloud; the browser the agent drives is started without them.
* **Frontend supply chain:** `.npmrc` disables lifecycle scripts and makes `npm install` skip versions younger than three days, the build verifies the registry signatures of the installed packages (`npm audit signatures`), and Dependabot proposes npm updates only after the same cooldown.

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
  inclusion of the ONNX embedding model (bundled `bge-small-en-v15`, or `multilingual-e5-small` downloaded on first startup) as a
  dependency of LangChain4j, and the native OpenCV libraries required for visual element location.
* **Unit Tests:** The project now includes unit tests for many components including agents, DTOs, managers, and tools. All future
  contributions and pull requests to the `main` branch **should** include relevant unit tests. Contributing by adding new unit tests
  to existing code is, as always, welcome.
