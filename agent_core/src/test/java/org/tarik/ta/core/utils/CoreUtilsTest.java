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
package org.tarik.ta.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tarik.ta.core.utils.CoreUtils.isBlank;
import static org.tarik.ta.core.utils.CoreUtils.isNotBlank;
import static org.tarik.ta.core.utils.CoreUtils.parseStringAsDouble;
import static org.tarik.ta.core.utils.CoreUtils.parseStringAsInteger;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
@DisplayName("CoreUtils Tests")
class CoreUtilsTest {

    @Test
    @DisplayName("parseStringAsInteger: Should parse valid integer string")
    void parseStringAsIntegerValid() {
        // Given
        String intStr = " 123 ";

        // When
        Optional<Integer> result = parseStringAsInteger(intStr);

        // Then
        assertTrue(result.isPresent());
        assertEquals(123, result.get());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for invalid string")
    void parseStringAsIntegerInvalid() {
        // Given
        String invalidStr = "abc";

        // When
        Optional<Integer> result = parseStringAsInteger(invalidStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for null")
    void parseStringAsIntegerNull() {
        // When
        Optional<Integer> result = parseStringAsInteger(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for blank string")
    void parseStringAsIntegerBlank() {
        // Given
        String blankStr = "   ";

        // When
        Optional<Integer> result = parseStringAsInteger(blankStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should parse valid double string")
    void parseStringAsDoubleValid() {
        // Given
        String doubleStr = " 123.45 ";

        // When
        Optional<Double> result = parseStringAsDouble(doubleStr);

        // Then
        assertTrue(result.isPresent());
        assertEquals(123.45, result.get());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for invalid string")
    void parseStringAsDoubleInvalid() {
        // Given
        String invalidStr = "abc.def";

        // When
        Optional<Double> result = parseStringAsDouble(invalidStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for null")
    void parseStringAsDoubleNull() {
        // When
        Optional<Double> result = parseStringAsDouble(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for blank string")
    void parseStringAsDoubleBlank() {
        // Given
        String blankStr = "   ";

        // When
        Optional<Double> result = parseStringAsDouble(blankStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("isBlank: Should return true for null")
    void isBlankNull() {
        assertTrue(isBlank(null));
    }

    @Test
    @DisplayName("isBlank: Should return true for empty string")
    void isBlankEmpty() {
        assertTrue(isBlank(""));
    }

    @Test
    @DisplayName("isBlank: Should return true for blank string")
    void isBlankBlank() {
        assertTrue(isBlank("   "));
    }

    @Test
    @DisplayName("isBlank: Should return false for non-blank string")
    void isBlankNotBlank() {
        assertFalse(isBlank("abc"));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for null")
    void isNotBlankNull() {
        assertFalse(isNotBlank(null));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for empty string")
    void isNotBlankEmpty() {
        assertFalse(isNotBlank(""));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for blank string")
    void isNotBlankBlank() {
        assertFalse(isNotBlank("   "));
    }

    @Test
    @DisplayName("isNotBlank: Should return true for non-blank string")
    void isNotBlankNotBlank() {
        assertTrue(isNotBlank("abc"));
    }
}