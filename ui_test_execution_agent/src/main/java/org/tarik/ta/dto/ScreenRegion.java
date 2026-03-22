/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.dto;

import dev.langchain4j.model.output.structured.Description;

import java.awt.*;

/**
 * A rectangular region on the screen in physical pixel coordinates (before any DPI scaling).
 * Callers are responsible for applying DPI scaling when converting to logical coordinates
 * for Robot interaction or screen capture.
 */
@Description("A rectangular region on the screen defined in physical pixel coordinates.")
public record ScreenRegion(
        @Description("The x-coordinate of the top-left corner in physical screen pixels") int x,
        @Description("The y-coordinate of the top-left corner in physical screen pixels") int y,
        @Description("The width of the region in physical screen pixels") int width,
        @Description("The height of the region in physical screen pixels") int height) {

    public Rectangle toRectangle() {
        return new Rectangle(x, y, width, height);
    }
}
