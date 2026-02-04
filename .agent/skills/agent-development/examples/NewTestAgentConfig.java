/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
