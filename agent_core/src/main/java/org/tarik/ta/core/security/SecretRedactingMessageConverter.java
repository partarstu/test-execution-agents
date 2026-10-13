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

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback conversion word which writes the log message with every registered secret replaced by its placeholder. It is
 * the safety net for the agent's own log files and console output: the code paths that handle secrets never log them,
 * but a third-party library which echoes a request body would otherwise write one in clear.
 */
public class SecretRedactingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretRegistry.global().redact(super.convert(event));
    }
}
