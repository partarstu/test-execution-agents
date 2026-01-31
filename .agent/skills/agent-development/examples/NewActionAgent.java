package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.agents.GenericAiAgent;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.dto.NewActionResult;

import static org.tarik.ta.core.AgentConfig.getActionRetryPolicy;

/**
 * Agent responsible for [specific task description].
 */
public interface NewActionAgent extends GenericAiAgent<NewActionResult> {
    RetryPolicy RETRY_POLICY = getActionRetryPolicy();

    @UserMessage("""
            Action to execute: {{action}}
            
            Context data: {{context}}
            """)
    Result<String> execute(
            @V("action") String action,
            @V("context") String context);

    @Override
    default String getAgentTaskDescription() {
        return "Executing new action";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}
