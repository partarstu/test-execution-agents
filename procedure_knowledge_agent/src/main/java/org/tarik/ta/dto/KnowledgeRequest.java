/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The test case a caller sends as the data part of its request, unvalidated: its preconditions, one per entry, and its test steps.
 * Their positions give the item IDs, P1, P2, … for the preconditions and S1, S2, … for the test steps.
 */
public record KnowledgeRequest(@Nullable String name, @Nullable List<String> preconditions, @Nullable List<Step> steps) {

    public record Step(@Nullable String action, @Nullable List<String> testData, @Nullable String expectedResults) {
    }
}
