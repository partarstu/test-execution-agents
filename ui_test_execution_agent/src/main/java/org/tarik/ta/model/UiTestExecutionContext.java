/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.model;

import jakarta.inject.Inject;
import org.tarik.ta.core.model.TestExecutionContext;
import org.tarik.ta.config.scopes.UiAgentRequestScope;

/**
 * Holds the context and state of the current UI test execution, including visual state.
 */
@UiAgentRequestScope
public class UiTestExecutionContext extends TestExecutionContext {
    private VisualState visualState;

    @Inject
    public UiTestExecutionContext(VisualState visualState) {
        this.visualState = visualState;
    }

    public synchronized VisualState getVisualState() {
        return visualState;
    }

    public synchronized void setVisualState(VisualState visualState) {
        this.visualState = visualState;
    }
}
