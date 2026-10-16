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
package org.tarik.ta.knowledge;

import org.tarik.ta.dto.ItemKind;
import org.tarik.ta.dto.KnowledgeTestCase;
import org.tarik.ta.dto.TestCaseItem;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.SubtreeEntry;
import org.tarik.ta.knowledge_graph.repository.ProcedureRepository.TargetedUiElement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds the procedures, subtree rows and test cases the knowledge agent's tests share.
 */
public final class ProcedureFixtures {
    private static final Instant RECORDED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private ProcedureFixtures() {
    }

    public static Procedure atomic(String description) {
        return new Procedure(UUID.randomUUID(), description, List.of("username"), "Field is filled", true, false, false,
                List.of("login page open"), List.of("username entered"), List.of(), "Uses the main form", RECORDED_AT, RECORDED_AT,
                null, null);
    }

    public static Procedure composite(String description) {
        return new Procedure(UUID.randomUUID(), description, List.of(), "Done", false, true, false, List.of(), List.of("logged in"),
                List.of("logged out"), null, RECORDED_AT, RECORDED_AT, null, null);
    }

    public static SubtreeEntry root(Procedure procedure, int childCount) {
        return new SubtreeEntry(procedure, null, 0, childCount, 4, null);
    }

    public static SubtreeEntry child(Procedure procedure, Procedure parent, int sequence, int childCount) {
        var element = procedure.isAtomic() ? new TargetedUiElement("Username field", "Text input", "Below the logo", "Login form") : null;
        return new SubtreeEntry(procedure, parent.id(), sequence, childCount, 2, element);
    }

    public static KnowledgeTestCase testCase() {
        return new KnowledgeTestCase("Create an order", List.of(
                new TestCaseItem(ItemKind.PRECONDITION, 1, "The user is logged in", List.of(), ""),
                new TestCaseItem(ItemKind.PRECONDITION, 2, "The cart is empty", List.of(), ""),
                new TestCaseItem(ItemKind.STEP, 1, "Open the catalog", List.of(), "The catalog is shown"),
                new TestCaseItem(ItemKind.STEP, 2, "Add the product to the cart", List.of("product=Lamp"), "The cart holds one product"),
                new TestCaseItem(ItemKind.STEP, 3, "Check out", List.of(), "The order is confirmed")));
    }
}
