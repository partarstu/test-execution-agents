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
package org.tarik.ta;

import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig.ModelProvider;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeAgentConfigTest {

    @Test
    void constructor_shouldLoadTheKnowledgeSettingsFromTheConfigurationFile() {
        var config = new KnowledgeAgentConfig();

        assertThat(config.getSearchTopN()).isEqualTo(10);
        assertThat(config.getSearchMinScore()).isEqualTo(0.80);
        assertThat(config.getMaxSelectionsPerItem()).isEqualTo(3);
        assertThat(config.getSelectionMaxRetries()).isEqualTo(3);
        assertThat(config.getExplorationMaxToolCalls()).isEqualTo(40);
        assertThat(config.getMaxTreeDepth()).isEqualTo(5);
        assertThat(config.getMaxTreeNodes()).isEqualTo(200);
        assertThat(config.getItemSelectionAgentModelName()).isEqualTo("gemini-3.8-flash");
        assertThat(config.getItemSelectionAgentModelProvider()).isEqualTo(ModelProvider.GOOGLE);
        assertThat(config.getItemSelectionAgentPromptVersion()).isEqualTo("v1.0.0");
        assertThat(config.getAggregationSelectionAgentModelName()).isEqualTo("gemini-3.8-flash");
        assertThat(config.getAggregationSelectionAgentModelProvider()).isEqualTo(ModelProvider.GOOGLE);
        assertThat(config.getAggregationSelectionAgentPromptVersion()).isEqualTo("v1.0.0");
    }

    @Test
    void constructor_shouldLoadTheRequestAndConcurrencySettingsFromTheConfigurationFile() {
        var config = new KnowledgeAgentConfig();

        assertThat(config.getMaxRequestItems()).isEqualTo(100);
        assertThat(config.getMaxRequestTextLength()).isEqualTo(4000);
        assertThat(config.getItemConcurrency()).isEqualTo(8);
        assertThat(config.getTaskCapacity()).isEqualTo(4);
        assertThat(config.getAgentExecutionTimeBudgetSeconds()).isEqualTo(840);
        assertThat(config.getStartPort()).isEqualTo(8007);
    }

    @Test
    void constructor_shouldLoadTheKnowledgeGraphSettingsFromTheConfigurationFile() {
        var config = new KnowledgeAgentConfig();

        assertThat(config.getVectorDbUrl()).isEqualTo("bolt://localhost:7687");
        assertThat(config.getNeo4jUsername()).isEqualTo("neo4j");
        assertThat(config.getNeo4jDatabase()).isEqualTo("neo4j");
        assertThat(config.getNeo4jMaxConnectionPoolSize()).isEqualTo(20);
        assertThat(config.getNeo4jConnectionAcquisitionTimeoutSeconds()).isEqualTo(30);
        assertThat(config.getNeo4jConnectionTimeoutSeconds()).isEqualTo(10);
        assertThat(config.getNeo4jMaxTransactionRetryTimeSeconds()).isEqualTo(120);
        assertThat(config.getKnowledgeEmbeddingModel()).isEqualTo("bge-small-en-v15");
        assertThat(config.getKnowledgeEmbeddingBatchSize()).isEqualTo(10);
        assertThat(config.getTimingEwmaAlpha()).isEqualTo(0.2);
        assertThat(config.getStabilityEwmaAlpha()).isEqualTo(0.3);
    }
}
