/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
