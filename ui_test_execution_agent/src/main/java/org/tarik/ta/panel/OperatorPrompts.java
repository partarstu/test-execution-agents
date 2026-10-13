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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.dto.TestStep;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.dto.ExecutionItemContext;
import org.tarik.ta.dto.IngestionNode;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.ProcedureDialogOutcome;
import org.tarik.ta.dto.ProcedureExecutionConfirmationResult;
import org.tarik.ta.dto.RerunOptions;
import org.tarik.ta.dto.UserSelectionResult;
import org.tarik.ta.knowledge_graph.model.node.FailureContext;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.service.KnowledgeService;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Every interaction of a run with the operator; each method blocks the run until the operator answers or its default applies. */
public interface OperatorPrompts {

    /** Returns the 0-based index of the test step the run starts from, or a negative value when the operator aborts the run. */
    int selectStartingStep(@NotNull List<TestStep> testSteps);

    @NotNull ProcedureExecutionConfirmationResult confirmExecution(@NotNull String procedureDescription,
                                                                   @NotNull List<String> ancestorDescriptions,
                                                                   @NotNull ExecutionItemContext itemContext,
                                                                   boolean beforeExecution);

    /**
     * Asks how to resolve an execution item no procedure was found for, browsing candidates scored by {@code effectNodeIds} and
     * re-ranked by {@code recentParentIds}; empty when the operator terminates.
     */
    @NotNull Optional<UserSelectionResult> chooseProcedure(@NotNull String message, @NotNull String itemDescription,
                                                           @NotNull KnowledgeService knowledgeService,
                                                           @NotNull Set<UUID> effectNodeIds, @NotNull Set<UUID> recentParentIds,
                                                           boolean isPrecondition);

    /** Asks for the next action after a failure or halt, offering the reruns of {@code rerunOptions}; empty on termination. */
    @NotNull Optional<UserSelectionResult> chooseNextAction(@NotNull String message, @NotNull String itemDescription,
                                                            @NotNull KnowledgeService knowledgeService,
                                                            @NotNull RerunOptions rerunOptions);

    void showMessage(@NotNull String title, @NotNull String message, @Nullable BufferedImage screenshot, @NotNull PopupType type);

    void showVerificationFailure(@NotNull String verificationDescription, @NotNull String failureReason,
                                 @Nullable BufferedImage screenshot);

    /** Returns the operator's account of a failure, or empty when the operator dismissed it. */
    @NotNull Optional<FailureContext> captureFailureContext(@NotNull ErrorCategory preSelectedCategory, @NotNull String preFilledSymptom);

    /**
     * Opens the procedure editor for a new procedure, pre-filled with AI suggestions, whose prerequisites may be picked from {@code
     * precedingEffects}; empty when the operator cancels.
     */
    @NotNull Optional<IngestionNode> createProcedure(@NotNull ProcedureEditorSession session, @NotNull List<String> precedingEffects);

    /** Opens the procedure editor for a persisted procedure, from which the operator may move up to edit one of its parents. */
    @NotNull ProcedureDialogOutcome editProcedure(@NotNull ProcedureEditorSession session, @NotNull Procedure procedure,
                                                  @NotNull Supplier<List<String>> precedingEffects);

    /** Shows what the agent is doing, e.g. while a tool works, without asking anything. */
    void showStatus(@NotNull String message);

    void clearStatus();
}
