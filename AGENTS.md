## Project

Multi-module Maven project: A2A test-execution agents (UI and API) built on LangChain4j and Avaje Inject; they run as part of the QuAIA framework (`agentic-qa-framework` repository).

* Modules: `agent_core` (shared server, A2A executor, agent and tool base types, configuration, budget management), `ui_test_execution_agent`, `api_test_execution_agent`. In a module, agents are in `src/main/java/org/tarik/ta/agents/`, tools in `tools/`, A2A executors and agent cards in `a2a/`, Avaje Inject factories in the `*BeanFactory` classes, configuration in the `*Config` classes and `config.properties`, the UI agent's knowledge graph in `knowledge_graph/`; the architecture model is in `calm/`.
* The language guidelines are [JAVA_GUIDELINES.md](JAVA_GUIDELINES.md): follow them in every Java change and check them in every review. Project skills are in `.agents/skills/`.
* Local development is on Windows 11 in IntelliJ IDEA; CI (`.github/workflows/`) runs on ubuntu-latest.

## Commands

Run from the repository root. The `windows` Maven profile, which selects the UI agent's native OpenCV/JavaCV libraries, is active by default; on Linux add `-P linux` to every Maven command.

* Build and unit tests of all modules (smoke tests are excluded): `mvn clean install`
* Unit tests: `mvn test`; one module: `mvn test -pl <module> -am`; one class: `mvn test -pl <module> -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=<TestClass>` (one method: `-Dtest=<TestClass>#<method>`).
* License headers: `mvn license:check`; `mvn license:format` adds the missing ones.
* CALM validation; a clean run prints `No issues found.` and exits `0`:
  ```bash
  npx -y @finos/calm-cli@1.60.1 validate -p calm/patterns/execution-agents.pattern.json -a calm/architecture/execution-agents.arch.json --strict -f pretty
  ```
* CALM rendering; the diagram is in `docs/index.md` of the output:
  ```bash
  npx -y @finos/calm-cli@1.60.1 docify -a calm/architecture/execution-agents.arch.json -o <directory outside the repository>
  ```
* Smoke suite: see *Smoke suite*.

## Project conventions

* Result records reuse a type from `org.tarik.ta.core.dto` when one fits; otherwise they implement `FinalResult`, carry `@Description` on the record and on every component, and provide the static `@Tool` method that the LLM calls to return the result.
* Agent interfaces extend `GenericAiAgent`, bind their `@UserMessage` placeholders with `@V`, return `Result<String>` and implement `getAgentTaskDescription()`. Callers run them through `executeWithRetry(...)` or `executeAndGetResult(...)`, which handle budget checks, retries and result extraction, and never reimplement those.
* Tool classes extend `AbstractTools`, with one focused operation per `@Tool` method, a precise description and `@P` on every parameter. A recoverable failure throws `ToolExecutionException` with an `ErrorCategory`; `DefaultToolErrorHandler` turns it into a retry or an error, so tools have no retry loops of their own.
* Sub-agents are request-scoped and injected through `Provider`s; no agent state leaks between requests.
* The shared status contract: `FAILED` only for failed verifications, `ERROR` for execution errors and precondition failures.
* Untrusted input (test case text, HTTP responses, screen content, LLM output and the arguments an LLM chooses for a tool) is clearly delimited in prompts and validated before it is used as a URL, path, query or command: URLs are allow-listed, file paths confined (`JAVA_GUIDELINES.md` § 11).
* New settings are loaded in the module's `*Config` class with a property key and an environment variable, defaulted in its `config.properties` and documented in the module's `README.md`. Property keys use lowercase dot-separated names (e.g. `api.request.timeout.millis`), environment variables SCREAMING_SNAKE_CASE (e.g. `API_REQUEST_TIMEOUT_MILLIS`).

## Unit tests

The test rules are in `JAVA_GUIDELINES.md` § 13. The project's specifics, and the failures that show they were broken:

* Tests tagged `@Tag("smoke")` are excluded from the default run (`test.excluded.groups`); they are the end-to-end suite, not unit tests.
* Strict stubbing of `MockitoExtension` fails a test with `UnnecessaryStubbingException` or `PotentialStubbingProblem`: remove the unused stub, fix its argument, or make that one stub `lenient()`. A `NullPointerException` on a `@Mock` field means the mocks are not initialised.
* A `MockedStatic` that is not closed fails later tests in the same thread with "static mocking is already registered in the current thread".
* `BudgetManager`, static mocks, system properties and singletons are shared state: mock them, or reset them in `@BeforeEach`/`@AfterEach`. A test that passes alone but fails in the module run has leaked one of them.
* The JDK warning "Final field ... has been mutated reflectively" means a test sets a final field by reflection (`JAVA_GUIDELINES.md` § 2); pass the test double through the constructor instead.
* The boundaries to mock are the LangChain4j agent interfaces (returning `Result` or `OperationExecutionResult` objects), Neo4j repositories, the embedding model, HTTP targets (a local WireMock server, never a real endpoint), and the screen, mouse, keyboard and operator prompts (`UiCommonUtils`, `Robot`, `OperatorPrompts`). Unit tests run headless and never wait for user input; a slow test, a real network call or a `HeadlessException` means a boundary is not mocked.

## Versioning

Every agent module carries its version in the `agent.version` property of its `config.properties` (`MAJOR.MINOR.PATCH`), which its `AgentCardProducer` advertises in the agent card. Every sub-agent prompt is versioned by its directory, `prompt_templates/system/agents/<agent path>/v<MAJOR.MINOR.PATCH>/`, and selected by the `<agent>.prompt.version` default in the module's `config.properties`. A change that alters the logic of an agent (its prompts, tools, workflow, output content or integration behaviour) bumps the affected version in the same change: a changed prompt gets a new version directory with the new text (the old one stays) and the new default in `config.properties`; any other logic change bumps the agent card version.

* **PATCH** for a fix or an internal change that keeps the observable behaviour and the contract.
* **MINOR** for a backwards-compatible change of behaviour or a new capability (a new tool, a changed prompt, a new workflow step, changed output content).
* **MAJOR** for an incompatible change (a changed result record, skill, request contract or A2A endpoint).

A change that touches none of their logic (documentation, tests, tooling, a shared helper refactored without a behaviour change) bumps nothing. A new agent starts at `1.0.0`, a new prompt at `v1.0.0`.

## Architecture first (CALM)

The architecture is modelled with the FINOS CALM standard under `calm/` and gated by the blocking `Architecture (CALM)` CI job (`.github/workflows/architecture.yml`). `calm/architecture/execution-agents.arch.json` holds the agents, external systems and runtimes (`nodes`) and their integration edges (`relationships`); `calm/patterns/execution-agents.pattern.json` requires every node and relationship of the architecture, so the two files stay in lockstep, including the `minItems`/`maxItems` counts. *Architecture model (CALM)* in `README.md` explains the design decisions. The CALM CLI is a Node.js 24+ tool run with `npx`, never a dependency in a `pom.xml`.

An architecture change adds, removes or renames an agent, an external system, a runtime or an integration edge (e.g. a new agent module, a new outbound integration). It is modelled and approved before anything is implemented:

1. Update both CALM files, mirroring the existing nodes and relationships and adjusting the item counts.
2. Validate them (*Commands*).
3. When the change is more than a single edge, render the documentation outside the repository (*Commands*) and show the user the diagram and the pages of the changed nodes and relationships.
4. Present the change to the user - what changes in the model and why, the validation result and the diagram - and get their explicit approval.
5. Record the approval in the *Architecture* section of the plan, which lists the changed elements, with the line `CALM updated, validated and approved by the user on <date>`. A plan that names an architecture change without that line is not ready to be implemented.
6. Only then implement; the updated CALM files stay in the working tree as part of the change. A CALM update bolted on after the implementation does not count.

## Smoke suite

The smoke suite drives the real agent flows end to end with only the external boundaries mocked (LLM, target HTTP API, screen and OS, Neo4j). Its tests are tagged `@Tag("smoke")`, live in the `smoke` test packages of each module and are excluded from the default build; the `Smoke Tests` workflow runs them on every pull request. *Smoke Tests* in `README.md` describes the suites and their layout.

* A change that adds or extends end-to-end behaviour - a new agent, A2A endpoint or transport behaviour, external integration or step in a flow, or a change to what a flow produces - updates the suite in the same change:
    - **New flow**: a smoke test in the module's `smoke` package that drives it through the agent's public entry point and asserts on what reached the mocked boundary.
    - **Extended flow**: stronger assertions in the existing tests that cover the new behaviour.
    - **New external boundary**: a test double for it (e.g. a scripted chat model, a WireMock stub) next to the existing ones.
* A change that adds no observable end-to-end behaviour (e.g. an internal refactor) needs no smoke change; say so explicitly instead of skipping it silently.
* Run the suite locally only when the user asks (on Linux it needs a display, e.g. `xvfb-run -a`):
  ```bash
  mvn -B test -P <windows|linux> -Dtest.excluded.groups= -Dgroups=smoke -Djacoco.skip=true
  ```

<!-- Shared rules: start. This block is identical in the agentic-qa-framework and test-execution-agents repositories; edit it in one of them and copy it unchanged to the other. -->

## Working with the user

* Before implementing a task, present a short plan: what will be implemented and how, as numbered steps with their checks (`1. <step> → verify: <check>`), not every individual change. Wait for the user's confirmation, then carry out the plan without asking them to confirm individual changes.
* When the user or an instruction asks for a plan (an implementation plan, a TO-DO list or similar), write it straight to an .MD file in the `plans` folder and reply with only its path and a short summary; never print the plan in the conversation. An implementation plan follows `.agents/skills/architecting-features/resources/implementation_plan_template.md` and the presentation rules in step 6 of that skill: comprehensive, visually well structured and easy to read.
* When a change touches several modules, keep its TO-DO list in an .MD file in the `plans` folder, tick items as you finish them and add what you discover. After a context compaction, re-read that file instead of relying on the conversation.
* Once the plan is confirmed, work until the *Definition of done* is met. Put status notes in the same message as your next action. Never end a turn with a summary that announces the next step without taking it, an offer to continue, or a list of decisions that don't block the work.
* Stop and ask the user only when you can't continue without them, when two readings of the request would lead to materially different work, when an architecture change needs their approval (*Architecture first (CALM)*), before spawning a subagent (*Subagents*), or before anything destructive or outward-facing: deleting files you didn't create, pushing, creating or changing cloud resources, or changing anything outside this repository. Make routine judgement calls yourself.

## Git

* The main branch is `main`. Commit only when the user asks. Commit messages and pull request titles follow Conventional Commits (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`) and stay under 72 characters.
* **Never push** anything to the remote repository (any `git push`, including force-pushes, tags and pushes run by a skill or script) without the user's explicit consent to that specific push in the current conversation. A request to commit, an approved plan, auto mode or consent given for an earlier push is not consent; when in doubt, ask and wait.

## Subagents

* Never start a subagent without the user's explicit consent to that specific spawn, unless a project skill starts it as part of its procedure. If the user doesn't consent, do the work yourself.
* When asking, propose a model and effort for each subagent and whether it forks this conversation or starts with a fresh context, and always offer "no subagents". Spawn exactly what is approved; any extra spawn or resume needs a new approval. If something can't be set per subagent, say so.
* Keep the usage low: the cheapest sufficient model and the effort the task needs, a self-contained prompt with the files and facts already known, a short capped result, a fork when it needs most of the conversation. A subagent never redoes the work of the main agent.

## Coding rules

* Before implementing, state your assumptions. If a simpler approach exists, say so, and push back when warranted.
* Keep the implementation simple and short: no features beyond the request, no abstractions for single-use code, no "flexibility" or "configurability" that wasn't requested, no error handling for impossible scenarios. If you write 200 lines and it could be 50, rewrite it.
* Minimal never means dropping input validation at trust boundaries, error handling that prevents data loss, security controls, or anything explicitly requested. Between two equally small solutions, take the one that is correct on edge cases.
* Every line you change traces directly to the request. Don't improve, refactor or reformat code you don't otherwise change, and match the existing style even if you'd do it differently. Mention unrelated dead code instead of deleting it; the code you write or change leaves nothing redundant behind.
* Before changing code, read every file the change touches and trace the real flow end to end: a small diff in the wrong place is a second bug. Fix a bug at its root: find every caller of the function first, and prefer one fix in the shared code to a guard in each caller.
* Never duplicate existing functionality: reuse it, and if it can't be reused directly, extract it (inheritance or composition) and reuse it.
* Turn tasks into verifiable goals: "add validation" means tests for invalid inputs that then pass, "fix the bug" a test that reproduces it and then passes, "refactor" tests that pass before and after. Never skip, disable (`xfail`, `@Disabled`), delete or weaken a test or an assertion to get a green run.
* When you deliberately choose a simpler solution with a known limit, state the limit and the upgrade path in your summary and in the PR description, not as a code comment.
* Never trust user-supplied data: validate and sanitize every input from outside (requests, webhooks, tool arguments, LLM output) against injection. Secrets come only from environment variables or a secrets manager, never from the source code.
* Never hard-wrap Markdown or any other text file you create or edit: a paragraph, a list item or a table row is one line, however long. This applies to every generated file, including plans, skills, prompts and documentation.

## Comments and docstrings

The code must be self-explanatory: names, types and structure carry the meaning, and a comment is the exception, never the norm. This applies to every file (source, tests, scripts, prompts, templates); the language guidelines fix the syntax.

* A function or method doc comment is one sentence; a class, type, module or package doc comment is at most two sentences. A unit whose name and signature already say what it does gets none.
* Parameter, return and exception sections appear only where a name and its type don't already say it. Texts that the LLM reads as a tool specification (the docstring of a tool an LLM calls, `@Tool`, `@P` and `@Description` texts) always stay complete and precise.
* An inline comment states a non-obvious *why* - a constraint, a workaround, a decision. It never restates *what* the code does and never records history (no plan, work-stream, ticket or "previously" references). Before writing one, try to make the code say it with a better name, a smaller function or an explicit type.
* In the code you change, remove comments and doc comments that violate this rule; leave untouched code as it is.

## Research

Search the web before relying on memory for anything version-sensitive: a library, API or tool you're about to use or upgrade, its flags, an error you don't recognise. Read the most relevant result pages in full with `curl` (`curl.exe` in PowerShell), because the built-in fetch tool returns a summary written by a small model that can drop details crucial to the task. Mark anything you couldn't confirm and say where you looked.

## Context budget

Every turn re-reads the whole context, so its size is the cost:

* Read a file over about 300 lines with search and ranged reads, not whole; never print several files at once; don't re-read a file after editing it.
* Redirect long test or build output to a log file and read only its summary and its failure blocks.

## Temporary files and clean-up

Temporary files - scratch scripts, drafts, PR descriptions, review JSON, copies made for a check - go outside the repository, into the scratchpad directory when there is one. Before reporting a task as done, clean up everything you created temporarily, without being asked: stop the stacks and services you started, remove their containers, networks and volumes and any temporary cloud resources (VMs, disks, firewall rules, tunnels), and delete every file that isn't part of the deliverable. Nothing stays running or lying around "in case it's needed later".

## Definition of done

A change is done when the unit tests of every touched module pass, the affected versions are bumped (*Versioning*), the smoke suite and the CALM model cover the change (*Smoke suite*, *Architecture first (CALM)*), README.md describes the changed behaviour, and everything temporary is cleaned up. Skip what doesn't apply; if you skip something that might look applicable, say why.

Finish every implementation task with a report in three short parts:

* **Blocked on me**: decisions or approvals you need from the user, or "nothing".
* **Changed**: what and where.
* **Found**: known limits of a deliberately simple solution, unrelated dead code, anything you couldn't confirm.

<!-- Shared rules: end -->
