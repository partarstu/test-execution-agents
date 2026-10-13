/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
package org.tarik.ta.model;

import dev.langchain4j.model.output.structured.Description;

import static java.util.Objects.requireNonNullElse;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;

/**
 * One header of an outgoing request. A list of these records, rather than a map, is what the model is given as a tool
 * parameter, because a map is published to it as an object without properties, which it can only fill as {@code {}}.
 */
@Description("A single HTTP request header.")
public record HttpHeader(
        @Description("The header name, e.g. Authorization.") String name,
        @Description("The header value, exactly as it must be sent. Secret placeholders like {{secret:apiToken}} are passed unchanged.")
        String value) {

    public HttpHeader {
        if (isBlank(name)) {
            throw new IllegalArgumentException("Header name cannot be null or empty");
        }
        value = requireNonNullElse(value, "");
    }
}
