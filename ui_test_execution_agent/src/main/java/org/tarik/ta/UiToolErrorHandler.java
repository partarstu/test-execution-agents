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
package org.tarik.ta;

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.error.RetryState;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.exceptions.ElementLocationException;

import java.util.List;


import static org.tarik.ta.core.error.ErrorCategory.*;

class UiToolErrorHandler extends DefaultToolErrorHandler {
    private static final List<ErrorCategory> terminalErrors = List.of(NON_RETRYABLE_ERROR, TIMEOUT,
            TERMINATION_BY_USER);

    private final UiTestAgentConfig config;

    UiToolErrorHandler(RetryPolicy retryPolicy, UiTestAgentConfig config) {
        super(retryPolicy, config.isFullyUnattended());
        this.config = config;
    }

    @Override
    protected List<ErrorCategory> getTerminalErrors() {
        return terminalErrors;
    }

    @Override
    public ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context) {
        switch (error) {
            case ElementLocationException locationException -> {
                // In SUPERVISED mode, all element location failures are terminal — propagate
                // to executeAtomicStep for HITL retry.
                if (config.isSupervised()) {
                    throw locationException;
                }
                // In UNATTENDED mode, only visual grounding failures are retryable (agent can
                // retry locating on screen). DB-level failures and unknown errors are terminal.
                return switch (locationException.getStatus()) {
                    case ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED,
                            ELEMENT_NOT_FOUND_ON_SCREEN_VALIDATION_FAILED ->
                        handleRetryableToolError(locationException.getMessage());
                    default -> throw locationException;
                };
            }
            case ToolExecutionException toolExecutionException -> {
                if (config.isSupervised() || getTerminalErrors().contains(toolExecutionException.getErrorCategory())) {
                    throw toolExecutionException;
                } else {
                    return handleRetryableToolError(toolExecutionException.getMessage());
                }
            }
            case null, default -> throw new RuntimeException(error);
        }
    }
}
