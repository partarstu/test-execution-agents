/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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

import static java.util.Objects.requireNonNullElse;

/**
 * A single precondition of a test case, together with the data the test case supplies for it.
 */
@Description("A single precondition which needs to be fulfilled before the test execution can be started.")
public record Precondition(
        @Description("A natural language description of the state the application must be in.") String description,
        @Description("The data this precondition needs, taken verbatim from the test case. Allowed to be empty.")
        List<String> relevantData) {

    public Precondition {
        relevantData = List.copyOf(requireNonNullElse(relevantData, List.of()));
    }
}
