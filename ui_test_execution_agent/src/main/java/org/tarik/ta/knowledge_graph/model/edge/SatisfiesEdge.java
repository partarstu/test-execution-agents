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
package org.tarik.ta.knowledge_graph.model.edge;

import java.util.UUID;

public record SatisfiesEdge(
        UUID producerId,
        UUID consumerId,
        double score,
        String effectPhrase,
        String prerequisitePhrase,
        UUID prerequisiteNodeId) {

    public static final String PROP_SCORE = "score";
    public static final String PROP_EFFECT_PHRASE = "effectPhrase";
    public static final String PROP_PREREQUISITE_PHRASE = "prerequisitePhrase";
    public static final String PROP_PREREQUISITE_NODE_ID = "prerequisiteNodeId";
    public static final String PROP_CREATED_AT = "createdAt";
    public static final String PROP_LAST_VERIFIED_AT = "lastVerifiedAt";
}