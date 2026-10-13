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
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.model.ImageContentCreator;

import java.util.List;

public interface UiElementExtendedDescriptionAgent extends BaseUiAgent<UiElementIdentificationResult>, VisionBasedAgent {
    Result<String> describeUiElement(@UserMessage List<Content> userMessage, @UserMessage ImageContent screenshot);

    @Override
    default String getAgentTaskDescription() {
        return "Describing the UI element based on description";
    }

    @Override
    default @NotNull ImageContentCreator getImageContentCreator(@NotNull UiTestAgentConfig config) {
        return new ImageContentCreator(config.getUiElementDescriptionMatcherAgentImageDetailLevel(),
                config.getUiElementDescriptionMatcherAgentModelProvider(), config.getMaxModelImagePixels());
    }
}
