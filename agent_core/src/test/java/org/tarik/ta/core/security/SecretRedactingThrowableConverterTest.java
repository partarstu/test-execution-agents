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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactingThrowableConverterTest {

    private static final Pattern KEY_PATTERN = Pattern.compile("(?i).*(password|token).*");
    private static final Pattern NEVER_MATCHING = Pattern.compile("(?!)");

    private final SecretRedactingThrowableConverter converter = new SecretRedactingThrowableConverter();

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(KEY_PATTERN);
        converter.start();
    }

    @AfterEach
    void tearDown() {
        converter.stop();
        SecretRegistry.global().reset(NEVER_MATCHING);
    }

    @Test
    void convert_shouldReplaceARegisteredSecretInTheStackTraceByItsPlaceholder() {
        SecretRegistry.global().register("apiToken", "s3cr3t-token");
        var cause = new IllegalStateException("Connection refused: http://api.example.org/orders?token=s3cr3t-token");

        var converted = converter.convert(loggingEventWith(new RuntimeException("Request failed", cause)));

        assertThat(converted)
                .contains("token={{secret:apiToken}}")
                .contains("Request failed")
                .doesNotContain("s3cr3t-token");
    }

    private static LoggingEvent loggingEventWith(Throwable throwable) {
        var event = new LoggingEvent();
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        event.setLevel(Level.ERROR);
        event.setMessage("Error during sending request");
        event.setThrowableProxy(new ThrowableProxy(throwable));
        return event;
    }
}
