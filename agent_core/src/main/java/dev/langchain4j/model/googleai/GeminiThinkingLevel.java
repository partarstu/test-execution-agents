package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum GeminiThinkingLevel {
    @JsonProperty("MINIMAL")
    MINIMAL,
    @JsonProperty("LOW")
    LOW,
    @JsonProperty("MEDIUM")
    MEDIUM,
    @JsonProperty("HIGH")
    HIGH
}
