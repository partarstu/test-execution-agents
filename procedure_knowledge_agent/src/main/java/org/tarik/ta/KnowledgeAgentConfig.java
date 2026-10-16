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

import jakarta.inject.Singleton;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.knowledge_graph.KnowledgeGraphConfig;

/**
 * Configuration of the procedure knowledge agent: the knowledge graph connection, the exploration bounds and the selection sub-agents.
 */
@Singleton
public class KnowledgeAgentConfig extends AgentConfig implements KnowledgeGraphConfig {

    // -----------------------------------------------------
    // Knowledge graph connection and embeddings
    private final ConfigProperty<String> neo4jUsername;
    private final ConfigProperty<String> neo4jDatabase;
    private final ConfigProperty<Integer> neo4jMaxConnectionPoolSize;
    private final ConfigProperty<Integer> neo4jConnectionAcquisitionTimeoutSeconds;
    private final ConfigProperty<Integer> neo4jConnectionTimeoutSeconds;
    private final ConfigProperty<Integer> neo4jMaxTransactionRetryTimeSeconds;
    private final ConfigProperty<String> knowledgeEmbeddingModel;
    private final ConfigProperty<Integer> knowledgeEmbeddingBatchSize;
    private final ConfigProperty<Double> timingEwmaAlpha;
    private final ConfigProperty<Double> stabilityEwmaAlpha;

    // -----------------------------------------------------
    // Exploration and selection bounds
    private final ConfigProperty<Integer> searchTopN;
    private final ConfigProperty<Double> searchMinScore;
    private final ConfigProperty<Integer> maxSelectionsPerItem;
    private final ConfigProperty<Integer> explorationMaxToolCalls;
    private final ConfigProperty<Integer> maxTreeDepth;
    private final ConfigProperty<Integer> maxTreeNodes;
    private final ConfigProperty<Integer> selectionMaxRetries;

    // -----------------------------------------------------
    // Requests and concurrency
    private final ConfigProperty<Integer> maxRequestItems;
    private final ConfigProperty<Integer> maxRequestTextLength;
    private final ConfigProperty<Integer> itemConcurrency;
    private final ConfigProperty<Integer> taskCapacity;

    // -----------------------------------------------------
    // Selection sub-agents
    private final ConfigProperty<String> itemSelectionAgentModelName;
    private final ConfigProperty<ModelProvider> itemSelectionAgentModelProvider;
    private final ConfigProperty<String> itemSelectionAgentPromptVersion;
    private final ConfigProperty<String> aggregationSelectionAgentModelName;
    private final ConfigProperty<ModelProvider> aggregationSelectionAgentModelProvider;
    private final ConfigProperty<String> aggregationSelectionAgentPromptVersion;

    public KnowledgeAgentConfig() {
        super();

        this.neo4jUsername = loadProperty("neo4j.username", "NEO4J_USERNAME", "neo4j", s -> s, false);
        this.neo4jDatabase = loadProperty("neo4j.database", "NEO4J_DATABASE", "neo4j", s -> s, false);
        this.neo4jMaxConnectionPoolSize = loadPropertyAsInteger(
                "neo4j.max.connection.pool.size", "NEO4J_MAX_CONNECTION_POOL_SIZE", "20", false);
        this.neo4jConnectionAcquisitionTimeoutSeconds = loadPropertyAsInteger(
                "neo4j.connection.acquisition.timeout.seconds", "NEO4J_CONNECTION_ACQUISITION_TIMEOUT_SECONDS", "30", false);
        this.neo4jConnectionTimeoutSeconds = loadPropertyAsInteger(
                "neo4j.connection.timeout.seconds", "NEO4J_CONNECTION_TIMEOUT_SECONDS", "10", false);
        this.neo4jMaxTransactionRetryTimeSeconds = loadPropertyAsInteger(
                "neo4j.max.transaction.retry.time.seconds", "NEO4J_MAX_TRANSACTION_RETRY_TIME_SECONDS", "120", false);
        this.knowledgeEmbeddingModel = loadProperty(
                "knowledge.embedding.model", "KNOWLEDGE_EMBEDDING_MODEL", "bge-small-en-v15", s -> s, false);
        this.knowledgeEmbeddingBatchSize = loadPropertyAsInteger(
                "knowledge.embedding.batch.size", "KNOWLEDGE_EMBEDDING_BATCH_SIZE", "10", false);
        this.timingEwmaAlpha = loadPropertyAsDouble("timing.ewma.alpha", "TIMING_EWMA_ALPHA", "0.2", false);
        this.stabilityEwmaAlpha = loadPropertyAsDouble("stability.ewma.alpha", "STABILITY_EWMA_ALPHA", "0.3", false);

        this.searchTopN = loadPropertyAsInteger("knowledge.search.top.n", "KNOWLEDGE_SEARCH_TOP_N", "10", false);
        this.searchMinScore = loadPropertyAsDouble("knowledge.search.min.score", "KNOWLEDGE_SEARCH_MIN_SCORE", "0.80", false);
        this.maxSelectionsPerItem = loadPropertyAsInteger(
                "knowledge.selection.max.per.item", "KNOWLEDGE_SELECTION_MAX_PER_ITEM", "3", false);
        this.explorationMaxToolCalls = loadPropertyAsInteger(
                "knowledge.exploration.max.tool.calls", "KNOWLEDGE_EXPLORATION_MAX_TOOL_CALLS", "40", false);
        this.maxTreeDepth = loadPropertyAsInteger("knowledge.max.depth", "KNOWLEDGE_MAX_DEPTH", "5", false);
        this.maxTreeNodes = loadPropertyAsInteger("knowledge.tree.max.nodes", "KNOWLEDGE_TREE_MAX_NODES", "200", false);
        this.selectionMaxRetries = loadPropertyAsInteger(
                "knowledge.selection.max.retries", "KNOWLEDGE_SELECTION_MAX_RETRIES", "3", false);

        this.maxRequestItems = loadPropertyAsInteger("knowledge.request.max.items", "KNOWLEDGE_REQUEST_MAX_ITEMS", "100", false);
        this.maxRequestTextLength = loadPropertyAsInteger(
                "knowledge.request.max.text.length", "KNOWLEDGE_REQUEST_MAX_TEXT_LENGTH", "4000", false);
        this.itemConcurrency = loadPropertyAsInteger("knowledge.item.concurrency", "KNOWLEDGE_ITEM_CONCURRENCY", "8", false);
        this.taskCapacity = loadPropertyAsInteger("knowledge.task.capacity", "KNOWLEDGE_TASK_CAPACITY", "4", false);

        this.itemSelectionAgentModelName = loadProperty("procedure.item.selection.agent.model.name",
                "PROCEDURE_ITEM_SELECTION_AGENT_MODEL_NAME", "gemini-3.8-flash", s -> s, false);
        this.itemSelectionAgentModelProvider = getProperty("procedure.item.selection.agent.model.provider",
                "PROCEDURE_ITEM_SELECTION_AGENT_MODEL_PROVIDER", "google", this::getModelProvider, false);
        this.itemSelectionAgentPromptVersion = loadProperty("procedure.item.selection.agent.prompt.version",
                "PROCEDURE_ITEM_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
        this.aggregationSelectionAgentModelName = loadProperty("procedure.aggregation.selection.agent.model.name",
                "PROCEDURE_AGGREGATION_SELECTION_AGENT_MODEL_NAME", "gemini-3.8-flash", s -> s, false);
        this.aggregationSelectionAgentModelProvider = getProperty("procedure.aggregation.selection.agent.model.provider",
                "PROCEDURE_AGGREGATION_SELECTION_AGENT_MODEL_PROVIDER", "google", this::getModelProvider, false);
        this.aggregationSelectionAgentPromptVersion = loadProperty("procedure.aggregation.selection.agent.prompt.version",
                "PROCEDURE_AGGREGATION_SELECTION_AGENT_PROMPT_VERSION", "v1.0.0", s -> s, false);
    }

    @Override
    public String getNeo4jUsername() {
        return neo4jUsername.value();
    }

    @Override
    public String getNeo4jDatabase() {
        return neo4jDatabase.value();
    }

    @Override
    public int getNeo4jMaxConnectionPoolSize() {
        return neo4jMaxConnectionPoolSize.value();
    }

    @Override
    public int getNeo4jConnectionAcquisitionTimeoutSeconds() {
        return neo4jConnectionAcquisitionTimeoutSeconds.value();
    }

    @Override
    public int getNeo4jConnectionTimeoutSeconds() {
        return neo4jConnectionTimeoutSeconds.value();
    }

    @Override
    public int getNeo4jMaxTransactionRetryTimeSeconds() {
        return neo4jMaxTransactionRetryTimeSeconds.value();
    }

    @Override
    public String getKnowledgeEmbeddingModel() {
        return knowledgeEmbeddingModel.value();
    }

    @Override
    public int getKnowledgeEmbeddingBatchSize() {
        return knowledgeEmbeddingBatchSize.value();
    }

    @Override
    public double getTimingEwmaAlpha() {
        return timingEwmaAlpha.value();
    }

    @Override
    public double getStabilityEwmaAlpha() {
        return stabilityEwmaAlpha.value();
    }

    public int getSearchTopN() {
        return searchTopN.value();
    }

    public double getSearchMinScore() {
        return searchMinScore.value();
    }

    public int getMaxSelectionsPerItem() {
        return maxSelectionsPerItem.value();
    }

    public int getExplorationMaxToolCalls() {
        return explorationMaxToolCalls.value();
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth.value();
    }

    public int getMaxTreeNodes() {
        return maxTreeNodes.value();
    }

    /**
     * Returns how often a run may correct a selection its result tool rejected before the run ends.
     */
    public int getSelectionMaxRetries() {
        return selectionMaxRetries.value();
    }

    /**
     * Returns the most preconditions and test steps a request may carry together.
     */
    public int getMaxRequestItems() {
        return maxRequestItems.value();
    }

    /**
     * Returns the most characters any single text of a request may have.
     */
    public int getMaxRequestTextLength() {
        return maxRequestTextLength.value();
    }

    /**
     * Returns how many item runs may run at the same time, across all tasks.
     */
    public int getItemConcurrency() {
        return itemConcurrency.value();
    }

    /**
     * Returns how many tasks the agent card declares the agent runs at the same time.
     */
    public int getTaskCapacity() {
        return taskCapacity.value();
    }

    public String getItemSelectionAgentModelName() {
        return itemSelectionAgentModelName.value();
    }

    public ModelProvider getItemSelectionAgentModelProvider() {
        return itemSelectionAgentModelProvider.value();
    }

    public String getItemSelectionAgentPromptVersion() {
        return itemSelectionAgentPromptVersion.value();
    }

    public String getAggregationSelectionAgentModelName() {
        return aggregationSelectionAgentModelName.value();
    }

    public ModelProvider getAggregationSelectionAgentModelProvider() {
        return aggregationSelectionAgentModelProvider.value();
    }

    public String getAggregationSelectionAgentPromptVersion() {
        return aggregationSelectionAgentPromptVersion.value();
    }
}
