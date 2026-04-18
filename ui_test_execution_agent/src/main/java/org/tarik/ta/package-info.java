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
@InjectModule(requires = {
        AgentConfig.class,
        BaseAgentRequestScope.class,
        BudgetManager.class,
        ModelFactory.class,
        TestExecutionContext.class,
        TestContextDataTools.class,
        LogCapture.class,
        TestCaseExtractor.class,
        AbstractServer.class,
        AgentExecutionResource.class,
        VisualState.class
})
package org.tarik.ta;

import io.avaje.inject.InjectModule;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.core.utils.LogCapture;
import org.tarik.ta.core.utils.TestCaseExtractor;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.config.scopes.BaseAgentRequestScope;
import org.tarik.ta.model.VisualState;