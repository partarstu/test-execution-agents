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
package org.tarik.ta.knowledge_graph.service;

import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.knowledge_graph.repository.GraphHealthRepository;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CLI entry point for generating a knowledge graph health report outside the running application.
 *
 * <p>Usage: {@code mvn exec:java -pl ui_test_execution_agent
 *   -Dexec.mainClass=org.tarik.ta.knowledge_graph.service.GraphHealthReportCli
 *   [-Dexec.args="--output path/to/report.html"]}</p>
 */
public class GraphHealthReportCli {

    public static void main(String[] args) {
        try (io.avaje.inject.BeanScope scope = io.avaje.inject.BeanScope.builder().build()) {
            var config = scope.get(org.tarik.ta.UiTestAgentConfig.class);
            var outputPath = parseOutputPath(args, config);
            var service = scope.get(GraphHealthService.class);
            var reportPath = service.generateHtmlReport(outputPath);
            System.out.println("Health report generated: " + reportPath.toAbsolutePath());
        } catch (IllegalStateException e) {
            System.err.println("Neo4j connection failed: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to write report: " + e.getMessage());
            System.exit(2);
        }
    }

    private static Path parseOutputPath(String[] args, org.tarik.ta.UiTestAgentConfig config) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--output".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return Path.of(config.getHealthReportOutputPath());
    }
}
