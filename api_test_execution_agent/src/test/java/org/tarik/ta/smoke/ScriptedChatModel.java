/*
 * api-test-execution-agent - Agent specializing in execution of API tests.
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
package org.tarik.ta.smoke;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.jetbrains.annotations.NotNull;

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
 * tool (which returns immediately), which ends the loop.
 */
class ScriptedChatModel implements ChatModel {

    /** A single scripted decision: the tool to call and its JSON arguments. */
    record ScriptedToolCall(String toolName, String argumentsJson) {
    }

    /**
     * Chooses the next tool call for the current agent invocation. Unlike a fixed list, an implementation can branch on
     * the round index and on the tool results already produced in this invocation.
     */
    @FunctionalInterface
    interface Script {
        ScriptedToolCall nextCall(@NotNull String userText, int round, @NotNull List<ToolExecutionResultMessage> priorResults);
    }

    private final Script script;

    ScriptedChatModel(@NotNull Script script) {
        this.script = script;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
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
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("scripted-call-%d".formatted(round))
                .name(call.toolName())
                .arguments(call.argumentsJson())
                .build();
        return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest)).build();
    }

    /**
     * Adapts a simple per-user-message sequence of calls (indexed by round) into a {@link Script}, for linear scripts
     * that don't need to branch. The sequence chosen for an invocation is keyed by its first user message, so one shared
     * model instance can serve every step and precondition of a test case.
     */
    static Script ofSequence(@NotNull Function<String, List<ScriptedToolCall>> sequenceByUserMessage) {
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
