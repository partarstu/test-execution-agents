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
import org.tarik.ta.dto.BoundingBoxes;
import org.tarik.ta.dto.LocatedElementInfo;
import org.tarik.ta.dto.UiOperationExecutionResult;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private UiElementCache mockUiElementCache;
    @Mock
    private UiElementBoundingBoxAgent mockBBoxAgent;

    @Mock
    private BestUiElementMatchSelectionAgent mockSelectionAgent;
    @Mock
    private org.tarik.ta.agents.UiStateCheckAgent mockUiStateCheckAgent;
    @Mock
    private org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder mockLocationHistoryRecorder;
    @Mock
    private UiTestAgentConfig configMock;

    private MockedStatic<UiCommonUtils> uiCommonUtilsMock;
    private MockedStatic<org.tarik.ta.user_dialogs.SpinnerManager> spinnerManagerMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uiCommonUtilsMock = mockStatic(UiCommonUtils.class);

        spinnerManagerMock = mockStatic(org.tarik.ta.user_dialogs.SpinnerManager.class);
        spinnerManagerMock.when(org.tarik.ta.user_dialogs.SpinnerManager::hideIfVisible)
                .thenReturn(mock(org.tarik.ta.user_dialogs.SpinnerState.class));

        lenient().when(configMock.getElementBoundingBoxColorName()).thenReturn("red");
        lenient().when(configMock.getElementLocatorVisualGroundingVoteCount()).thenReturn(1);
        lenient().when(configMock.getBboxClusteringMinIntersectionRatio()).thenReturn(0.9);
        lenient().when(configMock.getBboxScreenshotLongestAllowedDimensionPixels()).thenReturn(5000);
        lenient().when(configMock.getBboxScreenshotMaxSizeMegapixels()).thenReturn(10.0);
        lenient().when(configMock.isBoundingBoxAlreadyNormalized()).thenReturn(false);

        elementLocatorTools = new ElementLocatorTools(mockUiElementCache, mockRepository, mockUiStateCheckAgent,
                mockLocationHistoryRecorder, mockBBoxAgent, mockSelectionAgent, configMock);
    }

    @AfterEach
    void tearDown() {
        uiCommonUtilsMock.close();
        spinnerManagerMock.close();
    }

    @Test
    @DisplayName("locateKnownElementById should return element info when found")
    void locateKnownElementById_shouldReturnElementInfo_whenFound() {
        UUID id = UUID.randomUUID();
        UiElement uiElement = mock(UiElement.class);
        when(uiElement.name()).thenReturn("test-element");
        when(uiElement.id()).thenReturn(id);
        when(mockUiElementCache.get(id)).thenReturn(Optional.of(uiElement));

        BufferedImage mockScreen = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        uiCommonUtilsMock.when(UiCommonUtils::captureScreen).thenReturn(mockScreen);
        uiCommonUtilsMock.when(() -> UiCommonUtils.getScaledBoundingBox(any(Rectangle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        uiCommonUtilsMock.when(() -> UiCommonUtils.getColorByName(anyString())).thenReturn(Color.RED);

        BoundingBoxes boundingBoxes = new BoundingBoxes(List.of(new org.tarik.ta.dto.BoundingBox(10, 10, 20, 20)));
        UiOperationExecutionResult<BoundingBoxes> executionResult =
                new UiOperationExecutionResult<>(SUCCESS, "success", boundingBoxes, null);

        when(mockBBoxAgent.executeAndGetResult(any())).thenReturn(executionResult);
        lenient().when(configMock.skipBestUiElementMatchSelection()).thenReturn(true);

        LocatedElementInfo result = elementLocatorTools.locateKnownElementById(id, "some data");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("test-element");
        assertThat(result.elementId()).isEqualTo(id);
        assertThat(result.centerXCoordinate()).isEqualTo(15);
        assertThat(result.centerYCoordinate()).isEqualTo(15);
    }

    @Test
    @DisplayName("locateKnownElementById should throw ElementLocationException with NO_ELEMENTS_FOUND_IN_DB when element not in DB")
    void locateKnownElementById_shouldThrowElementLocationException_whenElementNotInDb() {
        UUID id = UUID.randomUUID();
        when(mockUiElementCache.get(id)).thenReturn(Optional.empty());
        when(mockRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById(id, null))
                .isInstanceOf(ElementLocationException.class)
                .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                        .isEqualTo(ElementLocationStatus.NO_ELEMENTS_FOUND_IN_DB));
    }
}
