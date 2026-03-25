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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.exceptions.ElementLocationException;
import org.tarik.ta.exceptions.ElementLocationException.ElementLocationStatus;

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
}
