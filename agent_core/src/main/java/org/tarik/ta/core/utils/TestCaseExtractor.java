/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.TestCaseExtractionAgent;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.InheritanceAwareToolProvider;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.langchain4j.service.AiServices.builder;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.tarik.ta.core.model.DefaultToolErrorHandler.forToolArguments;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.core.utils.PromptUtils.loadSystemPrompt;

/**
 * Utility class for extracting test cases from user messages using an AI model.
 * This class provides shared functionality for both UI and API test agents.
 */
@Singleton
public class TestCaseExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(TestCaseExtractor.class);
    public static final String OPERATION_NAME = "test_case_extraction";

    private final AgentConfig agentConfig;
    private final ModelFactory modelFactory;
    private final TestCaseExtractionAgent testCaseExtractionAgent;

    @Inject
    public TestCaseExtractor(ModelFactory modelFactory, AgentConfig agentConfig) {
        this.modelFactory = modelFactory;
        this.agentConfig = agentConfig;
        
        var model = this.modelFactory.getModel(OPERATION_NAME, agentConfig.getTestCaseExtractionAgentModelName(),
                agentConfig.getTestCaseExtractionAgentModelProvider());
        var prompt = loadSystemPrompt("test_case_extractor", agentConfig.getTestCaseExtractionAgentPromptVersion(),
                "test_case_extraction_prompt.md");
        this.testCaseExtractionAgent = builder(TestCaseExtractionAgent.class)
                .chatModel(model.chatModel())
                .systemMessageProvider(_ -> prompt)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(), TestCase.class))
                .toolArgumentsErrorHandler(forToolArguments(agentConfig.getActionRetryPolicy()))
                .build();
    }

    /**
     * Extracts a TestCase from a user message using an AI model.
     *
     * @param message the user message containing test case information
     * @return an Optional containing the extracted TestCase, or empty if extraction
     * fails
     */
    public Optional<TestCase> extractTestCase(String message) {
        LOG.info("Attempting to extract TestCase instance from user message using AI model.");
        if (isBlank(message)) {
            LOG.error("User message is blank, cannot extract a TestCase.");
            return empty();
        }

        try {
            TestCase extractedTestCase = testCaseExtractionAgent
                    .executeAndGetResult(() -> testCaseExtractionAgent.extractTestCase(userMessageContents(message))).getResultPayload();
            if (isTestCaseInvalid(extractedTestCase)) {
                // The message is not logged: its secret values are not registered for redaction before a test case is extracted
                LOG.warn("Model could not extract a valid TestCase from the provided user message ({} characters)", message.length());
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
     * Validates a TestCase to ensure it has all required fields.
     *
     * @param extractedTestCase the test case to validate
     * @return true if the test case is invalid, false otherwise
     */
    boolean isTestCaseInvalid(TestCase extractedTestCase) {
        return extractedTestCase == null
                || isBlank(extractedTestCase.name())
                || extractedTestCase.testSteps() == null
                || extractedTestCase.testSteps().isEmpty()
                || extractedTestCase.testSteps().stream()
                .anyMatch(step -> isBlank(step.stepDescription()));
    }
}
