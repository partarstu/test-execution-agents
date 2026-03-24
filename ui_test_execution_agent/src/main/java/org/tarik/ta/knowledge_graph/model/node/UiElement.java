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
package org.tarik.ta.knowledge_graph.model.node;

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.location_history.LocationStrategy;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.UUID;

import static org.tarik.ta.utils.ImageUtils.*;

public class UiElement implements IEntity {

    public static final String LABEL = "UiElement";
    public static final String PROP_NAME = "name";
    public static final String PROP_OWN_DESCRIPTION = "ownDescription";
    public static final String PROP_ANCHORS_DESCRIPTION = "anchorsDescription";
    public static final String PROP_PARENT_ELEMENT_SUMMARY = "parentElementSummary";
    public static final String PROP_SCREENSHOT_FILE_EXTENSION = "screenshotFileExtension";
    public static final String PROP_SCREENSHOT_MIME_TYPE = "screenshotMimeType";
    public static final String PROP_SCREENSHOT_IMAGE = "screenshotImage";
    public static final String PROP_IS_DATA_DEPENDENT = "isDataDependent";
    public static final String PROP_STABILITY_SCORE = "stabilityScore";
    public static final String PROP_AVG_LOCATION_TIME_MS = "avgLocationTimeMs";
    public static final String PROP_LOCATION_STRATEGY = "locationStrategy";
    public static final String PROP_FAILED_LOCATION_COUNT = "locationRetriesCount";
    public static final String PROP_LAST_LOCATED_AT = "lastLocatedAt";

    private final UUID id;
    private final String name;
    private final String ownDescription;
    private final String anchorsDescription;
    private final String parentElementSummary;
    private final Screenshot screenshot;
    private final boolean isDataDependent;

    public UiElement(@NotNull UUID id,
                     @NotNull String name,
                     @NotNull String ownDescription,
                     @NotNull String anchorsDescription,
                     @NotNull String parentElementSummary,
                     @Nullable Screenshot screenshot,
                     boolean isDataDependent) {
        this.id = id;
        this.name = name;
        this.ownDescription = ownDescription;
        this.anchorsDescription = anchorsDescription;
        this.parentElementSummary = parentElementSummary;
        this.screenshot = screenshot;
        this.isDataDependent = isDataDependent;
    }

    @Override
    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return ownDescription;
    }

    public String locationDetails() {
        return anchorsDescription;
    }

    public String parentElementSummary() {
        return parentElementSummary;
    }

    public Screenshot screenshot() {
        return screenshot;
    }

    public boolean isDataDependent() {
        return isDataDependent;
    }

    public record Screenshot(@NotNull String fileExtension, @NotNull String mimeType, @NotNull String base64EncodedImage) {
        public static Screenshot fromBufferedImage(@NotNull BufferedImage image, @NotNull String fileExtension) {
            String mimeType = "image/" + fileExtension;
            String base64EncodedImage = convertImageToBase64(image, fileExtension);
            return new Screenshot(fileExtension, mimeType, base64EncodedImage);
        }

        public BufferedImage toBufferedImage() {
            return convertBase64ToImage(base64EncodedImage);
        }
    }

    @Description("Historical location data for a UI element, used to decide the best approach for locating it")
    public record ElementLocationHistory(
            @Description("Stability score from 0.0 to 1.0, where 1.0 means the element was always located successfully and 0.0 means " +
                    "it always failed")
            double stabilityScore,
            @Description("Average time in milliseconds it took to locate this element in previous attempts")
            long avgLocationTimeMs,
            @Description("The location strategy that was most recently used to successfully locate this element (using visual " +
                    "grounding task, using hybrid search etc.)")
            LocationStrategy locationStrategy,
            @Description("Average number of retries needed to locate this element; higher values indicate an unstable element or " +
                    "element which gets visible after some delay")
            double locationRetriesCount,
            @Description("Timestamp of the last successful location of this element")
            Instant lastLocatedAt) {
    }

    @NotNull
    @Override
    public String toString() {
        return new StringJoiner(", ", UiElement.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("ownDescription='" + ownDescription + "'")
                .add("locationDescription='" + anchorsDescription + "'")
                .add("parentElementSummary='" + parentElementSummary + "'")
                .add("isDataDependent=" + isDataDependent)
                .toString();
    }
}
