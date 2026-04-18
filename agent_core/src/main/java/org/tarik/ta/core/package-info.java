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
@io.avaje.inject.InjectModule(
        provides = {
                org.tarik.ta.core.AgentConfig.class,
                org.tarik.ta.core.manager.BudgetManager.class,
                org.tarik.ta.core.config.scopes.BaseAgentRequestScope.class,
                org.tarik.ta.core.model.ModelFactory.class,
                org.tarik.ta.core.model.TestExecutionContext.class,
                org.tarik.ta.core.model.ChatModelEventListener.class,
                org.tarik.ta.core.tools.TestContextDataTools.class,
                org.tarik.ta.core.utils.TestCaseExtractor.class,
                org.tarik.ta.core.utils.LogCapture.class,
                org.tarik.ta.core.AbstractServer.class,
                org.tarik.ta.core.a2a.AgentExecutionResource.class
        },
        requires = {}
)
package org.tarik.ta.core;
