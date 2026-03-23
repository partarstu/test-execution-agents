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

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.tarik.ta.user_dialogs.SpinnerManager;

import io.avaje.inject.Singleton;

@Singleton
public class SpinnerTools {

    @Tool("Displays a spinner with the given message.")
    public void showSpinner(@P("The message to display on the spinner") String message) {
        SpinnerManager.show(message);
    }

    @Tool("Hides the currently visible spinner.")
    public void hideSpinner() {
        SpinnerManager.hide();
    }
}
