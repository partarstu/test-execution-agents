/*
 * procedure-knowledge-agent - Agent selecting the recorded procedures of the knowledge graph that match the items of a test case.
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
@InjectModule(requires = {
        BudgetManager.class,
        ModelFactory.class,
        AbstractServer.class,
        AgentExecutionResource.class
})
package org.tarik.ta;

import io.avaje.inject.InjectModule;
import org.tarik.ta.core.AbstractServer;
import org.tarik.ta.core.a2a.AgentExecutionResource;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.ModelFactory;
