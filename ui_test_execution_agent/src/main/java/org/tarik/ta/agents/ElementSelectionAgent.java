package org.tarik.ta.agents;


import org.tarik.ta.core.agents.BaseAiAgent;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.core.error.RetryPolicy;

public interface ElementSelectionAgent extends BaseAiAgent<UiElementIdentificationResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getActionRetryPolicy();

    Result<String> selectBestElement(@V("bounding_box_color") String boundingBoxColor, @UserMessage String prompt,
                                                            @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Selecting the best matching UI element";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}


