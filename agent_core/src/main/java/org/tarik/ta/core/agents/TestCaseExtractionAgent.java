package org.tarik.ta.core.agents;


import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.dto.TestCase;

public interface TestCaseExtractionAgent extends BaseAiAgent<TestCase> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @UserMessage("{{user_request}}")
    Result<String> extractTestCase(@V("user_request") String userRequest);

    @Override
    default String getAgentTaskDescription() {
        return "Extracting test case from user request";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}


