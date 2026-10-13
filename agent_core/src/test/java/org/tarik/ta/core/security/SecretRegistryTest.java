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
package org.tarik.ta.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRegistryTest {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("(?i).*(password|passwd|pwd|pin|token|secret|api[_-]?key|credential).*");
    private static final Pattern NEVER_MATCHING = Pattern.compile("(?!)");

    private final SecretRegistry registry = SecretRegistry.global();

    @BeforeEach
    void setUp() {
        registry.reset(KEY_PATTERN);
    }

    @AfterEach
    void tearDown() {
        registry.reset(NEVER_MATCHING);
    }

    @Test
    void register_shouldReturnThePlaceholder_whenTheKeyIsSensitive() {
        var placeholder = registry.register("password", "s3cr3t-value");

        assertThat(placeholder).contains("{{secret:password}}");
    }

    @Test
    void register_shouldReturnNothing_whenTheKeyIsNotSensitive() {
        var placeholder = registry.register("username", "admin");

        assertThat(placeholder).isEmpty();
        assertThat(registry.redact("admin")).isEqualTo("admin");
    }

    @Test
    void register_shouldReturnNothing_whenTheValueIsTooShortToMaskMeaningfully() {
        var placeholder = registry.register("pin", "42");

        assertThat(placeholder).isEmpty();
    }

    @Test
    void register_shouldReturnNothing_whenTheValueIsNotText() {
        var placeholder = registry.register("apiKey", 4711);

        assertThat(placeholder).isEmpty();
    }

    @Test
    void isSensitiveKey_shouldMatchTheConfiguredConventionCaseInsensitively() {
        assertThat(registry.isSensitiveKey("adminPassword")).isTrue();
        assertThat(registry.isSensitiveKey("API_KEY")).isTrue();
        assertThat(registry.isSensitiveKey("customerName")).isFalse();
        assertThat(registry.isSensitiveKey(null)).isFalse();
    }

    @Test
    void resolve_shouldPutTheRealValueBack() {
        registry.register("password", "s3cr3t-value");

        assertThat(registry.resolve("login with {{secret:password}} now")).isEqualTo("login with s3cr3t-value now");
    }

    @Test
    void resolve_shouldLeaveAnUnknownPlaceholderUntouched() {
        registry.register("password", "s3cr3t-value");

        assertThat(registry.resolve("{{secret:other}}")).isEqualTo("{{secret:other}}");
    }

    @Test
    void redact_shouldReplaceTheValueByThePlaceholder() {
        registry.register("password", "s3cr3t-value");

        assertThat(registry.redact("typed s3cr3t-value into the field"))
                .isEqualTo("typed {{secret:password}} into the field");
    }

    @Test
    void redact_shouldReplaceTheLongestValueFirst_whenOneValueContainsAnother() {
        registry.register("token", "abcd");
        registry.register("apiToken", "abcd-1234");

        assertThat(registry.redact("header: abcd-1234")).isEqualTo("header: {{secret:apiToken}}");
    }

    @Test
    void redact_shouldReturnTheTextUnchanged_whenNothingIsRegistered() {
        assertThat(registry.redact("nothing to hide")).isEqualTo("nothing to hide");
        assertThat(registry.redact(null)).isNull();
    }

    @Test
    void reset_shouldDropTheSecretsOfThePreviousRun() {
        registry.register("password", "s3cr3t-value");

        registry.reset(KEY_PATTERN);

        assertThat(registry.redact("s3cr3t-value")).isEqualTo("s3cr3t-value");
    }
}
