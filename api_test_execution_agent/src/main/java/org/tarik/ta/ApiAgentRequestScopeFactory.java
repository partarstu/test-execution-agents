/*
 * api-test-execution-agent - ${project.description}
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