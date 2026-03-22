# PR Review Criteria

This document defines the comprehensive review criteria for this project, combining project-specific guidelines from GEMINI.md with
industry best practices for Java 25 and Agentic development.

---

## 1. Project-Specific Rules (from GEMINI.md)

### 1.1 Java Version & Formatting

- [ ] Code uses Java 25 features appropriately
- [ ] No reformatting of unchanged code
- [ ] Lines under 140 characters are not wrapped
- [ ] Uses imports instead of qualified names
- [ ] Prefers static imports where possible

### 1.2 Code Reuse & Design

- [ ] No duplication of existing functionality
- [ ] Reuses existing logic via inheritance or composition
- [ ] Uses `String.formatted()` for string parameterization (not concatenation)
- [ ] Uses composition over inheritance for flexibility

### 1.3 Documentation

- [ ] README.md updated if functionality changed
- [ ] High-value comments explain *why*, not *what*
- [ ] Implementation plan (.MD TODO file) created for multi-class changes

### 1.4 Type System & Patterns (Data-Oriented Programming)

- [ ] Java records used for DTOs, API responses, value objects
- [ ] Sealed types define fixed subtypes for pattern matching
- [ ] Pattern matching used for `instanceof` checks
- [ ] Exhaustive `switch` with pattern matching
- [ ] `Optional` used to make absence explicit
- [ ] Parameterized generic types used (no raw types)

### 1.5 Access Modifiers & Module Encapsulation

- [ ] Classes without `public` modifier for package encapsulation
- [ ] Only `public` for module's explicit API
- [ ] Internal implementation classes remain package-private and aren't accidentally exposed

---

## 2. Java 25 & Agentic Best Practices

### 2.1 Intent vs. Syntax Mandate

- [ ] Review focus is strictly on **architecture, business logic, security, and intent**, trusting automated CI tools for formatting and
  syntax.

### 2.2 Modern Language Features

- [ ] **Primitive Types in Patterns** (JEP 507): Pattern matching in `switch`/`instanceof` for primitives used correctly
- [ ] **Flexible Constructor Bodies** (JEP 513): Statements before `super()`/`this()` don't reference object under construction
- [ ] **Structured Concurrency** (JEP 505): Related concurrent tasks treated as single units
- [ ] **Scoped Values** (JEP 506): Used instead of `ThreadLocal` with virtual threads
- [ ] **Stable Values** (JEP 502): Immutable data holders used for JVM optimization

### 2.3 Concurrency

- [ ] Virtual threads used for I/O-bound tasks
- [ ] Virtual threads NOT pooled
- [ ] `ReentrantLock` preferred over `synchronized` for long-held locks
- [ ] Thread-safety documented where applicable
- [ ] Independent I/O operations run in parallel

---

## 3. Code Quality Standards

### 3.1 Naming Conventions

- [ ] Packages: lowercase (e.g., `org.tarik.ta.agents`)
- [ ] Classes: PascalCase (e.g., `UiTestAgent`)
- [ ] Methods/Variables: camelCase (e.g., `executeTest`)
- [ ] Constants: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)
- [ ] Names are meaningful and self-documenting

### 3.2 Method Design

- [ ] Methods are short and focused (single responsibility)
- [ ] Early exits used to reduce nesting
- [ ] No overly "clever" or complex one-liners
- [ ] Clear, readable logic flow

### 3.3 Collections & Streams

- [ ] `.map()`, `.filter()`, `.collect()` for declarative processing
- [ ] Correct data structure for use case (HashMap for lookups, ArrayList for indexed access)
- [ ] Immutable collections preferred where appropriate

### 3.4 Performance

- [ ] Primitives used in performance-critical code (avoid boxing/unboxing)
- [ ] `StringBuilder` used in loops instead of `+` operator
- [ ] No unnecessary object creation in loops
- [ ] Efficient algorithms and data structures

---

## 4. Error Handling

### 4.1 Exception Handling

- [ ] No empty `catch` blocks (at minimum, log the exception)
- [ ] Specific exceptions caught (not generic `Exception`)
- [ ] Meaningful error messages provided
- [ ] Exceptions thrown early, caught late
- [ ] `Optional` or specific exceptions instead of returning `null`

### 4.2 Tool Error Handling (Agent-Specific)

- [ ] `ToolExecutionException` used for tool errors
- [ ] `DefaultToolErrorHandler` leveraged for retry logic
- [ ] Meaningful error messages in result types

---

## 5. Security

### 5.1 Input Validation

- [ ] User-supplied data validated and sanitized
- [ ] SQL injection prevention measures
- [ ] XSS (Cross-Site Scripting) prevention
- [ ] No trusting of external input

### 5.2 Secrets Management

- [ ] No hardcoded API keys, passwords, or credentials
- [ ] Sensitive data not logged
- [ ] Configuration loaded from environment/properties

### 5.3 Dependencies

- [ ] No known vulnerable dependencies
- [ ] Dependencies managed via `<dependencyManagement>`
- [ ] No custom implementations duplicating library functionality

---

## 6. Testing Requirements

### 6.1 Test Framework

- [ ] JUnit 5 for test structure (`@Test`, `@BeforeEach`, `@AfterEach`)
- [ ] AssertJ for fluent assertions
- [ ] Mockito for mocking (`@Mock`, `@Spy`)

### 6.2 Test Quality

- [ ] Tests are independent and automated
- [ ] Both happy paths and edge cases covered
- [ ] Test coverage does not decrease
- [ ] Mocks initialized in `@BeforeEach` with `MockitoAnnotations.openMocks(this)`

### 6.3 Test Organization

- [ ] Test files in `src/test/java` mirroring source structure
- [ ] Package-private members tested via same-package test classes
- [ ] Existing test patterns followed

---

## 7. Agent-Specific Architecture & Survivability

### 7.1 "AI-Generated Code" Survivability

- [ ] **Hallucination Checks:** All new dependencies, imports, and method calls are real and present in the `pom.xml`.
- [ ] **Logic Validation:** Ensure edge-case handling or security boundaries haven't been lost through AI "over-simplification".
- [ ] **Human Ownership:** The author can explain *why* the code works, backed by automated tests rather than assumptions based on "
  clean-looking" code.

### 7.2 Agent Interfaces & Execution

- [ ] `@UserMessage` with template variables `{{varName}}`
- [ ] `@V("varName")` for method parameters
- [ ] `Result<String>` returned from execution methods
- [ ] `getAgentTaskDescription()` implemented for logging
- [ ] **Prompt Injection Security:** User-supplied data is strictly sanitized/parameterized *before* being injected into prompts or
  `@UserMessage`.
- [ ] **Context Window Management:** New features/logs do not unnecessarily bloat the LLM's context window.
- [ ] **Resilience:** Agent handles tool execution failures gracefully (e.g., retries with backoff) without crashing.
- [ ] **Observability & Tracing:** Agent decisions, tool execution rationale, and state transitions are logged for debugging.

### 7.3 Result Types & Tool Methods

- [ ] Java records used for result DTOs
- [ ] `FinalResult` interface implemented
- [ ] Static `@Tool` method `submitResult` provided
- [ ] `@Tool` annotation with clear descriptions
- [ ] `@P` annotation for parameter descriptions
- [ ] Tools are focused and single-purpose

---

## 8. PR-Specific Checks

### 8.1 PR Size & Scope

- [ ] PR is focused and reasonably sized (< 400 lines ideal)
- [ ] Changes are self-contained
- [ ] No unrelated changes mixed in

### 8.2 Commit Quality

- [ ] Conventional commit style used (feat:, fix:, refactor:, etc.)
- [ ] Commit messages are descriptive

### 8.3 Documentation

- [ ] PR description clearly explains changes
- [ ] Breaking changes documented
- [ ] API changes documented

---

## Review Decision Matrix

| Finding Type                        | Severity      | Action          |
|-------------------------------------|---------------|-----------------|
| Security vulnerability              | 🔴 BLOCKER    | Request changes |
| Prompt Injection Vulnerability      | 🔴 BLOCKER    | Request changes |
| Hallucinated Dependency/Method      | 🔴 BLOCKER    | Request changes |
| Bug / incorrect behavior            | 🔴 BLOCKER    | Request changes |
| Breaking change without docs        | 🔴 BLOCKER    | Request changes |
| Missing error handling              | 🟠 MAJOR      | Request changes |
| Code duplication                    | 🟠 MAJOR      | Request changes |
| Context Window Bloat / Inefficiency | 🟠 MAJOR      | Request changes |
| Missing Tool Failure Handling       | 🟠 MAJOR      | Request changes |
| Missing tests for new code          | 🟠 MAJOR      | Comment         |
| Performance issue                   | 🟠 MAJOR      | Comment         |
| Style violation                     | 🟡 MINOR      | Comment         |
| Missing documentation               | 🟡 MINOR      | Comment         |
| Refactoring opportunity             | 🔵 SUGGESTION | Comment         |