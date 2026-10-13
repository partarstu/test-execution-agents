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
package org.tarik.ta.knowledge_graph.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.tarik.ta.knowledge_graph.health.HealthFinding;
import org.tarik.ta.knowledge_graph.health.HealthFinding.NodeRef;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.neo4j.driver.Values.NULL;
import static org.neo4j.driver.Values.value;

@ExtendWith(MockitoExtension.class)
class GraphHealthRepositoryTest {
    private static final UUID ELEMENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_PROCEDURE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID PHRASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID FAILURE_CONTEXT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock
    private Neo4jRepositorySupport mockRepositorySupport;

    private GraphHealthRepository repository;

    @BeforeEach
    void setUp() {
        when(mockRepositorySupport.cypher(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        repository = new GraphHealthRepository(mockRepositorySupport);
    }

    @Test
    void findOrphanedUiElements_shouldReferenceTheElement() {
        var row = record(Map.of("id", value(ELEMENT_ID.toString()), "label", value("Save")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findOrphanedUiElements();

        assertThat(findings).containsExactly(finding("UiElement[%s]: Save".formatted(ELEMENT_ID), elementRef()));
    }

    @Test
    void findLeafProceduresWithoutElement_shouldReferenceTheProcedure() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findLeafProceduresWithoutElement();

        assertThat(findings).containsExactly(finding("Procedure[%s]: Click Save".formatted(PROCEDURE_ID), procedureRef()));
    }

    @Test
    void findDeepHierarchies_shouldReferenceTheRootProcedure() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Checkout"), "depth", value(5)));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), eq(Map.of("maxDepth", 3)))).thenReturn(List.of(row));

        var findings = repository.findDeepHierarchies(3);

        assertThat(findings).containsExactly(finding("Procedure[%s] 'Checkout': depth=5".formatted(PROCEDURE_ID), procedureRef()));
    }

    @Test
    void findUnusedProcedures_shouldReferenceTheProcedure() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Checkout")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findUnusedProcedures();

        assertThat(findings).containsExactly(finding("Procedure[%s]: Checkout".formatted(PROCEDURE_ID), procedureRef()));
    }

    @Test
    void findProceduresWithMissingEffects_shouldReferenceTheProcedure() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findProceduresWithMissingEffects();

        assertThat(findings).containsExactly(finding("Procedure[%s]: Click Save".formatted(PROCEDURE_ID), procedureRef()));
    }

    @Test
    void findOrphanedPhraseEmbeddings_shouldReferenceThePhrase() {
        var row = record(Map.of("id", value(PHRASE_ID.toString()), "label", value("Cart is open")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findOrphanedPhraseEmbeddings();

        assertThat(findings).containsExactly(finding("PhraseEmbedding[%s]: Cart is open".formatted(PHRASE_ID),
                new NodeRef(PhraseEmbedding.LABEL, PHRASE_ID)));
    }

    @Test
    void findStaleSatisfiesEdges_shouldReferenceTheProducerAndTheConsumer() {
        var row = record(Map.of("producerId", value(PROCEDURE_ID.toString()), "producerDesc", value("Open cart"),
                "consumerId", value(OTHER_PROCEDURE_ID.toString()), "consumerDesc", value("Submit order"), "score", value(0.931)));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), eq(Map.of("staleMs", 30L * 86_400_000L)))).thenReturn(List.of(row));

        var findings = repository.findStaleSatisfiesEdges(30);

        // The finder formats the score in the default locale
        assertThat(findings).containsExactly(finding("SATISFIES: 'Open cart' -> 'Submit order' (score=%.3f, stale)".formatted(0.931),
                procedureRef(), new NodeRef(Procedure.LABEL, OTHER_PROCEDURE_ID)));
    }

    @Test
    void findOrphanedFailureContexts_shouldReferenceTheFailureContext() {
        var row = record(Map.of("id", value(FAILURE_CONTEXT_ID.toString()), "label", value("Button not ready")));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findOrphanedFailureContexts();

        assertThat(findings).containsExactly(finding("FailureContext[%s]: Button not ready".formatted(FAILURE_CONTEXT_ID),
                new NodeRef(FailureContext.LABEL, FAILURE_CONTEXT_ID)));
    }

    @Test
    void findLowStabilityElementsByBlastRadius_shouldReportDependentProceduresAndTestCases_belowTheThreshold() {
        var row = record(Map.of("id", value(ELEMENT_ID.toString()), "label", value("Save"), "stability", value(0.42),
                "procedures", value(12), "testCases", value(40)));
        when(mockRepositorySupport.executeSingleReadQuery(
                argThat((String query) -> query.contains("${REL_CONTAINS}*0..") && query.contains("${REL_USES_PROCEDURE}")),
                eq(Map.of("threshold", 0.5))))
                .thenReturn(List.of(row));

        var findings = repository.findLowStabilityElementsByBlastRadius(0.5);

        assertThat(findings).containsExactly(finding(
                "UiElement[%s] 'Save': stability=0.42, atomic procedures=12, test cases=40".formatted(ELEMENT_ID), elementRef()));
    }

    @Test
    void findFailingProceduresByBlastRadius_shouldCountStepFailuresInsideTheWindow() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save"), "failures", value(3),
                "testCases", value(7)));
        when(mockRepositorySupport.executeSingleReadQuery(argThat((String query) -> query.contains("${REL_OBSERVED_ON}")),
                eq(Map.of("kind", "STEP_FAILED", "windowDays", 30))))
                .thenReturn(List.of(row));

        var findings = repository.findFailingProceduresByBlastRadius(30);

        assertThat(findings).containsExactly(finding(
                "Procedure[%s] 'Click Save': 3 failures in 30 days, test cases=7".formatted(PROCEDURE_ID), procedureRef()));
    }

    @Test
    void findProceduresWithoutRecentSuccess_shouldShowTheSuccessCountAndTheLastSuccess() {
        var provenBefore = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save"),
                "successes", value(4), "lastSuccess", value("2026-05-01T10:00:00Z")));
        var neverProven = record(Map.of("id", value(OTHER_PROCEDURE_ID.toString()), "description", value("Click Cancel"),
                "successes", value(0), "lastSuccess", NULL));
        when(mockRepositorySupport.executeSingleReadQuery(argThat((String query) -> query.contains("datetime(p.${PROP_CREATED_AT})")),
                eq(Map.of("windowDays", 30))))
                .thenReturn(List.of(provenBefore, neverProven));

        var findings = repository.findProceduresWithoutRecentSuccess(30);

        assertThat(findings).containsExactly(
                finding("Procedure[%s] 'Click Save': successes=4, last success=2026-05-01T10:00:00Z".formatted(PROCEDURE_ID),
                        procedureRef()),
                finding("Procedure[%s] 'Click Cancel': successes=0, last success=never".formatted(OTHER_PROCEDURE_ID),
                        new NodeRef(Procedure.LABEL, OTHER_PROCEDURE_ID)));
    }

    @Test
    void findUnsatisfiablePrerequisites_shouldNameTheBestEffect_orItsAbsence() {
        var nearMiss = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Submit order"),
                "prerequisite", value("Cart is open"), "bestEffect", value("Cart page is shown"), "score", value(0.81)));
        var noEffect = record(Map.of("id", value(OTHER_PROCEDURE_ID.toString()), "description", value("Pay"),
                "prerequisite", value("Card is saved"), "score", NULL));
        when(mockRepositorySupport.executeSingleReadQuery(argThat((String query) -> query.contains("other <> p")),
                eq(Map.of("threshold", 0.9))))
                .thenReturn(List.of(nearMiss, noEffect));

        var findings = repository.findUnsatisfiablePrerequisites(0.9);

        assertThat(findings).containsExactly(
                finding("Procedure[%s] 'Submit order': prerequisite 'Cart is open' (best effect 0.81 'Cart page is shown')"
                        .formatted(PROCEDURE_ID), procedureRef()),
                finding("Procedure[%s] 'Pay': prerequisite 'Card is saved' (no effect in the graph)".formatted(OTHER_PROCEDURE_ID),
                        new NodeRef(Procedure.LABEL, OTHER_PROCEDURE_ID)));
    }

    @Test
    void findDuplicateAtomicProcedures_shouldReportPairsOnTheSameElement_withBothProceduresReferenced() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save"),
                "otherId", value(OTHER_PROCEDURE_ID.toString()), "otherDescription", value("Press Save"), "label", value("Save"),
                "score", value(0.97)));
        when(mockRepositorySupport.executeSingleReadQuery(argThat((String query) -> query.contains("p.${PROP_ID} < other.${PROP_ID}")),
                eq(Map.of("threshold", 0.95))))
                .thenReturn(List.of(row));

        var findings = repository.findDuplicateAtomicProcedures(0.95);

        assertThat(findings).containsExactly(finding(
                "Procedure[%s] 'Click Save' ~ Procedure[%s] 'Press Save' on UiElement 'Save' (similarity=0.97)"
                        .formatted(PROCEDURE_ID, OTHER_PROCEDURE_ID),
                procedureRef(), new NodeRef(Procedure.LABEL, OTHER_PROCEDURE_ID)));
    }

    @Test
    void findVolatileUiElements_shouldRequireTwoChangesInsideTheWindow() {
        var row = record(Map.of("id", value(ELEMENT_ID.toString()), "label", value("Save"), "changes", value(2)));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(), eq(Map.of("windowDays", 30, "minChanges", 2))))
                .thenReturn(List.of(row));

        var findings = repository.findVolatileUiElements(30);

        assertThat(findings).containsExactly(finding("UiElement[%s] 'Save': 2 appearance changes in 30 days".formatted(ELEMENT_ID),
                elementRef()));
    }

    @Test
    void findUnresolvedFailureHints_shouldDescribeTheHintAndItsOccurrences() {
        var row = record(Map.of("id", value(PROCEDURE_ID.toString()), "description", value("Click Save"), "category", value("TIMING"),
                "symptom", value("Button not ready"), "occurrences", value(3)));
        when(mockRepositorySupport.executeSingleReadQuery(anyString())).thenReturn(List.of(row));

        var findings = repository.findUnresolvedFailureHints();

        assertThat(findings).containsExactly(finding(
                "Procedure[%s] 'Click Save': [TIMING] Button not ready (seen 3 times, no known resolution)".formatted(PROCEDURE_ID),
                procedureRef()));
    }

    @Test
    void findRecentExecutionAnomalies_shouldListOnlyTheKindsThatOccurred_andReferenceTheSubjectByItsLabel() {
        var row = record(Map.of("subjectLabel", value("UiElement"), "id", value(ELEMENT_ID.toString()), "label", value("Save"),
                "countsByKind", value(List.of(2, 0, 1)), "lastTestCase", NULL));
        when(mockRepositorySupport.executeSingleReadQuery(anyString(),
                eq(Map.of("windowDays", 30, "kinds", List.of("ELEMENT_LOCATION_FAILED", "STEP_FAILED", "TIMING_OUTLIER")))))
                .thenReturn(List.of(row));

        var findings = repository.findRecentExecutionAnomalies(30);

        assertThat(findings).containsExactly(finding(
                "UiElement[%s] 'Save': ELEMENT_LOCATION_FAILED=2, TIMING_OUTLIER=1, last test case 'unknown'".formatted(ELEMENT_ID),
                elementRef()));
    }

    @Test
    void deleteOrphanedExecutionAnomalies_shouldDeleteTheAnomaliesWithoutASubjectAndReturnTheirCount() {
        var row = record(Map.of("deletedCount", value(4)));
        when(mockRepositorySupport.executeSingleWriteQuery(argThat((String query) ->
                query.contains("NOT (a)-[:${REL_OBSERVED_ON}]->()") && query.contains("DELETE a"))))
                .thenReturn(List.of(row));

        assertThat(repository.deleteOrphanedExecutionAnomalies()).isEqualTo(4);
    }

    private static HealthFinding finding(String text, NodeRef... refs) {
        return new HealthFinding(text, List.of(refs));
    }

    private static NodeRef elementRef() {
        return new NodeRef(UiElement.LABEL, ELEMENT_ID);
    }

    private static NodeRef procedureRef() {
        return new NodeRef(Procedure.LABEL, PROCEDURE_ID);
    }

    private static Record record(Map<String, Value> values) {
        var record = mock(Record.class);
        values.forEach((alias, value) -> when(record.get(alias)).thenReturn(value));
        return record;
    }
}
