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
