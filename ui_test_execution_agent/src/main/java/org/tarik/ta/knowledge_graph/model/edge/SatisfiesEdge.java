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
package org.tarik.ta.knowledge_graph.model.edge;

import java.util.UUID;

public record SatisfiesEdge(
        UUID producerId,
        UUID consumerId,
        double score,
        String effectPhrase,
        String prerequisitePhrase) {

    public static final String PROP_SCORE = "score";
    public static final String PROP_EFFECT_PHRASE = "effectPhrase";
    public static final String PROP_PREREQUISITE_PHRASE = "prerequisitePhrase";
    public static final String PROP_CREATED_AT = "createdAt";
    public static final String PROP_LAST_VERIFIED_AT = "lastVerifiedAt";
}