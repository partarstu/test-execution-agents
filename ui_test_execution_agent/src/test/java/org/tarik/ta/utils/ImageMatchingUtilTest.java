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
package org.tarik.ta.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.tarik.ta.UiTestAgentConfig;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ImageMatchingUtilTest {

    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        
        lenient().when(configMock.getElementLocatorTopVisualMatches()).thenReturn(5);
        lenient().when(configMock.getElementLocatorVisualSimilarityThreshold()).thenReturn(0.8);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("findMatchingRegionsWithTemplateMatching should return matches for identical images")
    void findMatchingRegionsWithTemplateMatching_shouldReturnMatch_whenIdentical() {
        assumeTrue(ImageMatchingUtil.isAvailable(), "OpenCV is not available in this environment");
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(java.awt.Color.RED);
        g.fillRect(10, 10, 20, 20);
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(15, 15, 10, 10);
        g.dispose();
        
        // Clone the subimage to make sure it's not a view
        BufferedImage template = ImageUtils.cloneImage(image.getSubimage(10, 10, 20, 20));
        
        List<Rectangle> matches = ImageMatchingUtil.findMatchingRegionsWithTemplateMatching(image, template, configMock);
        
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).x).isEqualTo(10);
        assertThat(matches.get(0).y).isEqualTo(10);
    }
}
