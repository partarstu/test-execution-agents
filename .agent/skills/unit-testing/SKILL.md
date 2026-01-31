---
name: Unit Testing
description: Guide for writing and maintaining unit tests using JUnit 5, AssertJ, and Mockito
---

# Unit Testing Skill

This skill provides comprehensive instructions for writing unit tests in the Test Execution Agents project following established patterns and conventions.

## When to use this skill

- Use this when writing new unit tests for any Java class.
- Use this when fixing or refactoring existing tests.
- Use this when setting up test infrastructure (mocks, assertions).

## Technology Stack

| Library | Purpose | Version |
|---------|---------|---------|
| **JUnit 5** | Test framework with annotations (`@Test`, `@BeforeEach`, etc.) | 5.x |
| **AssertJ** | Fluent assertions for readable tests | 3.x |
| **Mockito** | Mocking and stubbing dependencies | 5.x |

## Project Test Structure

```
src/test/java/
└── org/tarik/ta/
    ├── core/                    # Tests mirroring agent_core structure
    │   ├── agents/
    │   ├── dto/
    │   ├── manager/
    │   ├── model/
    │   ├── tools/
    │   └── utils/
    └── [module-specific]/       # Tests for specific agent modules
```

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

| Annotation | Usage |
|------------|-------|
| `@Test` | Mark a method as a test case |
| `@BeforeEach` | Run before each test method |
| `@AfterEach` | Run after each test method |
| `@BeforeAll` | Run once before all tests (static method) |
| `@AfterAll` | Run once after all tests (static method) |
| `@DisplayName` | Custom test name for reports |
| `@Disabled` | Skip a test |
| `@ParameterizedTest` | Run test with multiple inputs |
| `@ValueSource` | Provide values for parameterized tests |

## AssertJ Assertions

Prefer AssertJ (`assertThat`) over JUnit assertions.
See [`examples/AssertionExamples.java`](examples/AssertionExamples.java) for comprehensive examples of:
- String assertions
- Collection assertions
- Exception assertions
- Object assertions

## Mockito Patterns

Use Mockito to mock external dependencies. Do not mock the class under test.
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
Simulate dependency failures using `when(...).thenThrow(...)` and verify that your code handles it gracefully (e.g., returns a failure result or wraps the exception).

### 3. Testing with Conditional Skip
Use `if` statements at the beginning of a test to skip if preconditions aren't met (though prefer `@Disabled` or correct setup if possible).

### 4. Testing Record DTOs
Verify that record accessors return correct values.

## Running Tests

### Run Specific Test Class (Recommended)
```bash
# From project root
mvn test -pl agent_core -Dtest=BudgetManagerTest

# Run specific method
mvn test -pl agent_core -Dtest=BudgetManagerTest#checkTokenBudget_shouldNotThrow_whenUnderLimit
```

### Run All Tests in a Module
```bash
mvn test -pl agent_core
```

## Best Practices

### 1. Test Organization
- One test class per production class
- Group related tests using nested classes with `@Nested`
- Use descriptive test names following the naming convention

### 2. Test Independence
- Each test should be independent and repeatable
- Use `@BeforeEach` for setup, `@AfterEach` for cleanup
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

## Troubleshooting

### Mock Not Working
**Problem**: Stubbed method returns null instead of expected value.
**Solution**: Check that you're using the same argument matchers in both stub and call. Use `eq()` for literals when mixing with `any()`.

### Static Mock Not Cleaning Up
**Problem**: Static mock affects other tests.
**Solution**: Always use try-with-resources with `mockStatic`.
