/*
 * Test Execution Agent Parent - Parent build/dependency management for the Test Execution Agents system.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
