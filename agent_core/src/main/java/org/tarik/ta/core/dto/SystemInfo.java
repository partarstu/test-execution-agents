/*
 * agent-core - ${project.description}
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
package org.tarik.ta.core.dto;

import dev.langchain4j.model.output.structured.Description;

@Description("Description of the system on which the agent executed the test case")
public record SystemInfo(
        @Description("Device name or type (e.g., iPhone 14, Windows 11 PC)") String device,
        @Description("Operating System version") String osVersion,
        @Description("Browser name and version (if applicable)") String browser,
        @Description("Execution environment (e.g., QA Staging, Production, Dev)") String environment) {
}
