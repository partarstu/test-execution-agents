/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.tools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.agents.BestUiElementMatchSelectionAgent;
import org.tarik.ta.agents.UiElementBoundingBoxAgent;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.PromptUtils;
import org.tarik.ta.dto.BestUiElementVisualMatchResult;
import org.tarik.ta.dto.BoundingBoxes;
import org.tarik.ta.dto.LocatedElementInfo;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.SUCCESS;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElementLocatorToolsTest {

    private ElementLocatorTools elementLocatorTools;

    @Mock
    private UiElementRepository mockRepository;
    @Mock
    private UiElementBoundingBoxAgent mockBBoxAgent;
    @Mock
    private BestUiElementMatchSelectionAgent mockSelectionAgent;
    @Mock
    private GenAiModel mockGenAiModel;
    @Mock
    private ChatModel mockChatModel;

    private MockedStatic<ModelFactory> modelFactoryMock;
    private MockedStatic<PromptUtils> promptUtilsMock;
    private MockedStatic<UiCommonUtils> uiCommonUtilsMock;
    private MockedStatic<UiTestAgentConfig> configMock;
    private MockedStatic<AiServices> aiServicesMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        modelFactoryMock = mockStatic(ModelFactory.class);
        modelFactoryMock.when(() -> ModelFactory.getModel(anyString(), any())).thenReturn(mockGenAiModel);
        when(mockGenAiModel.chatModel()).thenReturn(mockChatModel);

        promptUtilsMock = mockStatic(PromptUtils.class);
        promptUtilsMock.when(() -> PromptUtils.loadSystemPrompt(anyString(), anyString(), anyString())).thenReturn("mock prompt");

        uiCommonUtilsMock = mockStatic(UiCommonUtils.class);
        
        configMock = mockStatic(UiTestAgentConfig.class);
        configMock.when(UiTestAgentConfig::getElementBoundingBoxColorName).thenReturn("red");
        configMock.when(UiTestAgentConfig::getElementBoundingBoxAgentModelName).thenReturn("model");
        configMock.when(UiTestAgentConfig::getElementBoundingBoxAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        configMock.when(UiTestAgentConfig::getElementBoundingBoxAgentPromptVersion).thenReturn("v1");
        configMock.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelName).thenReturn("model");
        configMock.when(UiTestAgentConfig::getUiElementVisualMatchAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        configMock.when(UiTestAgentConfig::getElementSelectionAgentPromptVersion).thenReturn("v1");
        configMock.when(UiTestAgentConfig::getElementLocatorVisualGroundingVoteCount).thenReturn(1);
        configMock.when(UiTestAgentConfig::getElementLocatorValidationVoteCount).thenReturn(1);
        configMock.when(UiTestAgentConfig::getBboxClusteringMinIntersectionRatio).thenReturn(0.9);
        configMock.when(UiTestAgentConfig::getBboxScreenshotLongestAllowedDimensionPixels).thenReturn(5000);
        configMock.when(UiTestAgentConfig::getBboxScreenshotMaxSizeMegapixels).thenReturn(10.0);
        configMock.when(UiTestAgentConfig::isBoundingBoxAlreadyNormalized).thenReturn(false);
        configMock.when(UiTestAgentConfig::getUiStateCheckAgentModelName).thenReturn("model");
        configMock.when(UiTestAgentConfig::getUiStateCheckAgentModelProvider).thenReturn(AgentConfig.ModelProvider.GOOGLE);
        configMock.when(UiTestAgentConfig::getUiStateCheckAgentPromptVersion).thenReturn("v1");
        configMock.when(UiTestAgentConfig::getAgentToolCallsBudget).thenReturn(10);

        aiServicesMock = mockStatic(AiServices.class);
        AiServices<UiElementBoundingBoxAgent> bboxBuilder = mock(AiServices.class);
        AiServices<BestUiElementMatchSelectionAgent> selectionBuilder = mock(AiServices.class);
        AiServices<org.tarik.ta.agents.UiStateCheckAgent> uiStateCheckBuilder = mock(AiServices.class);

        aiServicesMock.when(() -> AiServices.builder(UiElementBoundingBoxAgent.class)).thenReturn(bboxBuilder);
        when(bboxBuilder.chatModel(any())).thenReturn(bboxBuilder);
        when(bboxBuilder.systemMessageProvider(any())).thenReturn(bboxBuilder);
        when(bboxBuilder.tools(any(BoundingBoxes.class))).thenReturn(bboxBuilder);
        when(bboxBuilder.build()).thenReturn(mockBBoxAgent);

        aiServicesMock.when(() -> AiServices.builder(BestUiElementMatchSelectionAgent.class)).thenReturn(selectionBuilder);
        when(selectionBuilder.chatModel(any())).thenReturn(selectionBuilder);
        when(selectionBuilder.systemMessageProvider(any())).thenReturn(selectionBuilder);
        when(selectionBuilder.tools(any(BestUiElementVisualMatchResult.class))).thenReturn(selectionBuilder);
        when(selectionBuilder.build()).thenReturn(mockSelectionAgent);

        aiServicesMock.when(() -> AiServices.builder(org.tarik.ta.agents.UiStateCheckAgent.class)).thenReturn(uiStateCheckBuilder);
        when(uiStateCheckBuilder.chatModel(any())).thenReturn(uiStateCheckBuilder);
        when(uiStateCheckBuilder.systemMessageProvider(any())).thenReturn(uiStateCheckBuilder);
        when(uiStateCheckBuilder.maxSequentialToolsInvocations(anyInt())).thenReturn(uiStateCheckBuilder);
        when(uiStateCheckBuilder.tools(any(org.tarik.ta.dto.UiStateCheckResult.class))).thenReturn(uiStateCheckBuilder);
        when(uiStateCheckBuilder.build()).thenReturn(mock(org.tarik.ta.agents.UiStateCheckAgent.class));

        elementLocatorTools = new ElementLocatorTools(mockRepository, null, null);
    }

    @AfterEach
    void tearDown() {
        modelFactoryMock.close();
        promptUtilsMock.close();
        uiCommonUtilsMock.close();
        configMock.close();
        aiServicesMock.close();
    }

    @Test
    @DisplayName("locateKnownElementById should return element info when found")
    void locateKnownElementById_shouldReturnElementInfo_whenFound() {
        UUID id = UUID.randomUUID();
        UiElement uiElement = mock(UiElement.class);
        when(uiElement.name()).thenReturn("test-element");
        when(uiElement.id()).thenReturn(id);
        when(mockRepository.findById(id)).thenReturn(Optional.of(uiElement));

        BufferedImage mockScreen = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        uiCommonUtilsMock.when(UiCommonUtils::captureScreen).thenReturn(mockScreen);
        uiCommonUtilsMock.when(() -> UiCommonUtils.getScaledBoundingBox(any(Rectangle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        uiCommonUtilsMock.when(() -> UiCommonUtils.getColorByName(anyString())).thenReturn(Color.RED);

        BoundingBoxes boundingBoxes = new BoundingBoxes(List.of(new org.tarik.ta.dto.BoundingBox(10, 10, 20, 20)));
        UiOperationExecutionResult<BoundingBoxes> executionResult = new UiOperationExecutionResult<>(SUCCESS, "success", boundingBoxes, null);
        
        when(mockBBoxAgent.executeAndGetResult(any())).thenReturn(executionResult);
        configMock.when(UiTestAgentConfig::skipBestUiElementMatchSelection).thenReturn(true);

        LocatedElementInfo result = elementLocatorTools.locateKnownElementById(id, "some data");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("test-element");
        assertThat(result.elementId()).isEqualTo(id);
        assertThat(result.centerXCoordinate()).isEqualTo(15);
        assertThat(result.centerYCoordinate()).isEqualTo(15);
    }
}
