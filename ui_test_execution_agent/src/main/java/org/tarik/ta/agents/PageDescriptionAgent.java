package org.tarik.ta.agents;


import org.tarik.ta.core.agents.BaseAiAgent;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.dto.PageDescriptionResult;
import org.tarik.ta.core.error.RetryPolicy;

public interface PageDescriptionAgent extends BaseUiAgent<PageDescriptionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    @UserMessage("Screenshot is attached.")
    Result<String> describePage(@UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Generating the description of the page";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}


