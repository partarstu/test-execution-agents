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
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;
import org.tarik.ta.model.VisualState;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
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
    protected TestExecutionResult executeTestCase(String message) {
        try (var requestScope = requestScopeFactory.create(new VisualState(captureScreen()))) {
            return requestScope.get(UiTestAgent.class).executeTestCase(message);
        }
    }

    @Override
    protected void addSpecificArtifacts(TestExecutionResult result, List<Part<?>> parts) {
        result.getStepResults().stream()
                .filter(UiTestStepResult.class::isInstance)
                .map(UiTestStepResult.class::cast)
                .filter(r -> r.getScreenshot() != null)
                .map(r -> new FileWithBytes(
                        "image/" + SCREENSHOT_FORMAT,
                        "screenshot_for_the_test_step_%s.%s".formatted(
                                r.getTestStep().stepDescription().replaceAll("\\s", "_").toLowerCase(), SCREENSHOT_FORMAT),
                        convertImageToBase64(r.getScreenshot(), SCREENSHOT_FORMAT)))
                .map(FilePart::new)
                .forEach(parts::add);

        if (result instanceof UiTestExecutionResult uiResult) {
            ofNullable(uiResult.getScreenshot())
                    .ifPresent(screenshot -> parts.add(new FilePart(new FileWithBytes(
                            "image/" + SCREENSHOT_FORMAT,
                            "general_screenshot_for_the_test_case_%s.%s".formatted(
                                    result.getTestCaseName().replaceAll("\\s", "_").toLowerCase(), SCREENSHOT_FORMAT),
                            convertImageToBase64(screenshot, SCREENSHOT_FORMAT)))));
        }
    }

    @Override
    protected Optional<List<String>> extractLogs(TestExecutionResult result) {
        return ofNullable(result.getLogs());
    }
}
