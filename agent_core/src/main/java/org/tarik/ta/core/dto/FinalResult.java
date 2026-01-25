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
package org.tarik.ta.core.dto;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;

public interface FinalResult<T> {
    String TOOL_DESCRIPTION = "Ends the execution and returns the final result, if any.";
    String FINAL_RESULT_PARAM_DESCRIPTION = "The final result of the execution";
    Logger LOG = LoggerFactory.getLogger(FinalResult.class);

    @Tool(value = TOOL_DESCRIPTION, returnBehavior = IMMEDIATE)
    default T endExecutionAndGetFinalResult(@P(FINAL_RESULT_PARAM_DESCRIPTION) T result) {
        LOG.debug("Ending execution and returning the final result: {}", result);
        return result;
    }
}
