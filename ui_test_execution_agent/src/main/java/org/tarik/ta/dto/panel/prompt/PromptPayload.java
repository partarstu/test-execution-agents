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
package org.tarik.ta.dto.panel.prompt;

import org.jetbrains.annotations.Nullable;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.dto.PopupType;
import org.tarik.ta.dto.UserSelectionResult.SelectionAction;

import java.util.List;
import java.util.UUID;

/**
 * What a prompt shows the operator, one record per prompt type, each with the answer record the browser sends back. The type
 * travels next to the payload, so neither is serialized with a type field.
 */
public sealed interface PromptPayload {

    /** The answer of the prompts that only inform the operator. */
    record Acknowledgement() {
    }

    record ItemView(String description, List<String> testData, boolean precondition) {
        public ItemView {
            testData = List.copyOf(testData);
        }
    }

    record StartStep(List<StepRow> steps) implements PromptPayload {
        public StartStep {
            steps = List.copyOf(steps);
        }

        public record StepRow(String description, List<String> testData, @Nullable String expectedResults) {
            public StepRow {
                testData = List.copyOf(testData);
            }
        }

        /** {@code stepIndex} is the 0-based step to start from, or {@link #ABORT}. */
        public record Answer(int stepIndex) {
            public static final int ABORT = -1;
        }
    }

    record Confirmation(String procedureDescription, List<String> ancestors, ItemView item, boolean beforeExecution)
            implements PromptPayload {
        public Confirmation {
            ancestors = List.copyOf(ancestors);
        }
    }

    record NextAction(String message, String itemDescription, boolean precondition, List<SelectionAction> actions)
            implements PromptPayload {
        public NextAction {
            actions = List.copyOf(actions);
        }

        /** {@code procedureId} is the browsed procedure, set only for {@link SelectionAction#BROWSE}. */
        public record Answer(SelectionAction action, @Nullable UUID procedureId) {
        }
    }

    record Message(String title, String message, PopupType severity, @Nullable String screenshotId) implements PromptPayload {
    }

    record VerificationFailure(String verification, String reason, @Nullable String screenshotId) implements PromptPayload {
    }

    record FailureContextCapture(ErrorCategory category, String symptom, List<ErrorCategory> categories) implements PromptPayload {
        public FailureContextCapture {
            categories = List.copyOf(categories);
        }

        public record Answer(Choice choice, @Nullable ErrorCategory category, @Nullable String symptom, @Nullable String resolution) {
        }

        public enum Choice {
            SUBMIT,
            DISMISS
        }
    }

    /** {@code create} is true for a new procedure, whose editor offers AI suggestions and no deletion. */
    record ProcedureEditor(boolean create, ItemView item, EditorNode node) implements PromptPayload {

        /** {@code node} is the saved procedure for {@code SAVE}; {@code procedureId} the deleted one for {@code DELETE}. */
        public record Answer(Outcome outcome, @Nullable SavedNode node, @Nullable UUID procedureId) {
        }

        public enum Outcome {
            SAVE,
            CANCEL,
            DELETE
        }
    }
}
