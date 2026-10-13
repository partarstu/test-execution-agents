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
package org.tarik.ta.dto;

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.dto.FinalResult;

import java.awt.Rectangle;
import java.util.List;

/**
 * The outcome of visual grounding, whichever shape the model reported it in. Lets the element locator work with on-screen
 * rectangles without knowing whether the model returned two opposite corners or a click position plus a size.
 */
public interface IdentifiedRegions extends FinalResult {

    /**
     * Converts the identified regions to rectangles in the coordinate space of the screenshot sent to the model, reading the
     * coordinates as normalized to [0, 1000] when {@code alreadyNormalized} is set and as pixels otherwise.
     */
    @NotNull
    List<Rectangle> toRectangles(int imageWidth, int imageHeight, boolean alreadyNormalized);
}
