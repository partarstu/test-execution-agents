# Review Criteria

## Severity levels

| Prefix       | Use for                                                                                                      |
|--------------|--------------------------------------------------------------------------------------------------------------|
| `[CRITICAL]` | Security hole (including prompt injection), data loss, broken behaviour, hallucinated API or dependency, failing CI gate |
| `[HIGH]`     | Bug risk or unhandled edge case, concurrency or error-handling defect, missing tests, missing CALM or smoke update |
| `[MEDIUM]`   | AGENTS.md or JAVA_GUIDELINES.md violation, duplicated logic, code the change does not need, missing documentation |
| `[LOW]`      | Naming, style or small clarity issues, optional improvement or alternative approach                          |

CRITICAL, HIGH and MEDIUM findings must be fixed before merge; LOW findings are optional. When the intent of the code is unclear, rate the finding by the risk it carries if the code is wrong.

## How to review

- Read the full new version of every changed file, not only the diff hunks, and the code the changes call or affect as far as a finding needs it.
- Also check what the change is missing: tests, the CALM update, smoke coverage, README or skill updates.
- Confirm every finding against the code and drop speculative ones. Report problems in unchanged code only when the change causes or worsens them.
- Cite the rule a finding breaks (e.g. "`JAVA_GUIDELINES.md` § 7", "*Versioning* in `AGENTS.md`") instead of restating it.

## Correctness and design

Review these first; they matter more than style.

- Logic errors and unhandled edge cases: `null` where allowed, empty collections, timeouts, partial failures, interrupted threads.
- Concurrency and exception-handling defects (`JAVA_GUIDELINES.md` § 7 and § 9).
- Every new class, method and dependency exists: imports, library APIs and `pom.xml` entries are real and match the versions in the parent `pom.xml`.
- Logic duplicated from `agent_core` or another module instead of reused.
- Abstractions, options or code beyond what the change needs.
- New prompt text, logs and tool results that bloat the LLM context window without need.
- Agent decisions, tool calls and failures that are not logged well enough to debug a run.

## Rules

Check every changed line, tests included, against `JAVA_GUIDELINES.md` and every rule of `AGENTS.md`. Preview or incubator APIs (`JAVA_GUIDELINES.md` § 2) are `[CRITICAL]`: they do not compile in this build. Severities of the project rules:

- Comment bloat (*Comments and docstrings*): `[MEDIUM]`.
- A missing version bump, or one at the wrong level (*Versioning*): `[MEDIUM]`.
- An architecture change without the update of both CALM files (*Architecture first (CALM)*): `[HIGH]`.
- An added or extended end-to-end flow without smoke coverage (*Smoke suite*): `[HIGH]`. A pure internal refactor is exempt; confirm that it really is one.
