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
package org.tarik.ta.core.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MyClassTest {

    @Mock
    private DependencyClass mockDependency;

    private MyClass underTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        underTest = new MyClass(mockDependency);
    }

    @Test
    void methodName_shouldDoSomething_whenCondition() {
        // Given
        when(mockDependency.getData()).thenReturn("test-data");

        // When
        String result = underTest.processData();

        // Then
        assertThat(result).isEqualTo("processed: test-data");
        verify(mockDependency).getData();
    }
}
