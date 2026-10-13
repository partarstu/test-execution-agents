/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.utils;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptUtilsTest {

    @Test
    void loadSystemPrompt_ShouldLoadContent_WhenFileExists() {
        String content = PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-prompt.md");
        assertThat(content).contains("This is a test prompt.")
                           .contains("It has multiple lines.");
    }

    @Test
    void loadSystemPrompt_ShouldThrowException_WhenFileDoesNotExist() {
        assertThatThrownBy(() -> PromptUtils.loadSystemPrompt("non-existent", "v1.0.0", "prompt.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt file not found");
    }

    @Test
    void loadSystemPrompt_ShouldInjectIncludedFilesRecursively_WhenPromptContainsIncludeLines() {
        String content = PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-prompt-with-include.md");

        assertThat(content).isEqualTo(String.join(System.lineSeparator(),
                "Before the fragment.", "Fragment line.", "This is a test prompt.", "It has multiple lines.", "After the fragment."));
    }

    @Test
    void loadSystemPrompt_ShouldThrowException_WhenIncludedFileDoesNotExist() {
        assertThatThrownBy(() -> PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-prompt-with-missing-include.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt file not found: prompt_templates/system/agents/test-agent/v1.0.0/missing-fragment.md");
    }

    @Test
    void loadSystemPrompt_ShouldThrowException_WhenPromptIncludesItself() {
        assertThatThrownBy(() -> PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-prompt-including-itself.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt include cycle: test-agent/v1.0.0/test-prompt-including-itself.md -> "
                        + "test-agent/v1.0.0/test-prompt-including-itself.md");
    }

    @Test
    void loadSystemPrompt_ShouldThrowException_WhenTwoPromptsIncludeEachOther() {
        assertThatThrownBy(() -> PromptUtils.loadSystemPrompt("test-agent", "v1.0.0", "test-cycle-a.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt include cycle: test-agent/v1.0.0/test-cycle-a.md -> test-agent/v1.0.0/test-cycle-b.md -> "
                        + "test-agent/v1.0.0/test-cycle-a.md");
    }
}
