/*
 * Test Execution Agent Parent - ${project.description}
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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;
import org.tarik.ta.core.tools.AbstractTools;

@Singleton
public class NewAgentTools extends AbstractTools {
    
    @Tool("Performs a specific operation")
    public String performOperation(
            @P("The input parameter") String input) {
        // Implementation
        return "Result: " + input;
    }
    
    @Tool("Another tool for the agent")
    public boolean validateSomething(
            @P("Value to validate") String value) {
        return value != null && !value.isBlank();
    }
}
