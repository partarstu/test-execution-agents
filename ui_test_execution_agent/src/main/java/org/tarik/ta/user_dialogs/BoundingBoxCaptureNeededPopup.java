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
package org.tarik.ta.user_dialogs;

import org.tarik.ta.UiTestAgentConfig;

import java.awt.*;

public class BoundingBoxCaptureNeededPopup extends AbstractConfirmationDialog {
    private BoundingBoxCaptureNeededPopup(Window owner, UiTestAgentConfig config) {
        super(owner, "Further action required", config);
        setFocusableWindowState(false);

        initializeDialog("The screenshot of the first screen is to be made and after that you'll " +
                "be asked to highlight the target element on that screenshot. Please make sure that the target element is visible on the " +
                "first screen");
    }

    public static void display(Window owner, UiTestAgentConfig config) {
        new BoundingBoxCaptureNeededPopup(owner, config);
    }
}
