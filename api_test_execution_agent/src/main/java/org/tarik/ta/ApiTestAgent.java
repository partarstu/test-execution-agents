package org.tarik.ta;

import dev.langchain4j.service.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.agents.ApiTestStepActionAgent;
import org.tarik.ta.context.ApiContext;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.tools.ApiAssertionTools;
import org.tarik.ta.tools.ApiDataTools;
import org.tarik.ta.tools.ApiRequestTools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.service.AiServices.builder;
import static java.time.Instant.now;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.AgentConfig.isUnattendedMode;
import static org.tarik.ta.core.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.core.dto.TestStepResult.TestStepResultStatus.*;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.CoreUtils.isBlank;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

public class ApiTestAgent {
    private static final Logger LOG = LoggerFactory.getLogger(ApiTestAgent.class);

    public static TestExecutionResult executeTestCase(String receivedMessage) {
        Optional<TestCase> testCaseOpt = extractTestCase(receivedMessage);
        if (testCaseOpt.isEmpty()) {
             return new TestExecutionResult("Unknown", TestExecutionResult.TestExecutionStatus.ERROR, new ArrayList<>(), new ArrayList<>(), now(), now(), "Could not extract test case");
        }
        TestCase testCase = testCaseOpt.get();
        
        LOG.info("Starting execution of the API test case '{}'", testCase.name());
        
        ApiContext apiContext = new ApiContext();
        TestExecutionContext executionContext = new TestExecutionContext(testCase);
        
        ApiRequestTools requestTools = new ApiRequestTools(apiContext);
        ApiAssertionTools assertionTools = new ApiAssertionTools(apiContext);
        ApiDataTools dataTools = new ApiDataTools(apiContext);
        
        ApiTestStepActionAgent agent = getApiTestStepActionAgent(requestTools, assertionTools, dataTools);

        Instant start = now();
        
        if (testCase.preconditions() != null) {
            for (String precondition : testCase.preconditions()) {
                 LOG.info("Executing precondition: {}", precondition);
                 try {
                     agent.execute(precondition, "", executionContext.getSharedData().toString(), !isUnattendedMode());
                 } catch (Exception e) {
                     LOG.error("Precondition failed", e);
                     return new TestExecutionResult(testCase.name(), FAILED, executionContext.getPreconditionExecutionHistory(), executionContext.getTestStepExecutionHistory(), start, now(), "Precondition failed: " + e.getMessage());
                 }
            }
        }

        for (TestStep step : testCase.testSteps()) {
            Instant stepStart = now();
            try {
                String testData = ofNullable(step.testData()).map(Object::toString).orElse("");
                LOG.info("Executing step: {}", step.stepDescription());
                
                Result<String> result = agent.execute(step.stepDescription(), testData, executionContext.getSharedData().toString(), !isUnattendedMode());
                
                executionContext.addStepResult(new TestStepResult(step, SUCCESS, null, result.content(), stepStart, now()));

            } catch (Exception e) {
                LOG.error("Step failed", e);
                executionContext.addStepResult(new TestStepResult(step, FAILURE, e.getMessage(), null, stepStart, now()));
                return new TestExecutionResult(testCase.name(), FAILED, executionContext.getPreconditionExecutionHistory(), executionContext.getTestStepExecutionHistory(), start, now(), "Step failed: " + e.getMessage());
            }
        }

        return new TestExecutionResult(testCase.name(), PASSED, executionContext.getPreconditionExecutionHistory(), executionContext.getTestStepExecutionHistory(), start, now(), null);
    }

    public static Optional<TestCase> extractTestCase(String message) {
        LOG.info("Attempting to extract TestCase instance from user message using AI model.");
        if (isBlank(message)) {
            LOG.error("User message is blank, cannot extract a TestCase.");
            return empty();
        }

        try {
            var agent = getTestCaseExtractionAgent();
            TestCase extractedTestCase = agent.executeAndGetResult(() -> agent.extractTestCase(message)).getResultPayload();
            return of(extractedTestCase);
        } catch (Exception e) {
            LOG.error("Failed to extract test case", e);
            return empty();
        }
    }

    private static TestCaseExtractionAgent getTestCaseExtractionAgent() {
        var model = getModel(AgentConfig.getTestCaseExtractionAgentModelName(),
                AgentConfig.getTestCaseExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("test_case_extractor",
                AgentConfig.getTestCaseExtractionAgentPromptVersion(), "test_case_extraction_prompt.txt");
        return builder(TestCaseExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(new TestCase("", List.of(), List.of()))
                .build();
    }

    private static ApiTestStepActionAgent getApiTestStepActionAgent(ApiRequestTools requestTools, ApiAssertionTools assertionTools, ApiDataTools dataTools) {
        var model = getModel(AgentConfig.getTestStepActionAgentModelName(), AgentConfig.getTestStepActionAgentModelProvider());
        var prompt = loadSystemPrompt("api_test", "v1.0.0", "api_test_step_agent_prompt.txt");
        
        return builder(ApiTestStepActionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .tools(requestTools, assertionTools, dataTools)
                .build();
    }
}
