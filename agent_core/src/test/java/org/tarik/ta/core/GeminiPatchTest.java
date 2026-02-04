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
                .isEqualTo(GeminiMediaResolutionLevel.ULTRA_HIGH);
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
