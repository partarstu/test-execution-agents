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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Thread.currentThread;
import static java.nio.file.Files.isDirectory;
import static java.time.Instant.now;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public class CommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CommonUtils.class);

    public static Optional<String> getObjectPrettyPrinted(ObjectMapper mapper,
            Map<String, String> toolExecutionInfoByToolName) {
        try {
            return of(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolExecutionInfoByToolName));
        } catch (JsonProcessingException e) {
            LOG.error("Couldn't write the provided tool execution info by tool name as a pretty string.", e);
            return empty();
        }
    }

    public static Optional<Integer> parseStringAsInteger(String str) {
        if (isBlank(str)) {
            return empty();
        }
        try {
            return Optional.of(Integer.parseInt(str.trim()));
        } catch (NumberFormatException e) {
            LOG.error("Failed to parse string as integer: '{}'", str, e);
            return empty();
        }
    }

    public static Optional<Double> parseStringAsDouble(String str) {
        if (isBlank(str)) {
            return empty();
        }
        try {
            return Optional.of(Double.parseDouble(str.trim()));
        } catch (NumberFormatException e) {
            return empty();
        }
    }

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static long getDurationInMillis(Instant start) {
        return Duration.between(start, now()).toMillis();
    }

    public static void deleteFile(@NotNull File file) {
        if (file.exists()) {
            if (!file.delete()) {
                LOG.warn("Failed to delete file: {}", file.getAbsolutePath());
            }
        }
    }

    public static void waitUntil(Instant deadline) {
        while (now().isBefore(deadline)) {
            sleepMillis(100);
        }
    }

    public static void deleteFolderContents(@NotNull Path pathToFolder) {
        checkArgument(isDirectory(pathToFolder), "%s is not a directory".formatted(pathToFolder.toAbsolutePath()));
        try (Stream<Path> walk = Files.walk(pathToFolder)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(pathToFolder))
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> Optional<T> getFutureResult(Future<T> future, String resultDescription) {
        try {
            return ofNullable(future.get());
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("%s task failed".formatted(resultDescription), e);
            if (e instanceof InterruptedException) {
                currentThread().interrupt();
            }
            return empty();
        }
    }

    public static String getEnvironmentVariable(String name) {
        if (isBlank(name)) {
            return null;
        }
        String value = System.getenv(name);
        if (value == null) {
            value = System.getProperty(name);
        }
        return value;
    }
}