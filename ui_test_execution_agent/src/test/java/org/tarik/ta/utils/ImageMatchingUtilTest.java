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
package org.tarik.ta.utils;

import org.bytedeco.javacpp.Loader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.tarik.ta.UiTestAgentConfig;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImageMatchingUtilTest {

    @Mock
    private UiTestAgentConfig configMock;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);
        lenient().when(configMock.getElementLocatorTopVisualMatches()).thenReturn(5);
        lenient().when(configMock.getElementLocatorVisualSimilarityThreshold()).thenReturn(0.8);
        lenient().when(configMock.getFoundMatchesDimensionDeviationRatio()).thenReturn(0.1);
        
        // Reset static state for reliable testing
        Field initAttemptedField = ImageMatchingUtil.class.getDeclaredField("initializationAttempted");
        initAttemptedField.setAccessible(true);
        initAttemptedField.set(null, false);
        
        Field initializedField = ImageMatchingUtil.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(null, false);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("findMatchingRegionsWithTemplateMatching should return matches for identical images")
    void findMatchingRegionsWithTemplateMatching_shouldReturnMatch_whenIdentical() {
        try (MockedStatic<Loader> loader = mockStatic(Loader.class);
             MockedStatic<ORB> orb = mockStatic(ORB.class);
             MockedStatic<Imgcodecs> imgcodecs = mockStatic(Imgcodecs.class);
             MockedStatic<Imgproc> imgproc = mockStatic(Imgproc.class);
             MockedStatic<Core> core = mockStatic(Core.class);
             MockedConstruction<MatOfByte> matOfByte = mockConstruction(MatOfByte.class);
             MockedConstruction<Mat> mat = mockConstruction(Mat.class);
             MockedConstruction<Scalar> scalar = mockConstruction(Scalar.class)) {
            
            ORB mockOrb = mock(ORB.class);
            orb.when(() -> ORB.create(anyInt(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
               .thenReturn(mockOrb);

            Mat mockSource = mock(Mat.class);
            Mat mockTemplate = mock(Mat.class);
            imgcodecs.when(() -> Imgcodecs.imdecode(any(MatOfByte.class), eq(Imgcodecs.IMREAD_COLOR)))
                     .thenReturn(mockSource)
                     .thenReturn(mockTemplate);

            Core.MinMaxLocResult minMaxLocResult = new Core.MinMaxLocResult();
            minMaxLocResult.maxVal = 0.9;
            minMaxLocResult.maxLoc = new org.opencv.core.Point(10, 10);
            
            Core.MinMaxLocResult minMaxLocResult2 = new Core.MinMaxLocResult();
            minMaxLocResult2.maxVal = 0.5;
            minMaxLocResult2.maxLoc = new org.opencv.core.Point(0, 0);

            core.when(() -> Core.minMaxLoc(any(Mat.class)))
                .thenReturn(minMaxLocResult, minMaxLocResult2);
            
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            BufferedImage template = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            
            List<Rectangle> matches = ImageMatchingUtil.findMatchingRegionsWithTemplateMatching(image, template, configMock);
            
            assertThat(matches).isNotEmpty();
            assertThat(matches.get(0).x).isEqualTo(10);
            assertThat(matches.get(0).y).isEqualTo(10);
            assertThat(matches.get(0).width).isEqualTo(20);
            assertThat(matches.get(0).height).isEqualTo(20);
        }
    }
}
