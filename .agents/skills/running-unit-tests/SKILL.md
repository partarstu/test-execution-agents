---
name: running-unit-tests
description: Runs the Test Execution Agents unit tests with Maven, diagnoses failing tests and fixes their root cause, asking the user before changing production code. Use when the user asks to run the tests, when tests fail, or to verify a change before reporting it as done.
---

# Running Unit Tests

## Commands

The test commands and the OS profile are in *Commands* of `AGENTS.md`. Reports are in `<module>/target/surefire-reports/`, JaCoCo coverage in `<module>/target/site/jacoco/`. If a module cannot resolve `agent-core`, or fails with `NoSuchMethodError`/`NoClassDefFoundError` on `agent_core` classes, the `agent-core` artifact is stale: run with `-am`, or build it first with `mvn install -pl agent_core -DskipTests`.

## Fix loop

Copy this checklist and track progress:

```
- [ ] Run the affected module(s) and list the failing tests
- [ ] Reproduce one failing test class in isolation
- [ ] Find the root cause and decide: production code bug or outdated test
- [ ] Fix it and re-run that test class
- [ ] Repeat for the remaining failures
- [ ] Re-run the affected module(s)
```

1. **Reproduce** each failure by running only its test class, and read the full stack trace.
2. **Decide what is wrong.** Check recent changes to the code under test (`git diff`, `git log -p -- <file>`). If an intentional change altered the behaviour, update the test. If the test covers code that no longer exists, remove the obsolete test code.
3. **Production code bugs**: never change production code to make a test pass without the user's approval. Present the root cause and the proposed fix, and wait. If the intended behaviour is unclear, ask instead of making the test match the code.
4. **Fix the root cause** with the smallest change, following `JAVA_GUIDELINES.md` (§ 13 for test code). The project-specific failure causes and their fixes are in *Unit tests* of `AGENTS.md`; a compilation or loading error about OpenCV/JavaCV in `ui_test_execution_agent` means the wrong OS profile.
5. **Verify** the single test class, then the module, so the fix introduces no regression.

## Done when

- The affected modules' tests pass without new warnings.
- Each fix is minimal, and the summary to the user states per failure whether the code or the test was wrong.
