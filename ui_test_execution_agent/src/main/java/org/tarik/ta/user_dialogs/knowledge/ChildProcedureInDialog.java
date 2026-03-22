/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.user_dialogs.knowledge;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.knowledge_graph.model.node.Procedure;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a child step in the procedure dialog's list. The sealed hierarchy makes the
 * linked-vs-new distinction compiler-enforced, replacing the previous null-check on existingProcedureId.
 */
public sealed interface ChildProcedureInDialog {
    String description();
    boolean isAtomic();
    boolean isPrecondition();
    List<String> effects();
    @Nullable BufferedImage elementScreenshot();

    /**
     * A child step linked to an already-persisted procedure.
     * Editing opens the full dialog for the linked Procedure; on save the ingestion service updates it.
     */
    public record Linked(Procedure procedure, @Nullable BufferedImage elementScreenshot) implements ChildProcedureInDialog {
        public String description() { return procedure.description(); }
        public boolean isAtomic() { return procedure.isAtomic(); }
        public boolean isPrecondition() { return procedure.isPrecondition(); }
        public List<String> effects() { return procedure.effects(); }
    }

    /**
     * A new child step not yet persisted. {@code needsSave} is true until the user edits and saves it.
     */
    public record New(
            Procedure procedure,
            @Nullable UUID targetUiElementId,
            @Nullable BufferedImage elementScreenshot,
            boolean needsSave,
            List<ChildProcedureInDialog> children
    ) implements ChildProcedureInDialog {
        public New {
            children = children != null ? List.copyOf(children) : List.of();
        }
        public String description() { return procedure.description(); }
        public boolean isAtomic() { return procedure.isAtomic(); }
        public boolean isPrecondition() { return procedure.isPrecondition(); }
        public List<String> effects() { return procedure.effects(); }
    }

    /**
     * Converts a {@link ChildProcedureInDialog} to an {@link IngestionNode} for persistence.
     */
    static IngestionNode toIngestionNode(ChildProcedureInDialog step) {
        return switch (step) {
            case ChildProcedureInDialog.Linked linked -> new IngestionNode.ExistingReference(linked.procedure().id());
            case ChildProcedureInDialog.New newStep -> {
                var childNodes = newStep.children().stream().map(ChildProcedureInDialog::toIngestionNode).toList();
                yield new IngestionNode.NewProcedure(newStep.procedure(), newStep.targetUiElementId(), childNodes);
            }
        };
    }

    /**
     * Converts an {@link IngestionNode.NewProcedure} back to a {@link ChildProcedureInDialog.New} for the
     * in-memory child list when no ingestion service is available (e.g. tests or standalone mode).
     */
    static ChildProcedureInDialog.New fromNewProcedure(IngestionNode.NewProcedure np, @Nullable BufferedImage screenshot) {
        List<ChildProcedureInDialog> children = np.children().stream()
                .filter(c -> c instanceof IngestionNode.NewProcedure)
                .map(c -> (ChildProcedureInDialog) fromNewProcedure((IngestionNode.NewProcedure) c, null))
                .toList();
        return new ChildProcedureInDialog.New(np.procedure(), np.targetUiElementId(), screenshot, false, children);
    }

    /**
     * Recursively finds the first composite step in the tree that has no children.
     * Linked steps are skipped — their children live in the DB, not in memory.
     */
    static Optional<String> findInvalidComposite(List<ChildProcedureInDialog> steps) {
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
