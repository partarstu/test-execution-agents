package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GeminiMediaResolution(@JsonProperty("level") String level) {
    public static final GeminiMediaResolution HIGH = new GeminiMediaResolution("MEDIA_RESOLUTION_HIGH");
}
