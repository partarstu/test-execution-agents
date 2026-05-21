/*
 * ui-test-execution-agent - ${project.description}
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
package org.tarik.ta.knowledge_graph.model.node;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.tarik.ta.core.error.ErrorCategory;

public record FailureContext(
        UUID id,
        String symptom,
        ErrorCategory category,
        String resolution,
        int occurrences,
        Instant lastOccurred,
        Mode mode
) implements IEntity {

    public static final String LABEL = "FailureContext";
    public static final String PROP_SYMPTOM = "symptom";
    public static final String PROP_SYMPTOM_NORMALIZED = "symptomNormalized";
    public static final String PROP_CATEGORY = "category";
    public static final String PROP_RESOLUTION = "resolution";
    public static final String PROP_OCCURRENCES = "occurrences";
    public static final String PROP_LAST_OCCURRED = "lastOccurred";
    public static final String PROP_MODE = "mode";

    public enum Mode {
        SUPERVISED,
        UNATTENDED
    }

    public FailureContext {
        Objects.requireNonNull(symptom, "symptom cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(resolution, "resolution cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
    }
}