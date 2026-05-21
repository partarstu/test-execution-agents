/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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
package org.tarik.ta.examples;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.MockitoAnnotations;

class MockitoExamples {

    @Mock
    private MyDependency mock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void stubbingBehavior() {
        // Return value
        when(mock.getValue()).thenReturn("result");

        // Return different values on consecutive calls
        when(mock.getValue())
                .thenReturn("first")
                .thenReturn("second");

        // Throw exception
        when(mock.riskyMethod()).thenThrow(new RuntimeException("error"));

        // With argument matchers
        when(mock.process(any())).thenReturn("processed");
        when(mock.process(eq("specific"))).thenReturn("specific-result");
        when(mock.process(anyString())).thenReturn("string-result");
    }

    @Test
    void verifyingInteractions() {
        mock.methodName("arg1", "arg2");

        // Verify method called
        verify(mock).methodName(any(), any());

        // Verify with arguments
        verify(mock).methodName("arg1", "arg2");

        // Verify call count
        verify(mock, times(1)).methodName(any(), any());
        verify(mock, never()).otherMethod();
    }

    @Test
    void argumentCaptors() {
        mock.methodName("expected");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mock).methodName(captor.capture());
        assertThat(captor.getValue()).isEqualTo("expected");
    }
}
