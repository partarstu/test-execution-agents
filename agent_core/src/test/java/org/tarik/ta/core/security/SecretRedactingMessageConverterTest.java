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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactingMessageConverterTest {

    private static final Pattern KEY_PATTERN = Pattern.compile("(?i).*(password|token).*");
    private static final Pattern NEVER_MATCHING = Pattern.compile("(?!)");

    private final SecretRedactingMessageConverter converter = new SecretRedactingMessageConverter();

    @BeforeEach
    void setUp() {
        SecretRegistry.global().reset(KEY_PATTERN);
    }

    @AfterEach
    void tearDown() {
        SecretRegistry.global().reset(NEVER_MATCHING);
    }

    @Test
    void convert_shouldReplaceARegisteredSecretByItsPlaceholder() {
        SecretRegistry.global().register("password", "s3cr3t-value");

        var converted = converter.convert(loggingEvent("Typed s3cr3t-value into the field"));

        assertThat(converted).isEqualTo("Typed {{secret:password}} into the field");
    }

    @Test
    void convert_shouldLeaveAMessageWithoutASecretUnchanged() {
        SecretRegistry.global().register("password", "s3cr3t-value");

        var converted = converter.convert(loggingEvent("Clicked the login button"));

        assertThat(converted).isEqualTo("Clicked the login button");
    }

    private static LoggingEvent loggingEvent(String message) {
        var event = new LoggingEvent();
        event.setLoggerContext((LoggerContext) LoggerFactory.getILoggerFactory());
        event.setLevel(Level.INFO);
        event.setMessage(message);
        return event;
    }
}
