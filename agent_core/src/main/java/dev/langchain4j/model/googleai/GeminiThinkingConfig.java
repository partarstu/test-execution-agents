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
package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiThinkingConfig {
    @JsonProperty("includeThoughts")
    private final Boolean includeThoughts;
    @JsonProperty("thinkingBudget")
    private final Integer thinkingBudget;
    @JsonProperty("thinkingLevel")
    private final GeminiThinkingLevel thinkingLevel;

    public GeminiThinkingConfig(Boolean includeThoughts, Integer thinkingBudget, GeminiThinkingLevel thinkingLevel) {
        this.includeThoughts = includeThoughts;
        this.thinkingBudget = thinkingBudget;
        this.thinkingLevel = thinkingLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Boolean includeThoughts;
        private Integer thinkingBudget;
        private GeminiThinkingLevel thinkingLevel;

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder thinkingLevel(GeminiThinkingLevel thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public GeminiThinkingConfig build() {
            return new GeminiThinkingConfig(includeThoughts, thinkingBudget, thinkingLevel);
        }
    }
}
