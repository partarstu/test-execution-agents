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
