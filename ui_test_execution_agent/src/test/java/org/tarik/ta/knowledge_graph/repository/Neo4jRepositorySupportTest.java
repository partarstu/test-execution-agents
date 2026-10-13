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
package org.tarik.ta.knowledge_graph.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.Neo4jMigrationState;
import org.tarik.ta.knowledge_graph.model.node.PhraseEmbedding;
import org.tarik.ta.knowledge_graph.model.node.Procedure;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Neo4jRepositorySupportTest {
    private Neo4jRepositorySupport repositorySupport;

    @BeforeEach
    void setUp() {
        repositorySupport = new Neo4jRepositorySupport(mock(Driver.class), mock(UiTestAgentConfig.class), new Neo4jMigrationState());
    }

    @Test
    @DisplayName("cypher selects Cypher 25 for every query, independent of the database default language")
    void cypher_shouldPrefixTheQueryWithCypher25() {
        assertThat(repositorySupport.cypher("MATCH (n:${LABEL_PROCEDURE}) RETURN n"))
                .isEqualTo("CYPHER 25 MATCH (n:Procedure) RETURN n");
    }

    @Test
    @DisplayName("cypher resolves the vector index tokens to the index names the schema migrations create")
    void cypher_shouldResolveVectorIndexTokens() {
        assertThat(repositorySupport.cypher("${INDEX_PHRASE_EMBEDDING} ${INDEX_PROCEDURE_EMBEDDING} ${INDEX_UI_ELEMENT_EMBEDDING}"))
                .isEqualTo("CYPHER 25 phrase_embedding_vector_index procedure_embedding_index ui_element_embedding_index")
                .contains(PhraseEmbedding.VECTOR_INDEX, Procedure.VECTOR_INDEX, UiElement.VECTOR_INDEX);
    }
}
