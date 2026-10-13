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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tarik.ta.core.exceptions.ToolExecutionException;
import org.tarik.ta.core.security.SecretRegistry;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tarik.ta.core.error.ErrorCategory.NON_RETRYABLE_ERROR;
import static org.tarik.ta.core.error.ErrorCategory.UNKNOWN;

class AbstractToolsTest {

    private static final Pattern KEY_PATTERN = Pattern.compile("(?i).*token.*");
    private static final Pattern NEVER_MATCHING = Pattern.compile("(?!)");

    private final AbstractTools tools = new AbstractTools();

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(KEY_PATTERN);
        SecretRegistry.global().register("apiToken", "s3cr3t-token");
    }

    @AfterEach
    void tearDown() {
        SecretRegistry.global().reset(NEVER_MATCHING);
    }

    @Test
    void rethrowAsToolException_shouldReplaceTheSecret_whenAToolExceptionCarriesIt() {
        var original = new ToolExecutionException("Invalid URL 'http://host/?token=s3cr3t-token'", NON_RETRYABLE_ERROR);

        var rethrown = tools.rethrowAsToolException(original, "sending request");

        assertThat(rethrown).isInstanceOfSatisfying(ToolExecutionException.class, exception -> {
            assertThat(exception).hasMessage("Invalid URL 'http://host/?token={{secret:apiToken}}'").hasCause(original);
            assertThat(exception.getErrorCategory()).isEqualTo(NON_RETRYABLE_ERROR);
        });
    }

    @Test
    void rethrowAsToolException_shouldReturnTheSameException_whenItCarriesNoSecret() {
        var original = new ToolExecutionException("Host 'evil.example' is not allowed", NON_RETRYABLE_ERROR);

        assertThat(tools.rethrowAsToolException(original, "sending request")).isSameAs(original);
    }

    @Test
    void rethrowAsToolException_shouldReplaceTheSecret_whenAnUnexpectedExceptionCarriesIt() {
        var original = new IllegalStateException("Connection refused: /?token=s3cr3t-token");

        var rethrown = tools.rethrowAsToolException(original, "sending request");

        assertThat(rethrown).isInstanceOfSatisfying(ToolExecutionException.class, exception -> {
            assertThat(exception).hasMessage("Error while sending request: Connection refused: /?token={{secret:apiToken}}")
                    .hasCause(original);
            assertThat(exception.getErrorCategory()).isEqualTo(UNKNOWN);
        });
    }

    @Test
    void resolveWithinFolder_shouldResolveTheRelativePathInsideTheFolder(@TempDir Path folder) {
        var resolved = AbstractTools.resolveWithinFolder(folder, "products/product.json", "schema folder");

        assertThat(resolved).isEqualTo(folder.resolve("products").resolve("product.json"));
    }

    @Test
    void resolveWithinFolder_shouldRejectThePath_whenItLeavesTheFolder(@TempDir Path folder) {
        assertThatThrownBy(() -> AbstractTools.resolveWithinFolder(folder, "../outside.json", "schema folder"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("is outside of the schema folder");
    }
}
