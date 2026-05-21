/*
 * ui-test-execution-agent - ${project.description}
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

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

    @Test
    @DisplayName("applyHdrCorrection should gamma encode linear RGB values")
    void applyHdrCorrection_shouldGammaEncodeLinearRgbValues() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(128, 128, 128).getRGB());

        BufferedImage corrected = applyHdrCorrection(image);
        Color correctedColor = new Color(corrected.getRGB(0, 0));

        assertThat(correctedColor.getRed()).isEqualTo(188);
        assertThat(correctedColor.getGreen()).isEqualTo(188);
        assertThat(correctedColor.getBlue()).isEqualTo(188);
    }

    @Test
    @DisplayName("applyHdrCorrection should support custom buffered image types")
    void applyHdrCorrection_shouldSupportCustomBufferedImageTypes() {
        BufferedImage image = newCustomBufferedImage();
        image.setRGB(0, 0, new Color(128, 128, 128, 255).getRGB());

        BufferedImage corrected = applyHdrCorrection(image);
        Color correctedColor = new Color(corrected.getRGB(0, 0), true);

        assertThat(corrected.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
        assertThat(correctedColor.getRed()).isEqualTo(188);
        assertThat(correctedColor.getGreen()).isEqualTo(188);
        assertThat(correctedColor.getBlue()).isEqualTo(188);
        assertThat(correctedColor.getAlpha()).isEqualTo(255);
    }

    private static BufferedImage newCustomBufferedImage() {
        ColorModel colorModel = ColorModel.getRGBdefault();
        return new BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(1, 1), colorModel.isAlphaPremultiplied(), null);
    }
}
