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
package org.tarik.ta.knowledge_graph.health;

import java.util.List;

/**
 * A single health-check category containing findings and a computed severity level.
 */
public record HealthCheckCategory(String name, String description, List<String> findings, Severity severity) {

    public enum Severity { OK, WARNING, CRITICAL }

    public HealthCheckCategory {
        findings = List.copyOf(findings);
    }

    public static HealthCheckCategory of(String name, String description, List<String> findings,
                                   int warningThreshold, int criticalThreshold) {
        int count = findings.size();
        var severity = count >= criticalThreshold ? Severity.CRITICAL
                : count >= warningThreshold ? Severity.WARNING
                : Severity.OK;
        return new HealthCheckCategory(name, description, findings, severity);
    }
}
