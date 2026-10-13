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
package org.tarik.ta.core.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.EmptyExecutionResult;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.exceptions.BudgetExceededException;
import org.tarik.ta.core.manager.BudgetManager;
import org.tarik.ta.core.model.DefaultToolErrorHandler;
import org.tarik.ta.core.telemetry.TokenUsageRecord;

import java.util.List;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.agent.tool.ReturnBehavior.TO_LLM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class InheritanceAwareToolProviderTest {

    @AfterEach
    void resetBudget() {
        BudgetManager.getInstance().reset();
    }

    @Test
    void constructor_shouldThrowOnNullResultClass() {
        assertThatThrownBy(() -> new InheritanceAwareToolProvider<>(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultClass must not be null");

        assertThatThrownBy(() -> new InheritanceAwareToolProvider<>(List.of(new BaseTool()), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultClass must not be null");
    }

    @Test
    void provideTools_shouldFindToolsInObjectAndResultClass() {
        InheritanceAwareToolProvider<DummyResult> provider = new InheritanceAwareToolProvider<>(
                List.of(new ChildTool()), DummyResult.class);

        ToolProviderRequest request = mock(ToolProviderRequest.class);
        ToolProviderResult result = provider.provideTools(request);

        assertThat(result.tools()).hasSize(3);
        assertThat(result.tools().keySet()).extracting(dev.langchain4j.agent.tool.ToolSpecification::name)
                .containsExactlyInAnyOrder("baseMethod", "childMethod", "resultMethod");
        assertThat(result.aiServiceTools()).extracting(AiServiceTool::name, AiServiceTool::returnBehavior)
                .containsExactlyInAnyOrder(tuple("baseMethod", TO_LLM), tuple("childMethod", TO_LLM),
                        tuple("resultMethod", IMMEDIATE_IF_LAST));
    }

    @Test
    void provideTools_shouldWorkWithNoToolObjects() {
        InheritanceAwareToolProvider<DummyResult> provider = new InheritanceAwareToolProvider<>(DummyResult.class);

        ToolProviderRequest request = mock(ToolProviderRequest.class);
        ToolProviderResult result = provider.provideTools(request);

        assertThat(result.tools()).hasSize(1);
        assertThat(result.tools().keySet()).extracting(dev.langchain4j.agent.tool.ToolSpecification::name)
                .containsExactly("resultMethod");
        assertThat(result.aiServiceTools()).extracting(AiServiceTool::returnBehavior).containsExactly(IMMEDIATE_IF_LAST);
    }

    @Test
    void provideTools_shouldEndLoopWithoutAnotherModelTurn_whenBatchEndsWithFinalResultTool() {
        var model = new SingleBatchChatModel();
        var agent = AiServices.builder(ResultAgent.class)
                .chatModel(model)
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(new ChildTool()), EmptyExecutionResult.class))
                .build();

        Result<String> result = agent.run("act");

        assertThat(model.calls).isEqualTo(1);
        assertThat(result.toolExecutions()).extracting(execution -> execution.request().name())
                .containsExactly("childMethod", "endExecutionAndGetFinalResult");
        assertThat(result.toolExecutions().getLast().resultObject()).isEqualTo(new EmptyExecutionResult(true, "done"));
    }

    @Test
    void provideTools_shouldStopTheInvocationBeforeTheTool_whenTheTokenBudgetIsExhaustedMidInvocation() {
        BudgetManager.getInstance().consumeTokens(new TokenUsageRecord("test_step_action", "openai", "gpt-test", "gpt-test", null,
                2_000_000, 0, 0, 0));
        var tool = new CountingChildTool();
        var agent = AiServices.builder(ResultAgent.class)
                .chatModel(new SingleBatchChatModel())
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(tool), EmptyExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(new RetryPolicy(1, 0, 1000)))
                .build();

        assertThatThrownBy(() -> agent.run("act"))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageStartingWith("Token budget exceeded");
        assertThat(tool.calls).isZero();
    }

    @Test
    void provideTools_shouldKeepTheFinalResult_whenTheTokenBudgetIsExhaustedBeforeTheFinalResultTool() {
        BudgetManager.getInstance().consumeTokens(new TokenUsageRecord("test_step_action", "openai", "gpt-test", "gpt-test", null,
                2_000_000, 0, 0, 0));
        var agent = AiServices.builder(ResultAgent.class)
                .chatModel(new FinalResultOnlyChatModel())
                .toolProvider(new InheritanceAwareToolProvider<>(List.of(new CountingChildTool()), EmptyExecutionResult.class))
                .toolExecutionErrorHandler(new DefaultToolErrorHandler(new RetryPolicy(1, 0, 1000)))
                .build();

        Result<String> result = agent.run("act");

        assertThat(result.toolExecutions()).singleElement()
                .satisfies(execution -> assertThat(execution.resultObject()).isEqualTo(new EmptyExecutionResult(true, "done")));
    }

    interface ResultAgent {
        Result<String> run(String input);
    }

    static class CountingChildTool {
        int calls;

        @Tool("Child method")
        public void childMethod() {
            calls++;
        }
    }

    static class SingleBatchChatModel implements ChatModel {
        int calls;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            calls++;
            if (calls > 1) {
                return ChatResponse.builder().aiMessage(AiMessage.from("wrap-up turn")).build();
            }
            var action = ToolExecutionRequest.builder().id("1").name("childMethod").arguments("{}").build();
            var end = ToolExecutionRequest.builder().id("2").name("endExecutionAndGetFinalResult")
                    .arguments("{\"result\": {\"executionSuccess\": true, \"message\": \"done\"}}").build();
            return ChatResponse.builder().aiMessage(AiMessage.from(List.of(action, end))).build();
        }
    }

    static class FinalResultOnlyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            var end = ToolExecutionRequest.builder().id("1").name("endExecutionAndGetFinalResult")
                    .arguments("{\"result\": {\"executionSuccess\": true, \"message\": \"done\"}}").build();
            return ChatResponse.builder().aiMessage(AiMessage.from(List.of(end))).build();
        }
    }

    static class BaseTool {
        @Tool("Base method")
        public void baseMethod() {
        }

        public void notAToolBase() {
        }
    }

    static class ChildTool extends BaseTool {
        @Tool("Child method")
        public void childMethod() {
        }

        public void notAToolChild() {
        }
    }

    static class DummyResult implements FinalResult {
        @Tool(value = "Result method", returnBehavior = IMMEDIATE_IF_LAST)
        public void resultMethod() {
        }
    }
}
