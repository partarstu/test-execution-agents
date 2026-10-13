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

/**
 * The section headings shared by the action and verification user messages. They are the terms the action and verification
 * system prompts use for the same data.
 */
public final class UserMessageSections {
    public static final String ACTION_LABEL = "Action";
    public static final String PRECONDITION_LABEL = "Precondition";
    public static final String ACTION_DATA_LABEL = "Data relevant to this action";
    public static final String ADDITIONAL_INFO_LABEL = "Additional info";
    public static final String TARGET_ELEMENT_ID_LABEL = "Target UI element ID";
    public static final String SCREENSHOT_LABEL = "Screenshot";

    private UserMessageSections() {
    }
}
