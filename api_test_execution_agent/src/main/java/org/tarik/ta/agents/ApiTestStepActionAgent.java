package org.tarik.ta.agents;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.agents.BaseAiAgent;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.error.RetryPolicy;

public interface ApiTestStepActionAgent extends BaseAiAgent<EmptyExecutionResult> {

    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @Override
    default String getAgentTaskDescription() {
        return "Executing API test step action";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }

    @UserMessage("""
            Execute the following API test step: {{testStep}}
            
            Data related to the test step: {{testData}}
            
            Shared data: {{sharedData}}
            
            Interaction with the user is allowed: {{attendedMode}}
            """)
    Result<String> execute(
            @V("testStep") String testStep,
            @V("testData") String testData,
            @V("sharedData") String sharedData,
            @V("attendedMode") boolean attendedMode);
}
