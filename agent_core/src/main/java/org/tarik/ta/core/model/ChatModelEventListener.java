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
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.security.SecretRegistry;
import org.tarik.ta.core.telemetry.GenAiMetrics;
import org.tarik.ta.core.telemetry.TokenUsageRecord;
import org.tarik.ta.core.utils.TestCaseExtractor;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static java.lang.System.nanoTime;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.tarik.ta.core.utils.CommonUtils.isBlank;
import static org.tarik.ta.core.utils.CommonUtils.isNotBlank;

/**
 * Logs the traffic of one sub-agent's model and accounts for what it costs. One instance is created per sub-agent
 * model, so every token and every call duration is attributed to the operation it belongs to.
 */
public class ChatModelEventListener implements ChatModelListener {
    private static final Logger LOG = LoggerFactory.getLogger(ChatModelEventListener.class);
    private static final String MESSAGE_SEPARATOR = "---------------------------------------------------------------------";
    private static final String REQUEST_START_NANOS_ATTRIBUTE = "request.start.nanos";
    private static final String UNKNOWN_MODEL = "Unknown";
    // The test case is extracted before its secret values are known and registered for redaction, so whatever that call
    // sends and receives is never logged.
    private static final Set<String> OPERATIONS_WITHOUT_CONTENT_LOGGING = Set.of(TestCaseExtractor.OPERATION_NAME);

    private final BudgetManager budgetManager;
    private final GenAiMetrics metrics;
    private final String operationName;
    private final String providerName;
    private final String requestModel;
    private final String serverAddress;
    private final boolean contentLoggingEnabled;

    public ChatModelEventListener(@NotNull BudgetManager budgetManager, @NotNull GenAiMetrics metrics,
                                  @NotNull String operationName, @NotNull String providerName,
                                  @NotNull String requestModel, @Nullable String serverAddress) {
        this.budgetManager = budgetManager;
        this.metrics = metrics;
        this.operationName = operationName;
        this.providerName = providerName;
        this.requestModel = requestModel;
        this.serverAddress = serverAddress;
        this.contentLoggingEnabled = !OPERATIONS_WITHOUT_CONTENT_LOGGING.contains(operationName);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var chatResponse = responseContext.chatResponse();
        if (contentLoggingEnabled) {
            logResponseContent(chatResponse);
        }

        long durationMillis = elapsedMillis(responseContext.attributes()).orElse(0L);
        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata == null) {
            LOG.debug("Got a response without metadata from operation '{}', call duration = {} ms", operationName, durationMillis);
            return;
        }
        recordUsage(metadata, durationMillis);
    }

    private static void logResponseContent(ChatResponse chatResponse) {
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
    }

    /**
     * Turns the provider's token counts into the record which feeds the log line, the metrics and - once the task
     * finishes - the usage artifact.
     */
    private void recordUsage(ChatResponseMetadata metadata, long durationMillis) {
        var responseModel = metadata.modelName() != null ? metadata.modelName() : UNKNOWN_MODEL;
        TokenUsage tokenUsage = metadata.tokenUsage();
        if (tokenUsage == null) {
            LOG.debug("Got a response without token usage from operation '{}' (model '{}'), call duration = {} ms",
                    operationName, responseModel, durationMillis);
            return;
        }
        var callUsage = new TokenUsageRecord(operationName, providerName, requestModel, responseModel, serverAddress,
                ofNullable(tokenUsage.inputTokenCount()).orElse(0), ofNullable(tokenUsage.outputTokenCount()).orElse(0),
                CachedTokenReader.cachedTokens(tokenUsage), durationMillis);
        var attributes = callUsage.toAttributes();
        metrics.recordTokenUsage(attributes, callUsage.inputTokens(), callUsage.outputTokens(), callUsage.cachedTokens());
        metrics.recordOperationDuration(attributes, durationMillis);
        var accumulated = budgetManager.consumeTokens(callUsage);
        LOG.debug("Model call finished: {}. Accumulated for this operation: {}", callUsage.toLogLine(), accumulated.toLogLine());
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(REQUEST_START_NANOS_ATTRIBUTE, nanoTime());
        var chatRequest = requestContext.chatRequest();
        var messages = chatRequest.messages();

        LOG.debug("Sending {} messages to the model", messages.size());
        if (!contentLoggingEnabled) {
            return;
        }
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

    /** The duration of the whole non-streamed round trip rather than the time to the first token. */
    private static Optional<Long> elapsedMillis(@NotNull Map<Object, Object> attributes) {
        return ofNullable(attributes.get(REQUEST_START_NANOS_ATTRIBUTE))
                .filter(Long.class::isInstance)
                .map(Long.class::cast)
                .map(startNanos -> NANOSECONDS.toMillis(nanoTime() - startNanos));
    }

    private static void logWithSeparator(String typeOfMessage, String content) {
        // Prompts and tool results are logged verbatim, so a secret that slipped into one is replaced by its placeholder.
        LOG.debug("{}:\n{}\n{}\n{}", typeOfMessage, MESSAGE_SEPARATOR, SecretRegistry.global().redact(content), MESSAGE_SEPARATOR);
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
        var durationInfo = elapsedMillis(attributes)
                .map(" after %d ms"::formatted)
                .orElse("");
        if (error != null) {
            String message = "Chat model invocation failed%s: %s".formatted(durationInfo, error.toString());
            LOG.error(message, error);
        } else {
            LOG.error("Chat model invocation failed{} without a propagated error. Error context attributes: {}", durationInfo, attributes);
        }
    }
}