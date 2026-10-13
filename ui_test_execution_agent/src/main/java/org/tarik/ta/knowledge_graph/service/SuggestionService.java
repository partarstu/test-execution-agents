/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.knowledge_graph.service;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.KnowledgeSuggestionAgent;
import org.tarik.ta.config.scopes.UiAgentRequestScope;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.util.List;

import static org.tarik.ta.core.utils.MarkdownSections.SECTION_LEVEL;
import static org.tarik.ta.core.utils.MarkdownSections.bulletList;
import static org.tarik.ta.core.utils.MarkdownSections.heading;
import static org.tarik.ta.core.utils.MarkdownSections.joinSections;
import static org.tarik.ta.core.utils.MarkdownSections.section;
import static org.tarik.ta.core.utils.MarkdownSections.userMessageContents;
import static org.tarik.ta.knowledge_graph.service.ExecutionGraphContextBuilder.buildExecutionGraphContext;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;
import static org.tarik.ta.utils.UserMessageSections.SCREENSHOT_LABEL;

/** Asks the knowledge suggestion agent for the prerequisites, effects and child steps of a procedure being created. */
@UiAgentRequestScope
public class SuggestionService {
    private static final Logger LOG = LoggerFactory.getLogger(SuggestionService.class);

    private final KnowledgeSuggestionAgent knowledgeSuggestionAgent;
    private final UiTestAgentConfig uiTestAgentConfig;

    public SuggestionService(KnowledgeSuggestionAgent knowledgeSuggestionAgent, UiTestAgentConfig uiTestAgentConfig) {
        this.knowledgeSuggestionAgent = knowledgeSuggestionAgent;
        this.uiTestAgentConfig = uiTestAgentConfig;
    }

    /**
     * Captures the screen for the suggestions on a procedure executed after the run's atomics and {@code precedingAtomics}, so
     * that nothing shown while they load appears in the screenshot.
     */
    public PendingSuggestions prepare(SuggestionContext context, String procedureDescription, List<Procedure> precedingAtomics) {
        var agentContext = buildExecutionGraphContext(context.testCase(), context.executedAtomics(), precedingAtomics);
        var screenshot = knowledgeSuggestionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(captureScreen());
        var userMessage = userMessageContents(
                buildSuggestionUserMessage(procedureDescription, context.testData(), context.expectedResults(), agentContext));
        return new PendingSuggestions(procedureDescription, userMessage, screenshot);
    }

    /** Asks the agent, returning empty suggestions when it fails or answers nothing. */
    public KnowledgeSuggestionResult load(PendingSuggestions pending) {
        try {
            var result = knowledgeSuggestionAgent.executeAndGetResult(
                    () -> knowledgeSuggestionAgent.suggest(pending.userMessage(), pending.screenshot()));
            var payload = result.getResultPayload();
            if (payload != null) {
                return payload;
            }
            LOG.warn("Knowledge Suggestion Agent returned no result for '{}'", pending.procedureDescription());
        } catch (Exception e) {
            LOG.warn("Suggestion loading failed for '{}': {}", pending.procedureDescription(), e.getMessage(), e);
        }
        return KnowledgeSuggestionResult.empty();
    }

    static String buildSuggestionUserMessage(String itemDescription, List<String> testData, String expectedResults,
                                             String agentContext) {
        return joinSections(
                section(SECTION_LEVEL, "Test case scenario", itemDescription),
                section(SECTION_LEVEL, "Context", agentContext),
                section(SECTION_LEVEL, "Test data", bulletList(testData)),
                section(SECTION_LEVEL, "Expected results", expectedResults),
                heading(SECTION_LEVEL, SCREENSHOT_LABEL));
    }

    /** What the suggestions of one knowledge collection flow are based on, besides the procedure and its preceding atomics. */
    public record SuggestionContext(TestCase testCase, List<Procedure> executedAtomics, List<String> testData,
                                    String expectedResults) {
    }

    public record PendingSuggestions(String procedureDescription, List<Content> userMessage, ImageContent screenshot) {
    }
}
