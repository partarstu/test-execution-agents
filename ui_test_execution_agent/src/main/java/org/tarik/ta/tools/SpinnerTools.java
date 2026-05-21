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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.user_dialogs.SpinnerManager;

import jakarta.inject.Singleton;

@Singleton
public class SpinnerTools {

    @Tool("Displays a spinner with the given message.")
    public void showSpinner(@P("The message to display on the spinner") String message) {
        SpinnerManager.show(message);
    }

    @Tool("Hides the currently visible spinner.")
    public void hideSpinner() {
        SpinnerManager.hide();
    }
}
