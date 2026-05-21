/*
 * agent-core - ${project.description}
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
