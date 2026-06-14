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
package org.tarik.ta.a2a;

import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import jakarta.inject.Singleton;
import org.tarik.ta.UiAgentRequestScopeFactory;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.a2a.StreamingEventEmitter;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.core.dto.TestStepResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.model.VisualState;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;
import static org.tarik.ta.utils.UiCommonUtils.captureScreen;

@Singleton
public class UiAgentExecutor extends AbstractAgentExecutor {
    public static final String SCREENSHOT_FORMAT = "png";
    private final UiAgentRequestScopeFactory requestScopeFactory;

    public UiAgentExecutor(UiAgentRequestScopeFactory requestScopeFactory) {
        this.requestScopeFactory = requestScopeFactory;
    }

    @Override
    protected TestExecutionResult executeTestCase(String message, StreamingEventEmitter eventEmitter) {
        try (var requestScope = requestScopeFactory.create(new VisualState(captureScreen()), eventEmitter)) {
            return requestScope.get(UiTestAgent.class).executeTestCase(message);
        }
    }

    @Override
    protected List<Part<?>> buildStepArtifactParts(TestStepResult stepResult) {
        if (stepResult instanceof UiTestStepResult uiStepResult && uiStepResult.getScreenshot() != null) {
            String fileName = "screenshot_for_the_test_step_%s.%s".formatted(
                    uiStepResult.getTestStep().stepDescription().replaceAll("\\s", "_").toLowerCase(), SCREENSHOT_FORMAT);
            return List.of(buildScreenshotPart(uiStepResult.getScreenshot(), fileName));
        }
        return List.of();
    }

    @Override
    protected List<Part<?>> buildFinalArtifactParts(TestExecutionResult result) {
        // Per-step screenshots are already streamed with their step result artifacts and accumulated into the task, so
        // the final artifact only adds the end-of-test general screenshot to avoid transferring every screenshot twice.
        if (result instanceof UiTestExecutionResult uiResult && uiResult.getScreenshot() != null) {
            String fileName = "general_screenshot_for_the_test_case_%s.%s".formatted(
                    result.getTestCaseName().replaceAll("\\s", "_").toLowerCase(), SCREENSHOT_FORMAT);
            return List.of(buildScreenshotPart(uiResult.getScreenshot(), fileName));
        }
        return List.of();
    }

    private static Part<?> buildScreenshotPart(BufferedImage screenshot, String fileName) {
        return new FilePart(new FileWithBytes(
                "image/" + SCREENSHOT_FORMAT, fileName, convertImageToBase64(screenshot, SCREENSHOT_FORMAT)));
    }
}
