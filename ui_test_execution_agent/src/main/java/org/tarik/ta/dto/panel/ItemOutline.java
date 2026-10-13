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
package org.tarik.ta.dto.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * The decomposition of the timeline item at {@code item}: its test data and its procedures in execution order (pre-order), so a
 * procedure used twice is two nodes.
 */
public record ItemOutline(int item, List<String> testData, List<ProcedureNode> procedures) {

    public ItemOutline {
        testData = List.copyOf(testData);
        procedures = List.copyOf(procedures);
    }

    public ItemOutline withProcedure(int index, ProcedureNode procedure) {
        var updated = new ArrayList<>(procedures);
        updated.set(index, procedure);
        return new ItemOutline(item, testData, updated);
    }
}
