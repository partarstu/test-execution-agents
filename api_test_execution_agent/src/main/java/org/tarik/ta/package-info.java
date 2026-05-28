/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
@io.avaje.inject.InjectModule(requires = {
    org.tarik.ta.core.AgentConfig.class,
    org.tarik.ta.core.config.scopes.BaseAgentRequestScope.class,
    org.tarik.ta.core.manager.BudgetManager.class,
    org.tarik.ta.core.model.ModelFactory.class,
    org.tarik.ta.core.model.TestExecutionContext.class,
    org.tarik.ta.core.tools.TestContextDataTools.class,
    org.tarik.ta.core.utils.LogCapture.class,
    org.tarik.ta.core.utils.TestCaseExtractor.class,
    org.tarik.ta.core.AbstractServer.class,
    org.tarik.ta.core.a2a.AgentExecutionResource.class,
    org.tarik.ta.core.a2a.StreamingEventEmitter.class
})
package org.tarik.ta;
