---
name: Software Architect
description:  Allows to plan and guide the implementation of new features or modification of existing features, ensuring best practices, modern design patterns, and proper planning. Use this skill every time when the user asks for implementing somethig new or modifying already existing logic, architectural advice, or needs to design a new component.
---

// turbo-all

# Software Architect Skill

This skill enables you to act as a **Lead Software Architect** to plan, design, and guide the implementation of complex features or logic
changes. You enforce high standards of software engineering, ensuring code is robust, maintainable, and aligned with the project's
technology stack.

---

## 1. Project Context & Tech Stack

Before proposing any solution, you **MUST** understand and validate the project's specific technology stack.

### 1.1 Understand the main tech stack

Analyze the parent POM file and the POM file of each module which is modified. Extract the versions of Java, LangChain4j, Javalin,
RestAssured and A2A SDK. Remember those versions because they will be needed in the next tasks.

### 1.2 Understand the testing tech stack

Analyze the parent POM file and the POM file of each module which is modified. Extract the versions of JUnit, Mockito and AssertJ.
Remember those versions because they will be needed in the next tasks.

### 1.3 Project Structure

Analyze the project structure and identify all modules in the project

### 1.4 Coding Standards Reference

**CRITICAL**: Always refer to `GEMINI.md` for project-specific coding standards.
---

## 2. Mandatory Web Research

You **MUST** use the web search tool to gather up-to-date information for *every* library or technology involved in your current
implementation task.

### 2.1 Required Search Categories

| Category            | Example Query                                       |
|---------------------|-----------------------------------------------------|
| **Official Docs**   | `"LangChain4j 1.10 streaming response example"`     |
| **Best Practices**  | `"Java 25 virtual threads best practices 2025"`     |
| **Deprecations**    | `"Javalin 7 deprecated methods migration"`          |
| **Issues/Bugs**     | `site:github.com langchain4j [issue description]`   |
| **Design Patterns** | `"hexagonal architecture Java implementation 2025"` |

### 2.2 Priority Documentation Sources

1. **Official Documentation**: Always prefer official docs over blog posts
2. **GitHub Repositories**: Check examples in official repos
3. **Release Notes**: Review changelogs for breaking changes
4. **Stack Overflow**: For specific error resolutions
5. **JEP Documents**: For Java language features

---

## 3. Architecture Decision Records (ADRs)

For **any significant architectural decision**, you MUST create or update an ADR.

### 3.1 When to Create an ADR

- Introducing a new design pattern
- Adding a new dependency
- Changing data flow or component boundaries
- Selecting between multiple valid approaches
- Any decision with long-term implications

### 3.2 ADR Template

```markdown
# ADR-[NUMBER]: [Short Title]

**Status**: Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date**: YYYY-MM-DD
**Deciders**: [List of people involved]

## Context

[What is the issue that we're seeing that motivates this decision?]

## Decision

[What is the change that we're proposing and/or doing?]

## Consequences

### Positive

- [Benefit 1]
- [Benefit 2]

### Negative

- [Trade-off 1]
- [Trade-off 2]

### Neutral

- [Implication that is neither good nor bad]
```

### 3.3 ADR Storage

Store ADRs in: `.agent/architecture/decisions/ADR-XXX-title.md`

---

## 4. Modern Design Patterns

Based on your current task, identify the affected by this task tech stack (libraries, frameworks etc.) and use the web search tool in order
to find the most modern, popular, efficient and reliable design patterns which fit to the identified tech stack. If applicable, consider
using AI Agentic System patterns, Hexagonal Architecture patterns, Domain-Driven Design patterns, Event-Driven Architecture patterns, as
well as traditional GoF Patterns.

**LangChain4j Specific**:

- Use `@Tool` annotation for agent functions which need to be used by the agent in order to execute some logical tasks.
- Implement proper tool method and parameter descriptions for LLM understanding.
- Each DTO which is returned by the tool must have proper class- or record-level and parameter descriptions for LLM understanding.

---

## 5. Java Best Practices

Based on the project version of Java, use web search tool in order to identify all most up-to-date best coding practices for this version of
Java, so that not just the old ones, but also the newest features of Java are utilized correctly and efficiently.

---

## 6. Security-First Approach

### 6.1 Security Checklist

Before completing any implementation plan, verify:

- [ ] **Input Validation**: All user inputs validated and sanitized
- [ ] **No Hardcoded Secrets**: Use environment variables or secret managers
- [ ] **Dependency Security**: Run `mvn dependency:tree` to check for vulnerabilities
- [ ] **Logging Safety**: Never log sensitive data (passwords, tokens, PII)
- [ ] **Error Messages**: Don't expose internal details in error responses
- [ ] **API Security**: Authentication/authorization on all endpoints

### 6.2 Threat Modeling

For new features involving:

- External API calls → Consider rate limiting, timeouts, circuit breakers
- User data → Apply data classification and access controls
- File operations → Validate paths, prevent traversal attacks
- LLM prompts → Guard against prompt injection

---

## 7. Implementation Planning

### 7.1 Plan Structure

**CRITICAL**: Create and maintain an implementation plan as a `.md` file according to the modern guidelines.

```markdown
# Implementation Plan: [Feature Name]

**Created**: YYYY-MM-DD
**Status**: Draft | In Progress | Complete

## 1. Objective

[Clear statement of what will be achieved]

## 2. Technical Analysis

### Current State

[Relevant existing code/patterns]

### Dependencies

[New or modified dependencies]

### Impact Assessment

[Affected modules/components]

## 3. Architectural Design

### Design Patterns

[Patterns to be used with justification]

### Component Structure

[Classes, interfaces, their relationships]

### Data Flow

[How data moves through the system]

## 4. Implementation Phases

### Phase 1: Foundation

- [ ] Task 1.1
- [ ] Task 1.2

### Phase 2: Core Logic

- [ ] Task 2.1
- [ ] Task 2.2

### Phase 3: Integration

- [ ] Task 3.1
- [ ] Task 3.2

### Phase 4: Testing

- [ ] Unit tests
- [ ] Integration tests

## 5. Risk Assessment

| Risk | Mitigation |
|------|------------|
| [Risk 1] | [Strategy] |

## 6. Verification Checklist

- [ ] All tests pass
- [ ] README.md updated
- [ ] No new compiler warnings
- [ ] Code review ready
```

### 7.2 Planning Principles

1. **Incremental Delivery**: Break into small, independently valuable increments
2. **Test-First Mindset**: Plan how it will be tested *before* coding
3. **Risk-Driven**: Address highest-risk items early
4. **Dependency Aware**: Check transitive dependencies with `mvn dependency:tree`

---

## 8. Refactoring Guidelines

### 8.1 When Modifying Existing Code

1. **Assess Current State**:
    - Review existing code structure and patterns
    - Identify code smells (long methods, large classes, duplication)
    - Check test coverage

2. **Apply Incremental Changes**:
    - Focused code changes
    - Run unit tests after each change
    - Fix any unit test failures resulting from the introduced changes
    - Move on to the next increment

4. **Strangler Fig Pattern** (for major changes):
    - Build new implementation alongside old
    - Gradually redirect the workflow execution to new implementation
    - Remove old code only after full migration onto the new implementation

### 8.2 Common Refactoring Techniques

Use the web search tool in order to find the most modern, popular, efficient and reliable refactoring techniques wich fit to the identified
for the current task tech stack.

---

## 9. Observability Design

### 9.1 Logging Strategy

Using **SLF4J** with **Logback** (project standard):

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);

// Use appropriate levels
log.trace("Detailed diagnostic info");
log.debug("Development troubleshooting");
log.info("Normal operational events");
log.warn("Potentially problematic situations");
log.error("Error events that might still allow continuation",exception);
```

**Rules**:

- Use parameterized logging (not string concatenation)
- Log at method boundaries for traceability
- Include correlation IDs for distributed tracing
- Never log sensitive data

### 9.2 Error Handling

- Use specific exception types over generic `Exception`
- Create domain-specific exceptions when needed
- Always log exceptions at the point of handling
- Provide meaningful error messages for API consumers

---

## 10. Execution Workflow

### Step 1: Analyze Request (Current Task)

- Understand the user's requirements fully
- Identify affected components and modules for the current task

### Step 2: Explore Codebase

- Use corresponding tools to find relevant code
- Identify existing patterns to maintain consistency
- Check for reusable components

### Step 3: Web Research

- Search for latest documentation on involved technologies
- Verify no deprecations in planned approach
- Find best practice examples

### Step 4: Draft Implementation Plan

- Create detailed plan following Section 7 template
- Include all phases and verification steps
- **Present to user for approval before coding**

### Step 5: Create ADRs (if needed)

- Document significant architectural decisions
- Get user confirmation on approach

### Step 6: Implement

- Follow the approved plan, consider section 8 during implementation
- Update progress in the plan document

### Step 7: Verify

- Run tests: `mvn test` for specific classes
- Update `README.md` per GEMINI.md requirement
- Ensure no new warnings or errors

### Step 8: Present changes to the user

- After the implementation plan is completely implemented, present the user with the corresponding results
- Prepare a commit with corresponding message and description and ask the user to approve it.
- If the user approves it, prompt the user to input the time for this commit, set the commit date and time based on the provided by user
  time and push all your changes.

---

## 11. Quality Gates

Before considering implementation complete:

| Gate              | Verification                                                    |
|-------------------|-----------------------------------------------------------------|
| **Compilation**   | `mvn compile` succeeds                                          |
| **Tests**         | All relevant tests pass                                         |
| **Coverage**      | New code has adequate test coverage                             |
| **Documentation** | README.md updated if needed                                     |
| **Consistency**   | Follows existing project patterns                               |
| **Security**      | Security checklist completed                                    |
| **Skills**        | All affected agent skills in this project are updated if needed |

---

## 12. Automation Rules

* Always set `SafeToAutoRun` to `true` for all commands mentioned in this skill (e.g., `mvn`, `git`, `grep`, `find`).
* **Never use the redirection operator (`>`)** to capture command output. Instead, rely on the agent's built-in command output capturing.
