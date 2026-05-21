/*
 * agent-core - ${project.description}
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
package org.tarik.ta.core.model;

import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.ErrorCategory;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DefaultToolErrorHandlerTest {

    private DefaultToolErrorHandler handler;
    private RetryPolicy retryPolicy;

    @BeforeEach
    void setUp() {
        retryPolicy = new RetryPolicy(3, 100, 1000);
        handler = new DefaultToolErrorHandler(retryPolicy);
    }

    @Test
    void handle_shouldThrowTerminalError() {
        ToolExecutionException terminalError = new ToolExecutionException("Terminal", ErrorCategory.NON_RETRYABLE_ERROR);
        ToolErrorContext context = mock(ToolErrorContext.class);

        assertThatThrownBy(() -> handler.handle(terminalError, context))
                .isSameAs(terminalError);
    }

    @Test
    void handle_shouldReturnRetryableErrorResult() {
        ToolExecutionException retryableError = new ToolExecutionException("Retryable", ErrorCategory.TRANSIENT_TOOL_ERROR);
        ToolErrorContext context = mock(ToolErrorContext.class);

        ToolErrorHandlerResult result = handler.handle(retryableError, context);

        assertThat(result.text()).isEqualTo("Retryable");
    }

    @Test
    void handle_shouldWrapGenericException() {
        RuntimeException genericError = new RuntimeException("Generic");
        ToolErrorContext context = mock(ToolErrorContext.class);

        assertThatThrownBy(() -> handler.handle(genericError, context))
                .isInstanceOf(RuntimeException.class)
                .hasCause(genericError);
    }

    @Test
    void handle_shouldThrowOnMaxRetriesReached() {
        ToolExecutionException retryableError = new ToolExecutionException("Retryable", ErrorCategory.TRANSIENT_TOOL_ERROR);
        ToolErrorContext context = mock(ToolErrorContext.class);

        // Perform 3 retries (max is 3, so 4th call should throw)
        handler.handle(retryableError, context);
        handler.handle(retryableError, context);
        handler.handle(retryableError, context);

        assertThatThrownBy(() -> handler.handle(retryableError, context))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Retry policy exceeded because of max retries")
                .extracting(e -> ((ToolExecutionException) e).getErrorCategory())
                .isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    void handle_shouldThrowOnTimeout() throws InterruptedException {
        // Small timeout for testing
        RetryPolicy shortTimeoutPolicy = new RetryPolicy(10, 10, 50);
        handler = new DefaultToolErrorHandler(shortTimeoutPolicy);

        ToolExecutionException retryableError = new ToolExecutionException("Retryable", ErrorCategory.TRANSIENT_TOOL_ERROR);
        ToolErrorContext context = mock(ToolErrorContext.class);

        handler.handle(retryableError, context);
        Thread.sleep(100); // Wait for timeout

        assertThatThrownBy(() -> handler.handle(retryableError, context))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Retry policy exceeded because of timeout")
                .extracting(e -> ((ToolExecutionException) e).getErrorCategory())
                .isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    void reset_shouldClearRetryState() {
        ToolExecutionException retryableError = new ToolExecutionException("Retryable", ErrorCategory.TRANSIENT_TOOL_ERROR);
        ToolErrorContext context = mock(ToolErrorContext.class);

        handler.handle(retryableError, context);
        handler.reset();
        
        // After reset, attempts should start from 1 again
        ToolErrorHandlerResult result = handler.handle(retryableError, context);
        assertThat(result.text()).isEqualTo("Retryable");
    }

    @Test
    void equalsAndHashCode_shouldWork() {
        DefaultToolErrorHandler handler2 = new DefaultToolErrorHandler(retryPolicy);
        DefaultToolErrorHandler handler3 = new DefaultToolErrorHandler(new RetryPolicy(1, 1, 1));

        assertThat(handler).isEqualTo(handler2);
        assertThat(handler).hasSameHashCodeAs(handler2);
        assertThat(handler).isNotEqualTo(handler3);
        assertThat(handler).isNotEqualTo(null);
        assertThat(handler).isNotEqualTo("string");
    }

    @Test
    void toString_shouldWork() {
        assertThat(handler.toString()).contains("DefaultToolErrorHandler");
        assertThat(handler.toString()).contains("retryPolicy");
    }
}
