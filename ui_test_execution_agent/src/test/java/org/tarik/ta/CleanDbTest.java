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
package org.tarik.ta;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class CleanDbTest {

    @Test
    @Disabled("Integration test for cleaning remote Neo4j DB, requires manual trigger")
    public void testClean() {
        var config = new UiTestAgentConfig();
        String uri = config.getVectorDbUrl();
        String user = config.getNeo4jUsername();
        String pass = config.getVectorDbToken();

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass))) {
            System.out.println("Executing clean...");
            driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
            System.out.println("Executed successfully.");
        }
    }
}