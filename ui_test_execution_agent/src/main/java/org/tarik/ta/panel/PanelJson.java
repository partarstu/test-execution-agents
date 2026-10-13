/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.panel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.core.security.SecretRegistry;

/** The one JSON mapper of the panel's routes and event stream, writing timestamps as ISO-8601 strings. */
final class PanelJson {
    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private PanelJson() {
    }

    /** Converts the value to JSON with every string in it redacted, for content whose texts come from the graph or an LLM. */
    static @NotNull JsonNode redactedTree(@NotNull Object value) {
        return redact(MAPPER.valueToTree(value));
    }

    private static JsonNode redact(JsonNode node) {
        switch (node) {
            case TextNode text -> {
                return TextNode.valueOf(SecretRegistry.global().redact(text.textValue()));
            }
            case ObjectNode object -> object.properties().forEach(field -> field.setValue(redact(field.getValue())));
            case ArrayNode array -> {
                for (int index = 0; index < array.size(); index++) {
                    array.set(index, redact(array.get(index)));
                }
            }
            default -> {
            }
        }
        return node;
    }
}
