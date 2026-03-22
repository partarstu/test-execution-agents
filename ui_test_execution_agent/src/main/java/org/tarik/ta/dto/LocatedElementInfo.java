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

import java.util.UUID;

@Description("Represents the location of UI element on the screen.")
public record LocatedElementInfo(
        @Description("The name of the element retrieved from DB and used during location.") String name,
        @Description("The UUID of the UI element in the database.") UUID elementId,
        @Description("The X coordinate of the center point of UI element in logical pixels (DPI-scaled, for robot interaction).") int centerXCoordinate,
        @Description("The Y coordinate of the center point of UI element in logical pixels (DPI-scaled, for robot interaction).") int centerYCoordinate,
        @Description("The physical screen region in pixels where the UI element was identified.") ScreenRegion elementScreenRegion) {
}
