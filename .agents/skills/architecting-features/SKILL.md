---
name: architecting-features
description: Designs new features or changes to existing behaviour in the Test Execution Agents project and produces an implementation plan for user approval, covering code reuse, research of the libraries involved, the architecture-first CALM update (modelled, validated, rendered and approved before any code), Java design per JAVA_GUIDELINES.md, versions, smoke-suite impact and verification steps. Use before implementing a new agent, tool, integration or other non-trivial change, or when the user asks for a design or architectural advice.
---

# Architecting Features

Produce an approved plan before writing code. Scale the plan to the change: a small fix needs a few lines, a new agent or integration needs the full template.

Copy this checklist and track progress:

```
- [ ] 1. Understand the request
- [ ] 2. Study the existing code
- [ ] 3. Research the libraries and APIs involved
- [ ] 4. Architecture first (CALM)
- [ ] 5. Design
- [ ] 6. Write the plan and get approval
- [ ] 7. Hand off to the implementing skill
```

## 1. Understand the request

Restate the goal and list your assumptions.

## 2. Study the existing code

Identify the affected modules, find the closest existing implementation and follow its structure. List what the change reuses, and plan the extraction of logic that cannot be reused as it is (e.g. into `agent_core`).

## 3. Research

Read the versions from the parent `pom.xml` (Java, LangChain4j, A2A Java SDK, Avaje Inject, Javalin, Jackson, RestAssured, JUnit, Mockito, AssertJ) and research every library, protocol or external API the change touches for those versions, preferring official docs, release notes and GitHub repositories over blog posts and checking for deprecations. Mention a source in the plan only where it drives a design decision.

Java language and library rules come from `JAVA_GUIDELINES.md`; do not re-research them per task.

## 4. Architecture first (CALM)

Decide whether the change is an architecture change as *Architecture first (CALM)* in `AGENTS.md` defines it. If it is not, the plan's *Architecture* section (step 6) says "No architecture change". If it is, carry out steps 1-4 of that procedure now; the plan written in step 6 records the approval in its *Architecture* section.

## 5. Design

Decide and justify each point that applies:

- **Components**: which classes and modules change and which are new. Prefer extending existing components over new abstractions.
- **Alternatives**: for significant choices, weigh the options and record the chosen one with its reason.
- **Java design**: data models, null-safety, error handling, logging and the concurrency model follow `JAVA_GUIDELINES.md` (§ 4, § 5, § 7, § 8, § 9). No preview features (§ 2).
- **Agents and tools**: a new agent or sub-agent follows the `creating-new-agent` skill.
- **Architecture as code**: already settled in step 4; the design builds on the approved model and adds no node or edge that is not in it.
- **Versions**: every agent whose logic changes and the bump *Versioning* in `AGENTS.md` prescribes.
- **Comments**: the plan carries the reasoning; the code does not repeat it.
- **Smoke suite**: the update *Smoke suite* in `AGENTS.md` requires, or that none is needed.
- **Security**: how external input, LLM output, secrets, outbound URLs and file paths are handled and how prompt injection is prevented (*Project conventions* in `AGENTS.md`, `JAVA_GUIDELINES.md` § 11).
- **Dependencies**: a new dependency must meet `JAVA_GUIDELINES.md` § 15; check transitive conflicts with `mvn dependency:tree`.
- **Configuration**: new settings and their property keys and environment variables.
- **Observability**: the new flow logs its significant steps and failures (`JAVA_GUIDELINES.md` § 8).

## 6. Write the plan and get approval

Write the plan with [resources/implementation_plan_template.md](resources/implementation_plan_template.md), omitting sections that do not apply. The plan is read by a person, so it must be comprehensive, visually well structured and comfortable to read:

- **Top-down**: the *At a glance* table answers goal, scope, architecture, versions and risk first; the details follow in the template's section order.
- **Structure over prose**: put comparisons, component lists, decisions and impact in tables, steps in numbered or checkbox lists, and a flow that branches or involves several actors in a Mermaid `flowchart` or `sequenceDiagram`; an arrow chain is enough for a linear flow.
- **Scannable**: one emoji per section heading, bold only for the key term of a line, short sentences, one idea per line, consistent heading levels, no walls of text.
- **Understandable**: spell out an abbreviation or project term at first use, put the reason next to every decision, and name concrete classes, files and settings instead of describing them vaguely.
- **Renders everywhere**: plain Markdown plus Mermaid only; callouts are `> **Label**` blockquotes, not GitHub-only `[!NOTE]` alerts, and diagram labels must still make sense as plain text.
- **Scaled and not repetitive**: a small fix keeps the *At a glance* table and the TODO list; every added section earns its place, and nothing is said twice.

The summary in your reply names the decisions needing the user's input (trade-offs, new dependencies, security-sensitive choices) and asks for approval. Do not start implementing before the user approves.

## 7. Hand off

| Change                               | Continue with                                |
|--------------------------------------|----------------------------------------------|
| Implement with review and test loops | `implementing-changes`                       |
| New agent or sub-agent               | `creating-new-agent`                         |
| Tests                                | `writing-unit-tests`, `running-unit-tests`   |
| Ready for review                     | `preparing-pull-requests`                    |
