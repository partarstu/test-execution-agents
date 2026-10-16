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
package org.tarik.ta.core.smoke;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A deterministic, scripted stand-in for the LLM that drives a langchain4j {@code AiServices} agent through a fixed
 * sequence of real tool calls. It speaks the same wire contract as a real {@link ChatModel} (returns
 * {@link AiMessage}s carrying {@link ToolExecutionRequest}s), so the agent's real tool-calling loop, the real tools and
 * the real result-extraction all execute unchanged — only the model's "decisions" are canned.
 * <p>
 * A decision is produced by a {@link Script}, which is given the first user message (which embeds the step /
 * precondition description), the current round and the tool results already gathered in this invocation. Because the
 * script sees the prior results it can <em>branch</em> — e.g. react to a failed verification or repeat a call to
 * exercise the retry / error-handling path — rather than only replaying a fixed list. The simple linear case is still
 * available via {@link #ofSequence(Function)}. The last decision of an invocation must invoke the agent's final-result
 * tool, alone or last in a batch, which ends the loop.
 * <p>
 * The model implements {@link #doChat(ChatRequest)} + {@link #listeners()} rather than overriding
 * {@link #chat(ChatRequest)}, so the default {@code chat()} wraps every scripted response with the real listener
 * callbacks. Each response carries a fixed {@link TokenUsage} ({@value #INPUT_TOKENS_PER_RESPONSE} input /
 * {@value #OUTPUT_TOKENS_PER_RESPONSE} output tokens), which lets an attached production
 * {@code ChatModelEventListener} accumulate tokens into the {@code BudgetManager} exactly as with a real model.
 */
public class ScriptedChatModel implements ChatModel {

    public static final String MODEL_NAME = "scripted-model";
    public static final int INPUT_TOKENS_PER_RESPONSE = 7;
    public static final int OUTPUT_TOKENS_PER_RESPONSE = 3;

    /**
     * A single scripted decision: the tool to call, its JSON arguments and the call batched after it in the same response,
     * if any. A decision created via {@link #plainText} carries no tool name and makes the model answer with plain text
     * instead, ending the tool-calling loop the way a real LLM going off-script would.
     */
    public record ScriptedToolCall(String toolName, String argumentsJson, @Nullable ScriptedToolCall batchedCall) {

        public ScriptedToolCall(String toolName, String argumentsJson) {
            this(toolName, argumentsJson, null);
        }

        public static ScriptedToolCall plainText(@NotNull String text) {
            return new ScriptedToolCall(null, text);
        }

        /** Appends {@code next} to this decision's batch, so that the model returns all of them in one response. */
        public ScriptedToolCall batchedWith(@NotNull ScriptedToolCall next) {
            return new ScriptedToolCall(toolName, argumentsJson, batchedCall == null ? next : batchedCall.batchedWith(next));
        }

        public boolean isPlainText() {
            return toolName == null;
        }
    }

    /**
     * Chooses the next tool call for the current agent invocation. Unlike a fixed list, an implementation can branch on
     * the round index and on the tool results already produced in this invocation.
     */
    @FunctionalInterface
    public interface Script {
        ScriptedToolCall nextCall(@NotNull String userText, int round, @NotNull List<ToolExecutionResultMessage> priorResults);
    }

    private final Script script;
    private final List<ChatModelListener> listeners;

    public ScriptedChatModel(@NotNull Script script) {
        this(script, List.of());
    }

    public ScriptedChatModel(@NotNull Script script, @NotNull List<ChatModelListener> listeners) {
        this.script = script;
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        String userText = firstUserText(messages);
        List<ToolExecutionResultMessage> priorResults = messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
        int round = priorResults.size();
        ScriptedToolCall call = script.nextCall(userText, round, priorResults);
        if (call == null) {
            throw new IllegalStateException("Scripted model produced no tool call at round %d for user message: %s"
                    .formatted(round, userText));
        }
        if (call.isPlainText()) {
            return response(AiMessage.from(call.argumentsJson()));
        }
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();
        for (var batchedCall = call; batchedCall != null; batchedCall = batchedCall.batchedCall()) {
            toolRequests.add(ToolExecutionRequest.builder()
                    .id("scripted-call-%d".formatted(round + toolRequests.size()))
                    .name(batchedCall.toolName())
                    .arguments(batchedCall.argumentsJson())
                    .build());
        }
        return response(AiMessage.from(toolRequests));
    }

    private static ChatResponse response(@NotNull AiMessage aiMessage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .modelName(MODEL_NAME)
                .tokenUsage(new TokenUsage(INPUT_TOKENS_PER_RESPONSE, OUTPUT_TOKENS_PER_RESPONSE))
                .build();
    }

    /**
     * Adapts a simple per-user-message sequence of calls (indexed by round) into a {@link Script}, for linear scripts
     * that don't need to branch. The sequence chosen for an invocation is keyed by its first user message, so one shared
     * model instance can serve every step and precondition of a test case.
     */
    public static Script ofSequence(@NotNull Function<String, List<ScriptedToolCall>> sequenceByUserMessage) {
        return (userText, round, priorResults) -> {
            List<ScriptedToolCall> sequence = sequenceByUserMessage.apply(userText);
            if (sequence == null || sequence.isEmpty()) {
                throw new IllegalStateException("No scripted tool calls were provided for user message: %s".formatted(userText));
            }
            if (round >= sequence.size()) {
                throw new IllegalStateException(("Scripted model ran out of tool calls at round %d (sequence size %d) for " +
                        "user message: %s").formatted(round, sequence.size(), userText));
            }
            return sequence.get(round);
        };
    }

    private static String firstUserText(List<ChatMessage> messages) {
        return messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::singleText)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No user message found in the chat request"));
    }
}
