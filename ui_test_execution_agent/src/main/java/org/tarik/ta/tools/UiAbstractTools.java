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
package org.tarik.ta.tools;

import org.tarik.ta.agents.UiStateCheckAgent;
import org.tarik.ta.core.tools.AbstractTools;

public class UiAbstractTools extends AbstractTools {
    public static final String AGENT_PATH = "common/ui_state_checker";
    public static final String UI_STATE_CHECKER_PROMPT_FILE = "ui_state_checker_prompt.txt";
    protected final UiStateCheckAgent uiStateCheckAgent;

    protected UiAbstractTools(UiStateCheckAgent uiStateCheckAgent) {
        this.uiStateCheckAgent = uiStateCheckAgent;
    }
}