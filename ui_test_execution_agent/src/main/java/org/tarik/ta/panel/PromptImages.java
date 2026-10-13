/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
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
package org.tarik.ta.panel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.knowledge_graph.model.node.UiElement;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.tarik.ta.utils.ImageUtils.imageToByteArray;

/**
 * The screenshots one prompt shows, served under the prompt and dropped with it. Thread-safe; the oldest image is dropped beyond
 * {@link #MAX_IMAGES} and only the latest capture keeps its raw image, so repeated captures and searches cannot grow it without bound.
 */
final class PromptImages {
    static final int MAX_IMAGES = 100;
    static final String PNG = "image/png";
    private static final Set<String> SERVED_TYPES = Set.of(PNG, "image/jpeg");

    private final Map<String, PromptImage> images = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PromptImage> eldest) {
            return size() > MAX_IMAGES;
        }
    };
    private @Nullable LatestCapture latestCapture;

    record PromptImage(byte[] bytes, String contentType) {
    }

    private record LatestCapture(String id, BufferedImage screen) {
    }

    @NotNull String addScreen(@NotNull BufferedImage screen) {
        return put(new PromptImage(imageToByteArray(screen, "png"), PNG));
    }

    /** Adds a screen capture to crop an element screenshot from, replacing the previous one as the capture to crop. */
    @NotNull String addCapture(@NotNull BufferedImage screen) {
        var id = addScreen(screen);
        synchronized (images) {
            latestCapture = new LatestCapture(id, screen);
        }
        return id;
    }

    /** Adds the stored screenshot of an element when it has a type the panel serves. */
    @NotNull Optional<String> add(@NotNull UiElement.Screenshot screenshot) {
        if (!SERVED_TYPES.contains(screenshot.mimeType())) {
            return Optional.empty();
        }
        var bytes = Base64.getDecoder().decode(screenshot.base64EncodedImage());
        return Optional.of(put(new PromptImage(bytes, screenshot.mimeType())));
    }

    @NotNull Optional<PromptImage> find(@NotNull String id) {
        synchronized (images) {
            return Optional.ofNullable(images.get(id));
        }
    }

    /** Returns the raw image of the capture with this id while it is the latest capture. */
    @NotNull Optional<BufferedImage> latestCapture(@NotNull String id) {
        synchronized (images) {
            return latestCapture != null && latestCapture.id().equals(id) ? Optional.of(latestCapture.screen()) : Optional.empty();
        }
    }

    private String put(PromptImage image) {
        var id = UUID.randomUUID().toString();
        synchronized (images) {
            images.put(id, image);
        }
        return id;
    }
}
