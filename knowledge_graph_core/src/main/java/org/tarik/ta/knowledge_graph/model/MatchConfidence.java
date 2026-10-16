/*
 * knowledge-graph-core - Knowledge graph model, repositories and embeddings shared by the agents that use the knowledge graph.
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
package org.tarik.ta.knowledge_graph.model;

/**
 * Confidence level of a semantic match.
 */
public enum MatchConfidence {
    /** Score at or above the high confidence threshold: the match can be auto-executed. */
    HIGH,
    /** Score at or above the low but below the high threshold: the caller decides, e.g. asks the user to confirm. */
    LOW
}
