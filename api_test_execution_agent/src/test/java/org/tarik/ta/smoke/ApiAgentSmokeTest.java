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
import com.github.tomakehurst.wiremock.http.Fault;
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
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ChatModelEventListener;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.model.AuthType;
import org.tarik.ta.smoke.ScriptedChatModel.Script;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
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
    private static final String VALIDATE_SCHEMA_TOOL = "validateSchema";
    private static final String VALIDATE_OPENAPI_TOOL = "validateOpenApi";
    private static final String LOAD_JSON_TOOL = "loadJsonData";
    private static final String LOAD_CSV_TOOL = "loadCsvData";
    private static final String LAST_RESPONSE_TOOL = "getLastApiResponse";
    private static final String STORE_VARIABLE_TOOL = "storeVariableIntoContext";
    private static final String RESULT_TOOL = "endExecutionAndGetFinalResult";
    // langchain4j keys the final-result tool's single object argument by the method parameter's reflected name, which is
    // "result" when -parameters applies and "arg0" otherwise. Reading it the same way keeps the scripted JSON in sync
    // with whatever name the runtime exposes, so the test does not silently depend on the compiler flag.
    private static final String RESULT_PARAM = resultToolParameterName();

    // Auth credential seam: env-var names the config points at, and the values injected as system properties.
    private static final String BASIC_USERNAME_ENV = "SMOKE_API_USERNAME";
    private static final String BASIC_PASSWORD_ENV = "SMOKE_API_PASSWORD";
    private static final String BEARER_TOKEN_ENV = "SMOKE_API_BEARER";
    private static final String API_KEY_NAME_ENV = "SMOKE_API_KEY_NAME";
    private static final String API_KEY_VALUE_ENV = "SMOKE_API_KEY_VALUE";

    private static WireMockServer wireMock;
    private static WireMockServer wireMockHttps;

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
        // A separate server whose baseUrl() is HTTPS with WireMock's self-signed certificate, for the
        // relaxed-HTTPS-validation cases. Enabling HTTPS on the main server would flip its baseUrl() to HTTPS too.
        wireMockHttps = new WireMockServer(wireMockConfig().dynamicPort().dynamicHttpsPort());
        wireMockHttps.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
        wireMockHttps.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        wireMockHttps.resetAll();

        config = newSmokeConfig();

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
    @DisplayName("A failed verification recovers on the retry attempt and the test case passes")
    void verificationRetryRecoversOnSecondAttempt() {
        wireMock.stubFor(get(urlEqualTo("/eventually-ok")).willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Retry recovery", "Send a GET request to /eventually-ok", "Status is 200");
        // Each retry is a fresh agent invocation starting at round 0, which is where the attempt counter advances. The
        // verification fails on the first attempt and passes on the second, driving the executeWithRetry recovery path.
        var attempts = new AtomicInteger();
        Script script = (userText, round, priorResults) -> {
            if (round == 0) {
                attempts.incrementAndGet();
                return sendGet(wireMock.baseUrl() + "/eventually-ok");
            }
            return attempts.get() == 1 ? result(false, "Not ready yet") : result(true, "Status was 200");
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.SUCCESS);
            assertThat(stepResult.getActualResult()).isEqualTo("Status was 200");
        });
        wireMock.verify(2, getRequestedFor(urlEqualTo("/eventually-ok")));
    }

    @Test
    @DisplayName("A 5xx response reaches the agent as data and the verification fails")
    void serverErrorFailsVerification() {
        wireMock.stubFor(get(urlEqualTo("/unstable")).willReturn(aResponse().withStatus(500).withBody("boom")));

        var testCase = singleStepTestCase("Server error", "Send a GET request to /unstable", "Status is 200");
        var lastResponseSeenByModel = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> switch (round) {
            case 0 -> sendGet(wireMock.baseUrl() + "/unstable");
            case 1 -> new ScriptedToolCall(LAST_RESPONSE_TOOL, "{}");
            default -> {
                lastResponseSeenByModel.set(priorResults.getLast().text());
                yield result(false, "Expected status 200 but got 500");
            }
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.FAILURE);
            assertThat(stepResult.getErrorMessage()).contains("Expected status 200 but got 500");
        });
        // The 5xx flowed back to the model as data (via getLastApiResponse), not as an exception.
        assertThat(lastResponseSeenByModel.get()).contains("500").contains("boom");
    }

    @Test
    @DisplayName("A network fault becomes a retryable tool error and the agent recovers on the next attempt")
    void networkFaultIsRetriedAndRecovers() {
        // MALFORMED_RESPONSE_CHUNK (rather than a connection reset) is the fault of choice: the response has already
        // begun when it breaks, so Apache HttpClient cannot transparently re-send the idempotent GET and the failure
        // is guaranteed to surface as a tool exception.
        wireMock.stubFor(get(urlEqualTo("/flaky")).inScenario("fault-recovery")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
                .willSetStateTo("recovered"));
        wireMock.stubFor(get(urlEqualTo("/flaky")).inScenario("fault-recovery")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Fault recovery", "Send a GET request to /flaky", "Status is 200");
        // The fault surfaces as a retryable tool error whose message the error handler feeds back to the model instead
        // of aborting, so round 1 re-issues the same call against the now-recovered stub.
        var toolErrorFedBackToModel = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> switch (round) {
            case 0 -> sendGet(wireMock.baseUrl() + "/flaky");
            case 1 -> {
                toolErrorFedBackToModel.set(priorResults.getLast().text());
                yield sendGet(wireMock.baseUrl() + "/flaky");
            }
            default -> result(true, "Status was 200");
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(toolErrorFedBackToModel.get()).contains("Error while sending request");
        wireMock.verify(2, getRequestedFor(urlEqualTo("/flaky")));
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
        executionContext.addSharedData("resourceId", "42");

        var testCase = singleStepTestCase("Variable substitution", "Fetch the stored item", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/items/${resourceId}"),
                result(true, "Fetched item 42"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/items/42")));
    }

    @Test
    @DisplayName("POST with a JSON body resolves body variables, merges headers and injects the default Content-Type")
    void postWithBodyAndCustomHeaderReachesStub() {
        wireMock.stubFor(post(urlEqualTo("/orders")).willReturn(aResponse().withStatus(201).withBody("created")));
        executionContext.addSharedData("itemId", "42");

        var testCase = singleStepTestCase("Post order", "Create an order for the stored item", "Status is 201");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                new ScriptedToolCall(SEND_REQUEST_TOOL, """
                        {"method":"POST","url":"%s","headers":{"X-Request-Source":"smoke"},\
                        "body":"{\\"itemId\\":\\"${itemId}\\",\\"quantity\\":2}"}"""
                        .formatted(wireMock.baseUrl() + "/orders")),
                result(true, "Order created"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(postRequestedFor(urlEqualTo("/orders"))
                .withHeader("X-Request-Source", equalTo("smoke"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""
                        {"itemId":"42","quantity":2}""")));
    }

    @Test
    @DisplayName("A variable stored via the context tool in one step resolves in the next step's URL")
    void storedVariableFlowsAcrossSteps() {
        wireMock.stubFor(get(urlEqualTo("/items/42")).willReturn(aResponse().withStatus(200).withBody("found")));

        var storeStep = new TestStep("Store the resource id", List.of(), "The id is stored");
        var fetchStep = new TestStep("Fetch the stored item", List.of(), "Status is 200");
        var testCase = new TestCase("Cross-step data flow", List.of(), List.of(storeStep, fetchStep));

        var storeToolResponse = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> {
            if (userText.contains(storeStep.stepDescription())) {
                if (round == 0) {
                    return storeVariable("resourceId", "42");
                }
                storeToolResponse.set(priorResults.getLast().text());
                return result(true, "Stored");
            }
            return round == 0 ? sendGet(wireMock.baseUrl() + "/items/${resourceId}") : result(true, "Fetched item 42");
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(storeToolResponse.get()).contains("Added variable 'resourceId' with value '42'");
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
    @DisplayName("JSON-schema validation of the last response fails for a mismatching body")
    void jsonSchemaValidationFailsForMismatchingBody() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/user")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id":"not-a-number"}""")));
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

        var testCase = singleStepTestCase("Schema mismatch", "Fetch the user and validate the response schema",
                "Body matches the user schema");
        var validationOutcome = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> switch (round) {
            case 0 -> sendGet(wireMock.baseUrl() + "/user");
            case 1 -> new ScriptedToolCall(VALIDATE_SCHEMA_TOOL, """
                    {"schemaPath":"%s"}""".formatted(jsonPath(schema)));
            default -> {
                validationOutcome.set(priorResults.getLast().text());
                yield result(false, "Response body does not match the user schema");
            }
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getGeneralErrorMessage()).contains("Response body does not match the user schema");
        // The mismatch was reported to the model as the tool's result rather than as an exception.
        assertThat(validationOutcome.get()).contains("Schema validation failed");
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
        // A 1-second time budget plus a retry whose delay exceeds it (1.2s leaves a deterministic margin) makes the
        // budget check fail on the second attempt.
        when(config.getAgentExecutionTimeBudgetSeconds()).thenReturn(1);
        when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(3, 1200, 10000));
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

    @Test
    @DisplayName("An exhausted token budget terminates the run with ERROR, with zero sleep")
    void tokenBudgetExhaustionTerminatesRun() {
        // Two scripted responses consume 20 tokens through the real ChatModelEventListener; a 15-token budget trips
        // the budget check on the retry invocation.
        when(config.getAgentTokenBudget()).thenReturn(15);
        new BudgetManager(config);
        wireMock.stubFor(get(urlEqualTo("/tokens")).willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Token budget", "Send a GET request to /tokens", "Status is 201");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/tokens"), result(false, "Status was not 201 yet"));
        var model = new ScriptedChatModel(ScriptedChatModel.ofSequence(script),
                List.of(new ChatModelEventListener(BudgetManager.getInstance())));

        TestExecutionResult result = execute(testCase, model);

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("Token budget exceeded");
        });
    }

    @Test
    @DisplayName("An exhausted tool-call budget terminates the run with ERROR, with zero sleep")
    void toolCallBudgetExhaustionTerminatesRun() {
        // The first invocation executes two tools (request + final result); a budget of 1 trips the budget check on
        // the retry invocation.
        when(config.getAgentToolCallsBudget()).thenReturn(1);
        new BudgetManager(config);
        wireMock.stubFor(get(urlEqualTo("/tools")).willReturn(aResponse().withStatus(200).withBody("ok")));

        var testCase = singleStepTestCase("Tool call budget", "Send a GET request to /tools", "Status is 201");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/tools"), result(false, "Status was not 201 yet"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("Tool call budget exceeded");
        });
    }

    @Test
    @DisplayName("OpenAPI validation of the last response passes for a spec-conformant body")
    void openApiValidationPasses() throws Exception {
        wireMock.stubFor(get(urlEqualTo("/user")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id":1,"name":"Alice"}""")));
        Path spec = Files.writeString(tempDir.resolve("user-api.json"), openApiSpecForGetUser());

        var testCase = singleStepTestCase("OpenAPI validation", "Fetch the user and validate the response against the spec",
                "Response matches the OpenAPI spec");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/user"),
                validateOpenApi(spec),
                result(true, "Spec matched"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/user")));
    }

    @Test
    @DisplayName("OpenAPI validation reports a spec violation to the model as data and the verification fails")
    void openApiValidationFailsForMismatchingBody() throws Exception {
        // The body violates the spec: the required "name" property is missing.
        wireMock.stubFor(get(urlEqualTo("/user")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"id":1}""")));
        Path spec = Files.writeString(tempDir.resolve("user-api.json"), openApiSpecForGetUser());

        var testCase = singleStepTestCase("OpenAPI mismatch", "Fetch the user and validate the response against the spec",
                "Response matches the OpenAPI spec");
        var validationOutcome = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> switch (round) {
            case 0 -> sendGet(wireMock.baseUrl() + "/user");
            case 1 -> validateOpenApi(spec);
            default -> {
                validationOutcome.set(priorResults.getLast().text());
                yield result(false, "Response does not match the OpenAPI spec");
            }
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.getGeneralErrorMessage()).contains("Response does not match the OpenAPI spec");
        // The violation was reported to the model as the tool's result rather than as an exception.
        assertThat(validationOutcome.get()).contains("OpenAPI Validation Failed");
    }

    @Test
    @DisplayName("An unextractable test case yields ERROR without invoking the model")
    void unextractableTestCaseYieldsError() {
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.empty());
        var modelInvoked = new AtomicBoolean();
        Script script = (userText, round, priorResults) -> {
            modelInvoked.set(true);
            return result(true, "must never be reached");
        };

        TestExecutionResult result = buildAgent(new ScriptedChatModel(script)).executeTestCase("run");

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getGeneralErrorMessage()).contains("Could not extract test case");
        assertThat(modelInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("A relative URL resolves against the configured base URI")
    void relativeUrlResolvesAgainstBaseUri() {
        when(config.getTargetBaseUri()).thenReturn(Optional.of(wireMock.baseUrl()));
        // setUp built the ApiContext before the stubbing above, so it must be re-created to pick up the base URI.
        apiContext = new ApiContext(config);
        wireMock.stubFor(get(urlEqualTo("/relative-ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        var testCase = singleStepTestCase("Base URI resolution", "Send a GET request to /relative-ping", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet("/relative-ping"), result(true, "Received status 200"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/relative-ping")));
    }

    @Test
    @DisplayName("loadJsonData loads a file whose content resolves as the request body of a later call")
    void loadJsonDataFlowsIntoRequestBody() throws Exception {
        String payload = """
                {"itemId":"42","quantity":2}""";
        Path file = Files.writeString(tempDir.resolve("payload.json"), payload);
        wireMock.stubFor(post(urlEqualTo("/orders")).willReturn(aResponse().withStatus(201).withBody("created")));

        var testCase = singleStepTestCase("JSON data load", "Create an order from the payload file", "Status is 201");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                new ScriptedToolCall(LOAD_JSON_TOOL, """
                        {"filePath":"%s","variableName":"payload"}""".formatted(jsonPath(file))),
                new ScriptedToolCall(SEND_REQUEST_TOOL, """
                        {"method":"POST","url":"%s","body":"${payload}"}""".formatted(wireMock.baseUrl() + "/orders")),
                result(true, "Order created"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(postRequestedFor(urlEqualTo("/orders")).withRequestBody(equalToJson(payload)));
    }

    @Test
    @DisplayName("loadCsvData loads the rows and reports the count to the model")
    void loadCsvDataReportsLoadedRows() throws Exception {
        Path file = Files.writeString(tempDir.resolve("data.csv"), """
                id,name
                42,Alice""");

        var testCase = singleStepTestCase("CSV data load", "Load the test data file", "Data is loaded");
        var toolResponse = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> {
            if (round == 0) {
                return new ScriptedToolCall(LOAD_CSV_TOOL, """
                        {"filePath":"%s","variableName":"rows"}""".formatted(jsonPath(file)));
            }
            toolResponse.set(priorResults.getLast().text());
            return result(true, "Data loaded");
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        assertThat(toolResponse.get()).contains("Loaded 1 items");
    }

    @Test
    @DisplayName("A precondition action error yields ERROR without executing any step")
    void preconditionActionFailureYieldsError() {
        // With no allow-list configured, the SSRF guard aborts the request with a non-retryable tool error, which is
        // the action-error (as opposed to verification-failure) precondition path.
        when(config.getOutboundHostAllowlist()).thenReturn(Set.of());

        var precondition = "The metadata endpoint is reachable";
        var step = new TestStep("Send a GET request to /resource", List.of(), "Status is 200");
        var testCase = new TestCase("Precondition action error", List.of(precondition), List.of(step));
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet("http://169.254.169.254/latest/meta-data/"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).isEmpty();
        assertThat(result.getPreconditionResults()).singleElement().satisfies(preconditionResult -> {
            assertThat(preconditionResult.isSuccess()).isFalse();
            assertThat(preconditionResult.getErrorMessage()).contains("Failure while executing precondition").contains("blocked address");
        });
        assertThat(result.getGeneralErrorMessage()).contains("Failure while executing precondition");
    }

    @Test
    @DisplayName("A precondition answered with plain text instead of a tool call yields ERROR")
    void preconditionNoModelResultYieldsError() {
        var precondition = "The resource endpoint is reachable";
        var step = new TestStep("Send a GET request to /resource", List.of(), "Status is 200");
        var testCase = new TestCase("Precondition without tool call", List.of(precondition), List.of(step));
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                ScriptedToolCall.plainText("The precondition is met."));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).isEmpty();
        assertThat(result.getPreconditionResults()).singleElement().satisfies(preconditionResult -> {
            assertThat(preconditionResult.isSuccess()).isFalse();
            assertThat(preconditionResult.getErrorMessage()).contains("Got no result from the model");
        });
        assertThat(result.getGeneralErrorMessage()).contains("Got no result from the model");
    }

    @Test
    @DisplayName("A failed precondition verification yields ERROR, matching the UI agent's status contract")
    void preconditionVerificationFailureYieldsError() {
        wireMock.stubFor(get(urlEqualTo("/setup")).willReturn(aResponse().withStatus(500).withBody("not ready")));

        var precondition = "The resource endpoint is reachable";
        var step = new TestStep("Send a GET request to /resource", List.of(), "Status is 200");
        var testCase = new TestCase("Precondition verification failure", List.of(precondition), List.of(step));
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMock.baseUrl() + "/setup"), result(false, "Setup endpoint returned 500"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).isEmpty();
        assertThat(result.getPreconditionResults()).singleElement().satisfies(preconditionResult -> {
            assertThat(preconditionResult.isSuccess()).isFalse();
            assertThat(preconditionResult.getErrorMessage()).contains("Precondition verification failed")
                    .contains("Setup endpoint returned 500");
        });
        assertThat(result.getGeneralErrorMessage()).contains("Setup endpoint returned 500");
    }

    @Test
    @DisplayName("A step answered with plain text instead of a tool call yields ERROR")
    void stepWithNoToolCallYieldsError() {
        var testCase = singleStepTestCase("Step without tool call", "Send a GET request to /ping", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                ScriptedToolCall.plainText("I have sent the request and the status was 200."));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("Got no result from the model");
        });
        assertThat(result.getGeneralErrorMessage()).contains("Got no result from the model");
    }

    @Test
    @DisplayName("A self-signed HTTPS endpoint is rejected when relaxed HTTPS validation is off")
    void selfSignedHttpsFailsWithoutRelaxedValidation() {
        wireMockHttps.stubFor(get(urlEqualTo("/secure-ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        var testCase = singleStepTestCase("Strict HTTPS", "Send a GET request to the HTTPS endpoint", "Status is 200");
        // The certificate rejection surfaces as a tool error fed back to the model; the repeating error then exhausts
        // the retry policy, so the step ends as an action error rather than a failed verification.
        var toolErrorFedBackToModel = new AtomicReference<String>();
        Script script = (userText, round, priorResults) -> {
            if (round == 0) {
                return sendGet(wireMockHttps.baseUrl() + "/secure-ping");
            }
            toolErrorFedBackToModel.set(priorResults.getLast().text());
            return result(false, "The HTTPS request could not be sent");
        };

        TestExecutionResult result = execute(testCase, new ScriptedChatModel(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(ERROR);
        assertThat(result.getStepResults()).singleElement().satisfies(stepResult -> {
            assertThat(stepResult.getExecutionStatus()).isEqualTo(TestStepResultStatus.ERROR);
            assertThat(stepResult.getErrorMessage()).contains("Error while sending request");
        });
        assertThat(toolErrorFedBackToModel.get()).contains("PKIX path building failed");
        wireMockHttps.verify(0, getRequestedFor(urlEqualTo("/secure-ping")));
    }

    @Test
    @DisplayName("Relaxed HTTPS validation accepts the self-signed certificate")
    void selfSignedHttpsPassesWithRelaxedValidation() {
        when(config.getRelaxedHttpsValidation()).thenReturn(true);
        // setUp built the ApiContext before the stubbing above, so it must be re-created to pick up the relaxed flag.
        apiContext = new ApiContext(config);
        wireMockHttps.stubFor(get(urlEqualTo("/secure-ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        var testCase = singleStepTestCase("Relaxed HTTPS", "Send a GET request to the HTTPS endpoint", "Status is 200");
        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                sendGet(wireMockHttps.baseUrl() + "/secure-ping"), result(true, "Received status 200"));

        TestExecutionResult result = execute(testCase, model(script));

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMockHttps.verify(getRequestedFor(urlEqualTo("/secure-ping")));
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

    static ScriptedToolCall sendGet(@NotNull String url) {
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

    private static ScriptedToolCall validateOpenApi(@NotNull Path spec) {
        return new ScriptedToolCall(VALIDATE_OPENAPI_TOOL, """
                {"specPath":"%s","method":"GET","path":"/user"}""".formatted(jsonPath(spec)));
    }

    /** A minimal OpenAPI spec describing GET /user with a JSON response that requires "id" and "name". */
    private static String openApiSpecForGetUser() {
        return """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "User API", "version": "1.0"},
                  "paths": {
                    "/user": {
                      "get": {
                        "responses": {
                          "200": {
                            "description": "The user",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "required": ["id", "name"],
                                  "properties": {
                                    "id": {"type": "integer"},
                                    "name": {"type": "string"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }""";
    }

    private static ScriptedToolCall storeVariable(@NotNull String name, @NotNull String value) {
        return new ScriptedToolCall(STORE_VARIABLE_TOOL, """
                {"variableName":"%s","variableValue":"%s"}""".formatted(name, value));
    }

    static ScriptedToolCall result(boolean success, @NotNull String message) {
        return new ScriptedToolCall(RESULT_TOOL, """
                {"%s":{"success":%b,"message":"%s"}}""".formatted(RESULT_PARAM, success, message));
    }

    private static String resultToolParameterName() {
        try {
            return VerificationExecutionResult.class
                    .getMethod(RESULT_TOOL, VerificationExecutionResult.class)
                    .getParameters()[0].getName();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Final-result tool method not found", e);
        }
    }

    /** Escapes a filesystem path so it is a valid JSON string value (Windows paths contain backslashes). */
    private static String jsonPath(@NotNull Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    /** Creates the mocked config with the defaults every hand-wired smoke agent needs. */
    static ApiTestAgentConfig newSmokeConfig() {
        ApiTestAgentConfig config = mock(ApiTestAgentConfig.class);
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
        return config;
    }

    private ApiTestAgent buildAgent(ScriptedChatModel model) {
        return buildAgent(model, config, apiContext, executionContext, testCaseExtractor, logCapture);
    }

    /** Hand-wires the whole real API agent graph around a scripted model; shared with {@link ApiAgentA2aSmokeTest}. */
    static ApiTestAgent buildAgent(ScriptedChatModel model, ApiTestAgentConfig config, ApiContext apiContext,
                                   TestExecutionContext executionContext, TestCaseExtractor testCaseExtractor, LogCapture logCapture) {
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
