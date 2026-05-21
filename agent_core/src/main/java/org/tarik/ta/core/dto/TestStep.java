/*
 * agent-core - ${project.description}
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
package org.tarik.ta.core.dto;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

@Description("A single test step in a test case")
public record TestStep (
        @Description("A natural language description of the action to perform in this step.")
        String stepDescription,
        @Description("A test data to be used as input for the step. Allowed to be empty.")
        List<String> testData,
        @Description({"The expected outcome or state of the application after the step is executed. This is used for verification. Allowed " +
                "to be empty"})
        String expectedResults) {
}
