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
package org.tarik.ta.tools;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.DbUiElementSelectionAgent;
import org.tarik.ta.agents.UiElementExtendedDescriptionAgent;
import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.UiElementDescription;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository.UiElementMatch;
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;
import org.tarik.ta.model.ImageContentCreator;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;

@ExtendWith(MockitoExtension.class)
class UiElementDbToolsTest {
    @Mock
    private UiElementRepository elementRepository;
    @Mock
    private UiStateCheckAgent uiStateCheckAgent;
    @Mock
    private UiElementExtendedDescriptionAgent descriptionAgent;
    @Mock
    private DbUiElementSelectionAgent selectionAgent;
    @Mock
    private UiElementRefinementHelper refinementHelper;
    @Mock
    private UiTestAgentConfig config;

    private UiElementAliasRegistry aliasRegistry;
    private UiElementDbTools tools;

    @BeforeEach
    void setUp() {
        aliasRegistry = new UiElementAliasRegistry();
        tools = new UiElementDbTools(elementRepository, uiStateCheckAgent, descriptionAgent, selectionAgent, refinementHelper, config,
                aliasRegistry);
    }

    @Test
    void searchElementInDb_shouldReturnResolvableAlias_whenElementIsFound() {
        var element = new UiElement(UUID.randomUUID(), "Search field", "description", "location", "navigation", null, false);
        when(refinementHelper.retrieveUiElementsWithTargetSimilarity(elementRepository, "search field"))
                .thenReturn(List.of(new UiElementMatch(element, 0.99)));

        var result = tools.searchElementInDb("search field", "dashboard name");

        assertThat(result.found()).isTrue();
        assertThat(result.elementId()).startsWith("E");
        assertThat(aliasRegistry.resolve(result.elementId())).contains(element.id());
    }

    @Test
    void createElementInDb_shouldReturnResolvableAlias_whenElementIsCreated() {
        var screenshot = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var imageContentCreator = mock(ImageContentCreator.class);
        var imageContent = mock(ImageContent.class);
        var description = new UiElementDescription("Search field", "description", "location", "navigation", false);
        var identification = new UiElementIdentificationResult(true, false, description);
        when(descriptionAgent.getImageContentCreator(config)).thenReturn(imageContentCreator);
        when(imageContentCreator.createImageContent(screenshot)).thenReturn(imageContent);
        when(descriptionAgent.executeAndGetResult(any())).thenReturn(new OperationExecutionResult<>(SUCCESS, "success", identification));

        try (MockedStatic<UiCommonUtils> uiCommonUtils = mockStatic(UiCommonUtils.class)) {
            uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(screenshot);

            var result = tools.createElementInDb("search field", "dashboard name");

            var elementCaptor = ArgumentCaptor.forClass(UiElement.class);
            verify(elementRepository).create(elementCaptor.capture());
            assertThat(result.success()).isTrue();
            assertThat(result.elementId()).startsWith("E");
            assertThat(aliasRegistry.resolve(result.elementId())).contains(elementCaptor.getValue().id());
        }
    }

    @Test
    void createElementInDb_shouldSendTheDescriptionAndTheDataAsSectionsBeforeTheScreenshot() {
        var screenshot = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var imageContentCreator = mock(ImageContentCreator.class);
        var imageContent = mock(ImageContent.class);
        when(descriptionAgent.getImageContentCreator(config)).thenReturn(imageContentCreator);
        when(imageContentCreator.createImageContent(screenshot)).thenReturn(imageContent);
        when(descriptionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(SUCCESS, "success", new UiElementIdentificationResult(false, false, null));
        });

        try (MockedStatic<UiCommonUtils> uiCommonUtils = mockStatic(UiCommonUtils.class)) {
            uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(screenshot);

            tools.createElementInDb("search field", "dashboard name {{name}}");
        }

        verify(descriptionAgent).describeUiElement(List.of(TextContent.from("""
                ## Target element description
                search field

                ## Relevant data context
                dashboard name {{name}}

                ## Screenshot""")), imageContent);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchElementInDb_shouldSendEveryCandidateAsItsOwnSection_whenSeveralElementsMatch() {
        var save = new UiElement(UUID.randomUUID(), "Save button", "Blue button", "Bottom right", "Order form", null, false);
        var cancel = new UiElement(UUID.randomUUID(), "Cancel button", "Grey button", "Left of 'Save'", "Order form", null, false);
        when(refinementHelper.retrieveUiElementsWithTargetSimilarity(elementRepository, "save button"))
                .thenReturn(List.of(new UiElementMatch(save, 0.9), new UiElementMatch(cancel, 0.8)));
        when(selectionAgent.getImageContentCreator(config)).thenReturn(mock(ImageContentCreator.class));
        when(selectionAgent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(SUCCESS, "success", null);
        });

        try (MockedStatic<UiCommonUtils> uiCommonUtils = mockStatic(UiCommonUtils.class)) {
            uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));

            tools.searchElementInDb("save button", "order 17");
        }

        ArgumentCaptor<List<Content>> message = ArgumentCaptor.forClass(List.class);
        verify(selectionAgent).selectBestElementFromCandidates(message.capture(), any());
        assertThat(message.getValue()).singleElement().isInstanceOf(TextContent.class);
        assertThat(((TextContent) message.getValue().getFirst()).text())
                .startsWith("## Target element description\nsave button\n\n## Data related to this element\norder 17\n\n## Candidates\n\n")
                .contains("""
                        ### Candidate element_0
                        - Name: Save button
                        - Description: Blue button
                        - Location details: Bottom right
                        - Parent element info: Order form""")
                .contains("### Candidate element_1\n- Name: Cancel button\n- Description: Grey button\n")
                .endsWith("\n\n## Screenshot");
    }

    @Test
    void createElementInDb_shouldFailWithTheDescriptionError_whenDescribingTheElementFails() {
        var screenshot = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var imageContentCreator = mock(ImageContentCreator.class);
        when(descriptionAgent.getImageContentCreator(config)).thenReturn(imageContentCreator);
        when(imageContentCreator.createImageContent(screenshot)).thenReturn(mock(ImageContent.class));
        when(descriptionAgent.executeAndGetResult(any())).thenReturn(new OperationExecutionResult<>(ERROR,
                "Retry policy exceeded because of max retries", null));

        try (MockedStatic<UiCommonUtils> uiCommonUtils = mockStatic(UiCommonUtils.class)) {
            uiCommonUtils.when(UiCommonUtils::captureScreen).thenReturn(screenshot);

            assertThatThrownBy(() -> tools.createElementInDb("search field", "dashboard name"))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessage("The UI element could not be described: Retry policy exceeded because of max retries")
                    .extracting(e -> ((ToolExecutionException) e).getErrorCategory())
                    .isEqualTo(TRANSIENT_TOOL_ERROR);
            verify(elementRepository, never()).create(any());
        }
    }
}
