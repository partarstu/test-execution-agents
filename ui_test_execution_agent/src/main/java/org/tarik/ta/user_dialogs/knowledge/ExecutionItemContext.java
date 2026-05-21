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
package org.tarik.ta.user_dialogs.knowledge;

import java.util.List;

/**
 * Context about the currently executed test step or precondition, shown read-only in the dialog header
 * so the user always knows what needs to be executed while editing or creating a procedure.
 */
public record ExecutionItemContext(String description, List<String> testData, boolean isPrecondition) {

    boolean hasTestData() {
        return testData != null && testData.stream().anyMatch(s -> s != null && !s.isBlank());
    }

    String formattedTestData() {
        return String.join(", ", testData);
    }
}
