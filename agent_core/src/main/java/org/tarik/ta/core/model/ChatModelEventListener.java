/*
 * agent-core - Core execution engine, with common logic for all test execution agents.
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
package org.tarik.ta.core.model;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.manager.BudgetManager;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

@Singleton
public class ChatModelEventListener implements ChatModelListener {
    private static final Logger LOG = LoggerFactory.getLogger(ChatModelEventListener.class);
    private static final String MESSAGE_SEPARATOR = "---------------------------------------------------------------------";

    private final BudgetManager budgetManager;

    @Inject
    public ChatModelEventListener(BudgetManager budgetManager) {
        this.budgetManager = budgetManager;
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var chatResponse = responseContext.chatResponse();
        var aiMessage = chatResponse.aiMessage();

        if (isNotBlank(aiMessage.text())) {
            logWithSeparator("Received model text response", aiMessage.text());
        }
        if (isNotBlank(aiMessage.thinking())) {
            logWithSeparator("Received model thoughts", aiMessage.thinking());
        }
        if (!aiMessage.toolExecutionRequests().isEmpty()) {
            logWithSeparator("Received tool execution requests", aiMessage.toolExecutionRequests().toString());
        }

        if (isBlank(aiMessage.text()) && isBlank(aiMessage.thinking()) && aiMessage.toolExecutionRequests().isEmpty()) {
            LOG.warn("Got an empty response from the model. Original Response: {}", chatResponse);
        }

        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata != null) {
            var metadataInfo = "Got response from model '%s'".formatted(metadata.modelName());
            TokenUsage tokenUsage = metadata.tokenUsage();
            if (tokenUsage != null) {
                int input = ofNullable(tokenUsage.inputTokenCount()).orElse(0);
                int output = ofNullable(tokenUsage.outputTokenCount()).orElse(0);
                int total = ofNullable(tokenUsage.totalTokenCount()).orElse(0);
                int cached = 0;
                String modelName = metadata.modelName() != null ? metadata.modelName() : "Unknown";
                budgetManager.consumeTokens(modelName, input, output, cached);
                metadataInfo = ("%s, input tokens = %d, output tokens = %d, total tokens = %d. " +
                        "Accumulated during the test case execution: input = %d, output = %d, cached = %d, total = %d")
                        .formatted(metadataInfo, input, output, total,
                                budgetManager.getAccumulatedInputTokens(modelName),
                                budgetManager.getAccumulatedOutputTokens(modelName),
                                budgetManager.getAccumulatedCachedTokens(modelName),
                                budgetManager.getAccumulatedTotalTokens(modelName));
            }
            LOG.debug(metadataInfo);
        }
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        var chatRequest = requestContext.chatRequest();
        var messages = chatRequest.messages();

        LOG.debug("Sending {} messages to the model", messages.size());
        if (messages.size() > 2) {
            // System and user messages were already logged on the first request. Log only the messages which were sent,
            // i.e. every message that follows the last AiMessage (which may be multiple when the model issued
            // several tool calls in one response).
            var lastMessageToSkipIndex = IntStream.range(0, messages.size()).boxed()
                    .sorted(Comparator.reverseOrder())
                    .filter(i -> messages.get(i) instanceof AiMessage)
                    .findFirst()
                    .orElse(1);
            messages.subList(lastMessageToSkipIndex + 1, messages.size()).forEach(ChatModelEventListener::logMessage);
        } else {
            // That's the first request to the model, we log both user and system messages
            messages.forEach(ChatModelEventListener::logMessage);
        }
    }

    private static void logWithSeparator(String typeOfMessage, String content) {
        LOG.debug("{}:\n{}\n{}\n{}", typeOfMessage, MESSAGE_SEPARATOR, content, MESSAGE_SEPARATOR);
    }

    private static void logMessage(ChatMessage chatMessage) {
        switch (chatMessage) {
            case SystemMessage systemMessage -> logWithSeparator("Sending a System Message", systemMessage.text());
            case UserMessage userMessage -> logUserMessage(userMessage);
            case ToolExecutionResultMessage toolResult ->
                    logWithSeparator("Sending results of '%s' tool execution".formatted(toolResult.toolName()), toolResult.text());
            case CustomMessage customMessage -> logWithSeparator("Sending custom message", customMessage.toString());
            default -> {
                // Not logging other message types
            }
        }
    }

    private static void logUserMessage(UserMessage userMessage) {
        userMessage.contents().forEach(content -> {
            if (content instanceof TextContent textContent) {
                logWithSeparator("Sending a User Message with Text", textContent.text());
            } else {
                LOG.debug("Sending a User Message with <{}> content type", content.type());
            }
        });
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        Map<Object, Object> attributes = errorContext.attributes();
        if (error != null) {
            String message = "Chat model invocation failed: %s".formatted(error.toString());
            LOG.error(message, error);
        } else {
            LOG.error("Chat model invocation failed without a propagated error. Error context attributes: {}", attributes);
        }
    }
}