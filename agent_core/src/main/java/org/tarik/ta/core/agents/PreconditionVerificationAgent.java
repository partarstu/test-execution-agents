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
package org.tarik.ta.core.agents;

import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.dto.VerificationExecutionResult;

/**
 * Agent responsible for verifying executed test case preconditions.
 */
public interface PreconditionVerificationAgent extends BaseAiAgent<VerificationExecutionResult> {
    RetryPolicy RETRY_POLICY = AgentConfig.getVerificationRetryPolicy();

    @Override
    default String getAgentTaskDescription() {
        return "Verifying precondition";
    }

    @Override
    default RetryPolicy getRetryPolicy() {
        return RETRY_POLICY;
    }
}