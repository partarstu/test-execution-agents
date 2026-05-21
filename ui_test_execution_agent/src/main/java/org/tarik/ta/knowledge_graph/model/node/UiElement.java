/*
 * ui-test-execution-agent - ${project.description}
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
package org.tarik.ta.knowledge_graph.model.node;

import dev.langchain4j.model.output.structured.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
