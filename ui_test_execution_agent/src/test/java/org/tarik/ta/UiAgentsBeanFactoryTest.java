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
package org.tarik.ta;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.error.RetryPolicy;
import org.tarik.ta.core.model.GenAiModel;
import org.tarik.ta.core.model.ModelFactory;
import org.tarik.ta.core.tools.TestContextDataTools;
import org.tarik.ta.dto.UiElementDescription;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.ElementLocatorTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;
import org.tarik.ta.tools.SpinnerTools;
import org.tarik.ta.tools.UiElementDbTools;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiAgentsBeanFactoryTest {

    @Test
    void resolveMaxToolCallingRoundTrips_shouldReturnConfiguredBudget_whenFullyUnattended() {
        var config = mock(UiTestAgentConfig.class);
        when(config.isFullyUnattended()).thenReturn(true);
        when(config.getAgentToolCallsBudget()).thenReturn(10);
        var factory = new UiAgentsBeanFactory(mock(ModelFactory.class), config);

        assertThat(factory.resolveMaxToolCallingRoundTrips()).isEqualTo(10);
    }

    @Test
    void resolveMaxToolCallingRoundTrips_shouldReturnLangChain4jDefault_whenSupervised() {
        var config = mock(UiTestAgentConfig.class);
        when(config.isFullyUnattended()).thenReturn(false);
        var factory = new UiAgentsBeanFactory(mock(ModelFactory.class), config);

        assertThat(factory.resolveMaxToolCallingRoundTrips()).isEqualTo(100);
    }

    @Test
    void beanMethods_shouldBuildAgents_withResolvedRoundTripLimit() {
        var config = mock(UiTestAgentConfig.class);
        when(config.isFullyUnattended()).thenReturn(false);
        when(config.isHdrCorrectionEnabled()).thenReturn(false);
        when(config.getActionRetryPolicy()).thenReturn(mock(RetryPolicy.class));
        when(config.getVerificationRetryPolicy()).thenReturn(mock(RetryPolicy.class));
        when(config.getKnowledgeCollectionElementResolutionAgentPromptVersion()).thenReturn("v1.0.0");
        when(config.getUiStateCheckAgentPromptVersion()).thenReturn("v1.0.0");
        when(config.getTestStepVerificationAgentPromptVersion()).thenReturn("v1.0.0");
        when(config.getPreconditionVerificationAgentPromptVersion()).thenReturn("v1.0.0");
        when(config.getTestStepActionAgentPromptVersion()).thenReturn("v1.0.0");
        when(config.getPreconditionAgentPromptVersion()).thenReturn("v1.0.0");
        var modelFactory = mock(ModelFactory.class);
        when(modelFactory.getModel(any(), any(), any())).thenReturn(new GenAiModel(mock(ChatModel.class)));
        when(modelFactory.getModel(any(), any(), any(), anyInt())).thenReturn(new GenAiModel(mock(ChatModel.class)));
        var factory = new UiAgentsBeanFactory(modelFactory, config);

        assertThat(factory.getKnowledgeCollectionElementResolutionAgent(
                mock(ElementLocatorTools.class), mock(UiElementDbTools.class), mock(SpinnerTools.class))).isNotNull();
        assertThat(factory.getUiStateCheckAgent()).isNotNull();
        assertThat(factory.getTestStepVerificationAgent()).isNotNull();
        assertThat(factory.getPreconditionVerificationAgent()).isNotNull();
        assertThat(factory.getUiTestStepActionAgent(
                mock(CommonTools.class), mock(MouseTools.class), mock(KeyboardTools.class),
                mock(ElementLocatorTools.class), mock(TestContextDataTools.class))).isNotNull();
        assertThat(factory.getPreconditionActionAgent(
                mock(CommonTools.class), mock(MouseTools.class), mock(KeyboardTools.class),
                mock(ElementLocatorTools.class), mock(TestContextDataTools.class))).isNotNull();
    }

    @Test
    void uiElementExtendedDescriptionAgent_shouldFeedMalformedResultArgumentsBackToTheModel() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(2, 0, 10_000));
        when(config.getUiElementDescriptionMatcherAgentPromptVersion()).thenReturn("v1.0.0");
        var feedbackSentToModel = new AtomicReference<String>();
        ChatModel scriptedModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                if (request.messages().getLast() instanceof ToolExecutionResultMessage feedback) {
                    feedbackSentToModel.set(feedback.text());
                    return finalResultCall(VALID_RESULT_ARGUMENTS);
                }
                return finalResultCall(MALFORMED_RESULT_ARGUMENTS);
            }
        };
        var modelFactory = mock(ModelFactory.class);
        when(modelFactory.getModel(any(), any(), any())).thenReturn(new GenAiModel(scriptedModel));
        var agent = new UiAgentsBeanFactory(modelFactory, config).getUiElementExtendedDescriptionAgent();

        var result = agent.executeAndGetResult(() -> agent.describeUiElement(List.of(TextContent.from("temperature cell")),
                ImageContent.from("aW1hZ2U=", "image/png")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResultPayload()).isEqualTo(new UiElementIdentificationResult(true, false,
                new UiElementDescription("temperature cell", "Yellow cell", "Cellar row", "Status history panel", true)));
        assertThat(feedbackSentToModel.get()).startsWith("The arguments of this tool call could not be parsed: Unexpected end-of-input");
    }

    @Test
    void uiElementExtendedDescriptionAgent_shouldSendTheUserMessageVerbatimBeforeTheScreenshot_whenItContainsTemplatePlaceholders() {
        var config = mock(UiTestAgentConfig.class);
        when(config.getActionRetryPolicy()).thenReturn(new RetryPolicy(2, 0, 10_000));
        when(config.getUiElementDescriptionMatcherAgentPromptVersion()).thenReturn("v1.0.0");
        var userMessageSentToModel = new AtomicReference<UserMessage>();
        ChatModel scriptedModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                request.messages().stream().filter(UserMessage.class::isInstance).map(UserMessage.class::cast).findFirst()
                        .ifPresent(userMessageSentToModel::set);
                return finalResultCall(VALID_RESULT_ARGUMENTS);
            }
        };
        var modelFactory = mock(ModelFactory.class);
        when(modelFactory.getModel(any(), any(), any())).thenReturn(new GenAiModel(scriptedModel));
        var agent = new UiAgentsBeanFactory(modelFactory, config).getUiElementExtendedDescriptionAgent();
        var text = TextContent.from("## Relevant data context\nRoom = {{room}}, password = {{secret:password}}");
        var screenshot = ImageContent.from("aW1hZ2U=", "image/png");

        var result = agent.executeAndGetResult(() -> agent.describeUiElement(List.of(text), screenshot));

        assertThat(result.isSuccess()).isTrue();
        assertThat(userMessageSentToModel.get().contents()).containsExactly(text, screenshot);
    }

    // What a self-hosted model sent through a vLLM tool parser: the result object as a string, wrapped once more and missing a brace.
    private static final String MALFORMED_RESULT_ARGUMENTS = """
            {"result": "{\\"result\\": {\\"targetElementIdentified\\": true, \\"multipleElementsIdentified\\": false, \
            \\"elementDescription\\": {\\"name\\": \\"temperature cell\\", \\"ownDescription\\": \\"Yellow cell\\", \
            \\"locationDescription\\": \\"Cellar row\\", \\"parentSummary\\": \\"Status history panel\\", \
            \\"elementIsDataDependent\\": true}}"}""";
    private static final String VALID_RESULT_ARGUMENTS = """
            {"result": {"targetElementIdentified": true, "multipleElementsIdentified": false, "elementDescription": \
            {"name": "temperature cell", "ownDescription": "Yellow cell", "locationDescription": "Cellar row", \
            "parentSummary": "Status history panel", "elementIsDataDependent": true}}}""";

    private static ChatResponse finalResultCall(String arguments) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("call")
                        .name("endExecutionAndGetFinalResult")
                        .arguments(arguments)
                        .build()))
                .build();
    }
}
