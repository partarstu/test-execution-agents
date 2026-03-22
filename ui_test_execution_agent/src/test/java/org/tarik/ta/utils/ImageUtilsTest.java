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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.utils.ImageUtils.*;

class ImageUtilsTest {

    @Test
    @DisplayName("scaleImage should return scaled image")
    void scaleImage_shouldReturnScaledImage() {
        BufferedImage original = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        
        BufferedImage scaled = scaleImage(original, 0.5);
        
        assertThat(scaled.getWidth()).isEqualTo(50);
        assertThat(scaled.getHeight()).isEqualTo(50);
    }

    @Test
    @DisplayName("padImage should return padded image")
    void padImage_shouldReturnPaddedImage() {
        BufferedImage original = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        
        BufferedImage padded = padImage(original, 150, 160);
        
        assertThat(padded.getWidth()).isEqualTo(150);
        assertThat(padded.getHeight()).isEqualTo(160);
    }

    @Test
    @DisplayName("convertImageToBase64 should return base64 string")
    void convertImageToBase64_shouldReturnBase64String() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        
        String base64 = convertImageToBase64(image, "png");
        
        assertThat(base64).isNotEmpty();
    }
}
