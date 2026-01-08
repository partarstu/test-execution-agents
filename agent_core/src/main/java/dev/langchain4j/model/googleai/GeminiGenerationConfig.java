package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeminiGenerationConfig(
        @JsonProperty("stopSequences") List<String> stopSequences,
        @JsonProperty("responseMimeType") String responseMimeType,
        @JsonProperty("responseSchema") GeminiSchema responseSchema,
        @JsonProperty("responseJsonSchema") Map<String, Object> responseJsonSchema,
        @JsonProperty("candidateCount") Integer candidateCount,
        @JsonProperty("maxOutputTokens") Integer maxOutputTokens,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("topK") Integer topK,
        @JsonProperty("seed") Integer seed,
        @JsonProperty("topP") Double topP,
        @JsonProperty("presencePenalty") Double presencePenalty,
        @JsonProperty("frequencyPenalty") Double frequencyPenalty,
        @JsonProperty("thinkingConfig") GeminiThinkingConfig thinkingConfig,
        @JsonProperty("responseLogprobs") Boolean responseLogprobs,
        @JsonProperty("enableEnhancedCivicAnswers") Boolean enableEnhancedCivicAnswers,
        @JsonProperty("logprobs") Integer logprobs,
        @JsonProperty("media_resolution") GeminiMediaResolution mediaResolution) {

    public static GeminiGenerationConfigBuilder builder() {
        return new GeminiGenerationConfigBuilder();
    }

    public static class GeminiGenerationConfigBuilder {

        private List<String> stopSequences;
        private String responseMimeType;
        private GeminiSchema responseSchema;
        private Map<String, Object> responseJsonSchema;
        private Integer candidateCount;
        private Integer maxOutputTokens;
        private Double temperature;
        private Integer topK;
        private Integer seed;
        private Double topP;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Boolean responseLogprobs;
        private Boolean enableEnhancedCivicAnswers;
        private GeminiThinkingConfig thinkingConfig;
        private Integer logprobs;
        private GeminiMediaResolution mediaResolution = GeminiMediaResolution.MEDIA_RESOLUTION_HIGH;

        GeminiGenerationConfigBuilder() {
        }

        public GeminiGenerationConfigBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GeminiGenerationConfigBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public GeminiGenerationConfigBuilder responseSchema(GeminiSchema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public GeminiGenerationConfigBuilder responseJsonSchema(Map<String, Object> responseJsonSchema) {
            this.responseJsonSchema = responseJsonSchema;
            return this;
        }

        public GeminiGenerationConfigBuilder candidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
            return this;
        }

        public GeminiGenerationConfigBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GeminiGenerationConfigBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public GeminiGenerationConfigBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public GeminiGenerationConfigBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GeminiGenerationConfigBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GeminiGenerationConfigBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public GeminiGenerationConfigBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public GeminiGenerationConfigBuilder thinkingConfig(GeminiThinkingConfig thinkingConfig) {
            this.thinkingConfig = thinkingConfig;
            return this;
        }

        public GeminiGenerationConfigBuilder responseLogprobs(Boolean responseLogprobs) {
            this.responseLogprobs = responseLogprobs;
            return this;
        }

        public GeminiGenerationConfigBuilder enableEnhancedCivicAnswers(Boolean enableEnhancedCivicAnswers) {
            this.enableEnhancedCivicAnswers = enableEnhancedCivicAnswers;
            return this;
        }

        public GeminiGenerationConfigBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public GeminiGenerationConfigBuilder mediaResolution(GeminiMediaResolution mediaResolution) {
            this.mediaResolution = mediaResolution;
            return this;
        }

        public GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(
                    stopSequences,
                    responseMimeType,
                    responseSchema,
                    responseJsonSchema,
                    candidateCount,
                    maxOutputTokens,
                    temperature,
                    topK,
                    seed,
                    topP,
                    presencePenalty,
                    frequencyPenalty,
                    thinkingConfig,
                    responseLogprobs,
                    enableEnhancedCivicAnswers,
                    logprobs,
                    mediaResolution);
        }
    }
}
