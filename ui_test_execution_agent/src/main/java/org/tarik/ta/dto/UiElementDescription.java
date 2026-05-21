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

import dev.langchain4j.model.output.structured.Description;

@Description("the extracted by you information about the target UI element")
public record UiElementDescription(
        @Description("Name of the target element derived from its original description.")
        String name,
        @Description("An accurate, specific, compact information about the visual appearance of the target element. " +
                "This information must be enough for you to find this element on the screenshot, but at the same time this info shouldn't " +
                "contain any details which are too specific and might change over time (e.g. color, size etc.)")
        String ownDescription,
        @Description("The detailed description of the location of the target element relative to the nearest neighboring " +
                "element or elements. This information must be enough for you to find this element on the screenshot if multiple similar " +
                "elements are displayed on it, e.g. in case of multiple identical check-boxes or input fields with unique labels, " +
                "multiple identical buttons related to different forms or dialogs etc. This info shouldn't contain any details which are " +
                "too specific and might easily change over time during refactoring of UI.")
        String locationDescription,
        @Description("Name or very short description of the direct parent (enclosing) element (e.g. page/form/dialog/popup/view " +
                "etc.) in which the target element is located.")
        String parentSummary,
        @Description("Flag which defines if the target element depends on the data (if its content is dynamic). Examples of " +
                "data-dependent elements are: any option in the dropdown list, calendar day icon in the calendar grid, check-box with " +
                "dynamic label etc.")
        boolean elementIsDataDependent) {
}