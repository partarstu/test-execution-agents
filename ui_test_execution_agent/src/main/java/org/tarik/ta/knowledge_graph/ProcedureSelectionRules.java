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
package org.tarik.ta.knowledge_graph;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService.ScoredProcedure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.dto.ProcedureExecutionConfirmationResult.proceed;

/** Decisions shared by every operator prompt that picks a procedure or the next action for an execution item. */
public final class ProcedureSelectionRules {
    private ProcedureSelectionRules() {
    }

    public static List<ScoredProcedure> visibleResults(List<ScoredProcedure> found, Set<UUID> excludedIds,
                                                       boolean preconditionContext, boolean showAllTypes) {
        var notExcluded = found.stream()
                .filter(s -> !excludedIds.contains(s.procedure().id()))
                .toList();
        return filterByType(notExcluded, preconditionContext, showAllTypes);
    }

    /**
     * Keeps only the procedures of the type the lookup was opened for unless the user asked for all types, since preconditions
     * and test steps are never interchangeable.
     */
    public static List<ScoredProcedure> filterByType(List<ScoredProcedure> candidates, boolean preconditionContext,
                                                     boolean showAllTypes) {
        if (showAllTypes) {
            return candidates;
        }
        return candidates.stream()
                .filter(s -> s.procedure().isPrecondition() == preconditionContext)
                .toList();
    }

    /** Accepts a pick of the executed item's type directly and asks {@code crossTypeConfirmation} only for one of the other type. */
    public static boolean isSelectionAccepted(Procedure procedure, boolean preconditionContext, BooleanSupplier crossTypeConfirmation) {
        return !isCrossTypeSelection(procedure, preconditionContext) || crossTypeConfirmation.getAsBoolean();
    }

    /**
     * Whether the picked procedure is of the other type than the item it would be executed for, a legitimate but usually mistaken
     * pick that is therefore confirmed.
     */
    public static boolean isCrossTypeSelection(Procedure procedure, boolean preconditionContext) {
        return procedure.isPrecondition() != preconditionContext;
    }

    /**
     * Offers Retry, or after a failure or halt the way on from a halt followed by the reruns, and then Create, Browse and Terminate
     * ({@code CANCEL}).
     */
    public static List<SelectionAction> offeredActions(@Nullable RerunOptions rerunOptions) {
        List<SelectionAction> actions = new ArrayList<>();
        if (rerunOptions == null) {
            actions.add(SelectionAction.RETRY);
        } else {
            switch (rerunOptions.halt()) {
                case BEFORE_EXECUTION -> actions.add(SelectionAction.PROCEED);
                case AFTER_SUCCESS -> actions.add(SelectionAction.CONTINUE);
                case NONE -> {
                }
            }
            actions.add(SelectionAction.RERUN_TEST_STEP);
            if (rerunOptions.atomicRerunAvailable()) {
                actions.add(SelectionAction.RERUN_ATOMIC_PROCEDURE);
            }
        }
        actions.addAll(List.of(SelectionAction.CREATE, SelectionAction.BROWSE, SelectionAction.CANCEL));
        return List.copyOf(actions);
    }

    public static Optional<UserSelectionResult> toSelectionResult(SelectionAction action, @Nullable Procedure selectedProcedure) {
        if (action == SelectionAction.CANCEL) {
            return Optional.empty();
        }
        if (action == SelectionAction.BROWSE) {
            var procedure = requireNonNull(selectedProcedure, "A browse selection carries the selected procedure");
            return Optional.of(new UserSelectionResult(action, procedure.id(), procedure));
        }
        return Optional.of(new UserSelectionResult(action, null, null));
    }

    /** The decision taken when the operator lets the confirmation countdown elapse or closes it without choosing. */
    public static ProcedureExecutionConfirmationResult defaultDecision() {
        return proceed();
    }
}
