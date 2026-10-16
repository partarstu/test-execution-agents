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
package org.tarik.ta.a2a;

import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.KnowledgeAgentConfig;
import org.tarik.ta.dto.ItemKind;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.TestCaseItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRequestReaderTest {
    private static final int MAX_ITEMS = 3;
    private static final int MAX_TEXT_LENGTH = 30;

    @Mock
    private KnowledgeAgentConfig config;

    private KnowledgeRequestReader reader;

    @BeforeEach
    void setUp() {
        when(config.getMaxRequestItems()).thenReturn(MAX_ITEMS);
        when(config.getMaxRequestTextLength()).thenReturn(MAX_TEXT_LENGTH);
        reader = new KnowledgeRequestReader(config);
    }

    @Test
    void read_shouldNumberThePreconditionsAndStepsInTheirOrder() {
        var data = Map.<String, Object>of("name", " Create an order ", "preconditions", List.of("Logged in"), "steps", List.of(
                Map.of("action", "Open the catalog", "expected_results", "Catalog shown"),
                Map.of("action", "Add the lamp", "test_data", List.of("product=Lamp"))));

        var testCase = reader.read(message(new DataPart(data)));

        assertThat(testCase).isEqualTo(new KnowledgeTestCase("Create an order", List.of(
                new TestCaseItem(ItemKind.PRECONDITION, 1, "Logged in", List.of(), ""),
                new TestCaseItem(ItemKind.STEP, 1, "Open the catalog", List.of(), "Catalog shown"),
                new TestCaseItem(ItemKind.STEP, 2, "Add the lamp", List.of("product=Lamp"), ""))));
    }

    @Test
    void read_shouldReject_whenTheMessageCarriesNoDataPart() {
        assertThatThrownBy(() -> reader.read(message(new TextPart("Create an order"))))
                .isInstanceOf(InvalidKnowledgeRequestException.class)
                .hasMessageContaining("exactly one data part");
    }

    @Test
    void read_shouldReject_whenTheMessageCarriesSeveralDataParts() {
        var data = Map.<String, Object>of("name", "Order", "steps", List.of(Map.of("action", "Open")));

        assertThatThrownBy(() -> reader.read(message(new DataPart(data), new DataPart(data))))
                .isInstanceOf(InvalidKnowledgeRequestException.class)
                .hasMessageContaining("carries 2");
    }

    @Test
    void read_shouldReject_whenThereIsNoMessage() {
        assertThatThrownBy(() -> reader.read(null)).isInstanceOf(InvalidKnowledgeRequestException.class);
    }

    @Test
    void read_shouldReject_whenTheDataIsNotATestCase() {
        assertThatThrownBy(() -> reader.read(message(new DataPart(Map.of("name", "Order", "command", "drop")))))
                .isInstanceOf(InvalidKnowledgeRequestException.class)
                .hasMessageContaining("does not hold a test case");
    }

    @Test
    void read_shouldReject_whenTheNameIsBlank() {
        assertRejected(Map.of("name", " ", "steps", List.of(Map.of("action", "Open"))), "The test case name is blank");
    }

    @Test
    void read_shouldReject_whenTheTestCaseHasNoItems() {
        assertRejected(Map.of("name", "Order"), "neither preconditions nor test steps");
    }

    @Test
    void read_shouldReject_whenTheTestCaseHasMoreItemsThanAllowed() {
        assertRejected(Map.of("name", "Order", "preconditions", List.of("A", "B"), "steps", List.of(Map.of("action", "C"),
                Map.of("action", "D"))), "4 preconditions and test steps, more than the 3 allowed");
    }

    @Test
    void read_shouldReject_whenAPreconditionIsBlank() {
        assertRejected(Map.of("name", "Order", "preconditions", List.of("")), "Precondition 1 is blank");
    }

    @Test
    void read_shouldReject_whenAStepHasNoAction() {
        var stepWithoutAction = new HashMap<String, Object>();
        stepWithoutAction.put("action", null);

        assertRejected(Map.of("name", "Order", "steps", List.of(stepWithoutAction)), "The action of test step 1 is blank");
    }

    @Test
    void read_shouldReject_whenATestDataEntryIsBlank() {
        assertRejected(Map.of("name", "Order", "steps", List.of(Map.of("action", "Open", "test_data", List.of(" ")))),
                "A test data entry of test step 1 is blank");
    }

    @Test
    void read_shouldReject_whenATextIsLongerThanAllowed() {
        assertRejected(Map.of("name", "x".repeat(MAX_TEXT_LENGTH + 1), "steps", List.of(Map.of("action", "Open"))),
                "The test case name has 31 characters, more than the 30 allowed");
    }

    @Test
    void read_shouldReject_whenTheExpectedResultsAreLongerThanAllowed() {
        assertRejected(Map.of("name", "Order", "steps", List.of(Map.of("action", "Open", "expected_results", "y".repeat(31)))),
                "The expected results of test step 1 has 31 characters");
    }

    private void assertRejected(Map<String, Object> data, String reason) {
        assertThatThrownBy(() -> reader.read(message(new DataPart(data))))
                .isInstanceOf(InvalidKnowledgeRequestException.class)
                .hasMessageContaining(reason);
    }

    private static Message message(Part<?>... parts) {
        return new Message(Message.Role.ROLE_USER, List.of(parts), "message-1", null, null, null, null, null);
    }
}
