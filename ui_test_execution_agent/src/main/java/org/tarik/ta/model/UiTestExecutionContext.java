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
