package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeminiMediaResolutionLevel(@JsonProperty("level") String level) {
    public static final GeminiMediaResolutionLevel HIGH = new GeminiMediaResolutionLevel("MEDIA_RESOLUTION_HIGH");
    public static final GeminiMediaResolutionLevel ULTRA_HIGH = new GeminiMediaResolutionLevel("MEDIA_RESOLUTION_ULTRA_HIGH");
}
