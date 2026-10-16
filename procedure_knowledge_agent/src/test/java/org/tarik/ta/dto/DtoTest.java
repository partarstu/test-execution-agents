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

import org.junit.jupiter.api.Test;
import org.tarik.ta.agents.AggregationSelectionAgent;
import org.tarik.ta.agents.ItemSelectionAgent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.tarik.ta.knowledge.ProcedureFixtures.testCase;

class DtoTest {

    @Test
    void testCaseItem_shouldBeIdentifiedByTheKindPrefixAndItsNumber() {
        assertThat(new TestCaseItem(ItemKind.PRECONDITION, 2, "Logged in", List.of(), "").id()).isEqualTo("P2");
        assertThat(new TestCaseItem(ItemKind.STEP, 4, "Check out", List.of(), "").id()).isEqualTo("S4");
    }

    @Test
    void testCaseItem_shouldRejectANumberBelowOne() {
        assertThatThrownBy(() -> new TestCaseItem(ItemKind.STEP, 0, "Check out", List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0");
    }

    @Test
    void isConsecutiveRunOfOneKind_shouldAcceptOnlyConsecutiveItemsOfOneKindInTheirOrder() {
        var testCase = testCase();

        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("S1", "S2", "S3"))).isTrue();
        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("P1", "P2"))).isTrue();
        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("S2", "S1"))).isFalse();
        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("P2", "S1"))).isFalse();
        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("S3", "S4"))).isFalse();
        assertThat(testCase.isConsecutiveRunOfOneKind(List.of("S1"))).isFalse();
    }

    @Test
    void selectionAgents_shouldDescribeTheirTask() {
        assertThat(mock(ItemSelectionAgent.class, CALLS_REAL_METHODS).getAgentTaskDescription()).contains("test case item");
        assertThat(mock(AggregationSelectionAgent.class, CALLS_REAL_METHODS).getAgentTaskDescription()).contains("consecutive");
    }
}
