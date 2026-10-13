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

import org.bytedeco.javacpp.Loader;
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
import org.tarik.ta.agents.UiElementGrounder;
import org.tarik.ta.dto.LocatedElementInfo;
import org.tarik.ta.dto.ScreenRegion;
import org.tarik.ta.knowledge_graph.repository.UiElementRepository;
import org.tarik.ta.knowledge_graph.model.node.UiElement;
import org.tarik.ta.knowledge_graph.model.node.UiElementAppearance;
import org.tarik.ta.knowledge_graph.service.UiElementAliasRegistry;
import org.tarik.ta.knowledge_graph.service.UiElementCache;
import org.tarik.ta.utils.ImageMatchingUtil;
import org.tarik.ta.utils.UiCommonUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElementLocatorToolsTest {

    private ElementLocatorTools elementLocatorTools;

    @Mock
    private UiElementRepository mockRepository;
    @Mock
    private UiElementCache mockUiElementCache;
    @Mock
    private UiElementGrounder mockGrounder;

    @Mock
    private BestUiElementMatchSelectionAgent mockSelectionAgent;
    @Mock
    private org.tarik.ta.agents.UiStateCheckAgent mockUiStateCheckAgent;
    @Mock
    private org.tarik.ta.knowledge_graph.location_history.LocationHistoryRecorder mockLocationHistoryRecorder;
    @Mock
    private UiTestAgentConfig configMock;

    private final UiElementAliasRegistry aliasRegistry = new UiElementAliasRegistry();

    private MockedStatic<UiCommonUtils> uiCommonUtilsMock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uiCommonUtilsMock = mockStatic(UiCommonUtils.class);

        lenient().when(configMock.getElementBoundingBoxColorName()).thenReturn("red");
        lenient().when(configMock.getElementLocatorVisualGroundingVoteCount()).thenReturn(1);
        lenient().when(configMock.getBboxClusteringMinIntersectionRatio()).thenReturn(0.9);
        lenient().when(configMock.getBboxScreenshotLongestAllowedDimensionPixels()).thenReturn(5000);
        lenient().when(configMock.getBboxScreenshotMaxSizeMegapixels()).thenReturn(10.0);

        elementLocatorTools = new ElementLocatorTools(mockUiElementCache, mockRepository, mockUiStateCheckAgent,
                mockLocationHistoryRecorder, mockGrounder, mockSelectionAgent, aliasRegistry, configMock);
    }

    @AfterEach
    void tearDown() {
        uiCommonUtilsMock.close();
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
        uiCommonUtilsMock.when(() -> UiCommonUtils.getColorByName(anyString())).thenReturn(Color.RED);

        when(mockGrounder.locate(any(), any())).thenReturn(List.of(new Rectangle(10, 10, 10, 10)));
        lenient().when(configMock.skipBestUiElementMatchSelection()).thenReturn(true);

        String alias = aliasRegistry.aliasFor(id);
        LocatedElementInfo result = elementLocatorTools.locateKnownElementById(alias, "some data");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("test-element");
        assertThat(result.elementId()).isEqualTo(alias);
        assertThat(result.centerXCoordinate()).isEqualTo(150);
        assertThat(result.centerYCoordinate()).isEqualTo(150);
        assertThat(result.elementScreenRegion()).isEqualTo(new ScreenRegion(100, 100, 100, 100));
    }

    @Test
    @DisplayName("locateKnownElementById should throw UNKNOWN_ELEMENT_ID with guidance when the ID is neither an alias nor a UUID")
    void locateKnownElementById_shouldThrowUnknownElementId_whenIdIsInvented() {
        assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById("wrapHeaderTextToggle", null))
                .isInstanceOf(ElementLocationException.class)
                .hasMessage("No UI element with ID 'wrapHeaderTextToggle' exists. Use only the target UI element ID given in the " +
                        "task; if none was given, this tool must not be called.")
                .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                        .isEqualTo(ElementLocationStatus.UNKNOWN_ELEMENT_ID));
        verifyNoInteractions(mockUiElementCache, mockRepository, mockGrounder, mockLocationHistoryRecorder);
    }

    @Test
    @DisplayName("locateKnownElementById should throw ElementLocationException with NO_ELEMENTS_FOUND_IN_DB when element not in DB")
    void locateKnownElementById_shouldThrowElementLocationException_whenElementNotInDb() {
        UUID id = UUID.randomUUID();
        when(mockUiElementCache.get(id)).thenReturn(Optional.empty());
        when(mockRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById(id.toString(), null))
                .isInstanceOf(ElementLocationException.class)
                .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                        .isEqualTo(ElementLocationStatus.NO_ELEMENTS_FOUND_IN_DB));
    }

    @Test
    void locateKnownElementById_shouldMatchTheExpiredAppearances_whenTheCurrentAppearanceYieldsNoCandidate() {
        var element = new UiElement(UUID.randomUUID(), "Save", "The save button.", "Bottom", "Form", null, false);
        var expiredScreenshot = UiElement.Screenshot.fromBufferedImage(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png");
        var appearance = new UiElementAppearance(UUID.randomUUID(), "The old save button.", "Bottom", "Form", expiredScreenshot, null,
                Instant.parse("2026-09-01T10:00:00Z"));
        givenElementNotFoundByVision(element);
        when(configMock.isAlgorithmicSearchEnabled()).thenReturn(true);
        when(mockRepository.findExpiredAppearances(element.id())).thenReturn(List.of(appearance));

        try (var imageMatching = mockStatic(ImageMatchingUtil.class);
             var _ = mockStatic(Loader.class)) {
            imageMatching.when(() -> ImageMatchingUtil.findMatchingRegionsWithORB(any(), any(), any()))
                    .thenReturn(List.of(new Rectangle(10, 10, 10, 10)));
            imageMatching.when(() -> ImageMatchingUtil.findMatchingRegionsWithTemplateMatching(any(), any(), any())).thenReturn(List.of());

            assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(element.id()), null))
                    .isInstanceOf(ElementLocationException.class)
                    .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                            .isEqualTo(ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED));
            imageMatching.verify(() -> ImageMatchingUtil.findMatchingRegionsWithORB(any(),
                    argThat((BufferedImage image) -> image.getWidth() == 4), any()));
        }
    }

    @Test
    void locateKnownElementById_shouldNotLoadTheExpiredAppearances_whenAlgorithmicSearchIsDisabled() {
        var element = new UiElement(UUID.randomUUID(), "Save", "The save button.", "Bottom", "Form", null, false);
        givenElementNotFoundByVision(element);
        when(configMock.isAlgorithmicSearchEnabled()).thenReturn(false);

        assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(element.id()), null))
                .isInstanceOf(ElementLocationException.class)
                .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                        .isEqualTo(ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED));
        verify(mockRepository, never()).findExpiredAppearances(any());
    }

    @Test
    void locateKnownElementById_shouldReportNotFound_whenTheGroundedBoxIsCenteredOutsideTheScreen() {
        var element = new UiElement(UUID.randomUUID(), "Options pane", "The options pane.", "Right", "Editor", null, false);
        givenElementGroundedAt(element, new Rectangle(150, 10, 30, 10));
        when(configMock.isAlgorithmicSearchEnabled()).thenReturn(false);

        assertThatThrownBy(() -> elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(element.id()), null))
                .isInstanceOf(ElementLocationException.class)
                .satisfies(e -> assertThat(((ElementLocationException) e).getStatus())
                        .isEqualTo(ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED));
    }

    @Test
    void locateKnownElementById_shouldClipTheGroundedBoxToTheScreen_whenItReachesPastTheEdge() {
        var element = new UiElement(UUID.randomUUID(), "Options pane", "The options pane.", "Right", "Editor", null, false);
        givenElementGroundedAt(element, new Rectangle(70, 10, 40, 10));
        when(configMock.skipBestUiElementMatchSelection()).thenReturn(true);

        var result = elementLocatorTools.locateKnownElementById(aliasRegistry.aliasFor(element.id()), null);

        assertThat(result.centerXCoordinate()).isEqualTo(850);
        assertThat(result.elementScreenRegion()).isEqualTo(new ScreenRegion(700, 100, 300, 100));
    }

    private void givenElementNotFoundByVision(UiElement element) {
        givenElementGroundedAt(element);
    }

    private void givenElementGroundedAt(UiElement element, Rectangle... groundedBoxes) {
        when(mockUiElementCache.get(element.id())).thenReturn(Optional.of(element));
        uiCommonUtilsMock.when(UiCommonUtils::captureScreen).thenReturn(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        uiCommonUtilsMock.when(() -> UiCommonUtils.getColorByName(anyString())).thenReturn(Color.RED);
        uiCommonUtilsMock.when(() -> UiCommonUtils.getColorName(any())).thenReturn("red");
        when(mockGrounder.locate(any(), any())).thenReturn(List.of(groundedBoxes));
    }

    @Test
    @DisplayName("The best match message renders the target element, its specific data and the bounding box IDs as sections")
    void getBestElementVisualMatchUserMessage_shouldRenderTheSpecificData_whenElementIsDataDependent() {
        var element = new UiElement(UUID.randomUUID(), "day cell", "A day of the calendar.", "In the grid", "Calendar", null, true);

        var message = ElementLocatorTools.getBestElementVisualMatchUserMessage(element, "30", List.of("1", "2"));

        assertThat(message).isEqualTo("""
                ## Target element
                day cell. A day of the calendar. In the grid

                ## Specific data of this data-dependent element
                30

                ## Bounding box IDs
                - 1
                - 2

                ## Screenshot""");
    }

    @Test
    @DisplayName("The best match message leaves out the specific data of an element which does not depend on data")
    void getBestElementVisualMatchUserMessage_shouldLeaveOutTheSpecificData_whenElementIsNotDataDependent() {
        var element = new UiElement(UUID.randomUUID(), "Save", "The save button.", "Bottom", "Form", null, false);

        var message = ElementLocatorTools.getBestElementVisualMatchUserMessage(element, "30", List.of("1"));

        assertThat(message).doesNotContain("Specific data").contains("## Bounding box IDs\n- 1\n\n## Screenshot");
    }
}
