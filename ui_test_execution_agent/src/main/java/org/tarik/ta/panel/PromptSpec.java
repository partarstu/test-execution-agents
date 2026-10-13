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
import org.tarik.ta.dto.panel.prompt.PromptPayload;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Confirmation;
import org.tarik.ta.dto.panel.prompt.PromptPayload.FailureContextCapture;
import org.tarik.ta.dto.panel.prompt.PromptPayload.Message;
import org.tarik.ta.dto.panel.prompt.PromptPayload.NextAction;
import org.tarik.ta.dto.panel.prompt.PromptPayload.ProcedureEditor;
import org.tarik.ta.dto.panel.prompt.PromptPayload.StartStep;
import org.tarik.ta.dto.panel.prompt.PromptPayload.VerificationFailure;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Everything one prompt needs: what it shows, the answer it accepts and how that answer is checked, the default applied at its
 * deadline, the countdown that sets the deadline instead of the prompt upper bound, its actions and its images.
 */
record PromptSpec<A>(@NotNull PromptPayload payload, @NotNull Class<A> answerType, @Nullable A defaultAnswer,
                     @Nullable Duration countdown, @NotNull Consumer<A> check, @NotNull PromptActions actions,
                     @NotNull PromptImages images) {

    static <A> PromptSpec<A> of(@NotNull PromptPayload payload, @NotNull Class<A> answerType, @NotNull PromptImages images) {
        return new PromptSpec<>(payload, answerType, null, null, _ -> {
        }, PromptActions.NONE, images);
    }

    PromptSpec<A> withDefault(@NotNull A answer) {
        return new PromptSpec<>(payload, answerType, answer, countdown, check, actions, images);
    }

    PromptSpec<A> withCountdown(@NotNull Duration duration) {
        return new PromptSpec<>(payload, answerType, defaultAnswer, duration, check, actions, images);
    }

    PromptSpec<A> withCheck(@NotNull Consumer<A> answerCheck) {
        return new PromptSpec<>(payload, answerType, defaultAnswer, countdown, answerCheck, actions, images);
    }

    PromptSpec<A> withActions(@NotNull PromptActions promptActions) {
        return new PromptSpec<>(payload, answerType, defaultAnswer, countdown, check, promptActions, images);
    }

    /** The name the browser selects the prompt's view by. */
    String type() {
        return switch (payload) {
            case StartStep _ -> "start-step";
            case Confirmation _ -> "confirmation";
            case NextAction _ -> "next-action";
            case Message _ -> "message";
            case VerificationFailure _ -> "verification-failure";
            case FailureContextCapture _ -> "failure-context";
            case ProcedureEditor _ -> "procedure-editor";
        };
    }
}
