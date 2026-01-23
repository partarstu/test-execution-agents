/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.tarik.ta.agents.UiElementExtendedDescriptionAgent;
import org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult;
import org.tarik.ta.model.UiTestExecutionContext;
import org.tarik.ta.rag.UiElementRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.tarik.ta.dto.SemiAttendedModeElementLocationConfirmationResult.proceed;

class SemiAttendedModeCommonUserInteractionToolsTest {

    @Mock
    private UiElementRetriever uiElementRetriever;
    @Mock
    private UiTestExecutionContext uiTestExecutionContext;
    @Mock
    private UiElementExtendedDescriptionAgent uiElementExtendedDescriptionAgent;

    private SemiAttendedModeCommonUserInteractionTools tools;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tools = spy(new SemiAttendedModeCommonUserInteractionTools(uiElementRetriever, uiTestExecutionContext, uiElementExtendedDescriptionAgent));
    }

    @Test
    void confirmElementSelection_ShouldReturnResultFromPopup() {
        // Given
        String elementDescription = "Test Description";
        String elementName = "Test Element";
        String action = "Click";
        SemiAttendedModeElementLocationConfirmationResult expectedResult = proceed();

        // Mock the protected method to avoid UI interaction
        doReturn(expectedResult).when(tools).displayConfirmationPopup(anyString(), anyString(), anyString());

        // When
        SemiAttendedModeElementLocationConfirmationResult result = tools.confirmElementSelection(elementDescription, elementName, action);

        // Then
        assertThat(result).isEqualTo(expectedResult);
    }
}
