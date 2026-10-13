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
package org.tarik.ta;

/**
 * The shape in which the visual grounding model is asked to report an element's location.
 */
public enum GroundingOutputFormat {
    /** Two opposite corners of the element, the default for general-purpose vision models such as Gemini and Claude. */
    BOUNDING_BOX,

    /**
     * The position to click plus the element's size, as GUI grounding models such as Holo3.1 natively predict it; only the click
     * position is reliable, and the {@code hcompany} and {@code qwen} providers are asked for it alone through a JSON schema.
     */
    CENTER_AND_SIZE
}
