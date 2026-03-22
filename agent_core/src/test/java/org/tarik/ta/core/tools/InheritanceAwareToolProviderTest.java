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
package org.tarik.ta.core.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import org.tarik.ta.core.dto.FinalResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InheritanceAwareToolProviderTest {

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
        
        // The method from DummyResult should be marked as immediateReturn
        assertThat(result.immediateReturnToolNames()).containsExactly("resultMethod");
    }

    @Test
    void provideTools_shouldWorkWithNoToolObjects() {
        InheritanceAwareToolProvider<DummyResult> provider = new InheritanceAwareToolProvider<>(DummyResult.class);

        ToolProviderRequest request = mock(ToolProviderRequest.class);
        ToolProviderResult result = provider.provideTools(request);

        assertThat(result.tools()).hasSize(1);
        assertThat(result.tools().keySet()).extracting(dev.langchain4j.agent.tool.ToolSpecification::name)
                .containsExactly("resultMethod");
        assertThat(result.immediateReturnToolNames()).containsExactly("resultMethod");
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
        @Tool("Result method")
        public void resultMethod() {
        }
    }
}
