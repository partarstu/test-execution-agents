# Java Development Guidelines

The Java rules for the Test Execution Agents project. They apply to every `.java` file in the repository, tests included, and are what the skills in `.agents/skills/` write and review code against.

- General working rules (confirming a plan, minimal changes, no reformatting of untouched code, commits, documentation) are in [AGENTS.md](AGENTS.md). Project-specific conventions (agents, tools, A2A executors, Avaje Inject wiring, test fixtures) are in the skills.
- The rules apply to new and changed code. Do not rewrite untouched code only to make it comply.
- The compiler and the IntelliJ IDEA inspections enforce part of these rules; the rest is checked in review. Cite a rule by its section, e.g. "`JAVA_GUIDELINES.md` § 9 Concurrency".

## Contents

1. Principles
2. Language version
3. Style and naming
4. Types and null-safety
5. Data modelling
6. Methods and classes
7. Errors and exceptions
8. Logging
9. Concurrency
10. Standard library and I/O
11. Security
12. Performance
13. Testing
14. Javadoc and comments
15. Dependencies and build
16. Warnings and suppressions
17. Sources

## 1. Principles

- Prioritise clarity over cleverness. No dense one-liners, deeply chained ternaries or stream pipelines that need a second read; extract a well-named method instead.
- Keep nesting shallow: return early from guard clauses instead of wrapping the main path in `if`/`else`.
- Make invalid states hard to represent: records, enums and sealed types over loose strings, maps and boolean flags.
- Prefer immutability: `final` fields, records, unmodifiable collections. Mutable state is local, private and justified.
- Favour composition over inheritance (§ 6).

## 2. Language version

- Write for the version in `<jdk.version>` of the parent `pom.xml` (27) and use its idioms. No compatibility shims for older versions.
- **No preview or incubator features.** The build does not pass `--enable-preview`, so these do not compile and must not be used: structured concurrency (`StructuredTaskScope`, JEP 533), lazy constants (`LazyConstant`, formerly stable values, JEP 531), primitive types in patterns, `instanceof` and `switch` (JEP 532), PEM encodings (JEP 538) and the Vector API (JEP 537). Enabling preview is a project decision, not part of a feature change.
- Use the final features that fit the problem:
    - records, sealed types, record patterns and pattern matching for `switch` and `instanceof` (§ 5)
    - unnamed variables and patterns `_` for values that are deliberately ignored: `catch (InterruptedException _)`, `(_ -> prompt)`, `case Failure(_, var message)` (JEP 456)
    - text blocks for multi-line strings, prompts and JSON
    - `var` for local variables whose type is obvious from the right-hand side; spell the type out when it is not
    - sequenced collections: `getFirst()`, `getLast()`, `reversed()` (JEP 431). They throw `NoSuchElementException` on an empty collection, so guard the call.
    - `Stream.toList()` for unmodifiable results; stream gatherers (JEP 485) only when they are clearer than a loop
    - statements before `super(...)`/`this(...)` to validate or prepare arguments, without touching the instance under construction (JEP 513)
    - scoped values (`ScopedValue`, JEP 506) for context shared down a call chain (§ 9)
    - instance `main` methods for entry points, as `Server.main()` does (JEP 512)
- Do not use module import declarations (`import module java.base;`, JEP 511) in project code: they hide where a name comes from and can make simple names ambiguous. Import single types (§ 3).
- Never mutate `final` fields through deep reflection (`Field.setAccessible(true)` + `set`), in tests neither. JDK 26 and 27 warn about it and a future release will throw (JEP 500). Pass values through constructors instead.
- JDK 27 removed `ThreadPoolExecutor.finalize()`, the `-noverify`, `-Xverify:none` and `-noclassgc` launcher options and JVMCI, and renamed `-XX:InitiatingHeapOccupancyPercent` to `-XX:G1IHOP`. None of them may appear in new code, scripts or launch commands.
- Do not use `sun.misc.Unsafe` memory-access methods; they are deprecated for removal and warn at run time (JEPs 471, 498).

## 3. Style and naming

- The formatter is IntelliJ IDEA's, with the project settings in [code_style/intellij_code_style.xml](code_style/intellij_code_style.xml). The maximum line length is 140 characters: do not wrap a line that fits, even if wrapping would look tidier.
- Standard Java naming: `lowercase` packages, `PascalCase` classes, records, interfaces and enums, `camelCase` methods, fields and variables, `UPPER_SNAKE_CASE` for `static final` constants. Exceptions end in `Exception`, tests in `Test`.
- Names state intent: no abbreviations or single letters except loop indices and lambda parameters in very short lambdas. Booleans read as predicates (`isReady`, `hasAttachments`).
- No magic numbers or strings in logic: use a named constant, an enum or a configuration property.
- Use imports, never fully qualified names in code. Prefer static imports for static methods and constants (`assertThat`, `AiServices.builder`, `Collectors.toUnmodifiableSet`), unless the simple name becomes ambiguous.
- Build strings that contain values with `String.formatted()` (`"Element '%s' not found".formatted(name)`), never with `+` concatenation of parameters. Log messages are the exception (§ 8). Inside loops use `StringBuilder` or `String.join`/`Collectors.joining`.
- Compare enums with `==`, strings and other objects with `equals` or `Objects.equals`.

## 4. Types and null-safety

- Annotate every parameter, return value and field that must not be null with `org.jetbrains.annotations.NotNull`, and those that may be null with `org.jetbrains.annotations.Nullable`. Do not add manual `null` checks for `@NotNull` values. New code uses the JetBrains annotations; do not mix annotation libraries within one file.
- `@NotNull` documents a contract but does not validate data. Values from outside the process (A2A requests, configuration, LLM output, HTTP responses, the database) are validated explicitly where they enter (§ 11).
- Return `Optional<T>` when "no result" is a normal outcome, instead of returning `null`. Do not use `Optional` for fields, parameters or collections: pass a `@Nullable` value or an empty collection instead. Never call `Optional.get()` without a check; use `orElse`, `orElseThrow`, `map`, `ifPresent`.
- Return empty collections, never `null` collections.
- Always parameterise generic types: no raw `List`, `Map`, `Class` or `Provider`. Use bounded wildcards (`List<? extends FinalResult>`) where a method only reads or only writes.
- Declare variables and parameters with the most general type that works (`List`, `Map`, `Collection`) and return concrete, unmodifiable collections.
- Use enums for closed sets of values, and `EnumMap`/`EnumSet` for enum keys.

## 5. Data modelling

- Use records for DTOs, API and tool results, value objects and messages. Validate and normalise their components in the compact constructor, and copy mutable inputs (`List.copyOf(steps)`) so the record stays immutable.
- Model a fixed set of alternatives as a `sealed` interface with record implementations, and handle it with an exhaustive `switch` without a `default` branch, so that a new subtype becomes a compile error at every `switch`.
- Destructure records with record patterns (`case StepFailed(var step, var reason) ->`) instead of chains of accessor calls, and use guards (`case Result r when r.retryable() ->`) instead of nested `if`s.
- Use pattern matching for `instanceof` (`if (part instanceof TextPart text)`), never a type check followed by a cast.
- Use `switch` expressions with arrow labels; no fall-through.
- Replace maps with fixed keys and tuples of positional values with a record.
- Records that are exposed to an LLM as tool parameters or results carry descriptions for every component (see the `creating-new-agent` skill).

## 6. Methods and classes

- A method does one thing. Extract standalone logic into well-named private methods; split a method that grows beyond about 40 lines or needs comments to separate its steps. No monolithic methods.
- More than about five parameters is a sign that related ones belong in a record.
- Do not mutate objects passed in by the caller unless that is the method's documented purpose.
- Process collections declaratively with streams (`map`, `filter`, `collect`, `toList()`) instead of loops that build up mutable state. Keep lambdas free of side effects, and use a plain loop when it is clearer (checked exceptions, early exit, index arithmetic).
- Leave classes, records, interfaces and their members package-private by default. Make them `public` only when they are part of a module's API used from another package or module (e.g. `agent_core` types used by the agents).
- Prefer composition and delegation over inheritance. Extend a class only for a true "is-a" relationship that the framework requires (`AbstractAgentExecutor`, `AbstractTools`, `AgentConfig`), and keep hierarchies shallow. Use `final` or `sealed` for classes that are not designed for extension.
- Inject dependencies through the constructor into `final` fields. No field injection and no service lookups inside business logic.
- Never duplicate existing logic: reuse it, or extract it into a shared method or component and use it from both places.
- A class that owns a resource (client, executor, stream, `BeanScope`) implements `AutoCloseable`, and callers use try-with-resources.
- Utility classes are `final`, have a private constructor and hold only stateless static methods.
- Override `equals`, `hashCode` and `toString` together, or use a record.

## 7. Errors and exceptions

- Catch the most specific exception possible. Catch `Exception` or `RuntimeException` only at a boundary (A2A executor, tool error handler, request handler, scheduled task, entry point), where the error is logged and turned into a result, a task failure or a clean exit.
- Never leave a `catch` block empty. If continuing is correct, log the exception and make clear why continuing is safe.
- Keep `try` blocks to the statements that can throw the handled exception. Close resources with try-with-resources, never in a hand-written `finally`.
- When translating an exception, pass the original as the cause: `throw new ToolExecutionException(message, e)`.
- On `InterruptedException`, restore the flag with `Thread.currentThread().interrupt()` and stop the work, unless the exception is rethrown.
- Do not use exceptions for normal control flow; model expected outcomes with return values (`Optional`, a result record).
- Use standard exceptions for their standard meaning (`IllegalArgumentException`, `IllegalStateException`, `UnsupportedOperationException`) and a specific project exception otherwise. Never throw bare `Exception` or `RuntimeException`.
- Error messages say what failed and include the offending value, never a secret: `"Unsupported auth type: %s".formatted(type)`.
- Log an exception once, where it is handled, not at every layer it passes through.
- Use `assert` only in tests; assertions are disabled at run time by default.

## 8. Logging

- SLF4J with Logback. One logger per class: `private static final Logger LOG = LoggerFactory.getLogger(Foo.class);`. No `System.out`/`System.err` in application code.
- Pass values as `{}` placeholders, not pre-formatted strings: `LOG.info("Executing step {} of test case '{}'", index, name)`. Formatting is then skipped for disabled levels.
- Pass the exception as the last argument so the stack trace is logged: `LOG.error("Could not persist procedure {}", procedureId, e)`.
- When an error message with a cause is built with `String.formatted()` (e.g. because it is also returned or thrown), extract it into a variable first and pass that variable: `var message = "...".formatted(id); LOG.error(message, e);`.
- Levels: `DEBUG` for diagnostics, `INFO` for significant events, `WARN` for recoverable anomalies, `ERROR` for a failed operation.
- Include the identifiers that make a record traceable (task ID, test case name, step index, element ID). Never log secrets, tokens, credentials, full prompts or payloads that may carry personal data.
- Guard expensive argument computation with `if (LOG.isDebugEnabled())`.
- Every new flow logs enough to follow it in production: start and end of significant operations, decisions and failures.

## 9. Concurrency

Pick the model by workload:

| Workload                                                   | Use                                                                    |
|------------------------------------------------------------|------------------------------------------------------------------------|
| Independent blocking I/O calls (HTTP, LLM, database, disk) | virtual threads: `Executors.newVirtualThreadPerTaskExecutor()`         |
| CPU-bound work (image processing, embeddings, hashing)     | a bounded platform-thread pool sized to the cores                      |
| Periodic or delayed tasks                                  | `ScheduledExecutorService`                                             |
| Context shared down a call chain                           | `ScopedValue`                                                          |

### Virtual threads

- Use virtual threads for I/O-bound tasks and run independent I/O operations in parallel rather than sequentially.
- Never pool virtual threads: create one per task. Limit concurrent access to a scarce resource (rate-limited API, connection) with a `Semaphore`, not with the thread count.
- Do not use virtual threads for long-running CPU-bound work.
- Scope executors with try-with-resources: `ExecutorService.close()` waits for all submitted tasks, so no task outlives the method. Collect the `Future`s, and on the first failure cancel the rest before rethrowing. `StructuredTaskScope` is a preview API and not allowed (§ 2).
- Since JDK 24, `synchronized` no longer pins a virtual thread to its carrier (JEP 491). Choose between `synchronized` and `java.util.concurrent.locks` by what the code needs: use `ReentrantLock` for timed, interruptible or conditional locking (`tryLock`, `lockInterruptibly`, `Condition`), `synchronized` otherwise. Do not rewrite working `synchronized` code only for virtual threads.
- Pinning still happens in native code and foreign functions and in class initialisers. Keep blocking I/O out of static initialisers, and diagnose pinning with the JFR event `jdk.VirtualThreadPinned`.
- Use `ScopedValue` instead of `ThreadLocal` to pass context to code running in virtual threads. Never cache expensive objects in a `ThreadLocal` on virtual threads: each thread gets its own copy.

### Shared state

- Prefer immutable data and message passing. Protect shared mutable state with a lock, or use `java.util.concurrent` collections and atomics (`ConcurrentHashMap`, `AtomicInteger`, `LongAdder`). Check-then-act sequences are never atomic: use `computeIfAbsent`, `merge` or a lock.
- Document the thread-safety of a class that is shared between threads.
- Bound every wait: timeouts on `Future.get`, `await`, HTTP and LLM calls.
- Avoid `CompletableFuture` chains where plain blocking code on a virtual thread is clearer. If you use them, always pass an executor (the default is the common pool) and handle exceptions in the chain.
- Never start unmanaged `new Thread(...)` for application work; use an executor so the thread's lifecycle and failures are visible.

## 10. Standard library and I/O

- Manage every closeable resource with try-with-resources: streams, readers, HTTP responses, executors, `BeanScope`, `MockedStatic`.
- Use `java.nio.file.Path` and `Files` for file operations, not `java.io.File` string handling.
- Pass `StandardCharsets.UTF_8` whenever bytes and text are converted.
- Use `java.time`: `Instant` for timestamps, `Duration` for timeouts, `ZonedDateTime` only for display. Measure elapsed time with `System.nanoTime()` or `Instant`/`Duration`, never with `System.currentTimeMillis()` differences across clock changes.
- Run external processes with `ProcessBuilder` and an argument list, never a single shell string.
- Reuse `HttpClient`, `ObjectMapper`, LLM `ChatModel` and other expensive, thread-safe clients instead of creating them per call. The JDK `HttpClient` uses HTTP/2 by default; HTTP/3 (JEP 517) is opt-in.
- Since JDK 27 the JDK `HttpServer` matches a context path by path segments (`/foo` matches `/foo/bar`, not `/foobar`). Register exact paths in test doubles.
- Use `SecureRandom` for tokens and anything unguessable, `UUID.randomUUID()` for random IDs.
- Reach for the JDK, Guava and Apache Commons (already dependencies) before writing an equivalent by hand.

## 11. Security

The general rules (never trust external input, secrets only from environment variables or a secrets manager) are in [AGENTS.md](AGENTS.md). In Java code:

- Validate external input where it enters, before it reaches business logic, file paths, queries, commands or prompts: A2A messages, configuration values, HTTP responses and LLM output.
- Treat LLM output as untrusted input: parse it into a record, validate it, and never execute it or use it unchecked as a path, URL, query or command.
- Guard against prompt injection: keep untrusted text clearly delimited in prompts and never let it change tool permissions.
- Bind parameters in Cypher (Neo4j) and SQL queries; never concatenate or format input into a query string.
- Confine file paths built from input: `base.resolve(input).normalize()` and check `startsWith(base)`.
- Allow-list the scheme and host of any outbound request whose URL comes from input (SSRF); reuse the existing guard in the API agent (`OutboundRequestGuard`) instead of writing a new one.
- Never disable TLS certificate or host-name verification outside explicitly configured test setups.
- TLS 1.3 negotiates the post-quantum hybrid key exchange `X25519MLKEM768` by default (JEP 527). Do not override `jdk.tls.namedGroups` unless a peer requires it.
- Jackson: never enable default typing or polymorphic deserialization of untrusted data; deserialize into concrete records.
- Disable external entities (XXE) when parsing XML.
- Compare secrets and tokens with `MessageDigest.isEqual`. Use SHA-256 or stronger for integrity checks.
- Keep secrets out of logs, exception messages, `toString()` (override it in records holding secrets) and prompts.

## 12. Performance

- Measure before optimising: JFR and JDK Mission Control for profiling, JMH for micro-benchmarks. Keep the readable version unless the measured gain matters.
- G1 and compact object headers are the JVM defaults since JDK 27 (JEP 523, JEP 534). Do not pass `-XX:+UseG1GC`, `-XX:+UseCompactObjectHeaders` or the obsolete `-XX:[+|-]UseCompressedClassPointers` in launch commands, and profile with JFR knowing that it redacts command-line arguments, environment variables and system properties (JEP 536).
- Choose the right data structure: `HashMap`/`HashSet` for lookups and membership (O(1) on average), `ArrayList` for indexed access, `ArrayDeque` for queues and stacks, `EnumMap` for enum keys.
- Use primitives instead of wrapper types in performance-critical code and large collections to avoid boxing; use `IntStream`/`LongStream` and primitive arrays there.
- Use `StringBuilder` for string building in loops, never `+=`.
- Avoid creating large or unnecessary objects in loops and hot paths: hoist invariants, precompile `Pattern`s into constants, reuse buffers and clients.
- Batch remote calls (database, LLM, embeddings) instead of calling them per element.
- Stream large files and HTTP bodies instead of loading them into memory at once.
- Size collections when the size is known (`new ArrayList<>(expectedSize)`).
- Cache the results of pure, expensive computations with a bounded cache; an unbounded `Map` cache is a memory leak.

## 13. Testing

The project's test layout, reference tests and pitfalls are in the `writing-unit-tests` and `running-unit-tests` skills.

- JUnit 6 (Jupiter) for structure, AssertJ for assertions, Mockito for test doubles. Use AssertJ's `assertThat`, `assertThatThrownBy` and `assertThatCode` rather than JUnit's `Assertions`.
- Test files are named `<ClassUnderTest>Test` and live in `src/test/java` in the same package as the class under test, so package-private members are tested without making them `public`.
- One behaviour per test, named `method_shouldExpectedBehaviour_whenCondition`, structured as given, when, then. Add `@DisplayName` when the class you edit uses it.
- Cover the success path, every failure path the code handles explicitly and the edge cases it branches on (`null` where allowed, empty collections, boundary values).
- Use `@ParameterizedTest` for the same assertion over several inputs, `@TempDir` for files, `@Nested` to group related cases in large test classes.
- Initialise mocks with `@ExtendWith(MockitoExtension.class)`, which applies strict stubbing and fails on unused stubs, or with `MockitoAnnotations.openMocks(this)` in `@BeforeEach`, closing the returned `AutoCloseable` in `@AfterEach`. Follow the style of the test class you edit.
- Create mocks with `@Mock` or `mock(Type.class)`, stub with `when(...).thenReturn(...)` and verify the interactions that matter with `verify(mock).method(...)` and argument matchers (`any()`, `eq(...)`, `ArgumentCaptor`). Do not mix raw values and matchers in one call.
- Use `@Spy` or `spy(object)` for partial mocks and stub them with `doReturn(...).when(spy).method(...)`. Use `lenient()` only for a stub that some tests legitimately do not use.
- Mock boundaries only (LLM models, Neo4j, HTTP, screen and OS, clock) and exercise the real logic. Do not mock the class under test, records or value objects.
- Open `mockStatic(...)` and `mockConstruction(...)` in try-with-resources so they never leak into other tests.
- Tests are deterministic and independent of each other and of execution order: no real network, no shared mutable static state, no `Thread.sleep()` for synchronisation (use latches, `Future.get` with a timeout, or an injected clock).
- Assert observable behaviour and outputs, not private implementation details.
- When fixing tests, run the specific test class, not the whole package or module.
- Tests that need real infrastructure or a GUI are `@Disabled` with a reason; end-to-end tests are tagged `@Tag("smoke")`.

## 14. Javadoc and comments

The rule is the *Comments and docstrings* section of `AGENTS.md`; this section only fixes its Java form.

- A Javadoc starts with a one-sentence summary and matches the comment style of the file (`/** */` or Markdown `///`, JEP 467).
- Parameter, return and exception sections are `@param`, `@return` and `@throws` tags.
- No commented-out code. A `TODO` names its reason or issue.
- Update Javadoc and comments in the same change as the code they describe.

## 15. Dependencies and build

- Maven is the build tool. The parent `pom.xml` is the single source of truth for versions: declare them as properties and in `<dependencyManagement>`/`<pluginManagement>`; modules declare dependencies without versions.
- Use only dependencies declared directly in the module's `pom.xml`, never a transitive one. Check with `mvn dependency:tree` and `mvn dependency:analyze`.
- Before adding a dependency, prefer the JDK or an existing dependency. A new one must be actively maintained, license-compatible with AGPL-3.0 and free of known vulnerabilities.
- Every source file carries the AGPL license header; `mvn license:format` adds it.
- Native libraries (OpenCV, JavaCV) are selected by the `windows`/`linux` Maven profiles; build and test the UI agent with the profile of the target OS.

## 16. Warnings and suppressions

- A change introduces no new compiler warnings, deprecation usages or IntelliJ IDEA inspection warnings in the lines it touches.
- `@SuppressWarnings` is placed on the narrowest element (a variable, not the class) with a specific key (`"unchecked"`), and a comment explains why when it is not obvious.

## 17. Sources

- OpenJDK: [JDK 27](https://openjdk.org/projects/jdk/27/), [JEP 431](https://openjdk.org/jeps/431), [JEP 456](https://openjdk.org/jeps/456), [JEP 467](https://openjdk.org/jeps/467), [JEP 471](https://openjdk.org/jeps/471), [JEP 485](https://openjdk.org/jeps/485), [JEP 491](https://openjdk.org/jeps/491), [JEP 498](https://openjdk.org/jeps/498), [JEP 500](https://openjdk.org/jeps/500), [JEP 506](https://openjdk.org/jeps/506), [JEP 511](https://openjdk.org/jeps/511), [JEP 513](https://openjdk.org/jeps/513), [JEP 517](https://openjdk.org/jeps/517), [JEP 523](https://openjdk.org/jeps/523), [JEP 527](https://openjdk.org/jeps/527), [JEP 531](https://openjdk.org/jeps/531), [JEP 532](https://openjdk.org/jeps/532), [JEP 533](https://openjdk.org/jeps/533), [JEP 534](https://openjdk.org/jeps/534), [JEP 536](https://openjdk.org/jeps/536), [JEP 537](https://openjdk.org/jeps/537), [JEP 538](https://openjdk.org/jeps/538)
- Oracle: [Virtual Threads (Java SE 27 Core Libraries)](https://docs.oracle.com/en/java/javase/27/core/virtual-threads.html), [JDK 27 Release Notes](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html)
- [Java 27 Features](https://www.happycoders.eu/java/java-27-features/), HappyCoders
- [Essential Information on Virtual Threads](https://sapmachine.io/docs/essential-information-on-virtual-threads/), SapMachine
- Joshua Bloch, *Effective Java*, 3rd edition (2018)
- Brian Goetz et al., *Java Concurrency in Practice* (2006)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [JUnit User Guide](https://docs.junit.org/current/user-guide/), [Mockito documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html), [AssertJ documentation](https://assertj.github.io/doc/)
- [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/)
- [SLF4J manual](https://www.slf4j.org/manual.html)
