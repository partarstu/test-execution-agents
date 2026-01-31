package org.tarik.ta.dto;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.core.dto.FinalResult;

public record NewActionResult(
    boolean success,
    String message,
    String details
) implements FinalResult {
    
    @Tool(name = "submitResult", value = FinalResult.TOOL_DESCRIPTION)
    public static NewActionResult submitResult(
            @P(FinalResult.FINAL_RESULT_PARAM_DESCRIPTION) NewActionResult result) {
        return result;
    }
}
