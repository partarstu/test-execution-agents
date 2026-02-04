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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiContent(List<GeminiPart> parts, String role) {

    public GeminiContent {
        parts = parts == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(parts);
    }

    public void addPart(GeminiPart part) {
        parts.add(part);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiPart(
            String text,
            GeminiBlob inlineData,
            GeminiFunctionCall functionCall,
            GeminiFunctionResponse functionResponse,
            GeminiFileData fileData,
            GeminiExecutableCode executableCode,
            GeminiCodeExecutionResult codeExecutionResult,
            Boolean thought,
            String thoughtSignature,
            GeminiMediaResolutionLevel mediaResolution) {

        public GeminiPart {
            if ((inlineData != null || fileData != null) && mediaResolution == null) {
                mediaResolution = GeminiMediaResolutionLevel.ULTRA_HIGH;
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public Boolean isThought() {
            return thought;
        }

        public static class Builder {
            private String text;
            private GeminiBlob inlineData;
            private GeminiFunctionCall functionCall;
            private GeminiFunctionResponse functionResponse;
            private GeminiFileData fileData;
            private GeminiExecutableCode executableCode;
            private GeminiCodeExecutionResult codeExecutionResult;
            private Boolean thought;
            private String thoughtSignature;
            private GeminiMediaResolutionLevel mediaResolution;

            private Builder() {}

            public Builder text(String text) {
                this.text = text;
                return this;
            }

            public Builder inlineData(GeminiBlob inlineData) {
                this.inlineData = inlineData;
                return this;
            }

            public Builder functionCall(GeminiFunctionCall functionCall) {
                this.functionCall = functionCall;
                return this;
            }

            public Builder functionResponse(GeminiFunctionResponse functionResponse) {
                this.functionResponse = functionResponse;
                return this;
            }

            public Builder fileData(GeminiFileData fileData) {
                this.fileData = fileData;
                return this;
            }

            public Builder executableCode(GeminiExecutableCode executableCode) {
                this.executableCode = executableCode;
                return this;
            }

            public Builder codeExecutionResult(GeminiCodeExecutionResult codeExecutionResult) {
                this.codeExecutionResult = codeExecutionResult;
                return this;
            }

            public Builder thought(Boolean thought) {
                this.thought = thought;
                return this;
            }

            public Builder thoughtSignature(String thoughtSignature) {
                this.thoughtSignature = thoughtSignature;
                return this;
            }
            
            public Builder mediaResolution(GeminiMediaResolutionLevel mediaResolution) {
                this.mediaResolution = mediaResolution;
                return this;
            }

            public GeminiPart build() {
                return new GeminiPart(
                        text,
                        inlineData,
                        functionCall,
                        functionResponse,
                        fileData,
                        executableCode,
                        codeExecutionResult,
                        thought,
                        thoughtSignature,
                        mediaResolution);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiBlob(String mimeType, String data) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiFunctionCall(String name, Map<String, Object> args) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiFunctionResponse(String name, Map<String, String> response) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiFileData(String mimeType, String fileUri) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiExecutableCode(GeminiLanguage programmingLanguage, String code) {
            public enum GeminiLanguage {
                PYTHON,
                LANGUAGE_UNSPECIFIED;

                @Override
                public String toString() {
                    return name().toLowerCase();
                }
            }

            public GeminiExecutableCode {
                if (programmingLanguage == null) {
                    programmingLanguage = GeminiLanguage.PYTHON;
                }
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GeminiCodeExecutionResult(GeminiOutcome outcome, String output) {
            public enum GeminiOutcome {
                OUTCOME_UNSPECIFIED,
                OUTCOME_OK,
                OUTCOME_FAILED,
                OUTCOME_DEADLINE_EXCEEDED;

                @Override
                public String toString() {
                    return this.name().toLowerCase();
                }
            }
        }
    }
}
