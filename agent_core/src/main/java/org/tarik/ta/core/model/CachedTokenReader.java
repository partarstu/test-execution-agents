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
import org.jetbrains.annotations.Nullable;

/**
 * Reads the number of prompt tokens served from the provider's cache. The count is not part of LangChain4j's common
 * {@link TokenUsage}; every provider reports it on its own subclass, and a provider which does not report it at all
 * contributes zero.
 */
final class CachedTokenReader {

    private CachedTokenReader() {
    }

    static int cachedTokens(@Nullable TokenUsage tokenUsage) {
        return switch (tokenUsage) {
            case AnthropicTokenUsage anthropic -> orZero(anthropic.cacheReadInputTokens());
            case GoogleAiGeminiTokenUsage gemini -> orZero(gemini.cachedContentTokenCount());
            case OpenAiTokenUsage openAi -> openAi.inputTokensDetails() == null ? 0
                    : orZero(openAi.inputTokensDetails().cachedTokens());
            case null, default -> 0;
        };
    }

    private static int orZero(@Nullable Integer value) {
        return value != null ? value : 0;
    }
}
