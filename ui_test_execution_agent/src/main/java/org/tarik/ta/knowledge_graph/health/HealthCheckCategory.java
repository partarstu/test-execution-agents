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
