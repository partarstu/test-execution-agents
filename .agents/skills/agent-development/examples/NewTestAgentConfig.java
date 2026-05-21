/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
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

import org.tarik.ta.core.AgentConfig;

public class NewTestAgentConfig extends AgentConfig {
    
    // Agent-specific configuration properties
    public static String getCustomProperty() {
        return getProperty("custom.property", "default-value");
    }
    
    public static int getCustomTimeout() {
        return getIntProperty("custom.timeout", 30000);
    }
}
