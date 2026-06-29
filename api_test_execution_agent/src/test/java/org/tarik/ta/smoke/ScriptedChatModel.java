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

import java.util.List;
import java.util.function.Function;

/**
 * A deterministic, scripted stand-in for the LLM that drives a langchain4j {@code AiServices} agent through a fixed
 * sequence of real tool calls. It speaks the same wire contract as a real {@link ChatModel} (returns
 * {@link AiMessage}s carrying {@link ToolExecutionRequest}s), so the agent's real tool-calling loop, the real tools and
 * the real result-extraction all execute unchanged — only the model's "decisions" are canned.
 * <p>
 * The script for a given agent invocation is chosen from the first user message (which embeds the step / precondition
 * description), so a single shared model instance can serve every step and precondition of a test case. Within one
 * invocation, the call returned on each round is selected by how many tool results are already in the conversation:
 * round {@code 0} is the first action, round {@code 1} the next, and so on. The last scripted call must invoke the
 * agent's final-result tool (which returns immediately), which ends the loop.
 */
class ScriptedChatModel implements ChatModel {

    /** A single scripted decision: the tool to call and its JSON arguments. */
    record ScriptedToolCall(String toolName, String argumentsJson) {
    }

    private final Function<String, List<ScriptedToolCall>> scriptByUserMessage;

    ScriptedChatModel(Function<String, List<ScriptedToolCall>> scriptByUserMessage) {
        this.scriptByUserMessage = scriptByUserMessage;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        String userText = firstUserText(messages);
        List<ScriptedToolCall> script = scriptByUserMessage.apply(userText);
        if (script == null || script.isEmpty()) {
            throw new IllegalStateException("No scripted tool calls were provided for user message: " + userText);
        }
        int round = (int) messages.stream().filter(ToolExecutionResultMessage.class::isInstance).count();
        if (round >= script.size()) {
            throw new IllegalStateException(("Scripted model ran out of tool calls at round %d (script size %d) for user " +
                    "message: %s").formatted(round, script.size(), userText));
        }
        ScriptedToolCall call = script.get(round);
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("scripted-call-" + round)
                .name(call.toolName())
                .arguments(call.argumentsJson())
                .build();
        return ChatResponse.builder().aiMessage(AiMessage.from(toolRequest)).build();
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
