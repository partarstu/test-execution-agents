/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.core.utils;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptUtilsTest {

    @Test
    void loadSystemPrompt_ShouldLoadContent_WhenFileExists() {
        String content = PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-prompt.txt");
        assertThat(content).contains("This is a test prompt.")
                           .contains("It has multiple lines.");
    }

    @Test
    void loadSystemPrompt_ShouldThrowException_WhenFileDoesNotExist() {
        assertThatThrownBy(() -> PromptUtils.loadSystemPrompt("non-existent", "v1.0.0", "prompt.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt file not found");
    }
}
