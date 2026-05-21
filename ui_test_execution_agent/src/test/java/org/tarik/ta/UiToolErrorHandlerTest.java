/*
 * ui-test-execution-agent - ${project.description}
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UiToolErrorHandlerTest {

    private UiToolErrorHandler uiToolErrorHandler;

    @Mock
    private ToolErrorContext mockContext;

    @Mock
    private UiTestAgentConfig configMock;

    @BeforeEach
    void setUp() {
        lenient().when(configMock.isFullyUnattended()).thenReturn(true);
        // Use a real RetryPolicy to avoid mocking issues
        uiToolErrorHandler = new UiToolErrorHandler(new RetryPolicy(3, 100, 1000), configMock);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("handle should return retry result for retryable ElementLocationException in unattended mode")
    void handle_shouldReturnRetry_whenRetryableExceptionAndUnattended() {
        lenient().when(configMock.isSupervised()).thenReturn(false);
        ElementLocationException ex = new ElementLocationException(
                "not found",
                ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED
        );
        
        ToolErrorHandlerResult result = uiToolErrorHandler.handle(ex, mockContext);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("handle should throw exception for ElementLocationException in supervised mode")
    void handle_shouldThrow_whenSupervised() {
        lenient().when(configMock.isSupervised()).thenReturn(true);
        ElementLocationException ex = new ElementLocationException(
                "not found",
                ElementLocationStatus.ELEMENT_NOT_FOUND_ON_SCREEN_VISUAL_AND_ALGORITHMIC_FAILED
        );

        assertThatThrownBy(() -> uiToolErrorHandler.handle(ex, mockContext))
                .isSameAs(ex);
    }

    @Test
    @DisplayName("handle should throw transient ToolExecutionException immediately in supervised mode")
    void handle_shouldThrowImmediately_whenTransientToolExceptionAndSupervised() {
        lenient().when(configMock.isSupervised()).thenReturn(true);
        ToolExecutionException ex = new ToolExecutionException("tool failed", TRANSIENT_TOOL_ERROR);

        assertThatThrownBy(() -> uiToolErrorHandler.handle(ex, mockContext))
                .isSameAs(ex);
    }

    @Test
    @DisplayName("handle should pass transient ToolExecutionException back to agent in unattended mode")
    void handle_shouldPassToAgent_whenTransientToolExceptionAndUnattended() {
        lenient().when(configMock.isSupervised()).thenReturn(false);
        ToolExecutionException ex = new ToolExecutionException("tool failed", TRANSIENT_TOOL_ERROR);

        ToolErrorHandlerResult result = uiToolErrorHandler.handle(ex, mockContext);

        assertThat(result).isNotNull();
    }
}
