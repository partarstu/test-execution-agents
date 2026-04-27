/*
 * Copyright © 2026 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.core.model;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.AgentConfig;
import org.tarik.ta.core.manager.BudgetManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class ModelFactoryTest {

    private final AgentConfig agentConfig = new AgentConfig();
    private final BudgetManager budgetManager = new BudgetManager(agentConfig);
    private final ChatModelEventListener chatModelEventListener = new ChatModelEventListener(budgetManager);
    private final ModelFactory modelFactory = new ModelFactory(agentConfig, chatModelEventListener);

    @Test
    void shouldGetGoogleModel() {
        GenAiModel model = modelFactory.getModel("gemini-pro", AgentConfig.ModelProvider.GOOGLE);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetOpenAiModel() {
        GenAiModel model = modelFactory.getModel("gpt-5.4", AgentConfig.ModelProvider.OPENAI);
        assertThat(model).isNotNull();
        assertThat(model.chatModel()).isNotNull();
    }

    @Test
    void shouldGetGroqModel() {
        GenAiModel model = modelFactory.getModel("mixtral-8x7b", AgentConfig.ModelProvider.GROQ);
        assertThat(model).isNotNull();
    }

    @Test
    void shouldGetAnthropicModel() {
        GenAiModel model = modelFactory.getModel("claude-3", AgentConfig.ModelProvider.ANTHROPIC);
        assertThat(model).isNotNull();
    }

    @Test
    void genAiModel_shouldCloseIfCloseable() throws Exception {
        ChatModel chatModel = mock(ChatModel.class, withSettings().extraInterfaces(AutoCloseable.class));
        GenAiModel genAiModel = new GenAiModel(chatModel);

        genAiModel.close();

        verify((AutoCloseable) chatModel).close();
    }

    @Test
    void genAiModel_shouldNotFailIfNonCloseable() {
        ChatModel chatModel = mock(ChatModel.class);
        GenAiModel genAiModel = new GenAiModel(chatModel);

        genAiModel.close();
        // Should not throw anything
    }
}
