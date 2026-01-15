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
