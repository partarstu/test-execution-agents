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

import java.awt.*;

import org.tarik.ta.UiTestAgentConfig;

public class NewElementInfoNeededPopup extends AbstractConfirmationDialog {
    private NewElementInfoNeededPopup(Window owner, String elementDescription, UiTestAgentConfig config) {
        super(owner, "UI Element Not Found", config);

        initializeDialog(("I haven't found any UI element in my Database which matches the description '%s'." +
                " Please provide the info for the corresponding UI element.").formatted(elementDescription));
    }

    public static void display(Window owner, String elementDescription, UiTestAgentConfig config) {
        new NewElementInfoNeededPopup(owner, elementDescription, config);
    }
}
