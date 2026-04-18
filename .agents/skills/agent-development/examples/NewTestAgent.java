/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta;

import jakarta.inject.Singleton;
import org.tarik.ta.agents.NewActionAgent;
import org.tarik.ta.core.dto.TestExecutionResult;

@Singleton
public class NewTestAgent {
    private final NewActionAgent actionAgent;

    public NewTestAgent(NewActionAgent actionAgent) {
        this.actionAgent = actionAgent;
    }

    public TestExecutionResult executeTestCase(String message) {
        // Implementation of test execution orchestration
        var result = actionAgent.execute(message);
        return new TestExecutionResult(message, result.isSuccess());
    }
}
