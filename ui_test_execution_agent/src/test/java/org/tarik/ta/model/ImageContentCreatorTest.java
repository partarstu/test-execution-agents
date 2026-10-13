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
package org.tarik.ta.model;

import dev.langchain4j.data.message.ImageContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.LOW;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.tarik.ta.core.AgentConfig.ModelProvider.GOOGLE;
import static org.tarik.ta.core.AgentConfig.ModelProvider.QWEN;
import static org.tarik.ta.utils.ImageUtils.convertBase64ToImage;

class ImageContentCreatorTest {
    private static final int BUDGET = 8294400;
    private static final double LOW_BUDGET_FRACTION = 0.33;
    private static final double MEDIUM_BUDGET_FRACTION = 0.66;

    @Test
    @DisplayName("A provider which honours the detail hint gets the screenshot untouched at the configured detail level")
    void createImageContent_shouldSendTheDetailLevelForASupportingProvider() {
        BufferedImage screenshot = new BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB);

        var imageContent = new ImageContentCreator(MEDIUM, GOOGLE, BUDGET).createImageContent(screenshot);

        assertThat(imageContent.detailLevel()).isEqualTo(MEDIUM);
        var sent = sentImage(imageContent);
        assertThat(sent.getWidth()).isEqualTo(3840);
        assertThat(sent.getHeight()).isEqualTo(2160);
    }

    @Test
    @DisplayName("A provider which ignores the detail hint gets a downscaled screenshot instead")
    void createImageContent_shouldSpendTheDetailLevelOnResolutionForAnIgnoringProvider() {
        BufferedImage screenshot = new BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB);

        var imageContent = new ImageContentCreator(MEDIUM, QWEN, BUDGET).createImageContent(screenshot);

        assertThat(imageContent.detailLevel()).isEqualTo(LOW);
        var sent = sentImage(imageContent);
        assertThat((double) sent.getWidth() * sent.getHeight()).isLessThanOrEqualTo(BUDGET * MEDIUM_BUDGET_FRACTION);
    }

    @Test
    @DisplayName("A full detail level leaves the screenshot at its native resolution even for an ignoring provider")
    void createImageContent_shouldNotDownscaleAtFullDetail() {
        BufferedImage screenshot = new BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB);

        var imageContent = new ImageContentCreator(HIGH, QWEN, BUDGET).createImageContent(screenshot);

        var sent = sentImage(imageContent);
        assertThat(sent.getWidth()).isEqualTo(3840);
        assertThat(sent.getHeight()).isEqualTo(2160);
    }

    @Test
    @DisplayName("scaleForDetailLevel should leave a screenshot that already fits the budget untouched at full detail")
    void scaleForDetailLevel_shouldNotTouchAnImageThatFitsTheBudget() {
        BufferedImage original = new BufferedImage(3839, 2159, BufferedImage.TYPE_INT_RGB);

        BufferedImage scaled = new ImageContentCreator(HIGH, QWEN, BUDGET).scaleForDetailLevel(original);

        assertThat(scaled).isSameAs(original);
    }

    @Test
    @DisplayName("scaleForDetailLevel should bring an oversized screenshot inside the budget at full detail")
    void scaleForDetailLevel_shouldFitBudgetAtFullDetail() {
        BufferedImage original = new BufferedImage(3839, 2159, BufferedImage.TYPE_INT_RGB);

        BufferedImage scaled = new ImageContentCreator(HIGH, QWEN, 3686400).scaleForDetailLevel(original);

        assertThat((long) scaled.getWidth() * scaled.getHeight()).isLessThanOrEqualTo(3686400);
        assertThat(scaled.getWidth() % 32).isZero();
        assertThat(scaled.getHeight() % 32).isZero();
    }

    @Test
    @DisplayName("scaleForDetailLevel should spend a third of the budget on LOW and two thirds on MEDIUM")
    void scaleForDetailLevel_shouldSpendTheDetailLevelShareOfTheBudget() {
        BufferedImage original = new BufferedImage(3839, 2159, BufferedImage.TYPE_INT_RGB);

        BufferedImage low = new ImageContentCreator(LOW, QWEN, BUDGET).scaleForDetailLevel(original);
        BufferedImage medium = new ImageContentCreator(MEDIUM, QWEN, BUDGET).scaleForDetailLevel(original);

        assertThat((double) low.getWidth() * low.getHeight()).isLessThanOrEqualTo(BUDGET * LOW_BUDGET_FRACTION);
        assertThat((double) medium.getWidth() * medium.getHeight()).isLessThanOrEqualTo(BUDGET * MEDIUM_BUDGET_FRACTION);
        assertThat(low.getWidth()).isLessThan(medium.getWidth());
        assertThat(low.getWidth() % 32).isZero();
        assertThat(medium.getWidth() % 32).isZero();
    }

    @Test
    @DisplayName("scaleForDetailLevel should never upscale an image already under the budget")
    void scaleForDetailLevel_shouldNeverUpscale() {
        BufferedImage original = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);

        BufferedImage scaled = new ImageContentCreator(HIGH, QWEN, BUDGET).scaleForDetailLevel(original);

        assertThat(scaled.getWidth()).isEqualTo(640);
        assertThat(scaled.getHeight()).isEqualTo(480);
    }

    private static BufferedImage sentImage(ImageContent imageContent) {
        return convertBase64ToImage(imageContent.image().base64Data());
    }
}
