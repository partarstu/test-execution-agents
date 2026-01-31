package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.core.tools.AbstractTools;

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
