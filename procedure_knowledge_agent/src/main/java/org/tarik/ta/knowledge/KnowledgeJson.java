/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
package org.tarik.ta.knowledge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Converts the procedure data the tools return to the model and the data the agent exchanges with its caller to and from snake_case JSON
 * without null fields.
 */
public final class KnowledgeJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() {
    };

    private KnowledgeJson() {
    }

    /**
     * Returns the value as the JSON object structure an A2A data part carries.
     */
    public static @NotNull Map<String, Object> toData(@NotNull Object value) {
        return MAPPER.convertValue(value, DATA_TYPE);
    }

    /**
     * Reads the JSON object structure of an A2A data part into the given record, rejecting unknown fields.
     *
     * @throws IllegalArgumentException when the data does not match the record
     */
    public static <T> @NotNull T fromData(@NotNull Object data, @NotNull Class<T> type) {
        return MAPPER.convertValue(data, type);
    }

    public static @NotNull String toJson(@NotNull Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize %s to JSON".formatted(value.getClass().getSimpleName()), e);
        }
    }
}
