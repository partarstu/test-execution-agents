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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.ApiTestAgentConfig;
import org.tarik.ta.agents.ApiPreconditionActionAgent;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.PASSED;

/**
 * Hermetic end-to-end smoke test for the API test execution agent. The whole agent flow runs for real
 * ({@link ApiTestAgent} -> step / precondition agents -> real {@link ApiRequestTools} / {@link ApiAssertionTools});
 * only the external boundaries are mocked: the LLM is replaced by a {@link ScriptedChatModel}, and the target API by a
 * local WireMock server which records the requests that actually left the agent.
 */
@Tag("smoke")
@DisplayName("API agent - hermetic end-to-end smoke")
class ApiAgentSmokeTest {

    private static final String SEND_REQUEST_TOOL = "sendRequest";
    private static final String RESULT_TOOL = "endExecutionAndGetFinalResult";

    private static WireMockServer wireMock;

    private ApiTestAgentConfig config;
    private TestExecutionContext executionContext;
    private ApiContext apiContext;
    private TestCaseExtractor testCaseExtractor;
    private LogCapture logCapture;

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

        // Constructing the BudgetManager publishes it as the static instance the agents read via getInstance().
        new BudgetManager(config);

        executionContext = new TestExecutionContext(StreamingEventEmitter.NOOP);
        apiContext = new ApiContext(config);
        testCaseExtractor = mock(TestCaseExtractor.class);
        logCapture = mock(LogCapture.class);
        when(logCapture.getLogs()).thenReturn(List.of());
    }

    @Test
    @DisplayName("Single GET step executes a real HTTP request and passes")
    void singleGetStepPasses() {
        wireMock.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

        var testCase = new TestCase("Ping smoke", List.of(),
                List.of(new TestStep("Send a GET request to /ping", List.of(), "Status is 200")));
        when(testCaseExtractor.extractTestCase("run")).thenReturn(Optional.of(testCase));

        Function<String, List<ScriptedToolCall>> script = userText -> List.of(
                new ScriptedToolCall(SEND_REQUEST_TOOL,
                        """
                        {"method":"GET","url":"%s/ping"}""".formatted(wireMock.baseUrl())),
                new ScriptedToolCall(RESULT_TOOL,
                        """
                        {"result":{"success":true,"message":"Received status 200"}}"""));

        ApiTestAgent agent = buildAgent(new ScriptedChatModel(script));

        TestExecutionResult result = agent.executeTestCase("run");

        assertThat(result.getTestExecutionStatus()).isEqualTo(PASSED);
        wireMock.verify(getRequestedFor(urlEqualTo("/ping")));
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
