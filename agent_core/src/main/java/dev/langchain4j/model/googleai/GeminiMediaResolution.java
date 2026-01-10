package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum GeminiMediaResolution {
    MEDIA_RESOLUTION_HIGH,    MEDIA_RESOLUTION_ULTRA_HIGH;
}
