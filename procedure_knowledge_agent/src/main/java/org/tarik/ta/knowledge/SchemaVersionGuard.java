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

import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.RoutingControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.knowledge_graph.GraphSchemaGuard;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.tarik.ta.knowledge_graph.model.node.SchemaVersion.CURRENT;
import static org.tarik.ta.knowledge_graph.model.node.SchemaVersion.LABEL;
import static org.tarik.ta.knowledge_graph.model.node.SchemaVersion.PROP_VERSION;
import static org.tarik.ta.knowledge_graph.repository.Neo4jRepositorySupport.CYPHER_VERSION_PREFIX;

/**
 * Lets the agent read only a graph whose schema is the version its queries are written for; it never migrates, because the UI agent
 * owns the schema.
 */
public class SchemaVersionGuard implements GraphSchemaGuard {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaVersionGuard.class);
    private static final String READ_VERSION = CYPHER_VERSION_PREFIX + "MATCH (sv:%s) RETURN sv.%s AS version".formatted(LABEL, PROP_VERSION);

    private final Driver driver;
    private final String databaseName;
    private final AtomicBoolean verified = new AtomicBoolean(false);

    public SchemaVersionGuard(@NotNull Driver driver, @NotNull String databaseName) {
        this.driver = driver;
        this.databaseName = databaseName;
    }

    /**
     * Throws {@link IllegalStateException} when the graph has no schema version or another one than this agent requires.
     */
    @Override
    public void ensureSchemaReady() {
        if (verified.get()) {
            return;
        }
        var records = driver.executableQuery(READ_VERSION)
                .withConfig(QueryConfig.builder().withDatabase(databaseName).withRouting(RoutingControl.READ).build())
                .execute()
                .records();
        var version = records.isEmpty() ? 0 : records.getFirst().get(PROP_VERSION).asInt(0);
        if (version != CURRENT) {
            throw new IllegalStateException(("The knowledge graph schema is at version %d, but this agent reads version %d; run the UI agent's " +
                    "schema migrations or deploy the agent version matching the graph").formatted(version, CURRENT));
        }
        verified.set(true);
        LOG.info("Knowledge graph schema version {} verified", version);
    }
}
