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
                if (getTerminalErrors().contains(toolExecutionException.getErrorCategory())) {
                    throw toolExecutionException;
                } else {
                    return handleRetryableToolError(toolExecutionException.getMessage());
                }
            }
            case null, default -> throw new RuntimeException(error);
        }
    }
}
