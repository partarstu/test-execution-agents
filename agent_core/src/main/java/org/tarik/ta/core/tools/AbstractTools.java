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
package org.tarik.ta.core.tools;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.security.SecretRegistry;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

import static org.tarik.ta.core.error.ErrorCategory.TRANSIENT_TOOL_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.UNKNOWN;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);

    // The message goes back to the model, and an exception can carry a value the tool resolved (e.g. a URL with a secret).
    protected RuntimeException rethrowAsToolException(Exception e, String operationContext) {
        if (e instanceof ToolExecutionException toolExecutionException) {
            return withoutSecrets(toolExecutionException);
        } else {
            LOG.error("Error during {}", operationContext, e);
            var message = "Error while %s: %s".formatted(operationContext, e.getMessage());
            return new ToolExecutionException(SecretRegistry.global().redact(message), UNKNOWN, e);
        }
    }

    /**
     * Resolves a path the model supplied against {@code folder}, which must be absolute and normalized, and rejects every
     * path that would leave it, so that a crafted path cannot make the agent read an arbitrary file of the host.
     */
    protected static Path resolveWithinFolder(@NotNull Path folder, @Nullable String filePath, @NotNull String folderName) {
        if (isBlank(filePath)) {
            throw new ToolExecutionException("File path cannot be null or empty", TRANSIENT_TOOL_ERROR);
        }
        Path resolved;
        try {
            resolved = folder.resolve(filePath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new ToolExecutionException("File path is not a valid path: %s (%s)".formatted(filePath, e.getReason()),
                    TRANSIENT_TOOL_ERROR);
        }
        if (!resolved.startsWith(folder)) {
            throw new ToolExecutionException("File path '%s' is outside of the %s '%s'".formatted(filePath, folderName, folder),
                    TRANSIENT_TOOL_ERROR);
        }
        return resolved;
    }

    private static ToolExecutionException withoutSecrets(ToolExecutionException exception) {
        var redactedMessage = SecretRegistry.global().redact(exception.getMessage());
        return Objects.equals(redactedMessage, exception.getMessage())
                ? exception
                : new ToolExecutionException(redactedMessage, exception.getErrorCategory(), exception);
    }
}
