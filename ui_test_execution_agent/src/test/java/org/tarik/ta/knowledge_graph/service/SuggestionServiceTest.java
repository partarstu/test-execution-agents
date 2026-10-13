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

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.KnowledgeSuggestionAgent;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.dto.TestCase;
import org.tarik.ta.dto.KnowledgeSuggestionResult;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.SuggestionService.PendingSuggestions;
import org.tarik.ta.knowledge_graph.service.SuggestionService.SuggestionContext;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {
    private static final ImageContent SCREENSHOT = ImageContent.from("aW1hZ2U=", "image/png");

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KnowledgeSuggestionAgent knowledgeSuggestionAgent;
    @Mock
    private UiTestAgentConfig uiTestAgentConfig;

    @Test
    void prepare_shouldBuildTheContextFromTheExecutedAndThePrecedingAtomicsAndCaptureTheScreen() {
        var executedAtomic = Procedure.createAtomic("open the dashboard", List.of(), "", List.of(), List.of(), false);
        var precedingAtomic = Procedure.createAtomic("click edit", List.of(), "", List.of(), List.of(), false);
        var testCase = new TestCase("Panels", List.of(), List.of(), List.of());
        var screen = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        when(knowledgeSuggestionAgent.getImageContentCreator(uiTestAgentConfig).createImageContent(screen)).thenReturn(SCREENSHOT);

        try (var contextBuilder = mockStatic(ExecutionGraphContextBuilder.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
             var screenCapture = mockStatic(UiCommonUtils.class)) {
            screenCapture.when(UiCommonUtils::captureScreen).thenReturn(screen);
            contextBuilder.when(() -> ExecutionGraphContextBuilder.buildExecutionGraphContext(testCase, List.of(executedAtomic),
                    List.of(precedingAtomic))).thenReturn("- Test case: Panels");

            var pending = buildService().prepare(new SuggestionContext(testCase, List.of(executedAtomic), List.of("user=admin"), ""),
                    "open the panel editor", List.of(precedingAtomic));

            assertThat(pending.procedureDescription()).isEqualTo("open the panel editor");
            assertThat(pending.screenshot()).isSameAs(SCREENSHOT);
            assertThat(pending.userMessage()).containsExactly(TextContent.from("""
                    ## Test case scenario
                    open the panel editor

                    ## Context
                    - Test case: Panels

                    ## Test data
                    - user=admin

                    ## Screenshot"""));
        }
    }

    @Test
    void load_shouldReturnTheAgentsSuggestions_whenTheAgentAnswers() {
        var suggestions = new KnowledgeSuggestionResult(List.of("the dashboard is open"), List.of(), List.of(), List.of(), List.of(), true);
        when(knowledgeSuggestionAgent.executeAndGetResult(any())).thenReturn(new OperationExecutionResult<>(SUCCESS, "ok", suggestions));

        assertThat(buildService().load(pending())).isSameAs(suggestions);
    }

    @Test
    void load_shouldReturnEmptySuggestions_whenTheAgentAnswersNothing() {
        when(knowledgeSuggestionAgent.executeAndGetResult(any())).thenReturn(new OperationExecutionResult<>(SUCCESS, "ok", null));

        assertThat(buildService().load(pending())).isEqualTo(KnowledgeSuggestionResult.empty());
    }

    @Test
    void load_shouldReturnEmptySuggestions_whenTheAgentFails() {
        when(knowledgeSuggestionAgent.executeAndGetResult(any())).thenThrow(new IllegalStateException("the model is unavailable"));

        assertThat(buildService().load(pending())).isEqualTo(KnowledgeSuggestionResult.empty());
    }

    @Test
    @DisplayName("The suggestion message renders the scenario, the context, the test data as a list and the expected results")
    void buildSuggestionUserMessage_shouldRenderEveryPartAsItsOwnSection() {
        var message = SuggestionService.buildSuggestionUserMessage("Log in", List.of("user=admin", "tenant=staging"),
                "The dashboard is shown", "- Test case: Order flow");

        assertThat(message).isEqualTo("""
                ## Test case scenario
                Log in

                ## Context
                - Test case: Order flow

                ## Test data
                - user=admin
                - tenant=staging

                ## Expected results
                The dashboard is shown

                ## Screenshot""");
    }

    @Test
    @DisplayName("The suggestion message leaves out the test data and the expected results when there are none")
    void buildSuggestionUserMessage_shouldLeaveOutBlankSections() {
        var message = SuggestionService.buildSuggestionUserMessage("Log in", List.of(), "", "- Test case: Order flow");

        assertThat(message).doesNotContain("## Test data").doesNotContain("## Expected results")
                .endsWith("## Context\n- Test case: Order flow\n\n## Screenshot");
    }

    private SuggestionService buildService() {
        return new SuggestionService(knowledgeSuggestionAgent, uiTestAgentConfig);
    }

    private static PendingSuggestions pending() {
        return new PendingSuggestions("open the panel editor", List.of(TextContent.from("## Test case scenario")), SCREENSHOT);
    }
}
