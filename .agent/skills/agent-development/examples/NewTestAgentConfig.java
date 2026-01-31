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
