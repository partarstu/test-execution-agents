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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.exceptions.ToolExecutionException;

import static java.lang.String.format;
import static org.tarik.ta.core.error.ErrorCategory.UNKNOWN;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);

    protected RuntimeException rethrowAsToolException(Exception e, String operationContext) {
        if (e instanceof ToolExecutionException toolExecutionException) {
            return toolExecutionException;
        } else {
            LOG.error("Error during {}", operationContext, e);
            return new ToolExecutionException(format("Error while %s: %s", operationContext, e.getMessage()), UNKNOWN);
        }
    }
}
