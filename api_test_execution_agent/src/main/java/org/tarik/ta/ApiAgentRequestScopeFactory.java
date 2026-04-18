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

import io.avaje.inject.BeanScope;
import jakarta.inject.Singleton;
import org.tarik.ta.core.BaseAgentRequestModule;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.utils.TestCaseExtractor;

@Singleton
public class ApiAgentRequestScopeFactory {
    private final BeanScope appScope;

    public ApiAgentRequestScopeFactory(BeanScope appScope) {
        this.appScope = appScope;
    }

    public BeanScope create() {
        return BeanScope.builder()
                .parent(appScope)
                .bean(ApiTestAgentConfig.class, getAppConfig())
                .bean(ModelFactory.class, appScope.get(ModelFactory.class))
                .bean(TestCaseExtractor.class, appScope.get(TestCaseExtractor.class))
                .bean(BudgetManager.class, appScope.get(BudgetManager.class))
                .modules(new BaseAgentRequestModule(), new ApiAgentRequestModule())
                .build();
    }

    private ApiTestAgentConfig getAppConfig() {
        return appScope.get(ApiTestAgentConfig.class);
    }
}