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
package org.tarik.ta.a2a;

import io.a2a.spec.Part;
import jakarta.inject.Singleton;
import org.tarik.ta.ApiTestAgent;
import org.tarik.ta.ApiAgentRequestScopeFactory;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.dto.TestExecutionResult;

import java.util.List;
import java.util.Optional;

@Singleton
public class ApiAgentExecutor extends AbstractAgentExecutor {
    private final ApiAgentRequestScopeFactory requestScopeFactory;

    public ApiAgentExecutor(ApiAgentRequestScopeFactory requestScopeFactory) {
        this.requestScopeFactory = requestScopeFactory;
    }

    @Override
    protected TestExecutionResult executeTestCase(String message) {
        try (var requestScope = requestScopeFactory.create()) {
            return requestScope.get(ApiTestAgent.class).executeTestCase(message);
        }
    }

    @Override
    protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
        // No specific artifacts for API tests yet
    }

    @Override
    protected Optional<List<String>> extractLogs(TestExecutionResult result) {
        return Optional.ofNullable(result.getLogs());
    }
}
