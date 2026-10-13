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
package org.tarik.ta.agents;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.dto.IdentifiedRegions;
import org.tarik.ta.model.ImageContentCreator;

import java.util.List;

/**
 * Locates a UI element on a screenshot. The shape in which the model reports that location - two opposite corners or a click
 * position plus a size - is chosen by configuration and wired in as the agent's system prompt and final-result tool.
 */
public interface UiElementGroundingAgent extends BaseUiAgent<IdentifiedRegions>, VisionBasedAgent {
    Result<String> identifyElementRegions(@UserMessage List<Content> userMessage, @UserMessage ImageContent screenshot);

    /**
     * Exposed statically as well, because {@link SchemaConstrainedUiElementGrounder} asks the same model without going
     * through an agent and has to encode its screenshots the same way.
     */
    static ImageContentCreator imageContentCreator(UiTestAgentConfig config) {
        return new ImageContentCreator(config.getElementBoundingBoxAgentImageDetailLevel(),
                config.getElementBoundingBoxAgentModelProvider(), config.getMaxModelImagePixels());
    }

    @Override
    default String getAgentTaskDescription() {
        return "Identifying the location of the UI element";
    }

    @Override
    default @NotNull ImageContentCreator getImageContentCreator(@NotNull UiTestAgentConfig config) {
        return imageContentCreator(config);
    }
}
