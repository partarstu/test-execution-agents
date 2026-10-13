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
package org.tarik.ta.agents;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.UiTestAgentConfig;
import org.tarik.ta.core.dto.OperationExecutionResult;
import org.tarik.ta.model.ImageContentCreator;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tarik.ta.core.dto.OperationExecutionResult.ExecutionStatus.ERROR;

@ExtendWith(MockitoExtension.class)
class ToolCallingUiElementGrounderTest {
    @Mock
    private UiElementGroundingAgent agent;
    @Mock
    private UiTestAgentConfig config;

    @Test
    void locate_shouldSendTheElementDescriptionVerbatimBeforeTheScreenshot() {
        var screenshot = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var imageContentCreator = mock(ImageContentCreator.class);
        var imageContent = mock(ImageContent.class);
        when(agent.getImageContentCreator(config)).thenReturn(imageContentCreator);
        when(imageContentCreator.createImageContent(screenshot)).thenReturn(imageContent);
        when(agent.executeAndGetResult(any())).thenAnswer(invocation -> {
            invocation.<Supplier<Result<?>>>getArgument(0).get();
            return new OperationExecutionResult<>(ERROR, "no boxes", null);
        });
        var grounder = new ToolCallingUiElementGrounder(agent, config);

        assertThatThrownBy(() -> grounder.locate("The 'Save' button of {{form}}", screenshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Vision model failed to identify bounding boxes: no boxes");
        verify(agent).identifyElementRegions(List.of(TextContent.from("The 'Save' button of {{form}}")), imageContent);
    }
}
