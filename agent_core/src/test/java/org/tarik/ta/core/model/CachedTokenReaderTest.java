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
package org.tarik.ta.core.model;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachedTokenReaderTest {

    @Test
    void cachedTokens_shouldReadTheAnthropicCacheReadTokens() {
        var tokenUsage = AnthropicTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .cacheReadInputTokens(7)
                .build();

        assertThat(CachedTokenReader.cachedTokens(tokenUsage)).isEqualTo(7);
    }

    @Test
    void cachedTokens_shouldReadTheGeminiCachedContentTokens() {
        var tokenUsage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .cachedContentTokenCount(5)
                .build();

        assertThat(CachedTokenReader.cachedTokens(tokenUsage)).isEqualTo(5);
    }

    @Test
    void cachedTokens_shouldReadTheOpenAiCachedInputTokens() {
        var tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(20)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(3).build())
                .build();

        assertThat(CachedTokenReader.cachedTokens(tokenUsage)).isEqualTo(3);
    }

    @Test
    void cachedTokens_shouldBeZero_whenTheProviderReportsNone() {
        assertThat(CachedTokenReader.cachedTokens(new TokenUsage(10, 20))).isZero();
        assertThat(CachedTokenReader.cachedTokens(null)).isZero();
        assertThat(CachedTokenReader.cachedTokens(OpenAiTokenUsage.builder().inputTokenCount(10).build())).isZero();
    }
}
