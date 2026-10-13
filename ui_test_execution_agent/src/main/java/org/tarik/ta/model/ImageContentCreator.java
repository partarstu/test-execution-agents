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
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.AgentConfig.ModelProvider;

import java.awt.image.BufferedImage;

import static dev.langchain4j.data.message.ImageContent.from;
import static org.tarik.ta.utils.ImageUtils.*;

/**
 * How a single agent turns a screenshot into the image its model gets, pairing the agent's detail level with the provider serving
 * it. {@code maxImagePixels} is the pixel budget a full-detail screenshot may occupy.
 */
public record ImageContentCreator(@NotNull DetailLevel detailLevel, @NotNull ModelProvider modelProvider, int maxImagePixels) {
    private static final String DEFAULT_IMAGE_FORMAT = "png";
    private static final int PATCH_GRID = 32;
    private static final double LOW_DETAIL_BUDGET_FRACTION = 0.33;
    private static final double MEDIUM_DETAIL_BUDGET_FRACTION = 0.66;
    private static final double FULL_BUDGET_FRACTION = 1.0;
    private static final double NO_DOWNSCALING_RATIO = 1.0;

    /**
     * Sends the screenshot untouched to a model that honours the detail hint, and downscaled to its detail level to one whose
     * provider ignores the hint, since that is the only way a lower level costs less there.
     */
    @NotNull
    public ImageContent createImageContent(@NotNull BufferedImage screenshot) {
        if (modelProvider.isImageDetailSupported()) {
            return from(getImage(screenshot, DEFAULT_IMAGE_FORMAT), detailLevel);
        } else {
            // The detail is already spent on the resolution, so the wire value must not spend it a second time
            return from(getImage(scaleForDetailLevel(screenshot), DEFAULT_IMAGE_FORMAT));
        }
    }

    /** Downscales a screenshot to the share of {@code maxImagePixels} its detail level is worth, never upscaling. */
    @NotNull
    BufferedImage scaleForDetailLevel(@NotNull BufferedImage screenshot) {
        // Sides are snapped to the PATCH_GRID the vision tower bills in, so the server's own rounding is a no-op and it processes
        // exactly the image that was sent.
        long nativePixels = (long) screenshot.getWidth() * screenshot.getHeight();
        double budget = maxImagePixels * budgetFraction(detailLevel);
        double ratio = Math.sqrt(budget / nativePixels);
        if (ratio >= NO_DOWNSCALING_RATIO) {
            return screenshot;
        }
        int targetWidth = snapToPatchGrid(screenshot.getWidth() * ratio);
        int targetHeight = snapToPatchGrid(screenshot.getHeight() * ratio);
        return scaleImage(screenshot, targetWidth, targetHeight);
    }

    /**
     * The fraction of the pixel budget a detail level may spend, where HIGH and above take all the serving stack accepts because
     * the server downscales anything more again.
     */
    private static double budgetFraction(@NotNull DetailLevel detailLevel) {
        return switch (detailLevel) {
            case LOW -> LOW_DETAIL_BUDGET_FRACTION;
            case MEDIUM -> MEDIUM_DETAIL_BUDGET_FRACTION;
            case HIGH, ULTRA_HIGH, AUTO -> FULL_BUDGET_FRACTION;
        };
    }

    /** Rounds down, so that snapping can never push the result back over the budget it was scaled to fit. */
    private static int snapToPatchGrid(double dimension) {
        return Math.max(PATCH_GRID, (int) (dimension / PATCH_GRID) * PATCH_GRID);
    }
}
