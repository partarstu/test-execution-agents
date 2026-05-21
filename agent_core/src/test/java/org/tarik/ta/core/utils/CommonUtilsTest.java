/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@SuppressWarnings({"ALL", "unchecked"})
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
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> map = Map.of("key", "value");
        Optional<String> result = CommonUtils.getObjectPrettyPrinted(mapper, map);
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("\"key\" : \"value\""));
    }

    @Test
    void getObjectPrettyPrinted_ShouldReturnEmptyOnException() throws JsonProcessingException {
        ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
        ObjectWriter writer = Mockito.mock(ObjectWriter.class);
        
        Mockito.when(mapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
        Mockito.when(writer.writeValueAsString(ArgumentMatchers.any())).thenThrow(new JsonProcessingException("Error") {});
        
        Optional<String> result = CommonUtils.getObjectPrettyPrinted(mapper, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteFile_ShouldDeleteFile(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("test.txt").toFile();
        assertTrue(file.createNewFile());
        CommonUtils.deleteFile(file);
        assertFalse(file.exists());
    }
    
    @Test
    void deleteFile_ShouldHandleNonExistentFile() {
        File file = new File("non_existent_file.txt");
        assertDoesNotThrow(() -> CommonUtils.deleteFile(file));
    }

    @Test
    void deleteFolderContents_ShouldDeleteContents(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("file.txt"));
        
        CommonUtils.deleteFolderContents(tempDir);
        
        assertTrue(Files.exists(tempDir));
        try (Stream<Path> entries = Files.list(tempDir)) {
             assertEquals(0, entries.count());
        }
    }
    
    @Test
    void deleteFolderContents_ShouldThrowIfFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.createFile(file);
        
        assertThrows(IllegalArgumentException.class, () -> CommonUtils.deleteFolderContents(file));
    }

    @Test
    void getFutureResult_ShouldReturnResult() throws ExecutionException, InterruptedException {
        Future<String> future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenReturn("result");
        
        Optional<String> result = CommonUtils.getFutureResult(future, "task");
        assertTrue(result.isPresent());
        assertEquals("result", result.get());
    }

    @Test
    void getFutureResult_ShouldReturnEmptyOnException() throws ExecutionException, InterruptedException {
        Future<String> future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenThrow(new ExecutionException(new RuntimeException("Error")));
        
        Optional<String> result = CommonUtils.getFutureResult(future, "task");
        assertTrue(result.isEmpty());
    }
    
    @Test
    void getFutureResult_ShouldHandleInterruptedException() throws ExecutionException, InterruptedException {
        Future<String> future = Mockito.mock(Future.class);
        Mockito.when(future.get()).thenThrow(new InterruptedException("Interrupted"));
        
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

    @Test
    void getDurationInMillis_ShouldReturnCorrectDuration() {
        Instant start = Instant.now().minusMillis(500);
        long duration = CommonUtils.getDurationInMillis(start);
        assertTrue(duration >= 500);
    }
}
