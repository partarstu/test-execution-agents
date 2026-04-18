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
