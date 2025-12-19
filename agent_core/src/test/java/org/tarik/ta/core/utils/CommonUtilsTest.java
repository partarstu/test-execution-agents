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

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.tarik.ta.core.utils.CommonUtils.*;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
@DisplayName("CoreUtils Tests")
class CommonUtilsTest {

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

    @Test
    void getObjectPrettyPrinted_ShouldReturnPrettyJson() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String, String> map = java.util.Map.of("key", "value");
        Optional<String> result = CommonUtils.getObjectPrettyPrinted(mapper, map);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("\"key\" : \"value\""));
    }

    @Test
    void getObjectPrettyPrinted_ShouldReturnEmptyOnException() throws com.fasterxml.jackson.core.JsonProcessingException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        com.fasterxml.jackson.databind.ObjectWriter writer = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectWriter.class);
        
        org.mockito.Mockito.when(mapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
        org.mockito.Mockito.when(writer.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Error") {});
        
        Optional<String> result = CommonUtils.getObjectPrettyPrinted(mapper, java.util.Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteFile_ShouldDeleteFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.io.File file = tempDir.resolve("test.txt").toFile();
        assertTrue(file.createNewFile());
        CommonUtils.deleteFile(file);
        assertFalse(file.exists());
    }
    
    @Test
    void deleteFile_ShouldHandleNonExistentFile() {
        java.io.File file = new java.io.File("non_existent_file.txt");
        assertDoesNotThrow(() -> CommonUtils.deleteFile(file));
    }

    @Test
    void deleteFolderContents_ShouldDeleteContents(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path subDir = tempDir.resolve("subdir");
        java.nio.file.Files.createDirectory(subDir);
        java.nio.file.Files.createFile(subDir.resolve("file.txt"));
        
        CommonUtils.deleteFolderContents(tempDir);
        
        assertTrue(java.nio.file.Files.exists(tempDir));
        try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(tempDir)) {
             assertEquals(0, entries.count());
        }
    }
    
    @Test
    void deleteFolderContents_ShouldThrowIfFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path file = tempDir.resolve("file.txt");
        java.nio.file.Files.createFile(file);
        
        assertThrows(IllegalArgumentException.class, () -> CommonUtils.deleteFolderContents(file));
    }

    @Test
    void getFutureResult_ShouldReturnResult() throws java.util.concurrent.ExecutionException, InterruptedException {
        java.util.concurrent.Future<String> future = org.mockito.Mockito.mock(java.util.concurrent.Future.class);
        org.mockito.Mockito.when(future.get()).thenReturn("result");
        
        Optional<String> result = CommonUtils.getFutureResult(future, "task");
        assertTrue(result.isPresent());
        assertEquals("result", result.get());
    }

    @Test
    void getFutureResult_ShouldReturnEmptyOnException() throws java.util.concurrent.ExecutionException, InterruptedException {
        java.util.concurrent.Future<String> future = org.mockito.Mockito.mock(java.util.concurrent.Future.class);
        org.mockito.Mockito.when(future.get()).thenThrow(new java.util.concurrent.ExecutionException(new RuntimeException("Error")));
        
        Optional<String> result = CommonUtils.getFutureResult(future, "task");
        assertTrue(result.isEmpty());
    }
    
    @Test
    void getFutureResult_ShouldHandleInterruptedException() throws java.util.concurrent.ExecutionException, InterruptedException {
        java.util.concurrent.Future<String> future = org.mockito.Mockito.mock(java.util.concurrent.Future.class);
        org.mockito.Mockito.when(future.get()).thenThrow(new InterruptedException("Interrupted"));
        
        Optional<String> result = CommonUtils.getFutureResult(future, "task");
        assertTrue(result.isEmpty());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void getEnvironmentVariable_ShouldReturnValue() {
        String key = "TEST_PROP_KEY_" + System.currentTimeMillis();
        System.setProperty(key, "test_value");
        assertEquals("test_value", CommonUtils.getEnvironmentVariable(key));
        System.clearProperty(key);
    }
    
    @Test
    void getEnvironmentVariable_ShouldReturnNullForBlank() {
        assertNull(CommonUtils.getEnvironmentVariable(""));
        assertNull(CommonUtils.getEnvironmentVariable(null));
    }
    
    @Test
    void sleepSeconds_ShouldSleep() {
        long start = System.currentTimeMillis();
        CommonUtils.sleepSeconds(1);
        long end = System.currentTimeMillis();
        assertTrue((end - start) >= 1000);
    }
    
    @Test
    void sleepMillis_ShouldSleep() {
        long start = System.currentTimeMillis();
        CommonUtils.sleepMillis(100);
        long end = System.currentTimeMillis();
        assertTrue((end - start) >= 100);
    }
    
    @Test
    void waitUntil_ShouldWait() {
        Instant deadline = Instant.now().plusMillis(200);
        CommonUtils.waitUntil(deadline);
        assertFalse(Instant.now().isBefore(deadline));
    }
    
    @Test
    void waitUntil_ShouldReturnImmediatelyIfDeadlinePassed() {
        Instant deadline = Instant.now().minusMillis(200);
        long start = System.currentTimeMillis();
        CommonUtils.waitUntil(deadline);
        long end = System.currentTimeMillis();
        assertTrue((end - start) < 100); // Should be very fast
    }
}
