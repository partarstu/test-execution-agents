/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tarik.ta.smoke;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.langchain4j.service.AiServices;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.PreconditionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.model.AuthType;
import org.tarik.ta.smoke.ScriptedChatModel.ScriptedToolCall;
import org.tarik.ta.tools.ApiAssertionTools;
import org.tarik.ta.tools.ApiRequestTools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;

/**
 * Hermetic end-to-end smoke test for the API test execution agent. The whole agent flow runs for real
 * ({@link ApiTestAgent} -> step / precondition agents -> real {@link ApiRequestTools} / {@link ApiAssertionTools});
 * only the external boundaries are mocked: the LLM is replaced by a {@link ScriptedChatModel}, and the target API by a
 * local WireMock server which records the requests that actually left the agent. Authentication credentials are read
 * through {@code CommonUtils.getEnvironmentVariable}, which falls back to system properties, so the auth cases inject
 * credentials via {@link System#setProperty} rather than real environment variables.
 */
@Tag("smoke")
@DisplayName("API agent - hermetic end-to-end smoke")
class ApiAgentSmokeTest {

    private static final String SEND_REQUEST_TOOL = "sendRequest";
    private static final String UPLOAD_FILE_TOOL = "uploadFile";
    private static final String STORE_VARIABLE_TOOL = "storeVariableIntoContext";
    private static final String VALIDATE_SCHEMA_TOOL = "validateSchema";
    private static final String RESULT_TOOL = "endExecutionAndGetFinalResult";

    // Auth credential seam: env-var names the config points at, and the values injected as system properties.
    private static final String BASIC_USERNAME_ENV = "SMOKE_API_USERNAME";
    private static final String BASIC_PASSWORD_ENV = "SMOKE_API_PASSWORD";
    private static final String BEARER_TOKEN_ENV = "SMOKE_API_BEARER";
    private static final String API_KEY_NAME_ENV = "SMOKE_API_KEY_NAME";
    private static final String API_KEY_VALUE_ENV = "SMOKE_API_KEY_VALUE";

    private static WireMockServer wireMock;

    private ApiTestAgentConfig config;
    private TestExecutionContext executionContext;
    private ApiContext apiContext;
    private TestCaseExtractor testCaseExtractor;
    private LogCapture logCapture;

    @TempDir
    private Path tempDir;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        config = mock(ApiTestAgentConfig.class);
        lenient().when(config.getTargetBaseUri()).thenReturn(Optional.empty());
        lenient().when(config.getProxyHost()).thenReturn(Optional.empty());
        lenient().when(config.getProxyPort()).thenReturn(8080);
        lenient().when(config.getRelaxedHttpsValidation()).thenReturn(false);
        lenient().when(config.getDefaultContentType()).thenReturn("application/json");
        lenient().when(config.getDefaultAuthType()).thenReturn(AuthType.NONE);
        // WireMock binds to loopback, so it must be allow-listed to pass the SSRF guard.
        lenient().when(config.getOutboundHostAllowlist()).thenReturn(Set.of("localhost", "127.0.0.1"));
        lenient().when(config.getUploadBaseDir()).thenReturn(Optional.empty());
        lenient().when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(2, 10, 5000));
        lenient().when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(3000);
        lenient().when(config.getAgentTokenBudget()).thenReturn(1_000_000);
        lenient().when(config.getAgentToolCallsBudget()).thenReturn(10);
        lenient().when(config.getBasicAuthUsernameEnv()).thenReturn(BASIC_USERNAME_ENV);
        lenient().when(config.getBasicAuthPasswordEnv()).thenReturn(BASIC_PASSWORD_ENV);
        lenient().when(config.getBearerTokenEnv()).thenReturn(BEARER_TOKEN_ENV);
        lenient().when(config.getApiKeyNameEnv()).thenReturn(API_KEY_NAME_ENV);
        lenient().when(config.getApiKeyValueEnv()).thenReturn(API_KEY_VALUE_ENV);

        // Constructing the BudgetManager publishes it as the static instance the agents read via getInstance().
        new BudgetManager(config);

        executionContext = new TestExecutionContext(StreamingEventEmitter.NOOP);
        apiContext = new ApiContext(config);
        testCaseExtractor = mock(TestCaseExtractor.class);
        logCapture = mock(LogCapture.class);
        when(logCapture.getLogs()).thenReturn(List.of());
    }

    @AfterEach
    void clearAuthProperties() {
        System.clearProperty(BASIC_USERNAME_ENV);
        System.clearProperty(BASIC_PASSWORD_ENV);
        System.clearProperty(BEARER_TOKEN_ENV);
        System.clearProperty(API_KEY_NAME_ENV);
        System.clearProperty(API_KEY_VALUE_ENV);
    }

    @Test
    @DisplayName("Single GET step executes a real HTTP request and passes")
    void singleGetStepPasses() {
        wireMock.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        var testCase = singleStepTestCase("Ping smoke", "Send a GET request to /ping", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/ping"), result(true, "Received status 200"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/ping")));
    }

    @Test
    @DisplayName("Precondition + step happy path passes and carries the full result contract")
    void preconditionAndStepHappyPathFillsContract() {
        wireMock.stubFor(get(urlEqualTo("/setup")).willReturn(aResponse().withStatus(200).withBody("ready")));
        wireMock.stubFor(get(urlEqualTo("/resource")).willReturn(aResponse().withStatus(200).withBody("ok")));
        when(logCapture.getLogs()).thenReturn(List.of("execution started", "execution finished"));

        var precondition = "The resource endpoint is reachable";
        var step = new TestStep("Send a GET request to /resource", List.of(), "Status is 200");
        var testCase = new TestCase("Full contract", List.of(precondition), List.of(step));

        Function<String, List<ScriptedToolCall>> script = userText -> userText.contains("Precondition:")
                ? List.of(sendGet(wireMock.baseUrl() + "/setup"), result(true, "setup complete"))
                : List.of(sendGet(wireMock.baseUrl() + "/resource"), result(true, "Status was 200"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getTestCaseName()).isEqualTo("Full contract");
        assertThat(result.getGeneralErrorMessage()).isNull();
        // The API agent never populates SystemInfo (that is the UI agent's responsibility).
        assertThat(result.getSystemInfo()).isNull();
        assertThat(result.getExecutionStartTimestamp()).isNotNull();
        assertThat(result.getExecutionEndTimestamp()).isNotNull();
        assertThat(result.getLogs()).containsExactly("execution started", "execution finished");

        assertThat(result.getPreconditionResults()).singleElement().satisfies(pre -> {
            assertThat(pre.getPrecondition()).isEqualTo(precondition);
            assertThat(pre.isSuccess()).isTrue();
            assertThat(pre.getErrorMessage()).isNull();
        });
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getTestStep()).isEqualTo(step);
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.SUCCESS);
            assertThat(stepResult.getActualResult()).isEqualTo("Status was 200");
            assertThat(stepResult.getErrorMessage()).isNull();
        });

        wireMock.verify(getRequestedFor(urlEqualTo("/setup")));
        wireMock.verify(getRequestedFor(urlEqualTo("/resource")));
    }

    @Test
    @DisplayName("Failed verification yields FAILED with the failure details")
    void failedVerificationYieldsFailed() {
        wireMock.stubFor(get(urlEqualTo("/resource")).willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Failing verification", "Send a GET request to /resource", "Status is 201");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/resource"), result(false, "Expected status 201 but got 200"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.FAILURE);
            assertThat(stepResult.getErrorMessage()).contains("Expected status 201 but got 200");
        });
        assertThat(result.getGeneralErrorMessage()).contains("Expected status 201 but got 200");
    }

    @Test
    @DisplayName("BASIC auth attaches the credentials to the outgoing request")
    void basicAuthReachesStub() {
        System.setProperty(BASIC_USERNAME_ENV, "alice");
        System.setProperty(BASIC_PASSWORD_ENV, "s3cret");
        wireMock.stubFor(get(urlEqualTo("/secure")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("Basic auth", "Send an authenticated GET request to /secure", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGetWithAuth(wireMock.baseUrl() + "/secure", "BASIC"), result(true, "authenticated"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        var expected = "Basic " + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));
        wireMock.verify(getRequestedFor(urlEqualTo("/secure")).withHeader("Authorization", equalTo(expected)));
    }

    @Test
    @DisplayName("BEARER auth attaches the token to the outgoing request")
    void bearerAuthReachesStub() {
        System.setProperty(BEARER_TOKEN_ENV, "tok-123");
        wireMock.stubFor(get(urlEqualTo("/secure")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("Bearer auth", "Send an authenticated GET request to /secure", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGetWithAuth(wireMock.baseUrl() + "/secure", "BEARER"), result(true, "authenticated"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/secure")).withHeader("Authorization", equalTo("Bearer tok-123")));
    }

    @Test
    @DisplayName("API-Key auth attaches the configured header to the outgoing request")
    void apiKeyAuthReachesStub() {
        System.setProperty(API_KEY_NAME_ENV, "X-Api-Key");
        System.setProperty(API_KEY_VALUE_ENV, "key-xyz");
        wireMock.stubFor(get(urlEqualTo("/secure")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("API key auth", "Send an authenticated GET request to /secure", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGetWithAuth(wireMock.baseUrl() + "/secure", "API_TOKEN"), result(true, "authenticated"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/secure")).withHeader("X-Api-Key", equalTo("key-xyz")));
    }

    @Test
    @DisplayName("${var} placeholders in the URL resolve against the shared data")
    void variableSubstitutionResolvesAgainstSharedData() {
        wireMock.stubFor(get(urlEqualTo("/items/42")).willReturn(aResponse().withStatus(200).withBody("found")));

        var testCase = singleStepTestCase("Variable substitution", "Fetch the stored item", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                new ScriptedToolCall(STORE_VARIABLE_TOOL,
                        """
                        {"variableName":"resourceId","variableValue":"42"}"""),
                sendGet(wireMock.baseUrl() + "/items/${resourceId}"),
                result(true, "Fetched item 42"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/items/42")));
    }

    @Test
    @DisplayName("Cookies set by an earlier response are propagated to later requests")
    void cookiesPropagateAcrossRequests() {
        wireMock.stubFor(get(urlEqualTo("/login"))
                .willReturn(aResponse().withStatus(200).withHeader("Set-Cookie", "SMOKE_SESSION=abc123")));
        wireMock.stubFor(get(urlEqualTo("/profile")).willReturn(aResponse().withStatus(200).withBody("profile")));

        var testCase = singleStepTestCase("Cookie propagation", "Log in and then fetch the profile", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/login"),
                sendGet(wireMock.baseUrl() + "/profile"),
                result(true, "Profile fetched"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/profile")).withCookie("SMOKE_SESSION", equalTo("abc123")));
    }

    @Test
    @DisplayName("JSON-schema validation of the last response passes for a matching body")
    void jsonSchemaValidationPasses() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/user")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id":1,"name":"Alice"}""")));
        Path schema = Files.writeString(tempDir.resolve("user-schema.json"), """
                {
                  "$schema": "http://json-schema.org/draft-04/schema#",
                  "type": "object",
                  "required": ["id", "name"],
                  "properties": {
                    "id": {"type": "integer"},
                    "name": {"type": "string"}
                  }
                }""");

        var testCase = singleStepTestCase("Schema validation", "Fetch the user and validate the response schema",
                "Body matches the user schema");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/user"),
                new ScriptedToolCall(VALIDATE_SCHEMA_TOOL,
                        """
                        {"schemaPath":"%s"}""".formatted(jsonPath(schema))),
                result(true, "Schema matched"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/user")));
    }

    @Test
    @DisplayName("SSRF guard blocks a link-local/metadata host and nothing leaves the agent")
    void ssrfGuardBlocksMetadataHost() {
        // With no allow-list configured, the guard falls back to SSRF range-blocking.
        when(config.getOutboundHostAllowlist()).thenReturn(Set.of());

        var testCase = singleStepTestCase("SSRF guard", "Read the cloud metadata endpoint", "Metadata is returned");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet("http://169.254.169.254/latest/meta-data/"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("blocked address");
        });
        assertThat(wireMock.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("uploadFile is refused when no upload base directory is configured")
    void uploadFileRefusedWhenBaseDirUnset() throws Exception {
        Path file = Files.writeString(tempDir.resolve("payload.txt"), "data");
        wireMock.stubFor(post(urlEqualTo("/upload")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("Upload refused", "Upload the payload file", "File is uploaded");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                uploadFile(wireMock.baseUrl() + "/upload", file));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getErrorMessage()).contains("upload base directory"));
        wireMock.verify(0, postRequestedFor(urlEqualTo("/upload")));
    }

    @Test
    @DisplayName("uploadFile succeeds for a file inside the configured base directory")
    void uploadFileSucceedsWithinBaseDir() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("uploads"));
        Path file = Files.writeString(baseDir.resolve("payload.txt"), "data");
        when(config.getUploadBaseDir()).thenReturn(Optional.of(baseDir.toString()));
        wireMock.stubFor(post(urlEqualTo("/upload")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("Upload allowed", "Upload the payload file", "File is uploaded");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                uploadFile(wireMock.baseUrl() + "/upload", file), result(true, "File uploaded"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(postRequestedFor(urlEqualTo("/upload")));
    }

    @Test
    @DisplayName("uploadFile is refused for a file outside the configured base directory")
    void uploadFileRefusedOutsideBaseDir() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("uploads"));
        Path outsideFile = Files.writeString(Files.createDirectories(tempDir.resolve("outside")).resolve("payload.txt"), "data");
        when(config.getUploadBaseDir()).thenReturn(Optional.of(baseDir.toString()));
        wireMock.stubFor(post(urlEqualTo("/upload")).willReturn(aResponse().withStatus(200)));

        var testCase = singleStepTestCase("Upload escape", "Upload a file outside the upload directory", "File is uploaded");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                uploadFile(wireMock.baseUrl() + "/upload", outsideFile));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult ->
                assertThat(stepResult.getErrorMessage()).contains("outside the allowed upload directory"));
        wireMock.verify(0, postRequestedFor(urlEqualTo("/upload")));
    }

    @Test
    @DisplayName("An over-budget run terminates with ERROR instead of running away")
    void budgetExhaustionTerminatesRun() {
        // A 1-second time budget plus a retry whose delay exceeds it makes the budget check fail on the second attempt.
        when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(1);
        when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(3, 2100, 10000));
        new BudgetManager(config);
        wireMock.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Budget exhaustion", "Send a GET request to /slow", "Status is 201");
        // The verification keeps failing, forcing the agent into the retry path where the time budget is re-checked.
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/slow"), result(false, "Status was not 201 yet"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("budget exceeded");
        });
    }

    private TestExecutionResult execute(@NotNull TestCase testCase, @NotNull ScriptedChatModel model) {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));
        return buildAgent(model).executeTestCase("run");
    }

    private static TestCase singleStepTestCase(@NotNull String name, @NotNull String stepDescription, @NotNull String expectedResults) {
        return new TestCase(name, List.of(), List.of(new TestStep(stepDescription, List.of(), expectedResults)));
    }

    private static ScriptedChatModel model(@NotNull Function<String, List<ScriptedToolCall>> sequenceByUserMessage) {
        return new ScriptedChatModel(ScriptedChatModel.ofSequence(sequenceByUserMessage));
    }

    private static ScriptedToolCall sendGet(@NotNull String url) {
        return new ScriptedToolCall(SEND_REQUEST_TOOL, """
                {"method":"GET","url":"%s"}""".formatted(url));
    }

    private static ScriptedToolCall sendGetWithAuth(@NotNull String url, @NotNull String authType) {
        return new ScriptedToolCall(SEND_REQUEST_TOOL, """
                {"method":"GET","url":"%s","authType":"%s"}""".formatted(url, authType));
    }

    private static ScriptedToolCall uploadFile(@NotNull String url, @NotNull Path file) {
        return new ScriptedToolCall(UPLOAD_FILE_TOOL, """
                {"url":"%s","filePath":"%s","multipartName":"file"}""".formatted(url, jsonPath(file)));
    }

    private static ScriptedToolCall result(boolean success, @NotNull String message) {
        return new ScriptedToolCall(RESULT_TOOL, """
                {"result":{"success":%b,"message":"%s"}}""".formatted(success, message));
    }

    /** Escapes a filesystem path so it is a valid JSON string value (Windows paths contain backslashes). */
    private static String jsonPath(@NotNull Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private ApiTestAgent buildAgent(ScriptedChatModel model) {
        var requestTools = new ApiRequestTools(apiContext, executionContext, config);
        var assertionTools = new ApiAssertionTools(apiContext, executionContext);
        var dataTools = new TestContextDataTools(executionContext);
        var toolProvider = new InheritanceAwareToolProvider<>(List.of(requestTools, assertionTools, dataTools),
                VerificationExecutionResult.class);

        ApiTestStepActionAgent stepAgent = AiServices.builder(ApiTestStepActionAgent.class)
                .chatModel(model)
                .systemMessageProvider(request -> "You are an API test execution agent.")
                .toolProvider(toolProvider)
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(config.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(10)
                .build();
        ApiPreconditionActionAgent preconditionAgent = AiServices.builder(ApiPreconditionActionAgent.class)
                .chatModel(model)
                .systemMessageProvider(request -> "You are an API test execution agent.")
                .toolProvider(toolProvider)
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(config.getActionRetryPolicy()))
                .maxToolCallingRoundTrips(10)
                .build();

        return new ApiTestAgent(config, testCaseExtractor, BudgetManager.getInstance(), executionContext, logCapture,
                () -> preconditionAgent, () -> stepAgent);
    }
}
