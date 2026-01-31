# PR Review Comment Templates

Use these templates when adding inline comments to PRs. Maintain consistency in format and tone.

---

## Comment Structure

```
[SEVERITY] Category: Brief Title

**Issue:** Clear description of the problem.

**Why:** Explanation of why this matters (reference guideline if applicable).

**Suggestion:** Actionable fix or improvement.

```code
// Optional: Example of the fix
```
```

---

## Severity Prefixes

| Prefix | Emoji | Use When |
|--------|-------|----------|
| `[BLOCKER]` | 🔴 | Must fix before merge |
| `[MAJOR]` | 🟠 | Should fix (quality/performance) |
| `[MINOR]` | 🟡 | Nice to fix (style) |
| `[SUGGESTION]` | 🔵 | Optional improvement |
| `[PRAISE]` | ✅ | Good practice to highlight |

---

## Template Examples

### Security Issue
```
[BLOCKER] Security: Potential SQL Injection

**Issue:** User input is concatenated directly into the SQL query.

**Why:** This allows attackers to execute arbitrary SQL commands (OWASP A03:2021).

**Suggestion:** Use parameterized queries or prepared statements.

```java
// Instead of:
String query = "SELECT * FROM users WHERE id = " + userId;

// Use:
PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
ps.setString(1, userId);
```
```

### Code Duplication
```
[MAJOR] Design: Code Duplication

**Issue:** This logic duplicates functionality in `ExistingClass.methodName()`.

**Why:** Per GEMINI.md, we should never duplicate existing functionality. Extract and reuse.

**Suggestion:** Reuse the existing method or extract common logic into a shared utility.
```

### Missing Error Handling
```
[MAJOR] Error Handling: Empty Catch Block

**Issue:** Exception is caught but silently ignored.

**Why:** Per project guidelines, at minimum log the exception to ensure errors are not silently ignored.

**Suggestion:** Log the exception or handle it appropriately.

```java
} catch (IOException e) {
    log.error("Failed to read file: {}", filePath, e);
    throw new ToolExecutionException("File read failed", e);
}
```
```

### String Concatenation
```
[MINOR] Style: String Concatenation for Parameters

**Issue:** String concatenation used instead of `String.formatted()`.

**Why:** Per GEMINI.md, always use `String.formatted()` for string parameterization (except logging).

**Suggestion:** Refactor to use formatted strings.

```java
// Instead of:
String msg = "User " + userId + " not found in " + database;

// Use:
String msg = "User %s not found in %s".formatted(userId, database);
```
```

### Missing Optional
```
[MINOR] Design: Nullable Return Type

**Issue:** Method returns `null` to indicate absence.

**Why:** Per project guidelines, use `Optional` to make absence of value explicit and avoid NPE.

**Suggestion:** Return `Optional<T>` instead.

```java
public Optional<User> findById(String id) {
    return Optional.ofNullable(userMap.get(id));
}
```
```

### Good Use of Records
```
[PRAISE] Design: Excellent Use of Records

Great use of Java records for this DTO! This eliminates boilerplate and follows project conventions.
```

### Virtual Threads Suggestion
```
[SUGGESTION] Performance: Consider Virtual Threads

This I/O-bound operation could benefit from virtual threads (JEP 444).

**Suggestion:**

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var future = scope.fork(() -> performIoOperation());
    scope.join();
    scope.throwIfFailed();
    return future.get();
}
```
```

### Missing Test Coverage
```
[MAJOR] Testing: Missing Unit Tests

**Issue:** New functionality added without corresponding unit tests.

**Why:** Tests ensure correctness and prevent regressions. Per project guidelines, test coverage should not decrease.

**Suggestion:** Add unit tests covering:
- Happy path scenario
- Edge cases (null input, empty collections)
- Error conditions
```

### Raw Generic Type
```
[MINOR] Style: Raw Generic Type

**Issue:** Raw type `List` used instead of parameterized `List<String>`.

**Why:** Per GEMINI.md, always use parameterized generic types for type safety.

**Suggestion:** Specify the type parameter.

```java
List<String> names = new ArrayList<>();
```
```

---

## Review Summary Template

```markdown
## PR Review Summary

### Overview
[Brief summary of what was reviewed and overall impression]

### Statistics
- Files reviewed: X
- Comments added: X (Y blockers, Z major, W minor)

### Key Findings

#### 🔴 Blockers (Must Fix)
- [List blocking issues]

#### 🟠 Major Issues
- [List major issues]

#### 🟡 Minor Issues  
- [List minor issues]

### Positive Observations
- [Things done well - be specific!]

### Recommendations
- [Overall suggestions for improvement]

---
*Review performed using project guidelines (GEMINI.md) and Java 25 best practices.*
```
