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
package org.tarik.ta.knowledge_graph;

/**
 * Settings of the knowledge graph connection and of its embeddings, implemented by the configuration of every agent that uses the graph.
 */
public interface KnowledgeGraphConfig {
    String getVectorDbUrl();

    String getVectorDbToken();

    String getNeo4jUsername();

    String getNeo4jDatabase();

    int getNeo4jMaxConnectionPoolSize();

    int getNeo4jConnectionAcquisitionTimeoutSeconds();

    int getNeo4jConnectionTimeoutSeconds();

    int getNeo4jMaxTransactionRetryTimeSeconds();

    String getKnowledgeEmbeddingModel();

    int getKnowledgeEmbeddingBatchSize();

    double getTimingEwmaAlpha();

    double getStabilityEwmaAlpha();
}
