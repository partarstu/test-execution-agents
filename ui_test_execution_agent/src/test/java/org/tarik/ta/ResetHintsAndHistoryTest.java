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
package org.tarik.ta;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class ResetHintsAndHistoryTest {

    @Test
    @Disabled("Integration test for resetting hints and history in remote Neo4j DB, requires manual trigger")
    public void testReset() {
        var config = new UiTestAgentConfig();
        String uri = config.getVectorDbUrl();
        String user = config.getNeo4jUsername();
        String pass = config.getVectorDbToken();

        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(user, pass))) {
            System.out.println("Executing reset...");

            System.out.println("Deleting failure hints...");
            driver.executableQuery("MATCH (fc:FailureContext) DETACH DELETE fc").execute();

            System.out.println("Clearing UI element location history...");
            driver.executableQuery("""
                    MATCH (el:UiElement)
                    REMOVE el.stabilityScore,
                           el.avgLocationTimeMs,
                           el.locationRetriesCount,
                           el.lastLocatedAt
                    """).execute();

            System.out.println("Reset successfully.");
        }
    }
}
