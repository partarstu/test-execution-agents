/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.dto.VerificationExecutionResult;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;

import java.util.List;
import java.util.Optional;

import static dev.langchain4j.service.AiServices.builder;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.tarik.ta.core.AgentConfig.*;
import static org.tarik.ta.core.model.ModelFactory.getModel;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * Utility class for extracting test cases from user messages using an AI model.
 * This class provides shared functionality for both UI and API test agents.
 */
public final class TestCaseExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(TestCaseExtractor.class);

    private TestCaseExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts a TestCase from a user message using an AI model.
     *
     * @param message the user message containing test case information
     * @return an Optional containing the extracted TestCase, or empty if extraction
     * fails
     */
    public static Optional<TestCase> extractTestCase(String message) {
        LOG.info("Attempting to extract TestCase instance from user message using AI model.");
        if (isBlank(message)) {
            LOG.error("User message is blank, cannot extract a TestCase.");
            return empty();
        }

        try {
            var agent = getTestCaseExtractionAgent();
            TestCase extractedTestCase = agent.executeAndGetResult(() -> agent.extractTestCase(message)).getResultPayload();
            if (isTestCaseInvalid(extractedTestCase)) {
                LOG.warn("Model could not extract a valid TestCase from the provided user message, original message: {}", message);
                return empty();
            } else {
                LOG.info("Successfully extracted TestCase: '{}'", extractedTestCase.name());
                return of(extractedTestCase);
            }
        } catch (Exception e) {
            LOG.error("Failed to extract test case from message", e);
            return empty();
        }
    }

    /**
     * Creates and configures a TestCaseExtractionAgent instance.
     *
     * @return the configured TestCaseExtractionAgent
     */
    static TestCaseExtractionAgent getTestCaseExtractionAgent() {
        var model = getModel(getTestCaseExtractionAgentModelName(), getTestCaseExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("test_case_extractor", getTestCaseExtractionAgentPromptVersion(), "test_case_extraction_prompt.txt");
        return builder(TestCaseExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), TestCase.class))
                .build();
    }

    /**
     * Validates a TestCase to ensure it has all required fields.
     *
     * @param extractedTestCase the test case to validate
     * @return true if the test case is invalid, false otherwise
     */
    static boolean isTestCaseInvalid(TestCase extractedTestCase) {
        return extractedTestCase == null
                || isBlank(extractedTestCase.name())
                || extractedTestCase.testSteps() == null
                || extractedTestCase.testSteps().isEmpty()
                || extractedTestCase.testSteps().stream()
                .anyMatch(step -> isBlank(step.stepDescription()));
    }
}
