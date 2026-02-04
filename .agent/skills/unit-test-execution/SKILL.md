---
name: Test Execution & Fixing
description: Executes unit tests and systematically resolves all test failures.
---

// turbo-all

# Test Execution & Fixing Skill

This skill provides a systematic workflow for running the test suite, diagnosing failures, and resolving them.

> **Note**: For writing new tests or understanding test patterns, see the [Unit Testing](../unit-test-creation/SKILL.md) skill.

## When to use this skill

- Use this when you need to run any test or test class or the full test suite.
- Use this when tests are failing and need to be fixed.
- Use this before creating a Pull Request to ensure all tests pass.

## Technology Stack

Analyze the parent POM file and the POM file of each module which tests need to be executed. Extract the versions of JUnit, Mockito,
AssertJ. Remember those versions because they will be needed in the next tasks.

## Comprehensive Test Execution Workflow

Follow this systematic process when identifying and resolving test failures.

### Step 1: Assessment & Baseline

Run all tests to evaluate the current health of the codebase.

```bash
mvn test
```

* **Success**: If all tests pass, the task is complete.
* **Failure**: Proceed to the next step.

### Step 2: Failure Analysis

Analyze the console output or `target/surefire-reports` to categorize failures:

| Category               | Description                                                | Priority |
|------------------------|------------------------------------------------------------|----------|
| **Compilation Errors** | Code doesn't compile                                       | Highest  |
| **Runtime Exceptions** | NPE, IndexOutOfBounds, ClassCast, etc.                     | High     |
| **Assertion Failures** | Expected vs Actual mismatch                                | Medium   |
| **Mockito Errors**     | `UnnecessaryStubbingException`, `PotentialStubbingProblem` | Medium   |

### Step 3: Iterative Resolution (The Fix Loop)

For each failure, prioritize low-level/unit failures before integration tests.

1. **Reproduce**: Run *only* the failing test class to isolate the issue.
   ```bash
   mvn test -Dtest=ClassName
   ```

2. **Debug**:
    * Examine the full stack trace.
    * Review the test logic and the production code under test.
    * Check for strict stubbing in Mockito (`lenient()` might be needed if the stub is conditional).
    * Verify that mocks are properly initialized (`MockitoAnnotations.openMocks(this)`).

3. **Fix**: Apply the minimum necessary change to the test. If the root cause of failure is incorrect implementation, always ask the user to
   confirm the intended fix before implementing it. If the user doesn't confirm, request the user to provide the further instructions on how
   to fix the issue.
    * If the test expectation is wrong, fix the test.
    * Avoid large refactors during this phase.

4. **Verify**: Run the specific test again.
   ```bash
   mvn test -Dtest=ClassName
   ```

5. **Repeat**: Move to the next failing test class.

### Step 4: Regression Testing

Once individual issues are resolved, run the full module suite to ensure no side effects.

```bash
mvn test -pl module_name
```

If new failures appear, loop back to Step 3 for those tests.

### Step 5: Final Verification

Run the complete project test suite before finishing the task.

```bash
mvn test
```

All tests must pass before the task is considered complete.

## Quick Reference Commands

| Goal                                | Command                                 |
|-------------------------------------|-----------------------------------------|
| Run all tests                       | `mvn test`                              |
| Run tests               in a module | `mvn test -pl module_name`              |
| Run a specific test class           | `mvn test -Dtest=ClassNam e`            |
| Run a specific test method          | `mvn test -Dtest=ClassNam e#methodName` |
| Run tests with output               | `mvn test -Dtest=ClassNam     e -X`     |
| Skip tests during build             | `mvn install -DskipTests`               |

## Automation Rules

* Always set `SafeToAutoRun` to `true` for all validation and test execution commands (e.g., `mvn test`).
* **Never use the redirection operator (`>`)** to capture command output. Instead, rely on the agent's built-in command output capturing.
