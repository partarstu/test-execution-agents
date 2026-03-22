---
name: unit-test-creator
description: Allows to create new unit tests following established patterns and conventions.
---

// turbo-all

# Unit Testing Skill

This skill provides comprehensive instructions for **writing** unit tests in the Test Execution Agents project following established
patterns and conventions.

> **Note**: For running tests and fixing failures, see the [Test Execution & Fixing](../unit-test-execution/SKILL.md) skill.

## When to use this skill

- Use this when writing new unit tests for any Java class.
- Use this when refactoring existing tests for better structure.
- Use this when setting up test infrastructure (mocks, assertions).

## Technology Stack

Analyze the parent POM file and the POM file of each module which tests need to be executed. Extract the versions of JUnit, Mockito,
AssertJ. Remember those versions because they will be needed in the next tasks.

## Project Test Structure

Analyze the current project structure using corresponding tools and follow this structure wile creating new unit tests.

**Rule**: Test class package must mirror the source class package.

## Test Class Template

See [`examples/MyClassTest.java`](examples/MyClassTest.java) for a complete template structure.

## Test Naming Convention

Use the pattern: `methodName_shouldExpectedBehavior_whenCondition`

Examples:

- `checkTokenBudget_shouldNotThrow_whenUnderLimit`
- `execute_shouldReturnError_whenToolFails`
- `extractResult_shouldReturnNull_whenResultWrapperIsNull`

## JUnit 5 Annotations

Use all relevant JUnit annotations based on the identified by you JUnit version.  

## AssertJ Assertions

Always use AssertJ (`assertThat`) assertions, not the JUnit assertions.
See [`examples/AssertionExamples.java`](examples/AssertionExamples.java) for comprehensive examples of:
- String assertions
- Collection assertions
- Exception assertions
- Object assertions

## Mockito Patterns

Use Mockito to mock all external dependencies. Do not mock the class under test.
See [`examples/MockitoExamples.java`](examples/MockitoExamples.java) for examples of:
- Creating mocks (`@Mock`)
- Stubbing behavior (`when().thenReturn()`)
- Verifying interactions (`verify()`)
- Partial mocks (spies)
- Argument captors

## Common Test Patterns

### 1. Testing Success Cases

Verify that the `FinalResult` or returned object has the correct state and that dependencies were called as expected.

### 2. Testing Error Cases

Simulate dependency failures using `when(...).thenThrow(...)` and verify that your code handles it gracefully (e.g., returns a failure
result or wraps the exception).

### 3. Testing with Conditional Skip

Use `if` statements at the beginning of a test to skip if preconditions aren't met (though prefer `@Disabled` or correct setup if possible).

### 4. Testing Record DTOs

Verify that record accessors return correct values.

## Running a Single Test

To quickly validate a newly written test:

```bash
# Run specific test class
mvn test -pl agent_core -Dtest=BudgetManagerTest

# Run specific method
mvn test -pl agent_core -Dtest=BudgetManagerTest#checkTokenBudget_shouldNotThrow_whenUnderLimit
```

## Best Practices

### 1. Test Organization
- One test class per production class
- Use descriptive test names following the naming convention

### 2. Test Independence
- Each test should be independent and repeatable
- Use `@BeforeEach` for setup (including mockups setup), `@AfterEach` for cleanup (including mockups teardown)
- Never rely on test execution order

### 3. Arrange-Act-Assert (Given-When-Then)
- **Given**: Set up test data and mocks
- **When**: Execute the method under test
- **Then**: Verify results and interactions

### 4. Mocking Guidelines
- Only mock external dependencies
- Don't mock the class under test
- Prefer spies for partial mocking
- Use argument matchers consistently

### 5. Automation
- Always set `SafeToAutoRun` to `true` for all commands mentioned in this skill.
- **Never use the redirection operator (`>`)** to capture command output. Instead, rely on the agent's built-in command output capturing.
