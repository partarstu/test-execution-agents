/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
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
package org.tarik.ta.a2a;

import io.a2a.spec.FilePart;
import io.a2a.spec.FileWithBytes;
import io.a2a.spec.Part;
import org.tarik.ta.UiTestAgent;
import org.tarik.ta.core.a2a.AbstractAgentExecutor;
import org.tarik.ta.core.dto.TestExecutionResult;
import org.tarik.ta.dto.UiTestExecutionResult;
import org.tarik.ta.dto.UiTestStepResult;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;

public class UiAgentExecutor extends AbstractAgentExecutor {
    public static final String SCREENSHOT_FORMAT = "png";

    @Override
    protected TestExecutionResult executeTestCase(String message) {
        return UiTestAgent.executeTestCase(message);
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