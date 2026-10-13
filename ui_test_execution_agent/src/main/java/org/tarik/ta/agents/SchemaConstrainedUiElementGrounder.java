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

import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.UiTestAgentConfig;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Asks a GUI grounding model for an element's location, constraining the answer with a JSON schema rather
 * than collecting it through a tool call.
 */
public final class SchemaConstrainedUiElementGrounder implements UiElementGrounder {
    /**
     * Names the one configuration that empties the answer, because the raw symptom - a null text where valid JSON is
     * expected - points nowhere near its cause.
     */
    private static final String NO_CONTENT_MESSAGE = """
            The grounding model returned no content. A schema-constrained request must be sent with thinking disabled: \
            with thinking on, the chat template opens a think block, the schema has the model emit its answer immediately \
            inside it, and the whole valid answer is reported as reasoning while the content stays empty. Measured on \
            Holo3.1: identical answer and token count either way, only the field it arrives in changes.""";

    private final ChatModel chatModel;
    private final UiTestAgentConfig config;
    private final GroundingProtocol protocol;

    public SchemaConstrainedUiElementGrounder(@NotNull ChatModel chatModel, @NotNull UiTestAgentConfig config,
                                              @NotNull GroundingProtocol protocol) {
        this.chatModel = chatModel;
        this.config = config;
        this.protocol = protocol;
    }

    @Override
    public @NotNull List<Rectangle> locate(@NotNull String elementDescription, @NotNull BufferedImage screenshot) {
        // No system message, and the image ahead of the text: the request H Company documents, which is what these models
        // were trained on.
        var userMessage = UserMessage.from(UiElementGroundingAgent.imageContentCreator(config).createImageContent(screenshot),
                TextContent.from(protocol.instruction(elementDescription)));
        var request = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(protocol.responseFormat())
                .build();
        var answer = chatModel.chat(request).aiMessage().text();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException(NO_CONTENT_MESSAGE);
        }
        return protocol.toRectangles(answer, screenshot.getWidth(), screenshot.getHeight(),
                config.isBoundingBoxAlreadyNormalized());
    }
}