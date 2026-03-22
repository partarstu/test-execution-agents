/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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