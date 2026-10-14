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
package org.tarik.ta.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.dto.AggregationSelection;
import org.tarik.ta.dto.AggregationSelectionResult;
import org.tarik.ta.dto.ItemSelection;
import org.tarik.ta.knowledge.ProcedureTreeAssembler;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository;
import org.tarik.ta.knowledge_graph.service.EmbeddingService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.knowledge.ProcedureFixtures.testCase;

class SelectionResultToolsTest {
    private static final int MAX_SELECTIONS = 3;
    private static final int MAX_CORRECTIONS = 5;

    private ProcedureKnowledgeTools explorationTools;
    private final List<String> knownIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    @BeforeEach
    void setUp() {
        var repository = mock(ProcedureRepository.class);
        var config = mock(KnowledgeAgentConfig.class);
        explorationTools = new ProcedureKnowledgeTools(repository, mock(EmbeddingService.class), new ProcedureTreeAssembler(repository, config),
                config);
        knownIds.forEach(id -> explorationTools.rememberProcedure(UUID.fromString(id), "Procedure " + id));
    }

    @Test
    void returnItemSelection_shouldReturnTheSelection_whenItIsValid() {
        var tool = itemTool("S2");
        var selection = new ItemSelection("S2", knownIds.subList(0, 2));

        assertThat(tool.returnItemSelection(selection)).isEqualTo(selection);
    }

    @Test
    void returnItemSelection_shouldAcceptAnEmptySelection() {
        assertThat(itemTool("S2").returnItemSelection(new ItemSelection("S2", List.of())).procedureIds()).isEmpty();
    }

    @Test
    void returnItemSelection_shouldAskForACorrection_whenAnIdWasNotReturnedByATool() {
        var foreignId = UUID.randomUUID().toString();

        assertRetried(() -> itemTool("S2").returnItemSelection(new ItemSelection("S2", List.of(foreignId))), foreignId);
    }

    @Test
    void returnItemSelection_shouldAskForACorrection_whenTheItemIdIsAnotherOne() {
        assertRetried(() -> itemTool("S2").returnItemSelection(new ItemSelection("S3", List.of(knownIds.getFirst()))), "item S2");
    }

    @Test
    void returnItemSelection_shouldAskForACorrection_whenMoreThanTheMaximumIsSelected() {
        assertRetried(() -> itemTool("S2").returnItemSelection(new ItemSelection("S2", knownIds)), "at most 3");
    }

    @Test
    void returnItemSelection_shouldAskForACorrection_whenAProcedureIsListedTwice() {
        var id = knownIds.getFirst();

        assertRetried(() -> itemTool("S2").returnItemSelection(new ItemSelection("S2", List.of(id, id))), "more than once");
    }

    @Test
    void returnAggregationSelections_shouldReturnTheSelections_whenEachCoversConsecutiveItemsOfOneKind() {
        var result = new AggregationSelectionResult(List.of(
                new AggregationSelection(List.of("S1", "S2", "S3"), List.of(knownIds.getFirst())),
                new AggregationSelection(List.of("P1", "P2"), List.of(knownIds.get(1)))));

        assertThat(aggregationTool().returnAggregationSelections(result)).isEqualTo(result);
    }

    @Test
    void returnAggregationSelections_shouldAskForACorrection_whenTheItemsAreNotConsecutive() {
        var result = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S1", "S3"), List.of(knownIds.getFirst()))));

        assertRetried(() -> aggregationTool().returnAggregationSelections(result), "consecutive");
    }

    @Test
    void returnAggregationSelections_shouldAskForACorrection_whenTheItemsAreOfMixedKinds() {
        var result = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("P2", "S1"), List.of(knownIds.getFirst()))));

        assertRetried(() -> aggregationTool().returnAggregationSelections(result), "consecutive");
    }

    @Test
    void returnAggregationSelections_shouldAskForACorrection_whenARunHasOneItemOrAnUnknownItem() {
        var single = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S1"), List.of(knownIds.getFirst()))));
        var unknown = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S3", "S4"), List.of(knownIds.getFirst()))));

        assertRetried(() -> aggregationTool().returnAggregationSelections(single), "at least two");
        assertRetried(() -> aggregationTool().returnAggregationSelections(unknown), "at least two");
    }

    @Test
    void returnAggregationSelections_shouldAskForACorrection_whenARunSelectsNoOrForeignOrTooManyProcedures() {
        var none = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S1", "S2"), List.of())));
        var foreignId = UUID.randomUUID().toString();
        var foreign = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S1", "S2"), List.of(foreignId))));
        var tooMany = new AggregationSelectionResult(List.of(new AggregationSelection(List.of("S1", "S2"), knownIds)));

        assertRetried(() -> aggregationTool().returnAggregationSelections(none), "no procedure");
        assertRetried(() -> aggregationTool().returnAggregationSelections(foreign), foreignId);
        assertRetried(() -> aggregationTool().returnAggregationSelections(tooMany), "at most 3");
    }

    @Test
    void returnItemSelection_shouldEndTheRun_whenTheSelectionIsStillInvalidAfterTheAllowedCorrections() {
        var tool = itemTool("S2");
        var foreign = new ItemSelection("S2", List.of(UUID.randomUUID().toString()));
        for (int correction = 0; correction < MAX_CORRECTIONS; correction++) {
            assertThatThrownBy(() -> tool.returnItemSelection(foreign))
                    .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(TRANSIENT_TOOL_ERROR));
        }

        assertThatThrownBy(() -> tool.returnItemSelection(foreign))
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(NON_RETRYABLE_ERROR))
                .hasMessageContaining("still invalid after %d corrections".formatted(MAX_CORRECTIONS));
    }

    @Test
    void returnAggregationSelections_shouldAskForACorrection_whenTheResultIsMissing() {
        assertRetried(() -> aggregationTool().returnAggregationSelections(null), "missing");
    }

    private ItemSelectionResultTool itemTool(String itemId) {
        var item = testCase().findItem(itemId).orElseThrow();
        return new ItemSelectionResultTool(item, explorationTools, MAX_SELECTIONS, MAX_CORRECTIONS);
    }

    private AggregationSelectionResultTool aggregationTool() {
        return new AggregationSelectionResultTool(testCase(), explorationTools, MAX_SELECTIONS, MAX_CORRECTIONS);
    }

    private static void assertRetried(Runnable returnSelection, String expectedReason) {
        assertThatThrownBy(returnSelection::run)
                .isInstanceOfSatisfying(ToolExecutionException.class, e -> assertThat(e.getErrorCategory()).isEqualTo(TRANSIENT_TOOL_ERROR))
                .hasMessageContaining(expectedReason)
                .hasMessageContaining("Correct the selection");
    }
}
