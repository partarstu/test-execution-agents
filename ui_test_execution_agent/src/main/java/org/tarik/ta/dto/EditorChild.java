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
package org.tarik.ta.dto;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A child step of the procedure being edited, either linked to a persisted procedure or new and not yet persisted. */
public sealed interface EditorChild {
    String description();
    boolean isAtomic();
    boolean isPrecondition();
    List<String> effects();
    List<String> negatedEffects();
    @Nullable BufferedImage elementScreenshot();

    /**
     * A child step linked to an already-persisted procedure.
     * Editing opens the linked Procedure in the procedure editor; on save the ingestion service updates it.
     */
    public record Linked(Procedure procedure, @Nullable BufferedImage elementScreenshot) implements EditorChild {
        public String description() { return procedure.description(); }
        public boolean isAtomic() { return procedure.isAtomic(); }
        public boolean isPrecondition() { return procedure.isPrecondition(); }
        public List<String> effects() { return procedure.effects(); }
        public List<String> negatedEffects() { return procedure.negatedEffects(); }
    }

    /**
     * A new child step not yet persisted. {@code needsSave} is true until the user edits and saves it.
     */
    public record New(
            Procedure procedure,
            @Nullable UUID targetUiElementId,
            @Nullable BufferedImage elementScreenshot,
            boolean needsSave,
            List<EditorChild> children
    ) implements EditorChild {
        public New {
            children = children != null ? List.copyOf(children) : List.of();
        }
        public String description() { return procedure.description(); }
        public boolean isAtomic() { return procedure.isAtomic(); }
        public boolean isPrecondition() { return procedure.isPrecondition(); }
        public List<String> effects() { return procedure.effects(); }
        public List<String> negatedEffects() { return procedure.negatedEffects(); }
    }

    static IngestionNode toIngestionNode(EditorChild step) {
        return switch (step) {
            case EditorChild.Linked linked -> new IngestionNode.ExistingReference(linked.procedure().id());
            case EditorChild.New newStep -> {
                var childNodes = newStep.children().stream().map(EditorChild::toIngestionNode).toList();
                yield new IngestionNode.NewProcedure(newStep.procedure(), newStep.targetUiElementId(), childNodes);
            }
        };
    }

    /**
     * Converts an {@link IngestionNode.NewProcedure} back to a {@link EditorChild.New} for the
     * in-memory child list when no ingestion service is available (e.g. tests or standalone mode).
     */
    static EditorChild.New fromNewProcedure(IngestionNode.NewProcedure np, @Nullable BufferedImage screenshot) {
        List<EditorChild> children = np.children().stream()
                .filter(c -> c instanceof IngestionNode.NewProcedure)
                .map(c -> (EditorChild) fromNewProcedure((IngestionNode.NewProcedure) c, null))
                .toList();
        return new EditorChild.New(np.procedure(), np.targetUiElementId(), screenshot, false, children);
    }

    /** Finds the first new composite step in the tree without children, skipping linked steps, whose children live in the DB. */
    static Optional<String> findInvalidComposite(List<EditorChild> steps) {
        for (var step : steps) {
            if (!step.isAtomic() && step instanceof New newStep) {
                if (newStep.children().isEmpty()) {
                    return Optional.of(step.description());
                }
                var nested = findInvalidComposite(newStep.children());
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }
}
