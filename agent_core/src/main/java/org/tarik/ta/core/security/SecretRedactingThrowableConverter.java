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

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.jetbrains.annotations.NotNull;

/**
 * Writes a logged exception's stack trace with the registered secrets replaced by their placeholders, since an exception
 * message can carry a value the tools resolved (e.g. a URL with a secret query parameter).
 */
public class SecretRedactingThrowableConverter extends ThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(@NotNull IThrowableProxy throwableProxy) {
        return SecretRegistry.global().redact(super.throwableProxyToString(throwableProxy));
    }
}
