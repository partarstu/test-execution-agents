package org.tarik.ta.core;

import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiMediaResolutionLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeminiPatchTest {

    @Test
    void shouldDefaultMediaResolutionToHighForInlineImages() {
        // Given
        GeminiBlob imageBlob = new GeminiBlob("image/png", "base64data");

        // When
        GeminiPart part = GeminiPart.builder()
                .inlineData(imageBlob)
                .build();

        // Then
        assertThat(part.mediaResolution())
                .as("Media resolution should automatically be set to HIGH for parts with inline data")
                .isEqualTo(GeminiMediaResolutionLevel.HIGH);
    }

    @Test
    void shouldNotSetMediaResolutionForTextOnlyParts() {
        // When
        GeminiPart part = GeminiPart.builder()
                .text("Hello World")
                .build();

        // Then
        assertThat(part.mediaResolution())
                .as("Media resolution should be null for text-only parts")
                .isNull();
    }
}
