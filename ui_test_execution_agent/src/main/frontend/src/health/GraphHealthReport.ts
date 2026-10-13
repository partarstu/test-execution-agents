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

// Mirrors org.tarik.ta.knowledge_graph.health.GraphHealthReport; GraphHealthRouteTest pins its JSON shape.

export type Severity = 'OK' | 'WARNING' | 'CRITICAL';

export interface NodeRef {
  label: string;
  id: string;
}

export interface HealthFinding {
  text: string;
  refs: NodeRef[];
}

export interface HealthCheckCategory {
  name: string;
  description: string;
  findings: HealthFinding[];
  severity: Severity;
}

export interface GraphHealthReport {
  categories: HealthCheckCategory[];
  generatedAt: string;
}
